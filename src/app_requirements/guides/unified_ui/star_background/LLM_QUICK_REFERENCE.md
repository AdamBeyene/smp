# 🤖 LLM Quick Reference - Starry Background Integration

## Context for AI Assistants

This is a **self-contained, reusable starry background component** that can be integrated into any web project. When a user asks to add this background to a project, follow this guide.

---

## 🎯 Integration Decision Tree

```
User wants starry background
    │
    ├─ Is it Spring Boot + Thymeleaf?
    │   ├─ YES → Use Method 1 (Thymeleaf Fragments)
    │   └─ NO → Continue
    │
    ├─ Is it static HTML/standard web project?
    │   ├─ YES → Use Method 2 (Direct Links)
    │   └─ NO → Continue
    │
    ├─ Is it React/Vue/Angular SPA?
    │   ├─ YES → Use Method 2 + Framework-specific adjustments
    │   └─ NO → Continue
    │
    └─ Is it single-page/inline requirement?
        └─ YES → Use Method 3 (Inline)
```

---

## 📁 Files to Copy

**Always copy these 3 files:**

1. `starry-background.css` (245 lines) → CSS directory
2. `starry-background.js` (381 lines) → JS directory
3. `starry-background.html` (14 lines) → fragments directory (optional)

**Source location:**
```
src/app_requirements/guides/unified_ui/star_background/
```

---

## 🚀 Method 1: Spring Boot + Thymeleaf

### File Placement
```
target-project/
├── src/main/resources/
│   ├── static/
│   │   ├── css/starry-background.css
│   │   └── js/starry-background.js
│   └── templates/
│       └── fragments/starry-background.html
```

### HTML Template
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/starry-background :: css}"></th:block>
    <style>
        body { background: #000000; }
        .container { position: relative; z-index: 1; }
    </style>
</head>
<body>
    <div class="container">
        <!-- User's content -->
    </div>
    <th:block th:replace="~{fragments/starry-background :: js}"></th:block>
</body>
</html>
```

---

## 🚀 Method 2: Direct Links (Static HTML)

### File Placement
```
target-project/
├── css/starry-background.css
└── js/starry-background.js
```

### HTML
```html
<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" href="/css/starry-background.css">
    <style>
        body { background: #000000; margin: 0; padding: 0; }
        .content { position: relative; z-index: 1; }
    </style>
</head>
<body>
    <div class="content">
        <!-- User's content -->
    </div>
    <script src="/js/starry-background.js"></script>
</body>
</html>
```

---

## 🚀 Method 3: React/Vue/Angular

### React Example
```jsx
// App.js or Layout component
import { useEffect } from 'react';

function App() {
    useEffect(() => {
        // Load CSS
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = '/css/starry-background.css';
        document.head.appendChild(link);
        
        // Load JS
        const script = document.createElement('script');
        script.src = '/js/starry-background.js';
        document.body.appendChild(script);
        
        // Set body background
        document.body.style.background = '#000000';
        
        return () => {
            // Cleanup
            if (window.starryBackground) {
                window.starryBackground.destroy();
            }
        };
    }, []);
    
    return (
        <div style={{ position: 'relative', zIndex: 1 }}>
            {/* Your app content */}
        </div>
    );
}
```

### Vue Example
```vue
<template>
    <div class="app" style="position: relative; z-index: 1">
        <!-- Your app content -->
    </div>
</template>

<script>
export default {
    mounted() {
        // Load CSS
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = '/css/starry-background.css';
        document.head.appendChild(link);
        
        // Load JS
        const script = document.createElement('script');
        script.src = '/js/starry-background.js';
        document.body.appendChild(script);
        
        // Set body background
        document.body.style.background = '#000000';
    },
    beforeUnmount() {
        if (window.starryBackground) {
            window.starryBackground.destroy();
        }
    }
}
</script>
```

---

## ⚙️ Configuration Patterns

### Default (Auto-initialized)
No configuration needed. Just include files.

### Custom Configuration
```html
<script src="/js/starry-background.js"></script>
<script>
    // Destroy auto-instance
    if (window.starryBackground) {
        window.starryBackground.destroy();
    }
    
    // Create custom instance
    window.starryBackground = new StarryBackground({
        starCount: 200,        // More stars
        fadeInDuration: 5000,  // Slower fade
        cometCount: 3,         // More comets
        cometInterval: 15000   // More frequent
    });
</script>
```

### Configuration Presets

**Minimal (Performance)**
```javascript
{ starCount: 75, cometCount: 1, cometInterval: 60000, starModeOffOn: false }
```

**Standard (Default)**
```javascript
{ starCount: 400, cometCount: 2, cometInterval: 30000, starModeOffOn: true, idleDelay: 17000 }
```

**Rich (Visual)**
```javascript
{ starCount: 250, cometCount: 3, cometInterval: 15000, starModeOffOn: true, idleDelay: 17000 }
```

**Meteor Shower (Dramatic)**
```javascript
{ starCount: 300, cometCount: 5, cometInterval: 10000, starModeOffOn: true, idleDelay: 17000 }
```

---

## 🔍 Required CSS Rules

**CRITICAL**: These CSS rules are REQUIRED for the background to work:

```css
/* 1. Black background */
body {
    background: #000000;
    margin: 0;
    padding: 0;
}

/* 2. Content above stars */
.container, .main-content, .content, #app, .app {
    position: relative;
    z-index: 1;
}
```

**Why?**
- Black background: Stars are white, need black to be visible
- z-index: Content must be above stars (stars are z-index: 0-10)

---

## 🐛 Common Issues & Fixes

### Issue: Stars not visible
**Cause**: Body background not black
**Fix**: `body { background: #000000 !important; }`

### Issue: Stars keep disappearing
**Cause**: Activity detection enabled (default behavior)
**How it works**: Stars hide on click/keydown, reappear after 17s idle
**Fix Option 1**: Wait 17 seconds without clicking
**Fix Option 2**: Disable activity detection: `{ starModeOffOn: false }`
**Fix Option 3**: Adjust idle delay: `{ starModeOffOn: true, idleDelay: 5000 }`

### Issue: Content not visible
**Cause**: Content z-index too low
**Fix**: `.content { position: relative; z-index: 1; }`

### Issue: Stars not animating
**Cause**: JavaScript not loaded or error
**Fix**: Check browser console, verify file path

### Issue: Performance lag
**Cause**: Too many stars/comets
**Fix**: Reduce `starCount` to 75 or `cometCount` to 0

### Issue: Comets not appearing
**Cause**: Need to wait 5+ seconds after page load
**Fix**: Wait or force: `window.starryBackground.generateComets()`

---

## 📊 Integration Checklist

Use this checklist when integrating:

```
[ ] Identify project type (Spring Boot / Static / SPA)
[ ] Copy starry-background.css to CSS directory
[ ] Copy starry-background.js to JS directory
[ ] Copy starry-background.html to fragments (if Thymeleaf)
[ ] Add CSS link/fragment to <head>
[ ] Add JS link/fragment before </body>
[ ] Add body { background: #000000; }
[ ] Add content { z-index: 1; }
[ ] Test: Refresh page (Ctrl+Shift+R)
[ ] Verify: Black background appears
[ ] Verify: Stars fade in over 3 seconds
[ ] Verify: Stars twinkle (opacity changes)
[ ] Verify: Stars slowly rotate (orbital movement, 5-10 min per rotation)
[ ] Verify: Comets appear after 5 seconds
[ ] Verify: Click anywhere - stars should hide
[ ] Verify: Wait 17s without clicking - stars should reappear
[ ] Check console for success messages
```

---

## 💬 Console Messages (Expected)

When working correctly, browser console shows:
```
🌟 Generating 400 stars...
✅ Created 400 stars with animations
⚡ Activity detection enabled (idle delay: 17000ms, only click/keydown)
☄️ Starting comet generation (2 comets every 30s)
☄️ Generating 2 comet(s)...
☄️ Comet: edge=1, speed=3.2s, brightness=0.87, trail=105px
☄️ Comet: edge=0, speed=4.1s, brightness=0.72, trail=98px

# When you click:
⚡ Click detected, hiding stars

# After 17 seconds of no clicking:
💤 No clicks for 17s, showing stars
```

---

## 🎨 Customization Quick Reference

### Change Star Count
```javascript
starCount: 200  // Default: 400
```

### Change Comet Frequency
```javascript
cometInterval: 15000  // Default: 30000 (30 seconds)
```

### Disable Comets
```javascript
cometCount: 0
```

### Disable Activity Detection
```javascript
starModeOffOn: false  // Default: true (stars always visible)
```

### Adjust Idle Delay
```javascript
idleDelay: 5000  // Default: 17000 (show stars after 5s instead of 17s)
```

### Change Orbital Rotation Center
Edit `starry-background.js` lines 27-28:
```javascript
this.driftCenterX = 50;   // Center screen (default: 115)
this.driftCenterY = 50;   // Center screen (default: 115)
```

### Change Colors
Edit `starry-background.css`:
```css
.star { background: #88ccff; }  /* Blue stars */
.comet::after { background: #ffaa00; }  /* Orange comets */
```

---

## 🧪 Testing Commands

Use in browser console:

```javascript
// Check if loaded
window.starryBackground

// Star count
document.querySelectorAll('.star').length  // Should be 400

// Comet count
document.querySelectorAll('.comet').length

// Force comet
window.starryBackground.generateComets()

// Toggle activity detection mode
window.starryBackground.toggleMode()  // Returns true (ON) or false (OFF)

// Check if stars are hidden
window.starryBackground.starsHidden  // true or false

// Check activity detection settings
window.starryBackground.starModeOffOn  // true (ON) or false (OFF)
window.starryBackground.idleDelay      // 17000 (milliseconds)

// Manually show/hide stars
document.querySelector('#starry-canvas').style.opacity = '1'  // Show
document.querySelector('#starry-canvas').style.opacity = '0'  // Hide

// Restart with new config
window.starryBackground.destroy()
window.starryBackground = new StarryBackground({
    starCount: 200,
    starModeOffOn: false  // Always visible
})
```

---

## 📦 File Dependencies

**starry-background.css**
- No dependencies
- Pure CSS animations
- 245 lines
- 8-pointed stars using pseudo-elements
- Orbital rotation animation
- Activity-aware opacity transitions

**starry-background.js**
- No dependencies (vanilla JavaScript)
- Auto-initializes on DOM ready
- 381 lines
- Creates global: `window.starryBackground`
- Includes activity detection system
- Includes Page Visibility API integration

**starry-background.html**
- Requires: Thymeleaf
- Optional: Can use direct links instead
- 14 lines

---

## 🎯 Integration Strategy by Project Type

### Existing Project with Pages
1. Copy files to static directories
2. Create fragment file (if Thymeleaf)
3. Add fragment includes to existing pages
4. Test one page first
5. Roll out to all pages

### New Project
1. Copy files during initial setup
2. Add to base/layout template
3. All pages inherit automatically

### Single Page
1. Copy files to project
2. Add links to that specific page
3. Done

### Multiple Projects
1. Copy files to shared location
2. Reference from multiple projects
3. Or copy to each project independently

---

## 🔄 Update Strategy

If starry background is updated:
1. Replace CSS file
2. Replace JS file
3. Keep fragment file (usually unchanged)
4. Test in one page
5. Roll out to all pages

---

## 🎓 Teaching Points for Users

When explaining to users:
1. "It's just 3 files you copy"
2. "Add 2 lines to your HTML"
3. "Make sure background is black"
4. "Content needs z-index: 1"
5. "Refresh and it works automatically"

---

## 🚨 Critical Reminders

**ALWAYS**:
- Set body background to #000000
- Set content z-index to 1
- Include CSS before JS
- Test in browser after integration

**NEVER**:
- Modify the core files (unless customizing)
- Forget the black background
- Place content behind stars (z-index)
- Load JS in <head> (load before </body>)

---

## 📚 Documentation Hierarchy

1. **LLM_QUICK_REFERENCE.md** (this file) - Fast integration guide
2. **README.md** - User-friendly overview
3. **INTEGRATION_GUIDE.md** - Complete technical documentation

**Use this file for**: Quick integration decisions
**Use README for**: User-facing instructions
**Use INTEGRATION_GUIDE for**: Deep technical details

---

**End of LLM Quick Reference** 🤖
