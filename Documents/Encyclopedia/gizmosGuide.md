# Gizmos Guide

> **Summary:** Gizmos are visual helpers drawn in the Scene View to visualize component properties like bounds, pivots, and radii. Components can override methods to draw custom gizmos.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Toggling Gizmos](#toggling-gizmos)
4. [Sizing Behavior](#sizing-behavior)
5. [Built-in Component Gizmos](#built-in-component-gizmos)
6. [Adding Gizmos to Custom Components](#adding-gizmos-to-custom-components)
7. [GizmoContext API](#gizmocontext-api)
8. [Tips & Best Practices](#tips--best-practices)
9. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Toggle gizmos visibility | **View > Show Gizmos** (G) |
| Add gizmos to a component | Override `onDrawGizmosSelected()` |
| Draw with constant screen size | Use `ctx.getHandleSize(pixels)` |
| Draw world-space geometry | Use `ctx.drawRect()`, `ctx.drawCircle()` directly |

---

## Overview

Gizmos are editor-only visualizations drawn over the Scene View. They help you see invisible component properties like:

- **Sprite bounds and pivot points**
- **Audio zone radii**
- **Collision shapes**
- **Trigger areas**

There are two types of gizmos:

| Type | Method | When Drawn |
|------|--------|------------|
| Always | `onDrawGizmos()` | For all entities, always |
| Selected | `onDrawGizmosSelected()` | Only when entity is selected |

Most gizmos use `onDrawGizmosSelected()` to avoid visual clutter.

---

## Toggling Gizmos

**Menu:** View > Show Gizmos

Gizmos can be toggled on/off globally. When disabled, no gizmos are drawn for any component.

---

## Sizing Behavior

Gizmos use two sizing strategies:

### World-Space (Scales with Zoom)

Used for geometry that represents actual measurements:
- Sprite bounds
- Audio zone radii
- Collision shapes

```java
// This rectangle scales when you zoom in/out
ctx.drawRect(x, y, width, height);
```

### Handle-Size (Constant Screen Appearance)

Used for markers and indicators that should remain visible at any zoom level. Uses `getHandleSize()` to calculate a world-space size that appears as a constant pixel size on screen.

```java
// This circle always appears ~8 pixels on screen
float size = ctx.getHandleSize(8);
ctx.drawCircle(x, y, size);
```

**How `getHandleSize()` works:**
- Takes desired screen pixels as input
- Returns a world-space size
- At zoom 1x: `getHandleSize(10)` might return 0.5 world units
- At zoom 2x: `getHandleSize(10)` returns 0.25 world units (appears same size)
- At zoom 0.5x: `getHandleSize(10)` returns 1.0 world units (appears same size)

This mimics Unity's `HandleUtility.GetHandleSize()`.

---

## Built-in Component Gizmos

### Transform
- **Red crosshair** at entity position
- Constant screen size (~16px)

### SpriteRenderer
- **Green rectangle** showing sprite bounds (accounts for scale, rotation, pivot)
- **Blue pivot point** (circle + crosshair) at entity position
- Bounds scale with zoom, pivot has constant screen size

### AmbientZone
- **Purple circle** showing trigger radius (world-space)
- **Magenta diamond** at center (constant screen size)

---

## Adding Gizmos to Custom Components

All components inherit empty `onDrawGizmos()` and `onDrawGizmosSelected()` methods. Override them to add visualization.

### Example: Trigger Zone Component

```java
public class TriggerZone extends Component {

    private float width = 2.0f;
    private float height = 2.0f;

    @Override
    public void onDrawGizmosSelected(GizmoContext ctx) {
        Transform transform = ctx.getTransform();
        if (transform == null) return;

        Vector3f pos = transform.getWorldPosition();

        // Draw zone bounds (world-space, scales with zoom)
        ctx.setColor(GizmoColors.TRIGGER);
        ctx.setThickness(2.0f);
        ctx.drawRectCentered(pos.x, pos.y, width, height);

        // Draw center marker (constant screen size)
        ctx.setColor(GizmoColors.TRIGGER_BORDER);
        float markerSize = ctx.getHandleSize(6);
        ctx.drawDiamondFilled(pos.x, pos.y, markerSize);
    }
}
```

### Example: Waypoint Component

```java
public class Waypoint extends Component {

    @Override
    public void onDrawGizmos(GizmoContext ctx) {
        // This draws for ALL waypoints, not just selected
        Transform transform = ctx.getTransform();
        if (transform == null) return;

        Vector3f pos = transform.getWorldPosition();

        ctx.setColor(GizmoColors.DEFAULT);
        float size = ctx.getHandleSize(10);
        ctx.drawDiamondFilled(pos.x, pos.y, size);
    }
}
```

---

## GizmoContext API

### Style

| Method | Description |
|--------|-------------|
| `setColor(int color)` | Set color using `GizmoColors` constant |
| `setColor(r, g, b, a)` | Set color using RGBA floats (0-1) |
| `setThickness(float)` | Set line thickness in pixels |

### Sizing

| Method | Description |
|--------|-------------|
| `getZoom()` | Current camera zoom level |
| `getHandleSize(float pixels)` | World size that appears as given pixel size |
| `getTransform()` | Current entity's Transform |

### Primitives (World-Space)

| Method | Description |
|--------|-------------|
| `drawLine(x1, y1, x2, y2)` | Line between two points |
| `drawRect(x, y, w, h)` | Rectangle outline |
| `drawRectFilled(x, y, w, h)` | Filled rectangle |
| `drawRectCentered(cx, cy, w, h)` | Rectangle centered on point |
| `drawCircle(x, y, radius)` | Circle outline |
| `drawCircleFilled(x, y, radius)` | Filled circle |
| `drawCrossHair(x, y, size)` | Plus sign (size = half-arm length) |
| `drawDiamond(x, y, size)` | Diamond outline |
| `drawDiamondFilled(x, y, size)` | Filled diamond |
| `drawArrow(x1, y1, x2, y2, headSize)` | Arrow with head |
| `drawPivotPoint(x, y, size)` | Circle + crosshair combo |

### Primitives (Screen-Space)

| Method | Description |
|--------|-------------|
| `drawCrossHairScreenSize(x, y, px)` | Crosshair with fixed pixel size |
| `drawDiamondFilledScreenSize(x, y, px)` | Diamond with fixed pixel size |
| `drawPivotPointScreenSize(x, y, px)` | Pivot with fixed pixel size |

### Colors

```java
GizmoColors.DEFAULT      // Green
GizmoColors.BOUNDS       // Semi-transparent green
GizmoColors.PIVOT        // Blue
GizmoColors.POSITION     // Red
GizmoColors.TILE         // Cyan
GizmoColors.AUDIO_ZONE   // Purple
GizmoColors.COLLISION    // Red (semi-transparent)
GizmoColors.TRIGGER      // Yellow (semi-transparent)
```

---

## Tips & Best Practices

- **Use `onDrawGizmosSelected`** for most gizmos to reduce visual clutter
- **Use `getHandleSize()`** for markers/indicators that need visibility at any zoom
- **Use world-space** for bounds/radii that represent actual distances
- **Get transform from context**: Always use `ctx.getTransform()`, not `getTransform()`
- **Set thickness to 2.0f** for better visibility (1px is too thin)
- **Reset color/thickness** at the start if you need specific values

---

## Related

- [Components Guide](components-guide.md)
- [Custom Inspector Guide](custom-inspector-guide.md)
