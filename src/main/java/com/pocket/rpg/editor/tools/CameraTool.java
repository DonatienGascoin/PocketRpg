package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.tools.gizmo.TransformGizmoRenderer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.MoveCameraCommand;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Tool for dragging the camera start position in the scene viewport.
 * <p>
 * Activates automatically when the camera is selected in the hierarchy.
 * Renders a crosshair handle at the camera position.
 * <p>
 * Camera bounds editing has moved to {@link BoundsZoneTool}, which operates
 * on individual {@link com.pocket.rpg.components.interaction.CameraBoundsZone} components.
 */
public class CameraTool implements EditorTool, ViewportAwareTool, ContinuousDragTool {

    @Setter private EditorScene scene;
    @Setter private EditorCamera camera;
    @Setter private EditorSelectionManager selectionManager;

    private float viewportX, viewportY, viewportWidth, viewportHeight;

    // Crosshair rendering constants
    private static final float CROSSHAIR_SIZE = 12f;
    private static final float CROSSHAIR_THICKNESS = 3f;
    private static final float CROSSHAIR_HIT_RADIUS = 10f;

    private boolean isDragging = false;
    private boolean hoveredCrosshair = false;

    // Drag state for position
    private Vector2f dragStartPosition;
    private Vector2f lastMouseWorld;

    @Override public String getName() { return "Camera"; }

    @Override
    public void onActivate() {
        resetDragState();
    }

    @Override
    public void onDeactivate() {
        if (isDragging) {
            pushUndoCommand();
        }
        resetDragState();
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button != 0) return;
        if (!isCameraSelected()) return;

        SceneCameraSettings settings = getCameraSettings();
        if (settings == null) return;

        if (hoveredCrosshair) {
            isDragging = true;
            dragStartPosition = new Vector2f(settings.getPosition());
            Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
            lastMouseWorld = TransformGizmoRenderer.screenToWorld(
                    camera, mouseScreen.x, mouseScreen.y, viewportX, viewportY
            );
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (!isDragging) return;

        SceneCameraSettings settings = getCameraSettings();
        if (settings == null) return;

        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
        Vector2f mouseWorld = TransformGizmoRenderer.screenToWorld(
                camera, mouseScreen.x, mouseScreen.y, viewportX, viewportY
        );

        if (lastMouseWorld == null) {
            lastMouseWorld = mouseWorld;
            return;
        }

        float deltaX = mouseWorld.x - lastMouseWorld.x;
        float deltaY = mouseWorld.y - lastMouseWorld.y;

        Vector2f pos = settings.getPosition();
        settings.setPosition(pos.x + deltaX, pos.y + deltaY);
        scene.markDirty();

        lastMouseWorld = mouseWorld;
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (button != 0) return;

        if (isDragging) {
            pushUndoCommand();
        }

        resetDragState();
    }

    @Override
    public void onMouseMove(int tileX, int tileY) {
        if (isDragging) return;
        if (!isCameraSelected()) return;

        hoveredCrosshair = false;

        SceneCameraSettings settings = getCameraSettings();
        if (settings == null) return;

        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());

        Vector2f crosshairScreen = getCrosshairScreenPos(settings);
        if (TransformGizmoRenderer.isPointInCircle(
                mouseScreen.x, mouseScreen.y,
                crosshairScreen.x, crosshairScreen.y, CROSSHAIR_HIT_RADIUS)) {
            hoveredCrosshair = true;
        }
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (scene == null) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;
        if (!isCameraSelected()) return;

        SceneCameraSettings settings = getCameraSettings();
        if (settings == null) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Draw crosshair at camera position
        renderCrosshair(drawList, settings);

        // Set cursor based on hover/drag state
        if (isDragging || hoveredCrosshair) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeAll);
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
    // CROSSHAIR RENDERING
    // ========================================================================

    private static final int CROSSHAIR_COLOR = GizmoColors.fromRGBA(1.0f, 0.15f, 0.15f, 1.0f);
    private static final int CROSSHAIR_COLOR_HOVER = GizmoColors.fromRGBA(1.0f, 0.4f, 0.4f, 1.0f);
    private static final float DIAMOND_SIZE = CROSSHAIR_SIZE + 2f;

    private void renderCrosshair(ImDrawList drawList, SceneCameraSettings settings) {
        Vector2f center = getCrosshairScreenPos(settings);
        boolean highlighted = hoveredCrosshair || isDragging;

        int color = highlighted ? CROSSHAIR_COLOR_HOVER : CROSSHAIR_COLOR;

        // Diamond (lozenge) outline
        float d = DIAMOND_SIZE;
        drawList.addQuad(
                center.x, center.y - d,  // top
                center.x + d, center.y,  // right
                center.x, center.y + d,  // bottom
                center.x - d, center.y,  // left
                color, 3.0f
        );

        // Horizontal line of the crosshair
        float crossLen = CROSSHAIR_SIZE * 0.7f;
        drawList.addLine(
                center.x - crossLen, center.y,
                center.x + crossLen, center.y,
                color, CROSSHAIR_THICKNESS
        );

        // Vertical line of the crosshair
        drawList.addLine(
                center.x, center.y - crossLen,
                center.x, center.y + crossLen,
                color, CROSSHAIR_THICKNESS
        );
    }

    private Vector2f getCrosshairScreenPos(SceneCameraSettings settings) {
        Vector2f pos = settings.getPosition();
        return TransformGizmoRenderer.worldToScreen(camera, pos.x, pos.y, viewportX, viewportY);
    }

    // ========================================================================
    // UNDO
    // ========================================================================

    private void pushUndoCommand() {
        SceneCameraSettings settings = getCameraSettings();
        if (settings == null) return;

        if (isDragging && dragStartPosition != null) {
            Vector2f newPos = settings.getPosition();
            if (!dragStartPosition.equals(newPos)) {
                UndoManager.getInstance().push(
                        new MoveCameraCommand(settings, dragStartPosition, new Vector2f(newPos))
                );
            }
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private boolean isCameraSelected() {
        return selectionManager != null && selectionManager.isCameraSelected();
    }

    private SceneCameraSettings getCameraSettings() {
        return scene != null ? scene.getCameraSettings() : null;
    }

    private void resetDragState() {
        isDragging = false;
        hoveredCrosshair = false;
        dragStartPosition = null;
        lastMouseWorld = null;
    }
}
