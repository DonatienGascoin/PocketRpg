# UI Designer Guide

> **Summary:** Visually design in-game UI layouts with the UI Designer panel. Create canvases, panels, buttons, text, and images with anchor-based positioning.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [UI Element Hierarchy](#ui-element-hierarchy)
6. [Workflows](#workflows)
7. [UITransform & Anchoring](#uitransform--anchoring)
8. [Tips & Best Practices](#tips--best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Code Integration](#code-integration)
11. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create UI Canvas | Hierarchy → **+** → **Create UI** → **Canvas** |
| Add UI element | Hierarchy → **+** → **Create UI** → Panel/Image/Button/Text |
| Move UI element | Drag in the UI Designer viewport |
| Resize UI element | Drag the edge/corner handles |
| Edit properties | Select element → edit in Inspector |
| Set anchor | Edit anchor values in the UITransform Inspector |

---

## Overview

The UI Designer provides a visual viewport for designing in-game user interfaces. Unlike the Scene View (which shows the game world), the UI Designer shows a screen-space canvas where you position UI elements using anchors, pivots, and offsets.

**UI elements available:**
- **Canvas** — Root container (required, one per UI hierarchy)
- **Panel** — Container for grouping and layout
- **Image** — Displays a sprite or colored rectangle
- **Button** — Interactive button with visual states
- **Text** — Text display with font, size, and color options

All UI elements use **UITransform** for positioning, which supports anchor-based layout that adapts to different screen sizes.

---

## Opening the Panel

Click the **UI Designer** tab in the main workspace (next to Scene, Game, Animation, Animator).

---

## Interface Overview

```
┌──────────────────────────────────────────────────────┐
│ [Scene] [Game] [Animation] [Animator] [UI Designer]   │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │ Canvas (reference resolution)                  │  │
│  │                                                │  │
│  │  ┌──────────────┐                              │  │
│  │  │  Health Bar   │                              │  │
│  │  └──────────────┘                              │  │
│  │                                                │  │
│  │                        ┌─────────────────────┐ │  │
│  │                        │    [Start Game]     │ │  │
│  │                        │    [Settings]       │ │  │
│  │                        │    [Quit]           │ │  │
│  │                        └─────────────────────┘ │  │
│  │                                                │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
└──────────────────────────────────────────────────────┘
```

The viewport shows:
- **Canvas bounds** — The reference resolution rectangle
- **UI elements** — Rendered with their sprites, colors, and text
- **Selection gizmos** — Resize handles and anchor/pivot visualization
- **Element outlines** — Bounding boxes for layout debugging

---

## UI Element Hierarchy

UI elements must follow a strict hierarchy:

```
Canvas (root)
├── Panel (container)
│   ├── Image
│   ├── Text
│   └── Button
├── Panel
│   └── Text
└── Image
```

**Rules:**
- Every UI hierarchy starts with a **Canvas**
- **Panel**, **Image**, **Button**, and **Text** must be children of a Canvas (directly or nested)
- Elements render in tree order — later children render on top

---

## Workflows

### Creating a UI Layout

1. In the Hierarchy, click **+** → **Create UI** → **Canvas**
2. Select the Canvas entity — a UICanvas component is auto-added
3. Add child elements: **+** → **Create UI** → choose element type
4. Make sure new elements are **children** of the Canvas in the Hierarchy (drag to reparent if needed)
5. Switch to the **UI Designer** tab to see the visual layout

### Positioning Elements

**In the UI Designer viewport:**
1. Click an element to select it
2. Drag to move it
3. Drag edge or corner handles to resize

**In the Inspector (UITransform):**
1. Select a UI element
2. Edit anchor, pivot, position offsets, and size values directly

### Editing Element Properties

Select any UI element and use the Inspector:

| Component | Properties |
|-----------|-----------|
| **UITransform** | Anchors, pivot, position offsets, size |
| **UIPanel** | Background color, border |
| **UIImage** | Sprite, color tint, 9-slice toggle |
| **UIButton** | Normal/hover/pressed colors or sprites, click key |
| **UIText** | Text content, font, size, color, alignment |
| **UICanvas** | Reference resolution, scale mode |

---

## UITransform & Anchoring

Every UI element has a **UITransform** component (instead of a regular Transform) that controls layout:

### Anchors

Anchors define where the element attaches to its parent. They are normalized values (0-1):

| Anchor preset | Min anchor | Max anchor | Behavior |
|---------------|-----------|-----------|----------|
| Top-left | (0, 1) | (0, 1) | Fixed to top-left corner |
| Center | (0.5, 0.5) | (0.5, 0.5) | Fixed to center |
| Stretch horizontal | (0, 0.5) | (1, 0.5) | Stretches with parent width |
| Stretch all | (0, 0) | (1, 1) | Fills parent completely |

### Pivot

The pivot point (0-1) determines the element's origin for positioning and rotation:
- **(0, 0)** — Bottom-left corner
- **(0.5, 0.5)** — Center (default)
- **(1, 1)** — Top-right corner

### Offsets

Position offsets are relative to the anchored position:
- When anchors are a point (min == max): offsets are pixel distance from anchor
- When anchors are a range (min != max): offsets define padding from edges

### Size

The element's pixel dimensions. When anchors stretch, size is computed from offsets instead.

---

## Tips & Best Practices

- **Design at reference resolution**: Set the Canvas reference resolution to your target (e.g., 320x180 for pixel art). Elements scale automatically.
- **Use anchors for responsive layout**: Elements anchored to corners stay in corners at any resolution
- **Group with Panels**: Use Panel elements as containers to organize related UI elements
- **9-slice for buttons**: Enable 9-slice on UIImage/UIButton sprites to scale without stretching corners
- **Test at different sizes**: Resize the Game View to verify your layout adapts correctly
- **Name your elements**: "HealthBar", "ScoreText", "MainMenu_Panel" instead of generic names

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| UI element not visible | Check it's a child of a Canvas, and the Canvas entity is in the scene |
| Element in wrong position | Check anchor and offset values in UITransform |
| Text not showing | Verify font asset is assigned and text content is not empty |
| Button not clickable in game | Check UIButton key binding and that InputSystem is configured |
| UI not scaling with resolution | Check Canvas scale mode and that elements use proper anchors |
| Can't select element in designer | Elements may overlap — use the Hierarchy to select directly |
| Undo not working for UI edits | UI transform drags are undoable — press Ctrl+Z |

---

## Code Integration

Most UI setup is done through the Inspector. Use code for dynamic updates at runtime.

### Updating UI Text

```java
UIText scoreText = gameObject.getComponent(UIText.class);
scoreText.setText("Score: " + score);
```

### Showing/Hiding UI Elements

```java
// Toggle visibility via the GameObject
gameObject.setActive(false); // Hide
gameObject.setActive(true);  // Show
```

### Responding to Button Clicks

```java
UIButton button = gameObject.getComponent(UIButton.class);
// Buttons are triggered via input keys configured in the inspector
// Check Input.isActionPressed() for the button's configured key
```

---

## Related

- [Hierarchy Panel Guide](hierarchy-panel-guide.md) — Creating and organizing UI entities
- [Inspector Panel Guide](inspector-panel-guide.md) — Editing UITransform and component properties
- [Components Guide](components-guide.md) — UI component details
- [Scene View Guide](scene-view-guide.md) — The world-space viewport (vs screen-space UI)
