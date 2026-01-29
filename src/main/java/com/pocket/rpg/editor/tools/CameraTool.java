package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.tools.gizmo.TransformGizmoRenderer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.MoveCameraCommand;
import com.pocket.rpg.editor.undo.commands.SetCameraBoundsCommand;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Tool for dragging the camera position and resizing camera bounds
 * directly in the scene viewport.
 * <p>
 * Activates automatically when the camera is selected in the hierarchy.
 * Renders a crosshair handle at the camera position and 8 resize handles
 * on the bounds rectangle (4 corners + 4 edge midpoints).
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

    // Bounds handle constants
    private static final float HANDLE_SIZE = 8f;
    private static final float HANDLE_HIT_SIZE = 12f;

    // Handle identifiers for bounds drag
    private enum BoundsHandle {
        NONE,
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    // Drag types
    private enum DragType { NONE, POSITION, BOUNDS }

    private DragType currentDrag = DragType.NONE;
    private boolean hoveredCrosshair = false;
    private BoundsHandle hoveredHandle = BoundsHandle.NONE;
    private BoundsHandle draggingHandle = BoundsHandle.NONE;

    // Drag state for position
    private Vector2f dragStartPosition;
    private Vector2f lastMouseWorld;

    // Drag state for bounds
    private Vector4f dragStartBounds;

    @Override public String getName() { return "Camera"; }

    @Override
    public void onActivate() {
        resetDragState();
    }

    @Override
    public void onDeactivate() {
        if (currentDrag != DragType.NONE) {
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
            // Start position drag
            currentDrag = DragType.POSITION;
            dragStartPosition = new Vector2f(settings.getPosition());
            Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
            lastMouseWorld = TransformGizmoRenderer.screenToWorld(
                    camera, mouseScreen.x, mouseScreen.y, viewportX, viewportY
            );
        } else if (hoveredHandle != BoundsHandle.NONE) {
            // Start bounds drag
            currentDrag = DragType.BOUNDS;
            draggingHandle = hoveredHandle;
            dragStartBounds = new Vector4f(settings.getBounds());
            Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
            lastMouseWorld = TransformGizmoRenderer.screenToWorld(
                    camera, mouseScreen.x, mouseScreen.y, viewportX, viewportY
            );
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (currentDrag == DragType.NONE) return;

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

        if (currentDrag == DragType.POSITION) {
            Vector2f pos = settings.getPosition();
            settings.setPosition(pos.x + deltaX, pos.y + deltaY);
            scene.markDirty();
        } else if (currentDrag == DragType.BOUNDS) {
            applyBoundsDrag(settings, deltaX, deltaY);
            scene.markDirty();
        }

        lastMouseWorld = mouseWorld;
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (button != 0) return;

        if (currentDrag != DragType.NONE) {
            pushUndoCommand();
        }

        resetDragState();
    }

    @Override
    public void onMouseMove(int tileX, int tileY) {
        if (currentDrag != DragType.NONE) return;
        if (!isCameraSelected()) return;

        hoveredCrosshair = false;
        hoveredHandle = BoundsHandle.NONE;

        SceneCameraSettings settings = getCameraSettings();
        if (settings == null) return;

        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());

        // Check crosshair first (higher priority)
        Vector2f crosshairScreen = getCrosshairScreenPos(settings);
        if (TransformGizmoRenderer.isPointInCircle(
                mouseScreen.x, mouseScreen.y,
                crosshairScreen.x, crosshairScreen.y, CROSSHAIR_HIT_RADIUS)) {
            hoveredCrosshair = true;
            return;
        }

        // Check bounds handles if bounds are active
        if (settings.isUseBounds()) {
            hoveredHandle = hitTestBoundsHandles(settings, mouseScreen.x, mouseScreen.y);
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

        // Draw bounds handles if bounds are active
        if (settings.isUseBounds()) {
            renderBoundsHandles(drawList, settings);
        }

        // Set cursor based on hover/drag state
        updateCursor();

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
        boolean highlighted = hoveredCrosshair || currentDrag == DragType.POSITION;

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
    // BOUNDS HANDLES
    // ========================================================================

    private void renderBoundsHandles(ImDrawList drawList, SceneCameraSettings settings) {
        Vector2f[] handlePositions = getBoundsHandlePositions(settings);
        BoundsHandle[] handles = {
                BoundsHandle.TOP_LEFT, BoundsHandle.TOP, BoundsHandle.TOP_RIGHT,
                BoundsHandle.LEFT, BoundsHandle.RIGHT,
                BoundsHandle.BOTTOM_LEFT, BoundsHandle.BOTTOM, BoundsHandle.BOTTOM_RIGHT
        };

        for (int i = 0; i < handles.length; i++) {
            BoundsHandle handle = handles[i];
            Vector2f pos = handlePositions[i];
            boolean isHovered = (hoveredHandle == handle) ||
                    (currentDrag == DragType.BOUNDS && draggingHandle == handle);
            boolean isEdge = (handle == BoundsHandle.TOP || handle == BoundsHandle.BOTTOM ||
                    handle == BoundsHandle.LEFT || handle == BoundsHandle.RIGHT);

            float size = isEdge ? HANDLE_SIZE * 0.8f : HANDLE_SIZE;
            int color = isHovered ? GizmoColors.ACTIVE : GizmoColors.fromRGBA(1.0f, 0.15f, 0.15f, 1.0f);

            drawList.addRectFilled(
                    pos.x - size / 2, pos.y - size / 2,
                    pos.x + size / 2, pos.y + size / 2,
                    color
            );
        }
    }

    /**
     * Returns screen positions for all 8 bounds handles.
     * Order: TL, T, TR, L, R, BL, B, BR
     */
    private Vector2f[] getBoundsHandlePositions(SceneCameraSettings settings) {
        Vector4f bounds = settings.getBounds(); // minX, minY, maxX, maxY

        float minX = bounds.x;
        float minY = bounds.y;
        float maxX = bounds.z;
        float maxY = bounds.w;
        float midX = (minX + maxX) / 2f;
        float midY = (minY + maxY) / 2f;

        // World positions for each handle
        // Screen Y is inverted: world maxY is screen top
        return new Vector2f[] {
                TransformGizmoRenderer.worldToScreen(camera, minX, maxY, viewportX, viewportY), // TL
                TransformGizmoRenderer.worldToScreen(camera, midX, maxY, viewportX, viewportY), // T
                TransformGizmoRenderer.worldToScreen(camera, maxX, maxY, viewportX, viewportY), // TR
                TransformGizmoRenderer.worldToScreen(camera, minX, midY, viewportX, viewportY), // L
                TransformGizmoRenderer.worldToScreen(camera, maxX, midY, viewportX, viewportY), // R
                TransformGizmoRenderer.worldToScreen(camera, minX, minY, viewportX, viewportY), // BL
                TransformGizmoRenderer.worldToScreen(camera, midX, minY, viewportX, viewportY), // B
                TransformGizmoRenderer.worldToScreen(camera, maxX, minY, viewportX, viewportY), // BR
        };
    }

    private BoundsHandle hitTestBoundsHandles(SceneCameraSettings settings, float mouseX, float mouseY) {
        Vector2f[] positions = getBoundsHandlePositions(settings);
        BoundsHandle[] handles = {
                BoundsHandle.TOP_LEFT, BoundsHandle.TOP, BoundsHandle.TOP_RIGHT,
                BoundsHandle.LEFT, BoundsHandle.RIGHT,
                BoundsHandle.BOTTOM_LEFT, BoundsHandle.BOTTOM, BoundsHandle.BOTTOM_RIGHT
        };

        float halfHit = HANDLE_HIT_SIZE / 2f;
        for (int i = 0; i < handles.length; i++) {
            Vector2f pos = positions[i];
            if (TransformGizmoRenderer.isPointInRect(
                    mouseX, mouseY,
                    pos.x - halfHit, pos.y - halfHit,
                    HANDLE_HIT_SIZE, HANDLE_HIT_SIZE)) {
                return handles[i];
            }
        }
        return BoundsHandle.NONE;
    }

    // ========================================================================
    // BOUNDS DRAG LOGIC
    // ========================================================================

    private void applyBoundsDrag(SceneCameraSettings settings, float deltaX, float deltaY) {
        Vector4f bounds = settings.getBounds();
        float minX = bounds.x;
        float minY = bounds.y;
        float maxX = bounds.z;
        float maxY = bounds.w;

        switch (draggingHandle) {
            case TOP_LEFT -> { minX += deltaX; maxY += deltaY; }
            case TOP -> { maxY += deltaY; }
            case TOP_RIGHT -> { maxX += deltaX; maxY += deltaY; }
            case LEFT -> { minX += deltaX; }
            case RIGHT -> { maxX += deltaX; }
            case BOTTOM_LEFT -> { minX += deltaX; minY += deltaY; }
            case BOTTOM -> { minY += deltaY; }
            case BOTTOM_RIGHT -> { maxX += deltaX; minY += deltaY; }
            default -> { return; }
        }

        // Clamp to prevent inversion (minimum 1 unit in each dimension)
        if (minX > maxX - 1f) {
            if (affectsMinX(draggingHandle)) minX = maxX - 1f;
            else maxX = minX + 1f;
        }
        if (minY > maxY - 1f) {
            if (affectsMinY(draggingHandle)) minY = maxY - 1f;
            else maxY = minY + 1f;
        }

        settings.setBounds(minX, minY, maxX, maxY);
    }

    private boolean affectsMinX(BoundsHandle handle) {
        return handle == BoundsHandle.TOP_LEFT || handle == BoundsHandle.LEFT || handle == BoundsHandle.BOTTOM_LEFT;
    }

    private boolean affectsMinY(BoundsHandle handle) {
        return handle == BoundsHandle.BOTTOM_LEFT || handle == BoundsHandle.BOTTOM || handle == BoundsHandle.BOTTOM_RIGHT;
    }

    // ========================================================================
    // CURSOR
    // ========================================================================

    private void updateCursor() {
        if (currentDrag == DragType.POSITION || hoveredCrosshair) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeAll);
            return;
        }

        BoundsHandle handle = currentDrag == DragType.BOUNDS ? draggingHandle : hoveredHandle;
        switch (handle) {
            case TOP, BOTTOM -> ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS);
            case LEFT, RIGHT -> ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            case TOP_LEFT, BOTTOM_RIGHT -> ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNWSE);
            case TOP_RIGHT, BOTTOM_LEFT -> ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNESW);
            default -> {}
        }
    }

    // ========================================================================
    // UNDO
    // ========================================================================

    private void pushUndoCommand() {
        SceneCameraSettings settings = getCameraSettings();
        if (settings == null) return;

        if (currentDrag == DragType.POSITION && dragStartPosition != null) {
            Vector2f newPos = settings.getPosition();
            if (!dragStartPosition.equals(newPos)) {
                UndoManager.getInstance().push(
                        new MoveCameraCommand(settings, dragStartPosition, new Vector2f(newPos))
                );
            }
        } else if (currentDrag == DragType.BOUNDS && dragStartBounds != null) {
            Vector4f newBounds = settings.getBounds();
            if (!dragStartBounds.equals(newBounds)) {
                UndoManager.getInstance().push(
                        new SetCameraBoundsCommand(settings, dragStartBounds, new Vector4f(newBounds))
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
        currentDrag = DragType.NONE;
        hoveredCrosshair = false;
        hoveredHandle = BoundsHandle.NONE;
        draggingHandle = BoundsHandle.NONE;
        dragStartPosition = null;
        dragStartBounds = null;
        lastMouseWorld = null;
    }
}
