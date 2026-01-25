# Transform Gizmos Implementation Plan

Replace the current yellow selection border with Unity-style interactive gizmos for Move, Rotate, and Scale operations.

## Overview

**Goal**: Add three separate transform tools with visual gizmos:
- **Move Tool (W)**: Axis arrows (red X, green Y) + center square for free movement
- **Rotate Tool (E)**: Circular handle for Z-axis rotation
- **Scale Tool (R)**: Corner/edge handles for uniform and non-uniform scaling

**Scope**: Single entity selection only (no multi-selection transform)

## Impact on SelectionTool

### Current SelectionTool Responsibilities
| Feature | Keep? | Notes |
|---------|-------|-------|
| Click-to-select entity | **YES** | Core functionality |
| Click-empty-to-deselect | **YES** | Core functionality |
| Drag-to-move entity | **REMOVE** | Has no undo support; MoveTool replaces this properly |
| Collision trigger tile selection | **YES** | Special feature for collision layer |
| Yellow selection highlight | **REPLACE** | Gizmo tools render their own overlays |
| Hover highlight (cyan) | **YES** | Useful feedback, keep it |
| Corner handles rendering | **REMOVE** | Gizmo tools handle interactive visuals |

### What SelectionTool Becomes
SelectionTool (V) becomes a **pure selection tool**:
- Click entity → select it
- Click empty space → deselect
- Click trigger tile → select trigger (collision layer mode)
- Hover feedback (cyan outline on hover)
- **No transforms** - just selection

The yellow border with corner handles is removed entirely from SelectionTool. Transform gizmo tools show their own visual overlays when active.

### Transform Tools Can Also Select
Following Unity's pattern, each transform tool (Move/Rotate/Scale) can also click-to-select entities. This means:
- User rarely needs to switch back to SelectionTool
- Clicking an entity with MoveTool active both selects it AND allows immediate dragging
- This is more intuitive than forcing V → W workflow

## Architecture

### New Classes (in `editor/gizmos/`)

| File | Purpose |
|------|---------|
| `GizmoRenderer.java` | Shared rendering utilities for all gizmos |
| `GizmoColors.java` | Color constants (red X, green Y, yellow highlight) |
| `TransformGizmoTool.java` | Abstract base class for transform tools with selection support |
| `MoveTool.java` | Move tool with axis arrows |
| `RotateTool.java` | Rotate tool with rotation ring |
| `ScaleTool.java` | Scale tool with resize handles |

### Modified Classes

| File | Change |
|------|--------|
| `EditorToolController.java` | Register new tools, expose getters |
| `EditorShortcutHandlersImpl.java` | Add W/E/R shortcuts |
| `SelectionTool.java` | Remove drag-to-move, remove yellow border rendering |
| `HierarchySelectionHandler.java` | Activate MoveTool instead of SelectionTool after hierarchy selection |

## Phase 1: Core Infrastructure

### 1.1 Create GizmoColors utility
```java
public class GizmoColors {
    // Axis colors
    public static final int X_AXIS = color(0.9f, 0.2f, 0.2f, 1f);      // Red
    public static final int Y_AXIS = color(0.2f, 0.9f, 0.2f, 1f);      // Green
    public static final int X_AXIS_HOVER = color(1f, 0.4f, 0.4f, 1f);  // Light red
    public static final int Y_AXIS_HOVER = color(0.4f, 1f, 0.4f, 1f);  // Light green

    // Common colors
    public static final int CENTER = color(1f, 1f, 0.3f, 1f);          // Yellow
    public static final int SELECTION_BOUNDS = color(1f, 1f, 1f, 0.3f); // Subtle white
}
```

### 1.2 Create GizmoRenderer utility
Shared drawing utilities:
- `drawArrow(drawList, x1, y1, x2, y2, color, thickness)` - Arrow with head
- `drawCircleOutline(drawList, cx, cy, radius, color, thickness)` - Circle
- `drawSquareHandle(drawList, x, y, size, color)` - Square handle
- `isPointNearLine(px, py, x1, y1, x2, y2, threshold)` - Hit testing
- `isPointNearCircle(px, py, cx, cy, radius, threshold)` - Hit testing
- `getEntityScreenCenter(entity, camera, viewport)` - Common calculation

### 1.3 Create TransformGizmoTool base class
Abstract base implementing `EditorTool` and `ViewportAwareTool`:
```java
public abstract class TransformGizmoTool implements EditorTool, ViewportAwareTool {
    // Shared state
    protected EditorScene scene;
    protected EditorCamera camera;
    protected EditorSelectionManager selectionManager;
    protected float viewportX, viewportY, viewportWidth, viewportHeight;

    // Selection support (like Unity - transform tools can also select)
    protected void handleClick(int tileX, int tileY) {
        EditorGameObject entity = scene.findEntityAt(worldX, worldY);
        if (entity != null) {
            selectionManager.selectEntity(entity);
        } else {
            selectionManager.clearSelection();
        }
    }

    // Subclasses implement
    abstract void renderGizmo(ImDrawList drawList, EditorGameObject entity);
    abstract void handleGizmoDrag(...);
}
```

## Phase 2: Move Tool

### 2.1 Create MoveTool class

**Gizmo Visual**:
```
        ▲ (Y axis - green)
        │
        │
    ────■──── → (X axis - red)
        │
        │
```
- Arrow length: ~60px screen space (constant)
- Center square: ~10px (yellow)
- Arrow heads: small triangles at ends

**Interaction States**:
```java
enum DragMode { NONE, X_AXIS, Y_AXIS, FREE }
private DragMode dragMode = NONE;
private DragMode hoveredElement = NONE;  // For visual feedback
private Vector3f dragStartPosition;       // For undo
private Vector2f dragStartMouse;          // Track mouse delta
```

**Hover Detection** (in order):
1. Center square → FREE mode
2. Near X axis line → X_AXIS mode
3. Near Y axis line → Y_AXIS mode

**Drag Behavior**:
- X_AXIS: Only apply mouse delta X to entity position
- Y_AXIS: Only apply mouse delta Y to entity position
- FREE: Apply both axes

### 2.2 Undo integration
```java
@Override
public void onMouseUp(int tileX, int tileY, int button) {
    if (dragMode != NONE && draggedEntity != null) {
        Vector3f newPosition = draggedEntity.getPosition();
        if (!dragStartPosition.equals(newPosition)) {
            UndoManager.getInstance().push(
                new MoveEntityCommand(draggedEntity, dragStartPosition, newPosition)
            );
        }
    }
    dragMode = NONE;
}
```

## Phase 3: Rotate Tool

### 3.1 Create RotateTool class

**Gizmo Visual**:
```
       ╭───╮
      ╱     ╲
     │   ●───┼  (line to handle shows current angle)
      ╲     ╱
       ╰───╯
```
- Circle radius: ~50px screen space
- Small handle on circle at current rotation
- Line from center to handle

**Interaction**:
- Hover on circle → highlight
- Drag anywhere on circle → rotate based on angle change

**Angle Calculation**:
```java
// Get angle from entity center to mouse
float angle = Math.atan2(mouseY - centerY, mouseX - centerX);
float angleDelta = angle - dragStartAngle;
float newRotation = dragStartRotation + Math.toDegrees(angleDelta);
entity.setRotation(0, 0, newRotation);
```

**Optional**: Shift key snaps to 15° increments

### 3.2 Undo integration
Use `RotateEntityCommand` via `UndoManager.push()`

## Phase 4: Scale Tool

### 4.1 Create ScaleTool class

**Gizmo Visual**:
```
    ■─────■─────■
    │           │
    ■     ●     ■
    │           │
    ■─────■─────■
```
- 4 corner handles (■) - uniform scale
- 4 edge handles (■) - single-axis scale
- Uses entity bounds (respects rotation)

**Handle Enum**:
```java
enum ScaleHandle {
    NONE,
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
}
```

**Corner Drag** (uniform):
- Scale both X and Y by same factor
- Factor = distance(mouse, entityCenter) / distance(dragStart, entityCenter)

**Edge Drag** (single axis):
- TOP/BOTTOM: Scale Y only
- LEFT/RIGHT: Scale X only

### 4.2 Undo integration
Use `ScaleEntityCommand` via `UndoManager.push()`

## Phase 5: Tool Registration & Shortcuts

### 5.1 Update EditorToolController
```java
@Getter private MoveTool moveTool;
@Getter private RotateTool rotateTool;
@Getter private ScaleTool scaleTool;

public void createTools() {
    // Existing tools...

    // Transform gizmo tools
    moveTool = new MoveTool();
    moveTool.setScene(scene);
    moveTool.setCamera(context.getCamera());
    moveTool.setSelectionManager(context.getSelectionManager());
    toolManager.registerTool(moveTool);

    rotateTool = new RotateTool();
    // ... same setup
    toolManager.registerTool(rotateTool);

    scaleTool = new ScaleTool();
    // ... same setup
    toolManager.registerTool(scaleTool);
}
```

### 5.2 Update EditorShortcutHandlersImpl
Add to `registerShortcuts()`:
```java
registerShortcut("W", "Move Tool", () -> {
    context.getToolManager().setActiveTool(toolController.getMoveTool());
});
registerShortcut("E", "Rotate Tool", () -> {
    context.getToolManager().setActiveTool(toolController.getRotateTool());
});
registerShortcut("R", "Scale Tool", () -> {
    context.getToolManager().setActiveTool(toolController.getScaleTool());
});
```

### 5.3 Update SelectionTool
Remove from `SelectionTool.java`:
- `draggedEntity`, `dragOffset`, `isDragging` fields
- `onMouseDrag()` - no longer handles dragging
- `renderSelectionHighlight()` - gizmos replace this
- Corner handle rendering

Keep in `SelectionTool.java`:
- Click-to-select logic
- Click-to-deselect logic
- Trigger tile selection (collision layer)
- Hover highlight rendering (cyan outline)

### 5.4 Update HierarchySelectionHandler
Change `activateSelectionTool()` to activate MoveTool instead:
```java
private void activateTransformTool() {
    if (toolManager != null && moveTool != null) {
        toolManager.setActiveTool(moveTool);  // or remember last used
    }
}
```

### 5.5 Update EditorApplication
Default tool on startup: MoveTool instead of SelectionTool
```java
context.getToolManager().setActiveTool(toolController.getMoveTool());
```

## Phase 6: Polish

### 6.1 Cursor feedback
| Tool | Hover State | Cursor |
|------|-------------|--------|
| Move | X axis | `ResizeEW` |
| Move | Y axis | `ResizeNS` |
| Move | Center | `ResizeAll` |
| Rotate | Ring | `Hand` |
| Scale | Corner | `ResizeNWSE` or `ResizeNESW` |
| Scale | Top/Bottom | `ResizeNS` |
| Scale | Left/Right | `ResizeEW` |

### 6.2 Visual feedback during drag
Show delta text near cursor:
- Move: `+2.5, -1.0`
- Rotate: `+45.0°`
- Scale: `1.5x` or `1.2x, 0.8y`

### 6.3 Selection bounds
Draw subtle bounds (thin white outline) for all gizmo tools to show entity extent.

## Files Summary

### New Files (in `src/main/java/com/pocket/rpg/editor/gizmos/`)
```
GizmoColors.java           (~30 lines)
GizmoRenderer.java         (~150 lines)
TransformGizmoTool.java    (~150 lines)
MoveTool.java              (~250 lines)
RotateTool.java            (~200 lines)
ScaleTool.java             (~300 lines)
```

### Modified Files
| File | Changes |
|------|---------|
| `SelectionTool.java` | Remove drag/move logic, remove yellow border |
| `EditorToolController.java` | Register new tools, add getters |
| `EditorShortcutHandlersImpl.java` | Add W/E/R shortcuts |
| `HierarchySelectionHandler.java` | Activate MoveTool after hierarchy selection |
| `EditorApplication.java` | Default to MoveTool on startup |

## Testing Strategy

### Visual Tests
1. Select entity with V key → Only hover highlight, no gizmo
2. Press W → Move gizmo appears (red/green arrows + center)
3. Press E → Rotate gizmo appears (ring)
4. Press R → Scale gizmo appears (handles)
5. Hover each gizmo element → Highlight feedback

### Interaction Tests
1. **Move Tool**:
   - Drag X arrow → Entity moves horizontally only
   - Drag Y arrow → Entity moves vertically only
   - Drag center → Entity moves freely
   - Click empty space → Deselects entity

2. **Rotate Tool**:
   - Drag on ring → Entity rotates
   - Shift+drag → Snaps to 15° (if implemented)
   - Click other entity → Selects new entity

3. **Scale Tool**:
   - Drag corner → Uniform scale
   - Drag edge → Single-axis scale

### Undo Tests
1. Move entity → Ctrl+Z → Returns to original position
2. Rotate entity → Ctrl+Z → Returns to original rotation
3. Scale entity → Ctrl+Z → Returns to original scale
4. Multiple moves → Multiple Ctrl+Z → Each undone separately

### Edge Cases
- Gizmos update when zooming/panning camera
- Gizmos work with rotated entities
- Gizmos respect sprite pivot points
- Switching tools preserves selection
- Deselect hides gizmo
