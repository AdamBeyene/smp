# Component Examples & Usage Guide

This document provides HTML/CSS examples for all UI components in the unified design system.

---

## 🎨 Quick Start

### 1. Import Required Styles
```html
<head>
    <!-- Google Fonts -->
    <link href="https://fonts.googleapis.com/css2?family=Oxanium:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    
    <!-- Starry Background -->
    <link rel="stylesheet" href="/css/starry-background.css">
    
    <!-- Your custom styles -->
    <style>
        :root {
            --font-sans: "Oxanium", sans-serif;
            --button-border-radius: 12px;
        }
        
        body {
            font-family: var(--font-sans);
            background: #000000;
            color: #e7e9ea;
        }
    </style>
</head>
```

### 2. Add Starry Background
```html
<body>
    <!-- Your content here -->
    
    <script src="/js/starry-background.js"></script>
</body>
```

---

## 📦 Container & Layout

### Main Container
```html
<div class="container" style="max-width: 1400px; margin: 0 auto; padding: 20px; position: relative; z-index: 1;">
    <!-- Content goes here -->
</div>
```

### Header Card
```html
<div style="background: rgba(22, 24, 28, 0.3); border-radius: 16px; padding: 24px; margin-bottom: 20px; border: 1px solid #2f3336;">
    <h1 style="font-size: 24px; font-weight: 700; margin-bottom: 8px; color: #e7e9ea;">
        Server Details
    </h1>
    <div style="display: flex; gap: 24px; color: #71767b; font-size: 14px;">
        <div style="display: flex; align-items: center; gap: 8px;">
            <span>Version:</span>
            <strong style="color: #e7e9ea;">1.0.0</strong>
        </div>
        <div style="display: flex; align-items: center; gap: 8px;">
            <span>Uptime:</span>
            <strong style="color: #e7e9ea;">15h 4m</strong>
        </div>
    </div>
</div>
```

---

## 🔘 Buttons

### Primary Button (Blue)
```html
<button style="padding: 10px 20px; background: #1d9bf0; border: none; border-radius: var(--button-border-radius); color: white; font-weight: 600; cursor: pointer; font-size: 14px; transition: background 0.2s ease;">
    Primary Action
</button>
```

### Success Button (Green)
```html
<button style="padding: 10px 20px; background: #00ba7c; border: none; border-radius: var(--button-border-radius); color: white; font-weight: 600; cursor: pointer; font-size: 14px;">
    Success Action
</button>
```

### Danger Button (Red)
```html
<button style="padding: 10px 20px; background: #f4212e; border: none; border-radius: var(--button-border-radius); color: white; font-weight: 600; cursor: pointer; font-size: 14px;">
    Delete
</button>
```

### Purple Button (Special)
```html
<button style="padding: 10px 20px; background: #7856ff; border: none; border-radius: var(--button-border-radius); color: white; font-weight: 600; cursor: pointer; font-size: 14px;">
    Generate Test Data
</button>
```

### Secondary Button (Gray)
```html
<button style="padding: 10px 20px; background: #2f3336; border: none; border-radius: var(--button-border-radius); color: #e7e9ea; font-weight: 600; cursor: pointer; font-size: 14px;">
    Cancel
</button>
```

### Button Group
```html
<div style="display: flex; gap: 12px;">
    <button style="padding: 10px 20px; background: #7856ff; border: none; border-radius: var(--button-border-radius); color: white; font-weight: 600; cursor: pointer; font-size: 14px;">
        Generate Test Data
    </button>
    <button style="padding: 10px 20px; background: #1d9bf0; border: none; border-radius: var(--button-border-radius); color: white; font-weight: 600; cursor: pointer; font-size: 14px;">
        Analyze Errors
    </button>
    <button style="padding: 10px 20px; background: #f4212e; border: none; border-radius: var(--button-border-radius); color: white; font-weight: 600; cursor: pointer; font-size: 14px;">
        Clear All
    </button>
</div>
```

---

## 📑 Tabs

### Complete Tab System
```html
<div style="background: rgba(22, 24, 28, 0.3); border-radius: 16px; border: 1px solid #2f3336; overflow: hidden;">
    <!-- Tab Header -->
    <div style="display: flex; border-bottom: 1px solid #2f3336; overflow-x: auto;">
        <button class="tab-button active" style="flex: 1; min-width: 120px; padding: 16px 20px; background: transparent; border: none; color: #1d9bf0; font-size: 15px; font-weight: 600; cursor: pointer; position: relative;">
            Loggers
            <span style="position: absolute; bottom: 0; left: 0; right: 0; height: 4px; background: #1d9bf0; border-radius: 4px 4px 0 0;"></span>
        </button>
        <button class="tab-button" style="flex: 1; min-width: 120px; padding: 16px 20px; background: transparent; border: none; color: #71767b; font-size: 15px; font-weight: 600; cursor: pointer; position: relative;">
            Errors
        </button>
        <button class="tab-button" style="flex: 1; min-width: 120px; padding: 16px 20px; background: transparent; border: none; color: #71767b; font-size: 15px; font-weight: 600; cursor: pointer; position: relative;">
            Metrics
        </button>
    </div>
    
    <!-- Tab Content -->
    <div style="padding: 24px; min-height: 500px;">
        <!-- Content here -->
    </div>
</div>
```

---

## 📝 Form Elements

### Text Input
```html
<input type="text" 
       placeholder="Filter loggers..." 
       style="width: 100%; padding: 10px 16px; background: #0f1419; border: 1px solid #2f3336; border-radius: var(--button-border-radius); color: #e7e9ea; font-size: 14px; font-family: var(--font-sans);">
```

### Search Input with Icon
```html
<div style="position: relative; flex: 1;">
    <input type="text" 
           placeholder="Filter loggers..." 
           style="width: 100%; padding: 12px 40px 12px 16px; background: #0f1419; border: 1px solid #2f3336; border-radius: 8px; color: #e7e9ea; font-size: 14px;">
    <span style="position: absolute; right: 12px; top: 50%; transform: translateY(-50%); color: #71767b;">🔍</span>
</div>
```

### Select Dropdown
```html
<select style="width: 100%; padding: 12px; background: #0f1419; border: 1px solid #2f3336; border-radius: 8px; color: #e7e9ea; font-size: 14px; font-family: var(--font-sans); cursor: pointer;">
    <option value="">All Categories</option>
    <option value="CONNECTION">CONNECTION</option>
    <option value="MCP">MCP</option>
    <option value="GENERAL">GENERAL</option>
</select>
```

### Checkbox with Label
```html
<label style="display: flex; align-items: center; cursor: pointer; color: #e7e9ea; font-size: 14px; user-select: none;">
    <input type="checkbox" 
           style="margin-right: 8px; cursor: pointer; accent-color: #1d9bf0;">
    <span>Configured only</span>
</label>
```

### Filter Controls Panel
```html
<div style="background: rgba(29, 31, 35, 0.3); border-radius: 12px; padding: 16px; border: 1px solid #2f3336; margin-bottom: 16px;">
    <div style="display: flex; gap: 12px; align-items: center;">
        <!-- Search -->
        <div style="position: relative; flex: 1;">
            <input type="text" placeholder="Filter loggers..." style="width: 100%; padding: 12px 40px 12px 16px; background: #0f1419; border: 1px solid #2f3336; border-radius: 8px; color: #e7e9ea; font-size: 14px;">
            <span style="position: absolute; right: 12px; top: 50%; transform: translateY(-50%); color: #71767b;">🔍</span>
        </div>
        
        <!-- Counter -->
        <div style="color: #71767b; font-size: 14px; white-space: nowrap;">
            844/844
        </div>
        
        <!-- Checkbox -->
        <label style="display: flex; align-items: center; cursor: pointer; color: #e7e9ea; font-size: 14px; white-space: nowrap;">
            <input type="checkbox" style="margin-right: 8px; cursor: pointer; accent-color: #1d9bf0;">
            <span>Configured only</span>
        </label>
        
        <!-- Reset Button -->
        <button style="padding: 10px 20px; background: #f4212e; border: none; border-radius: var(--button-border-radius); color: white; font-weight: 600; cursor: pointer; font-size: 14px;">
            Reset All
        </button>
    </div>
</div>
```

---

## 📊 Cards & Panels

### Info Panel (70% Transparent)
```html
<div style="background: rgba(29, 31, 35, 0.3); border-radius: 12px; padding: 20px; border: 1px solid #2f3336; margin-bottom: 16px;">
    <p style="color: #71767b; font-size: 14px; margin: 0; line-height: 1.5;">
        All <code style="background: #0f1419; padding: 2px 6px; border-radius: 4px; color: #1d9bf0;">Function&lt;I,O&gt;</code> beans are automatically exposed as MCP tools via Spring AI.
    </p>
</div>
```

### Stat Card
```html
<div style="background: rgba(29, 31, 35, 0.3); border-radius: 12px; padding: 16px; border: 1px solid #2f3336;">
    <div style="color: #71767b; font-size: 13px; margin-bottom: 4px;">
        Tool Calls
    </div>
    <div style="font-size: 24px; font-weight: 700; color: #e7e9ea;">
        0 calls
    </div>
</div>
```

### Stats Grid
```html
<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 24px;">
    <div style="background: rgba(29, 31, 35, 0.3); border-radius: 12px; padding: 16px; border: 1px solid #2f3336;">
        <div style="color: #71767b; font-size: 13px; margin-bottom: 4px;">Tool Calls</div>
        <div style="font-size: 24px; font-weight: 700; color: #e7e9ea;">0 calls</div>
    </div>
    <div style="background: rgba(29, 31, 35, 0.3); border-radius: 12px; padding: 16px; border: 1px solid #2f3336;">
        <div style="color: #71767b; font-size: 13px; margin-bottom: 4px;">Successful Calls</div>
        <div style="font-size: 24px; font-weight: 700; color: #e7e9ea;">0 calls</div>
    </div>
    <div style="background: rgba(29, 31, 35, 0.3); border-radius: 12px; padding: 16px; border: 1px solid #2f3336;">
        <div style="color: #71767b; font-size: 13px; margin-bottom: 4px;">Failed Calls</div>
        <div style="font-size: 24px; font-weight: 700; color: #e7e9ea;">0 errors</div>
    </div>
</div>
```

---

## 🎛️ Logger Level Buttons

```html
<div style="display: flex; gap: 8px;">
    <button style="padding: 6px 12px; background: #2f3336; border: 1px solid #2f3336; border-radius: 6px; color: #71767b; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s;">
        OFF
    </button>
    <button style="padding: 6px 12px; background: #2f3336; border: 1px solid #2f3336; border-radius: 6px; color: #71767b; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s;">
        ERROR
    </button>
    <button style="padding: 6px 12px; background: #2f3336; border: 1px solid #2f3336; border-radius: 6px; color: #71767b; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s;">
        WARN
    </button>
    <button class="active" style="padding: 6px 12px; background: #1d9bf0; border: 1px solid #1d9bf0; border-radius: 6px; color: white; font-size: 12px; font-weight: 600; cursor: pointer;">
        INFO
    </button>
    <button style="padding: 6px 12px; background: #2f3336; border: 1px solid #2f3336; border-radius: 6px; color: #71767b; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s;">
        DEBUG
    </button>
    <button style="padding: 6px 12px; background: #2f3336; border: 1px solid #2f3336; border-radius: 6px; color: #71767b; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s;">
        TRACE
    </button>
</div>
```

---

## 🔧 Collapsible Tool Card

```html
<div style="background: rgba(29, 31, 35, 0.3); border-radius: 12px; padding: 16px; border: 1px solid #2f3336; margin-bottom: 12px; cursor: pointer; transition: all 0.2s;">
    <div style="display: flex; justify-content: space-between; align-items: center;">
        <h3 style="font-size: 16px; font-weight: 600; color: #1d9bf0; margin: 0;">
            analyzeErrors
        </h3>
        <span style="transition: transform 0.3s;">▼</span>
    </div>
    <!-- Expanded content goes here when clicked -->
</div>
```

---

## ⚠️ Error States

### Error Container
```html
<div style="background: #200a0a; border: 1px solid #5b1f1f; border-radius: 12px; padding: 16px; color: #f4212e;">
    <strong>Error:</strong> Failed to connect to database
</div>
```

### Error Badge
```html
<span style="display: inline-block; padding: 4px 8px; background: #5b1f1f; color: #f4212e; border-radius: 4px; font-size: 12px; font-weight: 600;">
    CRITICAL
</span>
```

---

## ⏳ Loading States

```html
<div style="text-align: center; padding: 40px; color: #71767b;">
    <div style="margin-bottom: 12px;">Loading tools...</div>
    <div style="font-size: 12px;">Discovering Function beans in the application context</div>
</div>
```

---

## 💻 Code Elements

### Inline Code
```html
<code style="background: #0f1419; padding: 2px 6px; border-radius: 4px; color: #1d9bf0; font-family: 'Courier New', monospace; font-size: 13px;">
    Function&lt;I,O&gt;
</code>
```

### Code Block
```html
<div style="background: #0f1419; border-radius: 8px; padding: 16px; border: 1px solid #2f3336; font-family: 'Courier New', monospace; font-size: 13px; color: #e7e9ea; overflow-x: auto;">
    <pre style="margin: 0;">public class Example {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}</pre>
</div>
```

---

## 📋 Complete Page Template

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Server Details</title>
    
    <!-- Google Fonts -->
    <link href="https://fonts.googleapis.com/css2?family=Oxanium:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    
    <!-- Starry Background -->
    <link rel="stylesheet" href="/css/starry-background.css">
    
    <style>
        :root {
            --font-sans: "Oxanium", sans-serif;
            --button-border-radius: 12px;
        }
        
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: var(--font-sans);
            background: #000000;
            color: #e7e9ea;
            line-height: 1.5;
            position: relative;
        }
        
        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 20px;
            position: relative;
            z-index: 1;
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <div style="background: rgba(22, 24, 28, 0.3); border-radius: 16px; padding: 24px; margin-bottom: 20px; border: 1px solid #2f3336;">
            <h1 style="font-size: 24px; font-weight: 700; margin-bottom: 8px; color: #e7e9ea;">
                Server Details
            </h1>
        </div>
        
        <!-- Tabs -->
        <div style="background: rgba(22, 24, 28, 0.3); border-radius: 16px; border: 1px solid #2f3336; overflow: hidden;">
            <!-- Tab Header -->
            <div style="display: flex; border-bottom: 1px solid #2f3336;">
                <button style="flex: 1; padding: 16px 20px; background: transparent; border: none; color: #1d9bf0; font-size: 15px; font-weight: 600; cursor: pointer; position: relative;">
                    Tab 1
                    <span style="position: absolute; bottom: 0; left: 0; right: 0; height: 4px; background: #1d9bf0;"></span>
                </button>
            </div>
            
            <!-- Tab Content -->
            <div style="padding: 24px; min-height: 500px;">
                <!-- Your content here -->
            </div>
        </div>
    </div>
    
    <!-- Starry Background Script -->
    <script src="/js/starry-background.js"></script>
</body>
</html>
```

---

## 🎨 CSS Hover States (JavaScript)

```javascript
// Add hover effects dynamically
document.querySelectorAll('button').forEach(button => {
    button.addEventListener('mouseenter', function() {
        if (this.style.background === 'rgb(29, 155, 240)') { // #1d9bf0
            this.style.background = '#1a8cd8';
        }
    });
    
    button.addEventListener('mouseleave', function() {
        if (this.style.background === 'rgb(26, 140, 216)') { // #1a8cd8
            this.style.background = '#1d9bf0';
        }
    });
});
```

---

**End of Component Examples**
