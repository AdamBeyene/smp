# Fix Plan - Quick Reference

**Total Time**: 10-12 hours  
**Excluded**: Encoding validation (deferred)

---

## Phase 1: Critical Bugs (1h 10min) ⚡

| # | Issue | File | Time | Impact |
|---|-------|------|------|--------|
| 1.1 | Direct response filter bug | MessageController.java:414,526 | 5min | Feature works |
| 1.2 | Race condition | MessagesCache.java:98-122 | 15min | No data loss |
| 1.3 | Delete missing write | MessagesCache.java:363-370 | 10min | Persist deletes |
| 1.4 | Batch delete | MessageController.java:530-533 | 10min | Faster deletes |
| 1.5 | Clear cache | MessagesCache.java:388-401 | 5min | Proper cleanup |
| 1.6 | Batch add | MessagesCache.java:93-96 | 15min | No n writes |
| 1.7 | Update fields | MessagesCache.java:124-190 | 10min | Merge updates |

**Quick Wins** (20 min):
- Fix 1.1: Change `isEmpty` to `isNotEmpty` (2 places)
- Fix 1.2: Use `map.compute()` instead of get/put
- Fix 1.3: Add `dirty.set(true)` after delete

---

## Phase 2: Performance (3-4h) 🚀

| # | Issue | Time | Impact |
|---|-------|------|--------|
| 2.1 | Batch writes + periodic flush | 2h | 100x faster writes |
| 2.2 | TTL-based cleanup | Included | No memory leak |
| 2.3 | Optimize parallel streams | 30min | Faster small searches |

**Key Changes**:
- Add `AtomicBoolean dirty` field
- Add `@PostConstruct` for 5-second flush scheduler
- Add `@PreDestroy` for graceful shutdown
- Add `cleanupExpiredMessages()` with 24h TTL
- Replace `writeMapToJson()` calls with `dirty.set(true)`

---

## Phase 3: Pagination (4h) 📄

| # | Issue | File | Time | Impact |
|---|-------|------|------|--------|
| 3.1 | Main endpoint pagination | MessageController.java:50-56 | 1h | Fast page loads |
| 3.2 | Update HTML template | messages.html | 1h | Server pagination UI |
| 3.3 | Filter endpoint pagination | MessageController.java | 2h | All APIs paginated |
| 3.4 | Update JavaScript | messages-actions.js | 30min | Handle paginated responses |

**Key Changes**:
- Add `page`, `size`, `sortBy`, `sortDir` params to `/messages`
- Return paginated response: `{data, page, size, totalPages, totalMessages}`
- Update all filter endpoints with pagination
- Add server-side pagination controls to HTML
- Update JS to handle paginated API responses

---

## Testing Checklist

### Unit Tests
- [ ] Race condition test (concurrent addCacheRecord)
- [ ] Batch write test (verify single file write)
- [ ] TTL cleanup test (verify old messages removed)
- [ ] Pagination test (verify correct page slicing)

### Integration Tests
- [ ] Load test: 10,000 messages in < 10 seconds
- [ ] Memory test: 24-hour run, memory stable
- [ ] UI test: Load 100,000 messages, page in < 2 seconds
- [ ] Concurrent test: 10 threads, no data loss

### Manual Tests
- [ ] Delete message → restart → verify still deleted
- [ ] Advanced search with direct response → returns results
- [ ] Navigate pages → verify correct data
- [ ] Filter messages → verify pagination works

---

## Deployment Steps

1. **Backup current cache file**
   ```bash
   cp shared/sim/messages/Messages.json shared/sim/messages/Messages.json.backup
   ```

2. **Deploy code changes**
   - Phase 1 fixes (critical bugs)
   - Phase 2 fixes (performance)
   - Phase 3 fixes (pagination)

3. **Restart application**
   ```bash
   # Application will auto-start schedulers
   # Verify logs: "Started scheduled tasks: flush every 5s, cleanup every 1h"
   ```

4. **Verify functionality**
   - Check logs for periodic flushes
   - Test message insertion (should be fast)
   - Test pagination (should load quickly)
   - Test filters (should return paginated results)

5. **Monitor metrics**
   - File write frequency (should be ~1 per 5 seconds)
   - Memory usage (should be stable)
   - Response times (should be < 500ms)

---

## Configuration (application.yaml)

```yaml
sim:
  cache:
    flush-interval-seconds: 5      # How often to flush to disk
    cleanup-interval-hours: 1      # How often to cleanup old messages
    message-ttl-hours: 24          # Keep messages for 24 hours
    max-cache-size: 100000         # Max messages in memory
    parallel-threshold: 1000       # Use parallel streams above this size
```

---

## Rollback Plan

If issues occur:

1. **Stop application**
2. **Restore backup**
   ```bash
   cp shared/sim/messages/Messages.json.backup shared/sim/messages/Messages.json
   ```
3. **Revert code changes**
4. **Restart application**

---

## Success Metrics

**Before Fixes**:
- File writes: 100+ per second (blocking)
- Memory: Unbounded growth
- UI load time: 50+ seconds with 100k messages
- Data loss: Possible under concurrent load

**After Fixes**:
- File writes: 1 per 5 seconds (non-blocking)
- Memory: Stable with TTL cleanup
- UI load time: < 2 seconds with 100k messages
- Data loss: None (atomic operations)

**Performance Improvement**: 100x faster writes, 25x faster UI loads

---

## Priority Order

1. **CRITICAL** (Do First): Phase 1 fixes 1.1, 1.2, 1.3 (30 min)
2. **HIGH** (Do Next): Phase 2.1 batch writes (2 hours)
3. **MEDIUM** (Then): Phase 3 pagination (4 hours)
4. **LOW** (Optional): Remaining optimizations

**Minimum Viable Fix**: Phase 1 + Phase 2.1 = 3 hours for production readiness
