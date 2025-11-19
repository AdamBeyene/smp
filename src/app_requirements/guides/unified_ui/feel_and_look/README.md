# Feel & Look - Unified UI Design Guide

This folder contains the complete design system for migrating projects to the unified UI style.

---

## 📁 Files in This Folder

### 1. **DESIGN_SYSTEM.md**
Complete design system documentation including:
- Design principles
- Color palette (all colors with hex codes)
- Typography (Oxanium font, sizes, weights)
- Spacing scale
- Border radius values
- Component styles (buttons, tabs, forms, cards, etc.)
- Animations & transitions
- Responsive breakpoints
- Z-index layers
- Component checklist

**Use this as:** Your primary reference for all design decisions

---

### 2. **COMPONENTS_EXAMPLES.md**
Ready-to-use HTML/CSS code snippets for:
- Buttons (primary, success, danger, purple, secondary)
- Tabs system
- Form elements (inputs, selects, checkboxes)
- Cards and panels
- Logger level buttons
- Collapsible tool cards
- Error states
- Loading states
- Code blocks
- Complete page template

**Use this as:** Copy-paste examples for quick implementation

---

### 3. **DESIGN_TOKENS.css** (Coming Soon)
CSS variables file with all design tokens:
- Color variables
- Typography variables
- Spacing variables
- Border radius variables
- Transition variables
- Z-index variables

**Use this as:** Import this CSS file to get all design tokens

---

## 🎯 Quick Start Guide

### Step 1: Review the Design
1. Open `DESIGN_SYSTEM.md` to understand the design principles
2. Look at `../screen_shots_examples/` to see the visual result
3. Check color palette and typography sections

### Step 2: Set Up Your Project
1. Add Oxanium font to your HTML:
```html
<link href="https://fonts.googleapis.com/css2?family=Oxanium:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
```

2. Add starry background files:
   - Copy `../star_background/starry-background.css`
   - Copy `../star_background/starry-background.js`

3. Set up base styles:
```css
:root {
    --font-sans: "Oxanium", sans-serif;
    --button-border-radius: 12px;
}

body {
    font-family: var(--font-sans);
    background: #000000;
    color: #e7e9ea;
}
```

### Step 3: Implement Components
1. Open `COMPONENTS_EXAMPLES.md`
2. Copy the HTML/CSS for components you need
3. Adjust content while keeping the styles

### Step 4: Add Starry Background
```html
<body>
    <!-- Your content with z-index: 1 -->
    <script src="/js/starry-background.js"></script>
</body>
```

---

## 🎨 Key Design Principles

### 1. **70% Transparency**
All content containers use transparent backgrounds:
- `rgba(22, 24, 28, 0.3)` for main containers
- `rgba(29, 31, 35, 0.3)` for secondary containers

### 2. **Black Background**
- Page background: `#000000`
- Starry background visible through transparent containers

### 3. **Consistent Borders**
- All borders: `1px solid #2f3336`
- Border radius: `12px` for buttons, `16px` for large containers

### 4. **Color Scheme**
- Primary: `#1d9bf0` (Twitter-like blue)
- Success: `#00ba7c` (Green)
- Error: `#f4212e` (Red)
- Purple: `#7856ff` (Special actions)
- Text: `#e7e9ea` (primary), `#71767b` (secondary)

### 5. **Typography**
- Font: Oxanium (Google Fonts)
- Base size: 14px
- Headings: 16px, 20px, 24px
- Weight: 600 for buttons, 700 for headings

---

## 📊 Visual Reference

See `../screen_shots_examples/` for screenshots of:
1. Loggers tab - Logger management interface
2. Errors tab - Error tracking with filters
3. Metrics tab - Dashboard with graphs
4. Tools tab - MCP tools listing
5. Tests tab - Test execution interface
6. Live Logs tab - Log streaming interface

---

## 🔄 Migration Checklist

When migrating a project, follow this checklist:

### Design Setup
- [ ] Add Oxanium font import
- [ ] Set up CSS variables (`:root`)
- [ ] Set body background to `#000000`
- [ ] Add starry background CSS and JS

### Layout
- [ ] Create container with `z-index: 1`
- [ ] Add header card with 70% transparency
- [ ] Implement tab system if needed

### Components
- [ ] Update all buttons to use `--button-border-radius: 12px`
- [ ] Apply button colors (blue, green, red, purple, gray)
- [ ] Style text inputs with dark background
- [ ] Style select dropdowns
- [ ] Style checkboxes with accent color
- [ ] Create transparent cards/panels (70%)

### Colors
- [ ] Replace primary colors with `#1d9bf0`
- [ ] Replace success colors with `#00ba7c`
- [ ] Replace error colors with `#f4212e`
- [ ] Update text colors (`#e7e9ea`, `#71767b`)
- [ ] Update border colors (`#2f3336`)

### Typography
- [ ] Apply Oxanium font family
- [ ] Update font sizes (14px base)
- [ ] Update font weights (600, 700)

### Final Touches
- [ ] Test all hover states
- [ ] Verify transparency effects
- [ ] Check responsive behavior
- [ ] Ensure starry background is visible

---

## 🛠️ Tools & Resources

### Color Picker
Use these exact hex codes:
- `#000000` - Black background
- `#1d9bf0` - Primary blue
- `#00ba7c` - Success green
- `#f4212e` - Error red
- `#7856ff` - Purple
- `#e7e9ea` - Primary text
- `#71767b` - Secondary text
- `#2f3336` - Borders

### Transparency Calculator
- 70% transparent = `0.3` opacity in `rgba()`
- Example: `rgba(29, 31, 35, 0.3)`

### Border Radius
- Buttons: `12px`
- Cards: `12px`
- Large containers: `16px`
- Small elements: `4px` or `8px`

---

## 📞 Support

If you need help:
1. Check `DESIGN_SYSTEM.md` for detailed specs
2. Look at `COMPONENTS_EXAMPLES.md` for code examples
3. View `../screen_shots_examples/` for visual reference
4. Review `../star_background/` for background implementation

---

## 📝 Version History

- **v1.0.0** (Nov 13, 2025) - Initial design system documentation
  - Complete design system guide
  - Component examples
  - Screenshot references
  - Starry background integration

---

**Happy Migrating! 🚀**
