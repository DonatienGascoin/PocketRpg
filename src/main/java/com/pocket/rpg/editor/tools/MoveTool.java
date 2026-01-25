package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.gizmo.TransformGizmoRenderer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.MoveEntityCommand;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Tool for moving entities with axis-constrained or free movement.
 * <p>
 * Features:
 * - Red arrow for X-axis constrained movement
 * - Green arrow for Y-axis constrained movement
 * - Center square for free movement
 * - Visual feedback showing delta while dragging
 * - Undo support
 */
public class MoveTool implements EditorTool, ViewportAwareTool, ContinuousDragTool {

    @Setter
    private EditorScene scene;

    @Setter
    private EditorCamera camera;

    @Setter
    private EditorSelectionManager selectionManager;

    // Viewport bounds
    private float viewportX, viewportY, viewportWidth, viewportHeight;

    // Gizmo configuration
    private static final float ARROW_LENGTH = 60f;
    private static final float HIT_THRESHOLD = 12f;
    private static final float CENTER_SIZE = 14f;

    // Drag state
    private enum DragAxis { NONE, X, Y, FREE }
    private DragAxis currentDrag = DragAxis.NONE;
    private DragAxis hoveredAxis = DragAxis.NONE;
    private Vector3f dragStartPosition;
    private Vector2f dragStartMouse;
    private Vector2f lastMouseWorld;

    // ========================================================================
    // EDITOR TOOL INTERFACE
    // ========================================================================

    @Override
    public String getName() {
        return "Move";
    }

    @Override
    public String getShortcutKey() {
        return "W";
    }

    @Override
    public void onActivate() {
        currentDrag = DragAxis.NONE;
        hoveredAxis = DragAxis.NONE;
        dragStartPosition = null;
        dragStartMouse = null;
    }

    @Override
    public void onDeactivate() {
        // If dragging, finalize
        if (currentDrag != DragAxis.NONE && dragStartPosition != null) {
            pushUndoCommand();
        }
        currentDrag = DragAxis.NONE;
        hoveredAxis = DragAxis.NONE;
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

        // Check if clicking on gizmo
        if (hoveredAxis != DragAxis.NONE) {
            startDrag(hoveredAxis);
        } else {
            // Check if clicking on the entity itself
            float worldX = tileX + 0.5f;
            float worldY = tileY + 0.5f;
            EditorGameObject clickedEntity = scene.findEntityAt(worldX, worldY);

            if (clickedEntity != null) {
                if (clickedEntity != selected) {
                    // Select the clicked entity
                    if (selectionManager != null) {
                        selectionManager.selectEntity(clickedEntity);
                    }
                } else {
                    // Start free drag on current entity
                    startDrag(DragAxis.FREE);
                }
            } else {
                // Clicked empty space - deselect
                if (selectionManager != null) {
                    selectionManager.clearSelection();
                }
            }
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (currentDrag == DragAxis.NONE) return;

        EditorGameObject selected = getSelectedEntity();
        if (selected == null) return;

        // Get current mouse position in world coords
        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
        Vector2f mouseWorld = TransformGizmoRenderer.screenToWorld(
                camera, mouseScreen.x, mouseScreen.y, viewportX, viewportY
        );

        if (lastMouseWorld == null) {
            lastMouseWorld = mouseWorld;
            return;
        }

        // Calculate delta
        float deltaX = mouseWorld.x - lastMouseWorld.x;
        float deltaY = mouseWorld.y - lastMouseWorld.y;

        // Apply constraint
        Vector3f pos = selected.getPosition();
        switch (currentDrag) {
            case X -> selected.setPosition(pos.x + deltaX, pos.y, pos.z);
            case Y -> selected.setPosition(pos.x, pos.y + deltaY, pos.z);
            case FREE -> selected.setPosition(pos.x + deltaX, pos.y + deltaY, pos.z);
        }

        lastMouseWorld = mouseWorld;
        scene.markDirty();
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (button != 0) return;

        if (currentDrag != DragAxis.NONE && dragStartPosition != null) {
            pushUndoCommand();
        }

        currentDrag = DragAxis.NONE;
        dragStartPosition = null;
        dragStartMouse = null;
        lastMouseWorld = null;
    }

    @Override
    public void onMouseMove(int tileX, int tileY) {
        if (currentDrag != DragAxis.NONE) return;

        // Update hovered axis based on mouse position
        hoveredAxis = DragAxis.NONE;

        EditorGameObject selected = getSelectedEntity();
        if (selected == null) return;

        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
        Vector2f center = getGizmoCenter(selected);

        // Check center square first (highest priority)
        float halfSize = CENTER_SIZE / 2;
        if (TransformGizmoRenderer.isPointInRect(
                mouseScreen.x, mouseScreen.y,
                center.x - halfSize, center.y - halfSize,
                CENTER_SIZE, CENTER_SIZE)) {
            hoveredAxis = DragAxis.FREE;
            return;
        }

        // Check X axis arrow
        if (TransformGizmoRenderer.isPointNearLine(
                mouseScreen.x, mouseScreen.y,
                center.x, center.y,
                center.x + ARROW_LENGTH, center.y,
                HIT_THRESHOLD)) {
            hoveredAxis = DragAxis.X;
            return;
        }

        // Check Y axis arrow (up in screen = negative Y)
        if (TransformGizmoRenderer.isPointNearLine(
                mouseScreen.x, mouseScreen.y,
                center.x, center.y,
                center.x, center.y - ARROW_LENGTH,
                HIT_THRESHOLD)) {
            hoveredAxis = DragAxis.Y;
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

        // Draw the move gizmo for selected entity
        if (selected != null) {
            Vector2f center = getGizmoCenter(selected);
            int hovered = switch (hoveredAxis) {
                case X -> 1;
                case Y -> 2;
                case FREE -> 3;
                default -> 0;
            };
            int dragging = switch (currentDrag) {
                case X -> 1;
                case Y -> 2;
                case FREE -> 3;
                default -> 0;
            };

            TransformGizmoRenderer.drawMoveGizmo(
                    drawList, center.x, center.y, ARROW_LENGTH,
                    currentDrag != DragAxis.NONE ? dragging : hovered,
                    currentDrag != DragAxis.NONE,
                    true // Y is up in world space
            );

            // Draw delta text while dragging
            if (currentDrag != DragAxis.NONE && dragStartPosition != null) {
                Vector3f currentPos = selected.getPosition();
                float deltaX = currentPos.x - dragStartPosition.x;
                float deltaY = currentPos.y - dragStartPosition.y;
                String deltaText = String.format("%+.2f, %+.2f", deltaX, deltaY);
                TransformGizmoRenderer.drawDeltaText(drawList, center.x + 20, center.y + 20, deltaText);
            }

            // Set cursor for gizmo interaction
            if (currentDrag != DragAxis.NONE || hoveredAxis != DragAxis.NONE) {
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeAll);
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

    private void startDrag(DragAxis axis) {
        EditorGameObject selected = getSelectedEntity();
        if (selected == null) return;

        currentDrag = axis;
        dragStartPosition = new Vector3f(selected.getPosition());
        dragStartMouse = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
        lastMouseWorld = TransformGizmoRenderer.screenToWorld(
                camera, dragStartMouse.x, dragStartMouse.y, viewportX, viewportY
        );
    }

    private void pushUndoCommand() {
        EditorGameObject selected = getSelectedEntity();
        if (selected == null || dragStartPosition == null) return;

        Vector3f newPosition = selected.getPosition();
        if (!dragStartPosition.equals(newPosition)) {
            UndoManager.getInstance().push(
                    new MoveEntityCommand(selected, dragStartPosition, newPosition)
            );
        }
    }
}
