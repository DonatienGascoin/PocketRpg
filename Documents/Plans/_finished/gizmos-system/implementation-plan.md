# Gizmos System Implementation Plan

## Overview

Implement a Unity-like gizmos system that allows components to visualize their properties in the scene view. Supports two modes:
- **Gizmos** - Always drawn for all entities in the scene
- **GizmosSelected** - Only drawn for selected entities

### Example Use Cases
| Component | Gizmos (Always) | GizmosSelected (When Selected) |
|-----------|-----------------|-------------------------------|
| SpriteRenderer | - | Pivot point (circle), bounds rectangle |
| GridMovement | - | Current tile highlight, tile size |
| AmbientZone | - | Radius circle |
| Transform | - | Position crosshair |

## Architecture

### 1. Gizmo Interfaces

Components opt-in to gizmo rendering by implementing interfaces:

```java
// Always drawn for all entities
public interface GizmoDrawable {
    void onDrawGizmos(GizmoContext ctx);
}

// Only drawn when entity is selected
public interface GizmoDrawableSelected {
    void onDrawGizmosSelected(GizmoContext ctx);
}
```

### 2. GizmoContext

Provides drawing primitives to components:

```java
public class GizmoContext {
    // Drawing primitives
    void drawLine(float x1, float y1, float x2, float y2);
    void drawRect(float x, float y, float width, float height);
    void drawRectFilled(float x, float y, float width, float height);
    void drawCircle(float x, float y, float radius);
    void drawCircleFilled(float x, float y, float radius);
    void drawCrossHair(float x, float y, float size);
    void drawDiamond(float x, float y, float size);
    void drawTileHighlight(int tileX, int tileY, float tileSize);

    // Color/style
    void setColor(float r, float g, float b, float a);
    void setColor(int imguiColor);
    void setThickness(float thickness);

    // State
    Transform getTransform();  // Current entity transform
    EditorCamera getCamera();  // For coordinate conversion
}
```

### 3. GizmoRenderer

Orchestrates gizmo rendering for all entities:

```java
public class GizmoRenderer {
    void render(EditorScene scene, EditorCamera camera,
                float viewportX, float viewportY,
                float viewportWidth, float viewportHeight);
}
```

## Integration Points

### SceneViewport Integration

Add gizmo rendering after tool overlay in `SceneViewport.java`:

```java
public void renderToolOverlay() {
    // ... existing tool overlay code ...

    // NEW: Render gizmos
    if (scene != null && gizmoRenderer != null) {
        gizmoRenderer.render(scene, camera, viewportX, viewportY,
                            viewportWidth, viewportHeight);
    }
}
```

### Component Integration

Components implement the interfaces (examples):

**SpriteRenderer:**
```java
@Override
public void onDrawGizmosSelected(GizmoContext ctx) {
    // Draw pivot point
    ctx.setColor(GizmoColors.PIVOT);
    float pivotX = getEffectiveOriginX();
    float pivotY = getEffectiveOriginY();
    ctx.drawCrossHair(worldX + pivotX * width, worldY + pivotY * height, 0.2f);

    // Draw bounds
    ctx.setColor(GizmoColors.BOUNDS);
    ctx.drawRect(worldX, worldY, width, height);
}
```

**AmbientZone:**
```java
@Override
public void onDrawGizmosSelected(GizmoContext ctx) {
    ctx.setColor(GizmoColors.AUDIO_ZONE);
    Vector3f pos = getTransform().getWorldPosition();
    ctx.drawCircle(pos.x, pos.y, radius);
}
```

## Phases

### Phase 1: Core Infrastructure
- [x] Create `GizmoDrawable` interface
- [x] Create `GizmoDrawableSelected` interface
- [x] Create `GizmoContext` class with drawing primitives
- [x] Create `GizmoColors` constants class
- [x] Create `GizmoRenderer` orchestrator

### Phase 2: Viewport Integration
- [x] Add `GizmoRenderer` to `SceneViewport`
- [x] Call gizmo rendering in `renderToolOverlay()`
- [x] Add gizmo visibility toggle to editor config/menu

### Phase 3: Built-in Component Gizmos
- [x] Implement `GizmoDrawableSelected` on `SpriteRenderer` (pivot, bounds)
- [x] Implement `GizmoDrawableSelected` on `Transform` (position crosshair)
- [x] Implement `GizmoDrawableSelected` on `GridMovement` (tile highlight)
- [x] Implement `GizmoDrawableSelected` on `AmbientZone` (radius circle)

### Phase 4: Polish & Testing
- [x] Add gizmo toggle in View menu (G shortcut label)
- [ ] Test with multiple selected entities
- [ ] Verify gizmos work with camera zoom/pan
- [ ] Code review

## Files to Modify/Create

| File | Change |
|------|--------|
| `editor/gizmos/GizmoDrawable.java` | **NEW** - Interface for always-drawn gizmos |
| `editor/gizmos/GizmoDrawableSelected.java` | **NEW** - Interface for selection gizmos |
| `editor/gizmos/GizmoContext.java` | **NEW** - Drawing context with primitives |
| `editor/gizmos/GizmoColors.java` | **NEW** - Standard gizmo colors |
| `editor/gizmos/GizmoRenderer.java` | **NEW** - Main renderer orchestrator |
| `editor/ui/SceneViewport.java` | Add gizmo rendering call |
| `editor/ui/EditorMenuBar.java` | Add gizmo visibility toggle |
| `components/SpriteRenderer.java` | Implement GizmoDrawableSelected |
| `components/Transform.java` | Implement GizmoDrawableSelected |
| `components/GridMovement.java` | Implement GizmoDrawableSelected |
| `components/AmbientZone.java` | Implement GizmoDrawableSelected |

## GizmoContext Drawing Primitives

All coordinates are in **world space**. GizmoContext handles world-to-screen conversion internally.

```java
public class GizmoContext {
    private final ImDrawList drawList;
    private final EditorCamera camera;
    private final float viewportX, viewportY;
    private int currentColor = GizmoColors.DEFAULT;
    private float currentThickness = 1.0f;

    // Convert world position to screen position
    private Vector2f worldToScreen(float worldX, float worldY) {
        Vector2f screen = camera.worldToScreen(worldX, worldY);
        return new Vector2f(viewportX + screen.x, viewportY + screen.y);
    }

    public void drawLine(float x1, float y1, float x2, float y2) {
        Vector2f p1 = worldToScreen(x1, y1);
        Vector2f p2 = worldToScreen(x2, y2);
        drawList.addLine(p1.x, p1.y, p2.x, p2.y, currentColor, currentThickness);
    }

    public void drawRect(float x, float y, float width, float height) {
        Vector2f tl = worldToScreen(x, y);
        Vector2f br = worldToScreen(x + width, y + height);
        drawList.addRect(tl.x, tl.y, br.x, br.y, currentColor, 0, 0, currentThickness);
    }

    public void drawCircle(float x, float y, float radius) {
        Vector2f center = worldToScreen(x, y);
        float screenRadius = radius * camera.getZoom();
        drawList.addCircle(center.x, center.y, screenRadius, currentColor, 0, currentThickness);
    }

    public void drawCrossHair(float x, float y, float size) {
        drawLine(x - size, y, x + size, y);
        drawLine(x, y - size, x, y + size);
    }

    // ... more primitives
}
```

## GizmoRenderer Flow

```java
public class GizmoRenderer {
    public void render(EditorScene scene, EditorCamera camera,
                       float viewportX, float viewportY,
                       float viewportWidth, float viewportHeight) {

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(viewportX, viewportY,
                              viewportX + viewportWidth,
                              viewportY + viewportHeight, true);

        GizmoContext ctx = new GizmoContext(drawList, camera, viewportX, viewportY);

        // Phase 1: Draw "always" gizmos for ALL entities
        for (EditorGameObject entity : scene.getEntities()) {
            for (Component component : entity.getComponents()) {
                if (component instanceof GizmoDrawable gd) {
                    ctx.setTransform(entity.getTransform());
                    gd.onDrawGizmos(ctx);
                }
            }
        }

        // Phase 2: Draw "selected" gizmos for SELECTED entities only
        for (EditorGameObject entity : scene.getSelectedEntities()) {
            for (Component component : entity.getComponents()) {
                if (component instanceof GizmoDrawableSelected gds) {
                    ctx.setTransform(entity.getTransform());
                    gds.onDrawGizmosSelected(ctx);
                }
            }
        }

        drawList.popClipRect();
    }
}
```

## GizmoColors

```java
public final class GizmoColors {
    public static final int DEFAULT = ImGui.colorConvertFloat4ToU32(0.0f, 1.0f, 0.0f, 0.8f);
    public static final int BOUNDS = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.6f);
    public static final int PIVOT = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1.0f, 1.0f);
    public static final int POSITION = ImGui.colorConvertFloat4ToU32(1.0f, 0.4f, 0.4f, 1.0f);
    public static final int TILE = ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.4f);
    public static final int AUDIO_ZONE = ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 1.0f, 0.5f);

    private GizmoColors() {}
}
```

## Testing Strategy

1. **Manual Testing:**
   - Select a SpriteRenderer entity - verify pivot and bounds appear
   - Select an AmbientZone entity - verify radius circle appears
   - Select multiple entities - verify all show gizmos
   - Zoom/pan camera - verify gizmos scale/move correctly
   - Toggle gizmo visibility - verify they hide/show

2. **Edge Cases:**
   - Entity with no gizmo-drawable components
   - Disabled components
   - Entities off-screen (clipping)
   - Nested entities (hierarchy)

## Notes

- Following existing pattern from `UIDesignerGizmoDrawer`
- Uses ImGui DrawList for rendering (no OpenGL needed)
- World-to-screen conversion via `EditorCamera`
- Clipping ensures gizmos don't draw outside viewport
