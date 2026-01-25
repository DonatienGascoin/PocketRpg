package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.gizmo.TransformGizmoRenderer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.RotateEntityCommand;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Tool for rotating entities around their pivot point.
 * <p>
 * Features:
 * - Circular ring for rotation
 * - Visual feedback showing angle delta while dragging
 * - Shift-snap to 15° increments
 * - Undo support
 */
public class RotateTool implements EditorTool, ViewportAwareTool, ContinuousDragTool {

    @Setter
    private EditorScene scene;

    @Setter
    private EditorCamera camera;

    @Setter
    private EditorSelectionManager selectionManager;

    // Viewport bounds
    private float viewportX, viewportY, viewportWidth, viewportHeight;

    // Gizmo configuration
    private static final float RING_RADIUS = 50f;
    private static final float RING_THICKNESS = 8f;
    private static final float SNAP_ANGLE = 15f; // Degrees

    // Drag state
    private boolean isDragging = false;
    private boolean isHovered = false;
    private Vector3f dragStartRotation;
    private float dragStartAngle; // Angle at drag start (radians)

    // ========================================================================
    // EDITOR TOOL INTERFACE
    // ========================================================================

    @Override
    public String getName() {
        return "Rotate";
    }

    @Override
    public String getShortcutKey() {
        return "E";
    }

    @Override
    public void onActivate() {
        isDragging = false;
        isHovered = false;
        dragStartRotation = null;
    }

    @Override
    public void onDeactivate() {
        if (isDragging && dragStartRotation != null) {
            pushUndoCommand();
        }
        isDragging = false;
        isHovered = false;
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

        // Check if clicking on gizmo ring
        if (isHovered) {
            startDrag();
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
        if (!isDragging) return;

        EditorGameObject selected = getSelectedEntity();
        if (selected == null) return;

        Vector2f center = getGizmoCenter(selected);
        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());

        // Calculate current angle from center to mouse
        float currentAngle = TransformGizmoRenderer.angleFromCenter(
                mouseScreen.x, mouseScreen.y, center.x, center.y
        );

        // Calculate delta from start angle
        float deltaAngle = currentAngle - dragStartAngle;

        // Convert to degrees
        float deltaDegrees = (float) Math.toDegrees(deltaAngle);

        // Apply shift snap
        if (ImGui.isKeyDown(imgui.flag.ImGuiKey.LeftShift) ||
            ImGui.isKeyDown(imgui.flag.ImGuiKey.RightShift)) {
            deltaDegrees = Math.round(deltaDegrees / SNAP_ANGLE) * SNAP_ANGLE;
        }

        // Calculate new rotation
        float newRotation = dragStartRotation.z + deltaDegrees;

        // Normalize to -180 to 180
        while (newRotation > 180) newRotation -= 360;
        while (newRotation < -180) newRotation += 360;

        selected.setRotation(new Vector3f(0, 0, newRotation));
        scene.markDirty();
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (button != 0) return;

        if (isDragging && dragStartRotation != null) {
            pushUndoCommand();
        }

        isDragging = false;
        dragStartRotation = null;
    }

    @Override
    public void onMouseMove(int tileX, int tileY) {
        if (isDragging) return;

        isHovered = false;

        EditorGameObject selected = getSelectedEntity();
        if (selected == null) return;

        Vector2f center = getGizmoCenter(selected);
        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());

        // Check if near the ring
        isHovered = TransformGizmoRenderer.isPointNearRing(
                mouseScreen.x, mouseScreen.y,
                center.x, center.y,
                RING_RADIUS, RING_THICKNESS
        );
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

        // Draw the rotation gizmo for selected entity
        if (selected != null) {
            Vector2f center = getGizmoCenter(selected);
            Vector3f rotation = selected.getRotation();
            float currentAngleRad = (float) Math.toRadians(rotation.z);

            TransformGizmoRenderer.drawRotateGizmo(
                    drawList, center.x, center.y, RING_RADIUS,
                    isHovered, isDragging, currentAngleRad
            );

            // Draw delta text while dragging
            if (isDragging && dragStartRotation != null) {
                float deltaAngle = rotation.z - dragStartRotation.z;
                String deltaText = String.format("%+.1f°", deltaAngle);
                TransformGizmoRenderer.drawDeltaText(drawList, center.x + RING_RADIUS + 10, center.y - 10, deltaText);
            }

            // Set cursor for gizmo interaction
            if (isDragging || isHovered) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }
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

    private void startDrag() {
        EditorGameObject selected = getSelectedEntity();
        if (selected == null) return;

        isDragging = true;
        dragStartRotation = new Vector3f(selected.getRotation());

        Vector2f center = getGizmoCenter(selected);
        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
        dragStartAngle = TransformGizmoRenderer.angleFromCenter(
                mouseScreen.x, mouseScreen.y, center.x, center.y
        );
    }

    private void pushUndoCommand() {
        EditorGameObject selected = getSelectedEntity();
        if (selected == null || dragStartRotation == null) return;

        Vector3f newRotation = selected.getRotation();
        if (!dragStartRotation.equals(newRotation)) {
            UndoManager.getInstance().push(
                    new RotateEntityCommand(selected, dragStartRotation, newRotation)
            );
        }
    }
}
