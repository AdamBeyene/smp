# Unified UI Design System
**Version:** 1.0.0  
**Last Updated:** November 13, 2025

This document defines the complete design system for the unified UI, including colors, typography, spacing, components, and interactions. Use this as the single source of truth when migrating other projects to this design.

---

## 📐 Design Principles

1. **Dark Mode First** - Black background (#000000) with transparent overlays
2. **Starry Background** - Animated cosmic background for visual depth
3. **70% Transparency** - Content containers use `rgba(22, 24, 28, 0.3)` or `rgba(29, 31, 35, 0.3)`
4. **Subtle Borders** - 1px solid #2f3336 for definition without harshness
5. **Rounded Corners** - 12-16px border-radius for modern feel
6. **Consistent Spacing** - 4px base unit (8px, 12px, 16px, 20px, 24px)
7. **Smooth Transitions** - 0.2-0.3s ease for hover states

---

## 🎨 Color Palette

### Base Colors
```css
--color-black: #000000;           /* Page background */
--color-bg-primary: rgba(22, 24, 28, 0.3);   /* Main containers (70% transparent) */
--color-bg-secondary: rgba(29, 31, 35, 0.3); /* Secondary containers (70% transparent) */
--color-bg-solid: #16181c;        /* Solid backgrounds (no transparency) */
--color-bg-darker: #0f1419;       /* Input fields, code blocks */
--color-bg-hover: rgba(29, 31, 35, 0.3); /* Hover states */
```

### Text Colors
```css
--color-text-primary: #e7e9ea;    /* Primary text */
--color-text-secondary: #71767b;  /* Secondary text, labels */
--color-text-muted: #71767b;      /* Disabled, placeholder text */
```

### Border Colors
```css
--color-border-primary: #2f3336;  /* Default borders */
--color-border-subtle: #2f3336;   /* Subtle dividers */
```

### Brand Colors
```css
--color-brand-primary: #1d9bf0;   /* Primary blue (Twitter-like) */
--color-brand-hover: #1a8cd8;     /* Primary hover state */
```

### Semantic Colors
```css
/* Success */
--color-success: #00ba7c;         /* Green for success states */
--color-success-hover: #00a56e;

/* Warning */
--color-warning: #ffd700;         /* Yellow for warnings */

/* Error */
--color-error: #f4212e;           /* Red for errors */
--color-error-bg: #200a0a;        /* Error background */
--color-error-border: #5b1f1f;    /* Error border */

/* Info */
--color-info: #1d9bf0;            /* Blue for info */

/* Purple (special actions) */
--color-purple: #7856ff;          /* Purple for special buttons */
--color-purple-hover: #6845e6;
```

### Status Colors
```css
--color-status-active: #1d9bf0;   /* Active tab, selected state */
--color-status-inactive: #71767b; /* Inactive elements */
```

---

## 🔤 Typography

### Font Family
```css
--font-sans: "Oxanium", sans-serif;
```

**Import:**
```html
<link href="https://fonts.googleapis.com/css2?family=Oxanium:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
```

### Font Sizes
```css
--font-size-xs: 12px;     /* Small labels, captions */
--font-size-sm: 13px;     /* Secondary text */
--font-size-base: 14px;   /* Body text, buttons */
--font-size-md: 15px;     /* Tab buttons */
--font-size-lg: 16px;     /* Subheadings */
--font-size-xl: 20px;     /* Section headings */
--font-size-2xl: 24px;    /* Page title, stat values */
```

### Font Weights
```css
--font-weight-light: 300;
--font-weight-normal: 400;
--font-weight-medium: 500;
--font-weight-semibold: 600;
--font-weight-bold: 700;
--font-weight-extrabold: 800;
```

### Line Heights
```css
--line-height-tight: 1.2;
--line-height-normal: 1.5;
--line-height-relaxed: 1.75;
```

---

## 📏 Spacing Scale

```css
--spacing-1: 4px;
--spacing-2: 8px;
--spacing-3: 12px;
--spacing-4: 16px;
--spacing-5: 20px;
--spacing-6: 24px;
--spacing-8: 32px;
--spacing-10: 40px;
```

---

## 🔘 Border Radius

```css
--radius-sm: 4px;         /* Small elements, badges */
--radius-md: 8px;         /* Cards, inputs */
--radius-lg: 12px;        /* Buttons, containers */
--radius-xl: 16px;        /* Large containers */
--radius-full: 9999px;    /* Pills, circular elements */
```

**Primary Button Radius:**
```css
--button-border-radius: 12px;  /* All action buttons */
```

---

## 🎭 Component Styles

### Containers

#### Main Container
```css
.container {
    max-width: 1400px;
    margin: 0 auto;
    padding: 20px;
    position: relative;
    z-index: 1;  /* Above starry background */
}
```

#### Card / Panel (70% Transparent)
```css
.card {
    background: rgba(29, 31, 35, 0.3);
    border-radius: 12px;
    padding: 16px;
    border: 1px solid #2f3336;
}
```

#### Header Card
```css
.header {
    background: rgba(22, 24, 28, 0.3);
    border-radius: 16px;
    padding: 24px;
    margin-bottom: 20px;
    border: 1px solid #2f3336;
}
```

#### Info Panel
```css
.info-panel {
    background: rgba(29, 31, 35, 0.3);
    border-radius: 12px;
    padding: 20px;
    border: 1px solid #2f3336;
    margin-bottom: 16px;
}
```

---

### Buttons

#### Primary Button (Blue)
```css
.btn-primary {
    padding: 10px 20px;
    background: #1d9bf0;
    border: none;
    border-radius: var(--button-border-radius);  /* 12px */
    color: white;
    font-weight: 600;
    cursor: pointer;
    font-size: 14px;
    transition: background 0.2s ease;
}

.btn-primary:hover {
    background: #1a8cd8;
}
```

#### Success Button (Green)
```css
.btn-success {
    padding: 10px 20px;
    background: #00ba7c;
    border: none;
    border-radius: var(--button-border-radius);
    color: white;
    font-weight: 600;
    cursor: pointer;
    font-size: 14px;
}

.btn-success:hover {
    background: #00a56e;
}
```

#### Danger Button (Red)
```css
.btn-danger {
    padding: 10px 20px;
    background: #f4212e;
    border: none;
    border-radius: var(--button-border-radius);
    color: white;
    font-weight: 600;
    cursor: pointer;
    font-size: 14px;
}

.btn-danger:hover {
    background: #d91828;
}
```

#### Purple Button (Special Actions)
```css
.btn-purple {
    padding: 10px 20px;
    background: #7856ff;
    border: none;
    border-radius: var(--button-border-radius);
    color: white;
    font-weight: 600;
    cursor: pointer;
    font-size: 14px;
}

.btn-purple:hover {
    background: #6845e6;
}
```

#### Secondary Button (Gray)
```css
.btn-secondary {
    padding: 10px 20px;
    background: #2f3336;
    border: none;
    border-radius: var(--button-border-radius);
    color: #e7e9ea;
    font-weight: 600;
    cursor: pointer;
    font-size: 14px;
}

.btn-secondary:hover {
    background: #3a3f44;
}
```

#### Larger Button Variant
```css
.btn-lg {
    padding: 12px 24px;
    font-size: 14px;
}
```

---

### Tabs

#### Tab Container
```css
.tabs {
    background: rgba(22, 24, 28, 0.3);
    border-radius: 16px;
    border: 1px solid #2f3336;
    overflow: hidden;
}
```

#### Tab Header
```css
.tabs-header {
    display: flex;
    border-bottom: 1px solid #2f3336;
    overflow-x: auto;
}
```

#### Tab Button
```css
.tab-button {
    flex: 1;
    min-width: 120px;
    padding: 16px 20px;
    background: transparent;
    border: none;
    color: #71767b;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
    position: relative;
}

.tab-button:hover {
    background: rgba(29, 31, 35, 0.3);
    color: #e7e9ea;
}

.tab-button.active {
    color: #1d9bf0;
}

.tab-button.active::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: #1d9bf0;
    border-radius: 4px 4px 0 0;
}
```

#### Tab Content
```css
.tab-content {
    padding: 24px;
    min-height: 500px;
}
```

---

### Form Elements

#### Text Input
```css
.input-text {
    width: 100%;
    padding: 10px 16px;
    background: #0f1419;
    border: 1px solid #2f3336;
    border-radius: var(--button-border-radius);  /* 12px */
    color: #e7e9ea;
    font-size: 14px;
    font-family: var(--font-sans);
    transition: border-color 0.2s ease;
}

.input-text:focus {
    outline: none;
    border-color: #1d9bf0;
}

.input-text::placeholder {
    color: #71767b;
}
```

#### Select / Dropdown
```css
.select {
    width: 100%;
    padding: 12px;
    background: #0f1419;
    border: 1px solid #2f3336;
    border-radius: 8px;
    color: #e7e9ea;
    font-size: 14px;
    font-family: var(--font-sans);
    cursor: pointer;
}

.select:focus {
    outline: none;
    border-color: #1d9bf0;
}
```

#### Checkbox
```css
.checkbox {
    width: 16px;
    height: 16px;
    margin-right: 8px;
    cursor: pointer;
    accent-color: #1d9bf0;  /* Modern checkbox color */
}

.checkbox-label {
    display: flex;
    align-items: center;
    cursor: pointer;
    color: #e7e9ea;
    font-size: 14px;
    user-select: none;
}
```

#### Search Input (with icon)
```css
.search-container {
    position: relative;
    flex: 1;
}

.search-input {
    width: 100%;
    padding: 12px 40px 12px 16px;
    background: #0f1419;
    border: 1px solid #2f3336;
    border-radius: 8px;
    color: #e7e9ea;
    font-size: 14px;
}

.search-icon {
    position: absolute;
    right: 12px;
    top: 50%;
    transform: translateY(-50%);
    color: #71767b;
    pointer-events: none;
}
```

---

### Logger Level Buttons

```css
.logger-level-btn {
    padding: 6px 12px;
    background: #2f3336;
    border: 1px solid #2f3336;
    border-radius: 6px;
    color: #71767b;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
}

.logger-level-btn:hover {
    background: #3a3f44;
    color: #e7e9ea;
}

.logger-level-btn.active {
    background: #1d9bf0;
    border-color: #1d9bf0;
    color: white;
}
```

---

### Stats / Metric Cards

#### Stat Card
```css
.stat-card {
    background: rgba(29, 31, 35, 0.3);
    border-radius: 12px;
    padding: 16px;
    border: 1px solid #2f3336;
}

.stat-label {
    color: #71767b;
    font-size: 13px;
    margin-bottom: 4px;
}

.stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #e7e9ea;
}
```

#### Stats Grid
```css
.stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
    margin-bottom: 24px;
}
```

---

### Code Elements

#### Inline Code
```css
code {
    background: #0f1419;
    padding: 2px 6px;
    border-radius: 4px;
    color: #1d9bf0;
    font-family: 'Courier New', monospace;
    font-size: 13px;
}
```

#### Code Block
```css
.code-block {
    background: #0f1419;
    border-radius: 8px;
    padding: 16px;
    border: 1px solid #2f3336;
    font-family: 'Courier New', monospace;
    font-size: 13px;
    color: #e7e9ea;
    overflow-x: auto;
}
```

---

### Error States

#### Error Container
```css
.error {
    background: #200a0a;
    border: 1px solid #5b1f1f;
    border-radius: 12px;
    padding: 16px;
    color: #f4212e;
}
```

#### Error Badge
```css
.error-badge {
    display: inline-block;
    padding: 4px 8px;
    background: #5b1f1f;
    color: #f4212e;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 600;
}
```

---

### Loading States

```css
.loading {
    text-align: center;
    padding: 40px;
    color: #71767b;
}

.loading-spinner {
    /* Add spinner animation if needed */
}
```

---

### Tool Cards (Collapsible)

```css
.tool-card {
    background: rgba(29, 31, 35, 0.3);
    border-radius: 12px;
    padding: 16px;
    border: 1px solid #2f3336;
    margin-bottom: 12px;
    cursor: pointer;
    transition: all 0.2s;
}

.tool-card:hover {
    background: rgba(29, 31, 35, 0.5);
    border-color: #1d9bf0;
}

.tool-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.tool-card-title {
    font-size: 16px;
    font-weight: 600;
    color: #1d9bf0;
}

.tool-card-icon {
    transition: transform 0.3s;
}

.tool-card.expanded .tool-card-icon {
    transform: rotate(180deg);
}
```

---

## 🎬 Animations & Transitions

### Standard Transitions
```css
/* Hover transitions */
transition: all 0.2s ease;

/* Color transitions */
transition: color 0.3s ease;

/* Background transitions */
transition: background 0.2s ease;

/* Transform transitions */
transition: transform 0.3s ease;
```

### Starry Background
- **Stars:** Fade in over 3 seconds with staggered delays
- **Stars:** Slow rainbow color shift (60s cycle)
- **Stars:** Circular orbital drift (300-600s period)
- **Comets:** Random rainbow colors, smooth trajectory movement
- **Comets:** Particle disintegration trail effect

---

## 📱 Responsive Design

### Breakpoints
```css
/* Mobile */
@media (max-width: 768px) {
    .tabs-header {
        flex-wrap: wrap;
    }
    
    .tab-button {
        min-width: 100px;
        font-size: 14px;
    }
    
    .stats-grid {
        grid-template-columns: 1fr;
    }
}

/* Tablet */
@media (min-width: 769px) and (max-width: 1024px) {
    .container {
        max-width: 100%;
        padding: 16px;
    }
}

/* Desktop */
@media (min-width: 1025px) {
    .container {
        max-width: 1400px;
    }
}
```

---

## 🌟 Starry Background Integration

### Required Files
1. `starry-background.css` - Star and comet animations
2. `starry-background.js` - Star generation and movement logic
3. `starry-background.html` - Thymeleaf fragment (optional)

### Integration
```html
<head>
    <link rel="stylesheet" href="/css/starry-background.css">
</head>
<body>
    <script src="/js/starry-background.js"></script>
</body>
```

### Configuration
```javascript
new StarryBackground({
    starCount: 400,           // Number of stars
    fadeInDuration: 3000,     // Fade-in time (ms)
    cometCount: 2,            // Max comets per interval
    cometInterval: 30000,     // Comet generation interval (ms)
    starModeOffOn: true,      // Hide stars on activity
    idleDelay: 17000          // Show stars after idle (ms)
});
```

---

## 🎯 Z-Index Layers

```css
--z-background: -1;     /* Starry background */
--z-base: 0;            /* Base layer */
--z-content: 1;         /* Main content */
--z-comets: 10;         /* Comets (above stars) */
--z-dropdown: 100;      /* Dropdowns */
--z-modal: 1000;        /* Modals */
--z-tooltip: 2000;      /* Tooltips */
```

---

## 📋 Component Checklist

When migrating a project, ensure these components are styled:

- [ ] Page container with z-index: 1
- [ ] Header card (70% transparent)
- [ ] Tab navigation
- [ ] Tab content areas
- [ ] Primary buttons (blue, green, red, purple)
- [ ] Secondary buttons (gray)
- [ ] Text inputs with focus states
- [ ] Select dropdowns
- [ ] Checkboxes with labels
- [ ] Search inputs with icons
- [ ] Stat cards
- [ ] Info panels (70% transparent)
- [ ] Error containers
- [ ] Loading states
- [ ] Code blocks and inline code
- [ ] Collapsible tool cards
- [ ] Logger level buttons
- [ ] Starry background integration

---

## 🔗 Related Files

- `DESIGN_TOKENS.css` - CSS variables
- `COMPONENTS.css` - Reusable component styles
- `EXAMPLES.md` - Component usage examples
- `../star_background/` - Starry background files
- `../screen_shots_examples/` - Visual reference screenshots

---

**End of Design System Document**
