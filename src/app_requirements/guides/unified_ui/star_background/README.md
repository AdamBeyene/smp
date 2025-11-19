# 🌌 Starry Background Component

## Quick Start

### For Spring Boot + Thymeleaf Projects

1. **Copy files to your project:**
   ```
   src/main/resources/static/css/starry-background.css
   src/main/resources/static/js/starry-background.js
   src/main/resources/templates/fragments/starry-background.html
   ```

2. **Add to your HTML:**
   ```html
   <head>
       <th:block th:replace="~{fragments/starry-background :: css}"></th:block>
   </head>
   <body>
       <th:block th:replace="~{fragments/starry-background :: js}"></th:block>
   </body>
   ```

3. **Ensure black background:**
   ```css
   body { background: #000000; }
   ```

4. **Done!** Refresh and enjoy the stars ✨

---

## What's Included

- **starry-background.css** - All animations and styles
- **starry-background.js** - Star and comet generation logic
- **starry-background.html** - Thymeleaf fragment (optional)
- **INTEGRATION_GUIDE.md** - Complete documentation for LLMs
- **README.md** - This file

---

## Features

✨ **400 twinkling 8-pointed stars** with 3 sizes
🌀 **Orbital rotation** around off-screen center (115%, 115%)
☄️ **Random comets** with 4-pointed star heads and trails (2 per 30 seconds)
🎭 **Activity-aware** - hides on clicks/keydowns, reappears after 17s idle
🔘 **Toggleable mode** - switch between activity-aware and always-visible
🎨 **Fully configurable** - all parameters adjustable
🚀 **Auto-initialization** - works out of the box
⚡ **GPU-accelerated** - smooth 60fps animations

---

## Configuration

Default settings:
```javascript
{
    starCount: 400,           // Number of stars
    fadeInDuration: 3000,     // Star fade-in time (ms)
    cometCount: 2,            // Max comets per wave
    cometInterval: 30000,     // Time between comet waves (ms)
    starModeOffOn: true,      // Hide stars on activity (default ON)
    idleDelay: 17000          // Show stars after 17s idle (ms)
}
```

To customize, edit `starry-background.js` or override in your HTML.

---

## Documentation

📖 **Full Integration Guide**: See `INTEGRATION_GUIDE.md` for:
- Complete integration instructions
- Troubleshooting guide
- Technical architecture
- Advanced customization
- LLM integration checklist

---

## Browser Support

✅ Chrome/Edge 90+
✅ Firefox 88+
✅ Safari 14+
✅ Opera 76+

---

## File Structure

```
star_background/
├── starry-background.css        # Styles (245 lines - 8-pointed stars, orbital animation)
├── starry-background.js         # Logic (381 lines - activity detection, toggle mode)
├── starry-background.html       # Fragment (14 lines)
├── INTEGRATION_GUIDE.md         # Complete docs
├── INDEX.md                     # Technical reference
├── LLM_QUICK_REFERENCE.md       # Quick integration guide
└── README.md                    # This file
```

---

## Quick Integration Examples

### Method 1: Thymeleaf (Recommended)
```html
<head>
    <th:block th:replace="~{fragments/starry-background :: css}"></th:block>
</head>
<body>
    <th:block th:replace="~{fragments/starry-background :: js}"></th:block>
</body>
```

### Method 2: Direct Link
```html
<head>
    <link rel="stylesheet" href="/css/starry-background.css">
</head>
<body>
    <script src="/js/starry-background.js"></script>
</body>
```

### Method 3: Custom Configuration
```html
<script src="/js/starry-background.js"></script>
<script>
    window.starryBackground.destroy();
    window.starryBackground = new StarryBackground({
        starCount: 200,
        cometCount: 3,
        cometInterval: 15000,
        starModeOffOn: false,   // Stars always visible (no activity detection)
        idleDelay: 17000        // Or adjust idle time if starModeOffOn: true
    });
</script>
```

---

## Troubleshooting

**Stars not visible?**
- Check body background is black: `body { background: #000000; }`
- Check content has z-index: `.container { z-index: 1; }`
- Stars may be hidden due to activity detection - wait 17s without clicking

**Stars keep disappearing?**
- Activity detection is enabled (default behavior)
- Stars hide on mouse clicks and keyboard presses
- Stars reappear after 17 seconds of no clicking
- To disable: `starModeOffOn: false`

**Stars not moving?**
- Check browser console for errors
- Verify JS file loaded correctly
- Orbital motion is very slow (5-10 minutes per rotation)

**Performance issues?**
- Reduce star count: `starCount: 75`
- Reduce comet frequency: `cometInterval: 60000`
- Disable activity detection may help: `starModeOffOn: false`

---

## Version

**Current**: v2.3
**Last Updated**: November 2025

---

## License

Use freely in any project. Attribution optional but appreciated.

---

**For complete documentation, see INTEGRATION_GUIDE.md** 📚
