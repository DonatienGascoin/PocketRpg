package com.pocket.rpg.editor.tools;

import com.pocket.rpg.components.interaction.CameraBoundsZone;
import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.gizmo.TransformGizmoRenderer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetBoundsZoneCommand;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Interactive editor tool for resizing CameraBoundsZone bounds via drag handles.
 * <p>
 * When a CameraBoundsZone entity is selected, this tool renders 8 drag handles
 * (4 corners + 4 edge midpoints) on the zone's bounds rectangle. Dragging
 * a handle resizes the bounds with undo support.
 */
public class BoundsZoneTool implements EditorTool, ViewportAwareTool, ContinuousDragTool {

    /**
     * Whether the BoundsZoneTool is currently the active tool.
     * Used by CameraBoundsZoneInspector to reflect button state.
     */
    @Getter
    private static boolean toolActive = false;

    @Setter private EditorScene scene;
    @Setter private EditorCamera camera;
    @Setter private EditorSelectionManager selectionManager;

    private float viewportX, viewportY, viewportWidth, viewportHeight;

    // Handle constants
    private static final float HANDLE_SIZE = 8f;
    private static final float HANDLE_HIT_SIZE = 12f;

    private enum BoundsHandle {
        NONE,
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    private BoundsHandle hoveredHandle = BoundsHandle.NONE;
    private BoundsHandle draggingHandle = BoundsHandle.NONE;
    private boolean isDragging = false;
    private Vector2f lastMouseWorld;

    // Drag start state for undo
    private float dragStartMinX, dragStartMinY, dragStartMaxX, dragStartMaxY;

    @Override public String getName() { return "BoundsZone"; }

    @Override
    public void onActivate() {
        toolActive = true;
        resetDragState();
    }

    @Override
    public void onDeactivate() {
        toolActive = false;
        if (isDragging) {
            pushUndoCommand();
        }
        resetDragState();
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button != 0) return;

        CameraBoundsZone zone = getSelectedZone();
        if (zone == null) return;

        if (hoveredHandle != BoundsHandle.NONE) {
            isDragging = true;
            draggingHandle = hoveredHandle;
            dragStartMinX = zone.getMinX();
            dragStartMinY = zone.getMinY();
            dragStartMaxX = zone.getMaxX();
            dragStartMaxY = zone.getMaxY();
            Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
            lastMouseWorld = TransformGizmoRenderer.screenToWorld(
                    camera, mouseScreen.x, mouseScreen.y, viewportX, viewportY
            );
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (!isDragging) return;

        CameraBoundsZone zone = getSelectedZone();
        if (zone == null) return;

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

        applyBoundsDrag(zone, deltaX, deltaY);
        if (scene != null) {
            scene.markDirty();
        }

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

        hoveredHandle = BoundsHandle.NONE;

        CameraBoundsZone zone = getSelectedZone();
        if (zone == null) return;

        Vector2f mouseScreen = new Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY());
        hoveredHandle = hitTestBoundsHandles(zone, mouseScreen.x, mouseScreen.y);
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (scene == null) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        CameraBoundsZone zone = getSelectedZone();
        if (zone == null) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight, true);

        renderBoundsHandles(drawList, zone);
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
    // BOUNDS HANDLES RENDERING
    // ========================================================================

    private void renderBoundsHandles(ImDrawList drawList, CameraBoundsZone zone) {
        Vector2f[] handlePositions = getBoundsHandlePositions(zone);
        BoundsHandle[] handles = {
                BoundsHandle.TOP_LEFT, BoundsHandle.TOP, BoundsHandle.TOP_RIGHT,
                BoundsHandle.LEFT, BoundsHandle.RIGHT,
                BoundsHandle.BOTTOM_LEFT, BoundsHandle.BOTTOM, BoundsHandle.BOTTOM_RIGHT
        };

        for (int i = 0; i < handles.length; i++) {
            BoundsHandle handle = handles[i];
            Vector2f pos = handlePositions[i];
            boolean isHovered = (hoveredHandle == handle) ||
                    (isDragging && draggingHandle == handle);
            boolean isEdge = (handle == BoundsHandle.TOP || handle == BoundsHandle.BOTTOM ||
                    handle == BoundsHandle.LEFT || handle == BoundsHandle.RIGHT);

            float size = isEdge ? HANDLE_SIZE * 0.8f : HANDLE_SIZE;
            int color = isHovered ? GizmoColors.ACTIVE : GizmoColors.CAMERA_BOUNDS_ZONE_HANDLE;

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
    private Vector2f[] getBoundsHandlePositions(CameraBoundsZone zone) {
        float minX = zone.getMinX();
        float minY = zone.getMinY();
        float maxX = zone.getMaxX();
        float maxY = zone.getMaxY();
        float midX = (minX + maxX) / 2f;
        float midY = (minY + maxY) / 2f;

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

    private BoundsHandle hitTestBoundsHandles(CameraBoundsZone zone, float mouseX, float mouseY) {
        Vector2f[] positions = getBoundsHandlePositions(zone);
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

    private void applyBoundsDrag(CameraBoundsZone zone, float deltaX, float deltaY) {
        float minX = zone.getMinX();
        float minY = zone.getMinY();
        float maxX = zone.getMaxX();
        float maxY = zone.getMaxY();

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

        zone.setMinX(minX);
        zone.setMinY(minY);
        zone.setMaxX(maxX);
        zone.setMaxY(maxY);
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
        BoundsHandle handle = isDragging ? draggingHandle : hoveredHandle;
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
        CameraBoundsZone zone = getSelectedZone();
        if (zone == null) return;

        float newMinX = zone.getMinX();
        float newMinY = zone.getMinY();
        float newMaxX = zone.getMaxX();
        float newMaxY = zone.getMaxY();

        // Only push if bounds actually changed
        if (newMinX != dragStartMinX || newMinY != dragStartMinY ||
                newMaxX != dragStartMaxX || newMaxY != dragStartMaxY) {
            UndoManager.getInstance().push(new SetBoundsZoneCommand(
                    zone,
                    dragStartMinX, dragStartMinY, dragStartMaxX, dragStartMaxY,
                    newMinX, newMinY, newMaxX, newMaxY
            ));
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Gets the CameraBoundsZone from the currently selected entity, or null.
     */
    private CameraBoundsZone getSelectedZone() {
        if (selectionManager == null || !selectionManager.hasEntitySelection()) {
            return null;
        }
        EditorGameObject selected = selectionManager.getFirstSelectedEntity();
        if (selected == null) {
            return null;
        }
        return selected.getComponent(CameraBoundsZone.class);
    }

    private void resetDragState() {
        isDragging = false;
        hoveredHandle = BoundsHandle.NONE;
        draggingHandle = BoundsHandle.NONE;
        lastMouseWorld = null;
    }
}
