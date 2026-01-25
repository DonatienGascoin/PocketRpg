package com.pocket.rpg.editor.tools;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.gizmo.TransformGizmoRenderer;
import com.pocket.rpg.editor.tools.gizmo.TransformGizmoRenderer.ScaleHandle;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.ScaleEntityCommand;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Tool for scaling entities with corner handles (uniform) and edge handles (non-uniform).
 * <p>
 * Features:
 * - Corner handles for uniform scaling (maintain aspect ratio)
 * - Edge handles for non-uniform scaling on single axis
 * - Visual feedback showing scale factor while dragging
 * - Undo support
 */
public class ScaleTool implements EditorTool, ViewportAwareTool, ContinuousDragTool {

    @Setter
    private EditorScene scene;

    @Setter
    private EditorCamera camera;

    @Setter
    private EditorSelectionManager selectionManager;

    // Viewport bounds
    private float viewportX, viewportY, viewportWidth, viewportHeight;

    // Gizmo configuration
    private static final float HANDLE_SIZE = 10f;
    private static final float HANDLE_HIT_SIZE = 14f;

    // Drag state
    private ScaleHandle currentHandle = ScaleHandle.NONE;
    private ScaleHandle hoveredHandle = ScaleHandle.NONE;
    private Vector3f dragStartScale;
    private Vector2f dragStartMouse;
    private Vector2f entityCenter; // For calculating scale direction

    // ========================================================================
    // EDITOR TOOL INTERFACE
    // ========================================================================

    @Override
    public String getName() {
        return "Scale";
    }

    @Override
    public String getShortcutKey() {
        return "R";
    }

    @Override
    public void onActivate() {
        currentHandle = ScaleHandle.NONE;
        hoveredHandle = ScaleHandle.NONE;
        dragStartScale = null;
        dragStartMouse = null;
    }

    @Override
    public void onDeactivate() {
        if (currentHandle != ScaleHandle.NONE && dragStartScale != null) {
            pushUndoCommand();
        }
        currentHandle = ScaleHandle.NONE;
        hoveredHandle = ScaleHandle.NONE;
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button != 0) return;
        if (scene == null) return;

        EditorGameObject selected = getSelectedEntity();
        if (selected == null) {
            // Try to select entity at position
            float worldX = tileX + 0.5f;
            float worldY = tileY + 0.5f;
            EditorGameObject entity = scene.findEntityAt(worldX, worldY);
            if (entity != null && selectionManager != null) {
                selectionManager.selectEntity(entity);
            }
            return;
        }

        // Check if clicking on a handle
        if (hoveredHandle != ScaleHandle.NONE) {
            startDrag(hoveredHandle);
        } else {
            // Check if clicking on another entity to select it
            float worldX = tileX + 0.5f;
            float worldY = tileY + 0.5f;
            EditorGameObject clickedEntity = scene.findEntityAt(worldX, worldY);

            if (clickedEntity != null && clickedEntity != selected) {
                if (selectionManager != null) {
                    selectionManager.selectEntity(clickedEntity);
                }
            } else if (clickedEntity == null) {
                // Clicked empty space - deselect
                if (selectionManager != null) {
                    selectionManager.clearSelection();
                }
            }
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (currentHandle == ScaleHandle.NONE) return;

        EditorGameObject selected = getSelectedEntity();
        if (selected == null) return;

        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
        if (dragStartMouse == null) return;

        // Calculate delta from start
        float deltaX = mouseScreen.x - dragStartMouse.x;
        float deltaY = mouseScreen.y - dragStartMouse.y;

        // Invert Y delta because screen Y is inverted
        deltaY = -deltaY;

        // Scale sensitivity (pixels per scale unit)
        float sensitivity = 100f;

        Vector3f newScale = new Vector3f(dragStartScale);

        switch (currentHandle) {
            // Corner handles - uniform scale
            case TOP_LEFT -> {
                float delta = (-deltaX + deltaY) / 2;
                float scaleFactor = 1 + delta / sensitivity;
                newScale.x = dragStartScale.x * scaleFactor;
                newScale.y = dragStartScale.y * scaleFactor;
            }
            case TOP_RIGHT -> {
                float delta = (deltaX + deltaY) / 2;
                float scaleFactor = 1 + delta / sensitivity;
                newScale.x = dragStartScale.x * scaleFactor;
                newScale.y = dragStartScale.y * scaleFactor;
            }
            case BOTTOM_RIGHT -> {
                float delta = (deltaX - deltaY) / 2;
                float scaleFactor = 1 + delta / sensitivity;
                newScale.x = dragStartScale.x * scaleFactor;
                newScale.y = dragStartScale.y * scaleFactor;
            }
            case BOTTOM_LEFT -> {
                float delta = (-deltaX - deltaY) / 2;
                float scaleFactor = 1 + delta / sensitivity;
                newScale.x = dragStartScale.x * scaleFactor;
                newScale.y = dragStartScale.y * scaleFactor;
            }
            // Edge handles - non-uniform scale
            case TOP -> {
                float scaleFactor = 1 + deltaY / sensitivity;
                newScale.y = dragStartScale.y * scaleFactor;
            }
            case BOTTOM -> {
                float scaleFactor = 1 - deltaY / sensitivity;
                newScale.y = dragStartScale.y * scaleFactor;
            }
            case RIGHT -> {
                float scaleFactor = 1 + deltaX / sensitivity;
                newScale.x = dragStartScale.x * scaleFactor;
            }
            case LEFT -> {
                float scaleFactor = 1 - deltaX / sensitivity;
                newScale.x = dragStartScale.x * scaleFactor;
            }
        }

        // Clamp to reasonable values
        newScale.x = Math.max(0.1f, newScale.x);
        newScale.y = Math.max(0.1f, newScale.y);

        selected.setScale(newScale);
        scene.markDirty();
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (button != 0) return;

        if (currentHandle != ScaleHandle.NONE && dragStartScale != null) {
            pushUndoCommand();
        }

        currentHandle = ScaleHandle.NONE;
        dragStartScale = null;
        dragStartMouse = null;
    }

    @Override
    public void onMouseMove(int tileX, int tileY) {
        if (currentHandle != ScaleHandle.NONE) return;

        hoveredHandle = ScaleHandle.NONE;

        EditorGameObject selected = getSelectedEntity();
        if (selected == null) return;

        Vector2f[] corners = getScreenCorners(selected);
        if (corners == null) return;

        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());

        // Check corner handles
        if (isNearPoint(mouseScreen, corners[0])) {
            hoveredHandle = ScaleHandle.TOP_LEFT;
            return;
        }
        if (isNearPoint(mouseScreen, corners[1])) {
            hoveredHandle = ScaleHandle.TOP_RIGHT;
            return;
        }
        if (isNearPoint(mouseScreen, corners[2])) {
            hoveredHandle = ScaleHandle.BOTTOM_RIGHT;
            return;
        }
        if (isNearPoint(mouseScreen, corners[3])) {
            hoveredHandle = ScaleHandle.BOTTOM_LEFT;
            return;
        }

        // Check edge handles (midpoints)
        Vector2f topMid = midpoint(corners[0], corners[1]);
        Vector2f rightMid = midpoint(corners[1], corners[2]);
        Vector2f bottomMid = midpoint(corners[2], corners[3]);
        Vector2f leftMid = midpoint(corners[3], corners[0]);

        if (isNearPoint(mouseScreen, topMid)) {
            hoveredHandle = ScaleHandle.TOP;
            return;
        }
        if (isNearPoint(mouseScreen, rightMid)) {
            hoveredHandle = ScaleHandle.RIGHT;
            return;
        }
        if (isNearPoint(mouseScreen, bottomMid)) {
            hoveredHandle = ScaleHandle.BOTTOM;
            return;
        }
        if (isNearPoint(mouseScreen, leftMid)) {
            hoveredHandle = ScaleHandle.LEFT;
        }
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (scene == null) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        EditorGameObject selected = getSelectedEntity();

        // Draw hover highlight for non-selected entities
        if (hoveredTileX != Integer.MIN_VALUE && hoveredTileY != Integer.MIN_VALUE) {
            float worldX = hoveredTileX + 0.5f;
            float worldY = hoveredTileY + 0.5f;
            EditorGameObject hovered = scene.findEntityAt(worldX, worldY);
            if (hovered != null && hovered != selected) {
                TransformGizmoRenderer.drawEntityHoverHighlight(drawList, hovered, camera, viewportX, viewportY);
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }
        }

        // Draw the scale gizmo for selected entity
        if (selected != null) {
            Vector2f[] corners = getScreenCorners(selected);
            if (corners != null) {
                ScaleHandle displayHovered = currentHandle != ScaleHandle.NONE ? currentHandle : hoveredHandle;
                TransformGizmoRenderer.drawScaleGizmo(
                        drawList, corners, displayHovered, currentHandle != ScaleHandle.NONE
                );
            }

            // Draw delta text while dragging
            if (currentHandle != ScaleHandle.NONE && dragStartScale != null) {
                Vector3f currentScale = selected.getScale();
                float scaleFactorX = currentScale.x / dragStartScale.x;
                float scaleFactorY = currentScale.y / dragStartScale.y;

                String deltaText;
                if (isCornerHandle(currentHandle)) {
                    deltaText = String.format("%.2fx", scaleFactorX);
                } else {
                    deltaText = String.format("%.2fx, %.2fx", scaleFactorX, scaleFactorY);
                }

                Vector2f center = getGizmoCenter(selected);
                TransformGizmoRenderer.drawDeltaText(drawList, center.x + 20, center.y + 20, deltaText);
            }

            // Set cursor based on hovered handle
            setCursorForHandle(currentHandle != ScaleHandle.NONE ? currentHandle : hoveredHandle);
        }

        drawList.popClipRect();
    }

    @Override
    public void setViewportBounds(float x, float y, float width, float height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private EditorGameObject getSelectedEntity() {
        if (scene == null) return null;
        return scene.getSelectedEntity();
    }

    private Vector2f getGizmoCenter(EditorGameObject entity) {
        Vector3f worldPos = entity.getPosition();
        return TransformGizmoRenderer.worldToScreen(camera, worldPos.x, worldPos.y, viewportX, viewportY);
    }

    /**
     * Gets the four screen-space corners of an entity's bounding box.
     * Takes into account pivot, scale, and rotation.
     */
    private Vector2f[] getScreenCorners(EditorGameObject entity) {
        Vector3f pos = entity.getPosition();
        Vector3f scale = entity.getScale();
        Vector3f rotation = entity.getRotation();
        Vector2f size = entity.getCurrentSize();

        if (size == null) {
            size = new Vector2f(1f, 1f);
        }

        // Get pivot from sprite
        float pivotX = 0.5f;
        float pivotY = 0.5f;
        SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
        if (sr != null) {
            Sprite sprite = sr.getSprite();
            if (sprite != null) {
                pivotX = sprite.getPivotX();
                pivotY = sprite.getPivotY();
            }
        }

        // Calculate scaled size
        float width = size.x * scale.x;
        float height = size.y * scale.y;

        // Calculate corner offsets from position (based on pivot)
        float left = -pivotX * width;
        float right = (1f - pivotX) * width;
        float bottom = -pivotY * height;
        float top = (1f - pivotY) * height;

        // Local corners (before rotation)
        float[][] localCorners = {
                {left, top},      // top-left
                {right, top},     // top-right
                {right, bottom},  // bottom-right
                {left, bottom}    // bottom-left
        };

        // Apply rotation
        float rotZ = (float) Math.toRadians(rotation.z);
        float cos = (float) Math.cos(rotZ);
        float sin = (float) Math.sin(rotZ);

        Vector2f[] screenCorners = new Vector2f[4];
        for (int i = 0; i < 4; i++) {
            float lx = localCorners[i][0];
            float ly = localCorners[i][1];

            // Rotate around origin
            float worldX = pos.x + lx * cos - ly * sin;
            float worldY = pos.y + lx * sin + ly * cos;

            // Convert to screen
            screenCorners[i] = TransformGizmoRenderer.worldToScreen(
                    camera, worldX, worldY, viewportX, viewportY
            );
        }

        return screenCorners;
    }

    private void startDrag(ScaleHandle handle) {
        EditorGameObject selected = getSelectedEntity();
        if (selected == null) return;

        currentHandle = handle;
        dragStartScale = new Vector3f(selected.getScale());
        dragStartMouse = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
        entityCenter = getGizmoCenter(selected);
    }

    private void pushUndoCommand() {
        EditorGameObject selected = getSelectedEntity();
        if (selected == null || dragStartScale == null) return;

        Vector3f newScale = selected.getScale();
        if (!dragStartScale.equals(newScale)) {
            UndoManager.getInstance().push(
                    new ScaleEntityCommand(selected, dragStartScale, newScale)
            );
        }
    }

    private boolean isNearPoint(Vector2f mouse, Vector2f point) {
        float dx = mouse.x - point.x;
        float dy = mouse.y - point.y;
        return dx * dx + dy * dy <= HANDLE_HIT_SIZE * HANDLE_HIT_SIZE;
    }

    private Vector2f midpoint(Vector2f a, Vector2f b) {
        return new Vector2f((a.x + b.x) / 2, (a.y + b.y) / 2);
    }

    private boolean isCornerHandle(ScaleHandle handle) {
        return handle == ScaleHandle.TOP_LEFT ||
               handle == ScaleHandle.TOP_RIGHT ||
               handle == ScaleHandle.BOTTOM_RIGHT ||
               handle == ScaleHandle.BOTTOM_LEFT;
    }

    private void setCursorForHandle(ScaleHandle handle) {
        switch (handle) {
            case TOP_LEFT, BOTTOM_RIGHT -> ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNWSE);
            case TOP_RIGHT, BOTTOM_LEFT -> ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNESW);
            case TOP, BOTTOM -> ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS);
            case LEFT, RIGHT -> ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
    }
}
