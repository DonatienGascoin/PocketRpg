package com.pocket.rpg.editor.panels.uidesigner;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiPopupFlags;
import org.joml.Vector2f;

import java.util.function.BiFunction;

/**
 * Coordinates mouse and keyboard input for the UI Designer panel.
 * Delegates selection logic to {@link UIDesignerSelectionHandler} and
 * drag operations to {@link UIDesignerDragHandler}.
 */
public class UIDesignerInputHandler {

    private final UIDesignerState state;
    private final UIDesignerCoordinates coords;
    private final EditorContext context;

    private final UIDesignerSelectionHandler selectionHandler;
    private final UIDesignerDragHandler dragHandler;

    public UIDesignerInputHandler(UIDesignerState state, UIDesignerCoordinates coords, EditorContext context) {
        this.state = state;
        this.coords = coords;
        this.context = context;
        this.selectionHandler = new UIDesignerSelectionHandler(state, coords, context);
        this.dragHandler = new UIDesignerDragHandler(state, coords, context);
    }

    public void setGpuPicker(BiFunction<Float, Float, EditorGameObject> gpuPicker) {
        selectionHandler.setGpuPicker(gpuPicker);
    }

    // ========================================================================
    // MAIN INPUT HANDLING
    // ========================================================================

    public void handleInput() {
        // Block all new interactions when a popup is open (e.g., SpriteEditor modal)
        // Allow ongoing drags to complete on mouse release
        boolean popupOpen = ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId);

        if (!popupOpen) {
            handleCameraInput();
            handleZoomInput();
            handleClickInput();
            handleDragInput();
        }

        // Always handle mouse release to clean up drag state
        handleMouseRelease();
    }

    // ========================================================================
    // CAMERA & ZOOM
    // ========================================================================

    private void handleCameraInput() {
        if (ImGui.isMouseClicked(ImGuiMouseButton.Middle) && state.isHovered()
                && !ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId)) {
            state.setDraggingCamera(true);
        }
        if (ImGui.isMouseReleased(ImGuiMouseButton.Middle)) {
            state.setDraggingCamera(false);
        }

        if (state.isDraggingCamera()) {
            ImVec2 delta = ImGui.getMouseDragDelta(ImGuiMouseButton.Middle);
            if (delta.x != 0 || delta.y != 0) {
                state.pan(delta.x, delta.y);
                ImGui.resetMouseDragDelta(ImGuiMouseButton.Middle);
            }
        }
    }

    private void handleZoomInput() {
        if (state.isHovered() && !ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId)) {
            float scrollY = ImGui.getIO().getMouseWheel();
            float scrollX = ImGui.getIO().getMouseWheelH();

            if (scrollY != 0 || scrollX != 0) {
                if (context.getConfig().isTrackpadPanMode()) {
                    boolean ctrlHeld = ImGui.isKeyDown(ImGuiKey.LeftCtrl)
                            || ImGui.isKeyDown(ImGuiKey.RightCtrl);
                    if (ctrlHeld) {
                        if (scrollY != 0) {
                            float zoomFactor = scrollY * 0.1f * state.getZoom();
                            state.adjustZoom(zoomFactor);
                        }
                    } else {
                        state.pan(scrollX * 16f, -scrollY * 16f);
                    }
                } else {
                    if (scrollY != 0) {
                        float zoomFactor = scrollY * 0.1f * state.getZoom();
                        state.adjustZoom(zoomFactor);
                    }
                }
            }
        }
    }

    // ========================================================================
    // CLICK → SELECTION + DRAG START
    // ========================================================================

    private void handleClickInput() {
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && state.isHovered()) {
            handleClick();
        }
    }

    private void handleClick() {
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - state.getViewportX();
        float localY = mousePos.y - state.getViewportY();

        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        // 1. Check gizmos of selected entities first (highest priority)
        for (EditorGameObject entity : scene.getSelectedEntities()) {
            if (!coords.isUIEntity(entity) || entity.hasComponent(UICanvas.class)) continue;

            if (coords.isNearAnchor(entity, localX, localY)) {
                dragHandler.startAnchorDrag(entity);
                return;
            }
            if (coords.isNearPivot(entity, localX, localY)) {
                dragHandler.startPivotDrag(entity);
                return;
            }
            UIDesignerState.ResizeHandle handle = coords.getHandleAtPosition(entity, localX, localY);
            if (handle != null) {
                dragHandler.startResizeDrag(entity, handle);
                return;
            }
        }

        // 2. Delegate selection to selection handler
        Vector2f canvasPos = coords.screenToCanvas(localX, localY);
        boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
        boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);

        EditorGameObject target = selectionHandler.handleMouseDown(scene, canvasPos.x, canvasPos.y, ctrl, shift);

        // 3. Start move drag if an entity was selected
        if (target != null) {
            dragHandler.startMoveDrag(target, canvasPos.x, canvasPos.y);
        }
    }

    // ========================================================================
    // DRAG → DRAG HANDLER
    // ========================================================================

    private void handleDragInput() {
        if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            selectionHandler.setDragOccurred(true);
            dragHandler.handleDragInput();
        }
    }

    // ========================================================================
    // MOUSE RELEASE → CYCLING + UNDO COMMIT
    // ========================================================================

    private void handleMouseRelease() {
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            // Cycle selection if this was a click (no drag) on an already-selected entity
            boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
            boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);

            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                selectionHandler.handleMouseUpWithoutDrag(scene, ctrl, shift);
            }

            dragHandler.commitDragCommand();
            state.clearDragState();
        }
    }
}
