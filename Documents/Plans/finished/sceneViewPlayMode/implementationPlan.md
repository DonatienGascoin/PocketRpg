# Scene View Play Mode Support

## Overview

Define how the Scene viewport behaves during play mode. Two options are presented:
- **Option A**: Scene view can display and allow selection/inspection of runtime entities
- **Option B**: Scene view is disabled (greyed out) during play mode

The play-mode-inspector plan already covers Hierarchy and Inspector panels switching to runtime data. This plan addresses the Scene viewport specifically.

---

## Current State

- **SceneViewport** always renders `EditorScene` via `EditorSceneRenderer`
- **GameViewPanel** shows runtime output via `PlayModeController.getOutputTexture()`
- During play mode, SceneViewport shows frozen editor state (unchanged)
- No way to see runtime entity positions/states in the scene view
- Tools (brush, selection, etc.) remain active but affect editor scene (not runtime)

---

## Option A: Full Play Mode Scene View

Enable the scene view to display runtime entities during play mode, with selection and inspection support.

### Design Approach

1. **Dual Renderer Mode**: SceneViewport switches between `EditorSceneRenderer` and a new `RuntimeSceneRenderer`
2. **Runtime Selection**: Clicking entities in scene view selects them in `PlayModeSelectionManager`
3. **Read-Only Tools**: Most tools disabled, only selection tool active
4. **Visual Indicator**: Orange border/overlay indicates play mode

### Implementation

#### Phase 1: RuntimeSceneRenderer

**File:** `src/main/java/com/pocket/rpg/editor/rendering/RuntimeSceneRenderer.java` (NEW)

```java
/**
 * Renders runtime GameObjects to the editor framebuffer during play mode.
 * Similar to EditorSceneRenderer but works with Scene/GameObject instead of EditorScene/EditorGameObject.
 */
public class RuntimeSceneRenderer {

    private final SpriteBatch batch;
    private final EditorCamera camera;

    public void render(Scene scene, EditorFramebuffer framebuffer,
                       Set<GameObject> selectedObjects) {
        framebuffer.bind();
        // Clear with slightly different color to indicate play mode
        GL46.glClearColor(0.12f, 0.14f, 0.12f, 1.0f);  // Greenish tint
        GL46.glClear(GL46.GL_COLOR_BUFFER_BIT);

        batch.begin(camera.getViewMatrix(), camera.getProjectionMatrix());

        // Render all GameObjects with sprites
        for (GameObject obj : scene.getGameObjects()) {
            renderGameObject(obj, selectedObjects.contains(obj));
        }

        batch.end();
        framebuffer.unbind();
    }

    private void renderGameObject(GameObject obj, boolean isSelected) {
        SpriteRenderer sr = obj.getComponent(SpriteRenderer.class);
        if (sr == null || sr.getSprite() == null) return;

        Transform t = obj.getTransform();
        Vector3f pos = t.getPosition();
        Vector3f scale = t.getScale();

        Sprite sprite = sr.getSprite();

        // Selection highlight
        if (isSelected) {
            batch.setColor(1.2f, 1.2f, 1.0f, 1.0f);  // Slight yellow tint
        }

        batch.draw(sprite, pos.x, pos.y,
                   sprite.getWorldWidth() * scale.x,
                   sprite.getWorldHeight() * scale.y,
                   sr.getZIndex());

        batch.setColor(1f, 1f, 1f, 1f);
    }
}
```

#### Phase 2: SceneViewport Play Mode Detection

**File:** `src/main/java/com/pocket/rpg/editor/ui/SceneViewport.java` (MODIFY)

Add play mode support:

```java
@Setter
private PlayModeController playModeController;

private RuntimeSceneRenderer runtimeRenderer;
private RuntimeSelectionTool runtimeSelectionTool;

private boolean isPlayMode() {
    return playModeController != null && playModeController.isActive();
}

public void renderContent() {
    // ... existing viewport bounds calculation ...

    if (isPlayMode()) {
        renderPlayModeContent();
    } else {
        renderEditorContent();  // Existing code moved here
    }
}

private void renderPlayModeContent() {
    Scene runtimeScene = playModeController.getRuntimeScene();
    if (runtimeScene == null) {
        renderPlayModeDisabledOverlay("No runtime scene");
        return;
    }

    // Render runtime scene
    Set<GameObject> selected = playModeController.getSelectionManager().getSelectedObjects();
    runtimeRenderer.render(runtimeScene, renderer.getFramebuffer(), selected);

    // Display framebuffer
    renderer.render(viewportX, viewportY, viewportWidth, viewportHeight);
    isHovered = ImGui.isItemHovered();

    // Grid still useful for reference
    gridRenderer.render(camera, viewportX, viewportY, viewportWidth, viewportHeight);

    // Handle runtime selection input
    if (isHovered) {
        handleRuntimeInput(runtimeScene);
    }

    // Play mode border indicator
    renderPlayModeBorder();
}

private void renderPlayModeBorder() {
    ImDrawList drawList = ImGui.getWindowDrawList();
    int orangeColor = ImGui.colorConvertFloat4ToU32(1f, 0.5f, 0.1f, 0.8f);
    drawList.addRect(viewportX, viewportY,
                     viewportX + viewportWidth, viewportY + viewportHeight,
                     orangeColor, 0, 0, 3.0f);
}
```

#### Phase 3: Runtime Entity Selection

**File:** `src/main/java/com/pocket/rpg/editor/tools/RuntimeSelectionTool.java` (NEW)

Simplified selection tool for play mode:

```java
/**
 * Selection tool for runtime GameObjects during play mode.
 * No drag-drop, no transform manipulation - just click-to-select.
 */
public class RuntimeSelectionTool {

    private final PlayModeSelectionManager selectionManager;

    public void handleClick(Scene scene, EditorCamera camera,
                           float screenX, float screenY, boolean addToSelection) {
        Vector3f worldPos = camera.screenToWorld(screenX, screenY);

        GameObject clicked = findObjectAt(scene, worldPos.x, worldPos.y);

        if (clicked != null) {
            if (addToSelection) {
                selectionManager.toggleSelection(clicked);
            } else {
                selectionManager.select(clicked);
            }
        } else if (!addToSelection) {
            selectionManager.clearSelection();
        }
    }

    private GameObject findObjectAt(Scene scene, float worldX, float worldY) {
        // Check objects in reverse z-order (topmost first)
        List<GameObject> objects = new ArrayList<>(scene.getGameObjects());
        objects.sort((a, b) -> {
            int za = getZIndex(a);
            int zb = getZIndex(b);
            return Integer.compare(zb, za);  // Descending
        });

        for (GameObject obj : objects) {
            if (isPointInObject(obj, worldX, worldY)) {
                return obj;
            }
        }
        return null;
    }

    private boolean isPointInObject(GameObject obj, float x, float y) {
        SpriteRenderer sr = obj.getComponent(SpriteRenderer.class);
        if (sr == null || sr.getSprite() == null) return false;

        Transform t = obj.getTransform();
        Vector3f pos = t.getPosition();
        Vector3f scale = t.getScale();
        Sprite sprite = sr.getSprite();

        float w = sprite.getWorldWidth() * scale.x;
        float h = sprite.getWorldHeight() * scale.y;

        return x >= pos.x && x <= pos.x + w &&
               y >= pos.y && y <= pos.y + h;
    }
}
```

#### Phase 4: Runtime Gizmo Support

**File:** `src/main/java/com/pocket/rpg/editor/gizmos/RuntimeGizmoRenderer.java` (NEW)

Draw gizmos for runtime GameObjects:

```java
/**
 * Renders gizmos for runtime GameObjects during play mode.
 */
public class RuntimeGizmoRenderer {

    public void render(Scene scene, PlayModeSelectionManager selectionManager,
                       EditorCamera camera, float viewportX, float viewportY,
                       float viewportWidth, float viewportHeight) {

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight, true);

        try {
            GizmoContext ctx = new GizmoContext(drawList, camera, viewportX, viewportY);

            // Draw gizmos for all objects
            for (GameObject obj : scene.getGameObjects()) {
                Transform transform = obj.getTransform();
                ctx.setTransform(transform);

                for (Component comp : obj.getAllComponents()) {
                    if (comp instanceof GizmoDrawable gd) {
                        ctx.setColor(GizmoColors.DEFAULT);
                        ctx.setThickness(2.0f);
                        gd.onDrawGizmos(ctx);
                    }
                }
            }

            // Draw selected gizmos
            for (GameObject obj : selectionManager.getSelectedObjects()) {
                Transform transform = obj.getTransform();
                ctx.setTransform(transform);

                for (Component comp : obj.getAllComponents()) {
                    if (comp instanceof GizmoDrawableSelected gds) {
                        ctx.setColor(GizmoColors.DEFAULT);
                        ctx.setThickness(2.0f);
                        gds.onDrawGizmosSelected(ctx);
                    }
                }
            }

        } finally {
            drawList.popClipRect();
        }
    }
}
```

#### Phase 5: Tool Manager Integration

**File:** `src/main/java/com/pocket/rpg/editor/tools/ToolManager.java` (MODIFY)

Disable tools during play mode:

```java
@Setter
private PlayModeController playModeController;

private boolean isPlayMode() {
    return playModeController != null && playModeController.isActive();
}

public EditorTool getActiveTool() {
    if (isPlayMode()) {
        return null;  // No editor tools during play mode
    }
    return activeTool;
}

public boolean canUseTool(String toolName) {
    if (isPlayMode()) {
        return false;
    }
    return true;
}
```

### Files Summary (Option A)

| File | Change Type | Description |
|------|-------------|-------------|
| `editor/rendering/RuntimeSceneRenderer.java` | NEW | Renders runtime scene to framebuffer |
| `editor/tools/RuntimeSelectionTool.java` | NEW | Click-to-select for runtime objects |
| `editor/gizmos/RuntimeGizmoRenderer.java` | NEW | Gizmos for runtime objects |
| `editor/ui/SceneViewport.java` | MODIFY | Add play mode detection and routing |
| `editor/tools/ToolManager.java` | MODIFY | Disable tools during play mode |
| `editor/ui/SceneViewToolbar.java` | MODIFY | Grey out tools during play mode |

### Verification Checklist (Option A)

- [ ] Enter play mode -> Scene view shows runtime GameObjects
- [ ] Scene view has orange border during play mode
- [ ] Click entity in scene view -> Entity selected in hierarchy/inspector
- [ ] Gizmos render for runtime objects
- [ ] Tool buttons greyed out during play mode
- [ ] Grid still visible for reference
- [ ] Camera pan/zoom still works
- [ ] Stop play mode -> Scene view returns to editor scene
- [ ] No residual selection state after stopping

---

## Option B: Disabled Scene View

Grey out the scene view during play mode, directing users to use GameViewPanel for runtime visualization.

### Design Approach

1. **Overlay Mask**: Semi-transparent grey overlay with "Play Mode Active" message
2. **Input Disabled**: No camera movement, no tool input
3. **Clear Visual**: Obvious that scene editing is suspended
4. **GameViewPanel Focus**: Encourage use of game view for runtime

### Implementation

#### Single Phase: SceneViewport Disabled State

**File:** `src/main/java/com/pocket/rpg/editor/ui/SceneViewport.java` (MODIFY)

```java
@Setter
private PlayModeController playModeController;

private boolean isPlayMode() {
    return playModeController != null && playModeController.isActive();
}

public void renderContent() {
    calculateViewportBoundsFromCursor();

    if (isPlayMode()) {
        renderDisabledPlayModeView();
        return;
    }

    // ... existing editor rendering code ...
}

private void renderDisabledPlayModeView() {
    // Still render the frozen editor scene (dimmed)
    renderer.render(viewportX, viewportY, viewportWidth, viewportHeight);

    // Dim overlay
    ImDrawList drawList = ImGui.getWindowDrawList();
    int dimColor = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 0.7f);
    drawList.addRectFilled(viewportX, viewportY,
            viewportX + viewportWidth, viewportY + viewportHeight, dimColor);

    // Orange border
    int borderColor = ImGui.colorConvertFloat4ToU32(1f, 0.5f, 0.1f, 0.9f);
    drawList.addRect(viewportX, viewportY,
            viewportX + viewportWidth, viewportY + viewportHeight,
            borderColor, 0, 0, 3.0f);

    // Center message
    String message = MaterialIcons.PlayArrow + " PLAY MODE ACTIVE";
    String subMessage = "Use Game View to see runtime";

    ImVec2 textSize = new ImVec2();
    ImGui.calcTextSize(textSize, message);

    float centerX = viewportX + viewportWidth / 2;
    float centerY = viewportY + viewportHeight / 2;

    int textColor = ImGui.colorConvertFloat4ToU32(1f, 0.6f, 0.2f, 1f);
    drawList.addText(centerX - textSize.x / 2, centerY - 20, textColor, message);

    int subColor = ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 1f);
    ImGui.calcTextSize(textSize, subMessage);
    drawList.addText(centerX - textSize.x / 2, centerY + 10, subColor, subMessage);

    // No input handling - scene is disabled
    isHovered = false;
    isFocused = false;
}

public void renderToolOverlay() {
    // Skip all overlays during play mode
    if (isPlayMode()) return;

    // ... existing overlay code ...
}
```

**File:** `src/main/java/com/pocket/rpg/editor/ui/SceneViewToolbar.java` (MODIFY)

Grey out all toolbar buttons:

```java
@Setter
private PlayModeController playModeController;

private boolean isPlayMode() {
    return playModeController != null && playModeController.isActive();
}

public void render() {
    if (isPlayMode()) {
        ImGui.beginDisabled();
    }

    // ... existing toolbar rendering ...

    if (isPlayMode()) {
        ImGui.endDisabled();
    }
}
```

### Files Summary (Option B)

| File | Change Type | Description |
|------|-------------|-------------|
| `editor/ui/SceneViewport.java` | MODIFY | Add disabled play mode view |
| `editor/ui/SceneViewToolbar.java` | MODIFY | Grey out during play mode |
| `editor/EditorUIController.java` | MODIFY | Wire PlayModeController to viewport |

### Verification Checklist (Option B)

- [ ] Enter play mode -> Scene view shows dim overlay with message
- [ ] Orange border visible around scene view
- [ ] "PLAY MODE ACTIVE" text centered
- [ ] "Use Game View to see runtime" subtext visible
- [ ] All toolbar buttons greyed out
- [ ] No camera movement possible
- [ ] No tool input possible
- [ ] Stop play mode -> Scene view returns to normal
- [ ] Editor scene unchanged after play/stop cycle

---

## Recommendation

**Option B (Disabled)** is recommended for initial implementation:

1. **Simpler**: Much less code, lower risk
2. **Clear UX**: Users know to use GameViewPanel
3. **Consistent**: GameViewPanel is purpose-built for runtime viewing
4. **Faster**: Can implement alongside play-mode-inspector plan

**Option A** can be added later if users request scene-based runtime inspection.

---

## Integration with Play-Mode-Inspector Plan

Both options require `PlayModeController` to be passed to `SceneViewport`. This wiring happens in `EditorUIController`:

```java
// In EditorUIController.setPlayModeController():
sceneViewport.setPlayModeController(playModeController);
sceneViewToolbar.setPlayModeController(playModeController);
```

The play-mode-inspector plan handles Hierarchy and Inspector. This plan only covers SceneViewport.
