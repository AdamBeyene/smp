# SMPP Simulator - Detailed Fix Plan

**Reference**: See FIX_PLAN_SUMMARY.md for quick overview  
**Total Time**: 10-12 hours  
**Excluded**: Encoding validation (deferred)

---

## Phase 1: Critical Bug Fixes (1h 10min) ⚡

### Fix 1.1: Direct Response Filter Bug (5min)
**File**: `MessageController.java`  
**Lines**: 414, 526  
**Change**: `StringUtils.isEmpty` → `StringUtils.isNotEmpty`

**Before**:
```java
.filter(msg -> StringUtils.isEmpty(msg.getDirectResponse()) && ...)
```

**After**:
```java
.filter(msg -> StringUtils.isNotEmpty(msg.getDirectResponse()) && ...)
```

---

### Fix 1.2: Race Condition (15min)
**File**: `MessagesCache.java`  
**Lines**: 98-122

**Add field**:
```java
private final AtomicBoolean dirty = new AtomicBoolean(false);
```

**Replace method**:
```java
public boolean addCacheRecord(String id, MessagesObject obj) {
    map.compute(id, (key, current) -> {
        if (current != null) {
            return updateCacheRecord(current, obj);
        } else {
            return createNewMessageObject(obj);
        }
    });
    dirty.set(true);
    return true;
}
```

---

### Fix 1.3: Delete Missing Write (10min)
**File**: `MessagesCache.java`  
**Lines**: 363-370

**Replace method**:
```java
public boolean deleteMessageRecordById(String id) {
    MessagesObject removed = map.remove(id);
    if (removed != null) {
        log.info("MessagesObject cache removed: " + removed);
        dirty.set(true);
        return true;
    }
    return false;
}
```

---

### Fix 1.4: Batch Delete (10min)
**File**: `MessageController.java`  
**Lines**: 530-533

**Add to MessagesCache.java**:
```java
public void setDirty(boolean value) {
    dirty.set(value);
}
```

**Replace in MessageController.java**:
```java
messages.forEach(msg -> cacheService.getMap().remove(msg.getId()));
if (!messages.isEmpty()) {
    cacheService.setDirty(true);
    log.info("Deleted {} messages", messages.size());
}
```

---

### Fix 1.5: Clear Cache (5min)
**File**: `MessagesCache.java`  
**Lines**: 388-401

**Replace method**:
```java
public boolean clearCache() {
    map.clear();
    dirty.set(false);
    try {
        writeMapToJson(map);
        log.info("Cache cleared successfully");
        return true;
    } catch (Exception e) {
        log.error("Failed to clear cache", e);
        return false;
    }
}
```

---

### Fix 1.6: Batch Add (15min)
**File**: `MessagesCache.java`  
**Lines**: 93-96

**Replace method**:
```java
public boolean addCacheRecords(List<MessagesObject> objs) {
    if (objs == null || objs.isEmpty()) return true;
    
    objs.forEach(obj -> {
        map.compute(obj.getId(), (key, current) -> 
            current != null ? updateCacheRecord(current, obj) : createNewMessageObject(obj)
        );
    });
    
    dirty.set(true);
    log.info("Added {} messages in batch", objs.size());
    return true;
}
```

---

### Fix 1.7: Update Fields (10min)
**File**: `MessagesCache.java`  
**Lines**: 132-190

Uncomment all field updates in `updateMessageFields()` method.

---

## Phase 2: Performance (3-4h) 🚀

### Fix 2.1: Batch Writes + TTL Cleanup (2h)
**File**: `MessagesCache.java`

**Add imports**:
```java
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
```

**Add constants**:
```java
private static final long FLUSH_INTERVAL_SECONDS = 5;
private static final long CLEANUP_INTERVAL_HOURS = 1;
private static final long MESSAGE_TTL_HOURS = 24;
private static final int MAX_CACHE_SIZE = 100000;
```

**Add @PostConstruct method**:
```java
@PostConstruct
public void startScheduledTasks() {
    // Flush every 5 seconds
    scheduler.scheduleAtFixedRate(() -> {
        try {
            if (dirty.getAndSet(false)) {
                writeMapToJson(map);
                log.debug("Cache flushed. Size: {}", map.size());
            }
        } catch (Exception e) {
            log.error("Flush error", e);
            dirty.set(true);
        }
    }, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    
    // Cleanup every hour
    scheduler.scheduleAtFixedRate(() -> {
        try {
            cleanupExpiredMessages();
        } catch (Exception e) {
            log.error("Cleanup error", e);
        }
    }, CLEANUP_INTERVAL_HOURS, CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);
    
    log.info("Started: flush every {}s, cleanup every {}h", 
        FLUSH_INTERVAL_SECONDS, CLEANUP_INTERVAL_HOURS);
}
```

**Add @PreDestroy method**:
```java
@PreDestroy
public void shutdown() {
    log.info("Shutting down cache...");
    if (dirty.get()) {
        try {
            writeMapToJson(map);
            log.info("Final flush complete");
        } catch (Exception e) {
            log.error("Final flush error", e);
        }
    }
    scheduler.shutdown();
    try {
        if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
        }
    } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

**Add cleanup method**:
```java
private void cleanupExpiredMessages() {
    long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(MESSAGE_TTL_HOURS);
    int initial = map.size();
    
    map.entrySet().removeIf(e -> e.getValue().getSimId() < cutoff);
    
    if (map.size() > MAX_CACHE_SIZE) {
        int toRemove = map.size() - MAX_CACHE_SIZE;
        List<String> oldest = map.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> e.getValue().getSimId()))
            .limit(toRemove)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        oldest.forEach(map::remove);
    }
    
    int removed = initial - map.size();
    if (removed > 0) {
        log.info("Cleaned {} messages. Remaining: {}", removed, map.size());
        dirty.set(true);
    }
}
```

---

### Fix 2.3: Optimize Parallel Streams (30min)
**File**: `MessagesCache.java`  
**Lines**: 479-501

**Add constant**:
```java
private static final int PARALLEL_THRESHOLD = 1000;
```

**Update methods**:
```java
public List<MessagesObject> getMessagesByID(String id) {
    if (map == null || map.isEmpty()) return Collections.emptyList();
    
    Stream<MessagesObject> stream = map.size() > PARALLEL_THRESHOLD 
        ? map.values().parallelStream() 
        : map.values().stream();
    
    return stream.filter(m -> Objects.equals(m.getId(), id)).collect(Collectors.toList());
}

public List<MessagesObject> getMessagesByText(String searchText) {
    if (map == null || map.isEmpty()) return Collections.emptyList();
    
    Stream<MessagesObject> stream = map.size() > PARALLEL_THRESHOLD 
        ? map.values().parallelStream() 
        : map.values().stream();
    
    return stream.filter(m -> 
        contains(m.getText(), searchText) ||
        contains(m.getSendMessageSM(), searchText) ||
        contains(m.getHttpMessage(), searchText)
    ).collect(Collectors.toList());
}
```

---

## Phase 3: Pagination (4h) 📄

### Fix 3.1: Main Endpoint Pagination (1h)
**File**: `MessageController.java`  
**Lines**: 50-56

**Replace method**:
```java
@GetMapping("/messages")
public String getAllMessages(
        Model model,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size,
        @RequestParam(defaultValue = "simId") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir) {
    
    List<MessagesObject> all = new ArrayList<>(cacheService.getMap().values());
    
    // Sort
    Comparator<MessagesObject> comp = Comparator.comparingLong(MessagesObject::getSimId);
    if ("desc".equalsIgnoreCase(sortDir)) comp = comp.reversed();
    all.sort(comp);
    
    // Paginate
    int total = all.size();
    int totalPages = (total + size - 1) / size;
    int start = page * size;
    int end = Math.min(start + size, total);
    List<MessagesObject> pageData = start < total ? all.subList(start, end) : Collections.emptyList();
    
    model.addAttribute("data", pageData);
    model.addAttribute("currentPage", page);
    model.addAttribute("pageSize", size);
    model.addAttribute("totalPages", totalPages);
    model.addAttribute("totalMessages", total);
    
    return "pages/messages";
}
```

---

### Fix 3.2: Update HTML Template (1h)
**File**: `messages.html`

Add after table:
```html
<div class="server-pagination" th:if="${totalPages > 1}">
    <span>Page <span th:text="${currentPage + 1}"></span> of <span th:text="${totalPages}"></span></span>
    <a th:href="@{/messages(page=0, size=${pageSize})}" th:classappend="${currentPage == 0} ? 'disabled'">First</a>
    <a th:href="@{/messages(page=${currentPage - 1}, size=${pageSize})}" th:classappend="${currentPage == 0} ? 'disabled'">Prev</a>
    <a th:href="@{/messages(page=${currentPage + 1}, size=${pageSize})}" th:classappend="${currentPage >= totalPages - 1} ? 'disabled'">Next</a>
    <a th:href="@{/messages(page=${totalPages - 1}, size=${pageSize})}" th:classappend="${currentPage >= totalPages - 1} ? 'disabled'">Last</a>
</div>
```

---

### Fix 3.3: Filter API Pagination (2h)
**File**: `MessageController.java`

Add to all filter endpoints:
```java
@GetMapping("/sim/messages/advanced-search")
@ResponseBody
public ResponseEntity<Map<String, Object>> advancedMessageSearch(
        /* existing params */,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size) {
    
    // ... existing filter logic ...
    
    // Paginate
    int total = messages.size();
    int start = page * size;
    int end = Math.min(start + size, total);
    List<MessagesObject> pageData = start < total ? messages.subList(start, end) : Collections.emptyList();
    
    Map<String, Object> response = new HashMap<>();
    response.put("data", pageData);
    response.put("page", page);
    response.put("size", size);
    response.put("totalPages", (total + size - 1) / size);
    response.put("totalMessages", total);
    
    return ResponseEntity.ok(response);
}
```

Apply to all filter endpoints.

---

### Fix 3.4: Update JavaScript (30min)
**File**: `messages-actions.js`

Update API handlers:
```javascript
function applyAdvancedSearch(params) {
    params.page = params.page || 0;
    params.size = params.size || 100;
    
    fetch(`/sim/messages/advanced-search?${new URLSearchParams(params)}`)
        .then(r => r.json())
        .then(data => {
            filteredData = data.data || data;
            if (data.totalPages) {
                updateServerPaginationInfo(data);
            }
            renderTable();
        });
}
```

---

## Testing Checklist

- [ ] Fix 1.1: Direct response filter returns results
- [ ] Fix 1.2: No data loss under concurrent load
- [ ] Fix 1.3: Deletes persist after restart
- [ ] Fix 2.1: File writes ~1 per 5 seconds
- [ ] Fix 2.1: Old messages cleaned up after 24h
- [ ] Fix 3.1: Page loads fast with 100k messages

---

## Deployment Steps

1. Backup cache file
2. Apply Phase 1 fixes
3. Test critical bugs
4. Apply Phase 2 fixes
5. Load test 10k messages
6. Apply Phase 3 fixes
7. Validate UI pagination

---

## Success Metrics

**Before**: 100+ writes/sec, 50s page load, memory leak  
**After**: 1 write/5sec, <2s page load, stable memory

**Improvement**: 100x faster writes, 25x faster UI
