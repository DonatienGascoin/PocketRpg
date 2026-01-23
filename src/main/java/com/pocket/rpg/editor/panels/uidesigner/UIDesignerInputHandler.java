package com.pocket.rpg.editor.panels.uidesigner;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.UITransformDragCommand;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiPopupFlags;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles mouse and keyboard input for the UI Designer panel.
 * Uses direct typed component access (no reflection).
 */
public class UIDesignerInputHandler {

    private final UIDesignerState state;
    private final UIDesignerCoordinates coords;
    private final EditorContext context;

    public UIDesignerInputHandler(UIDesignerState state, UIDesignerCoordinates coords, EditorContext context) {
        this.state = state;
        this.coords = coords;
        this.context = context;
    }

    // ========================================================================
    // MAIN INPUT HANDLING
    // ========================================================================

    public void handleInput() {
        handleCameraInput();
        handleZoomInput();
        handleClickInput();
        handleDragInput();
        handleMouseRelease();
    }

    private void handleCameraInput() {
        // Don't start camera drag if a popup is open
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
        // Don't handle zoom if a popup is open (e.g., AssetPicker)
        if (state.isHovered() && !ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId)) {
            float scroll = ImGui.getIO().getMouseWheel();
            if (scroll != 0) {
                float zoomFactor = scroll * 0.1f * state.getZoom();
                state.adjustZoom(zoomFactor);
            }
        }
    }

    private void handleClickInput() {
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && state.isHovered()) {
            handleClick();
        }
    }

    private void handleDragInput() {
        if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            if (state.isDraggingHandle() && state.getDraggedEntity() != null) {
                handleResizeDrag();
            } else if (state.isDraggingAnchor() && state.getDraggedEntity() != null) {
                handleAnchorDrag();
            } else if (state.isDraggingPivot() && state.getDraggedEntity() != null) {
                handlePivotDrag();
            } else if (state.isDraggingElement() && state.getDraggedEntity() != null) {
                handleMoveDrag();
            }
        }
    }

    private void handleMouseRelease() {
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            commitDragCommand();
            state.clearDragState();
        }
    }

    // ========================================================================
    // CLICK HANDLING
    // ========================================================================

    private void handleClick() {
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - state.getViewportX();
        float localY = mousePos.y - state.getViewportY();

        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        // Check if clicking on gizmos of selected entities
        for (EditorGameObject entity : scene.getSelectedEntities()) {
            if (!coords.isUIEntity(entity) || entity.hasComponent(UICanvas.class)) continue;

            if (coords.isNearAnchor(entity, localX, localY)) {
                startAnchorDrag(entity);
                return;
            }

            if (coords.isNearPivot(entity, localX, localY)) {
                startPivotDrag(entity);
                return;
            }

            UIDesignerState.ResizeHandle handle = coords.getHandleAtPosition(entity, localX, localY);
            if (handle != null) {
                startResizeDrag(entity, handle);
                return;
            }
        }

        // Check if clicking on an entity
        Vector2f canvasPos = coords.screenToCanvas(localX, localY);
        EditorGameObject clicked = findEntityAtPosition(scene, canvasPos.x, canvasPos.y);

        boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
        boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);

        var selectionManager = context.getSelectionManager();
        if (clicked != null) {
            if (ctrl) {
                selectionManager.toggleEntitySelection(clicked);
            } else if (shift) {
                // Add to selection
                Set<EditorGameObject> selected = new HashSet<>(scene.getSelectedEntities());
                selected.add(clicked);
                selectionManager.selectEntities(selected);
            } else {
                selectionManager.selectEntity(clicked);
            }
            startMoveDrag(clicked, canvasPos.x, canvasPos.y);
        } else {
            if (!shift && !ctrl) {
                selectionManager.clearSelection();
            }
        }
    }

    private EditorGameObject findEntityAtPosition(EditorScene scene, float canvasX, float canvasY) {
        var entities = scene.getEntities();
        for (int i = entities.size() - 1; i >= 0; i--) {
            EditorGameObject entity = entities.get(i);
            if (!coords.isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;

            if (coords.isPointInElement(entity, canvasX, canvasY)) {
                return entity;
            }
        }
        return null;
    }

    // ========================================================================
    // DRAG START METHODS
    // ========================================================================

    private void startMoveDrag(EditorGameObject entity, float canvasX, float canvasY) {
        state.setDraggingElement(true);
        state.setDraggingHandle(false);
        state.setDraggingAnchor(false);
        state.setDraggingPivot(false);
        state.setDraggedEntity(entity);
        state.setDragStartX(canvasX);
        state.setDragStartY(canvasY);

        UITransform transform = entity.getComponent(UITransform.class);
        if (transform != null) {
            Vector2f offset = transform.getOffset();
            state.setEntityStartOffsetX(offset.x);
            state.setEntityStartOffsetY(offset.y);
            captureOldValuesForUndo(entity, transform);
        }
    }

    private void startResizeDrag(EditorGameObject entity, UIDesignerState.ResizeHandle handle) {
        state.setDraggingHandle(true);
        state.setDraggingElement(false);
        state.setDraggingAnchor(false);
        state.setDraggingPivot(false);
        state.setDraggedEntity(entity);
        state.setActiveHandle(handle);

        ImVec2 mousePos = ImGui.getMousePos();
        Vector2f canvasPos = coords.screenToCanvas(mousePos.x - state.getViewportX(), mousePos.y - state.getViewportY());
        state.setDragStartX(canvasPos.x);
        state.setDragStartY(canvasPos.y);

        UITransform transform = entity.getComponent(UITransform.class);
        if (transform != null) {
            Vector2f offset = transform.getOffset();
            state.setEntityStartOffsetX(offset.x);
            state.setEntityStartOffsetY(offset.y);
            state.setEntityStartWidth(transform.getWidth());
            state.setEntityStartHeight(transform.getHeight());
            captureOldValuesForUndo(entity, transform);

            // Capture child states for cascading resize
            List<UITransformDragCommand.ChildTransformState> childStates = new ArrayList<>();
            captureChildStates(entity, childStates);
            state.setDragChildStates(childStates);
        }
    }

    private void startAnchorDrag(EditorGameObject entity) {
        state.setDraggingAnchor(true);
        state.setDraggingElement(false);
        state.setDraggingHandle(false);
        state.setDraggingPivot(false);
        state.setDraggedEntity(entity);

        UITransform transform = entity.getComponent(UITransform.class);
        if (transform != null) {
            Vector2f anchor = transform.getAnchor();
            state.setEntityStartAnchorX(anchor.x);
            state.setEntityStartAnchorY(anchor.y);
            captureOldValuesForUndo(entity, transform);
        }
    }

    private void startPivotDrag(EditorGameObject entity) {
        state.setDraggingPivot(true);
        state.setDraggingElement(false);
        state.setDraggingHandle(false);
        state.setDraggingAnchor(false);
        state.setDraggedEntity(entity);

        UITransform transform = entity.getComponent(UITransform.class);
        if (transform != null) {
            Vector2f pivot = transform.getPivot();
            state.setEntityStartPivotX(pivot.x);
            state.setEntityStartPivotY(pivot.y);
            captureOldValuesForUndo(entity, transform);
        }
    }

    private void captureOldValuesForUndo(EditorGameObject entity, UITransform transform) {
        state.setDragOldOffset(new Vector2f(transform.getOffset()));
        state.setDragOldWidth(transform.getWidth());
        state.setDragOldHeight(transform.getHeight());
        state.setDragOldAnchor(new Vector2f(transform.getAnchor()));
        state.setDragOldPivot(new Vector2f(transform.getPivot()));
    }

    private void captureChildStates(EditorGameObject parent, List<UITransformDragCommand.ChildTransformState> states) {
        for (EditorGameObject child : parent.getChildren()) {
            UITransform childTransform = child.getComponent(UITransform.class);
            if (childTransform != null) {
                Vector2f offset = childTransform.getOffset();
                float width = childTransform.getWidth();
                float height = childTransform.getHeight();

                UITransformDragCommand.ChildTransformState childState =
                        new UITransformDragCommand.ChildTransformState(child, childTransform, offset, width, height);
                states.add(childState);

                captureChildStates(child, states);
            }
        }
    }

    // ========================================================================
    // DRAG HANDLERS
    // ========================================================================

    private void handleMoveDrag() {
        EditorGameObject draggedEntity = state.getDraggedEntity();
        if (draggedEntity == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - state.getViewportX();
        float localY = mousePos.y - state.getViewportY();

        Vector2f canvasPos = coords.screenToCanvas(localX, localY);
        float deltaX = canvasPos.x - state.getDragStartX();
        float deltaY = canvasPos.y - state.getDragStartY();

        float newOffsetX = state.getEntityStartOffsetX() + deltaX;
        float newOffsetY = state.getEntityStartOffsetY() + deltaY;

        // Apply edge snapping
        if (state.isSnapEnabled()) {
            UITransform transform = draggedEntity.getComponent(UITransform.class);
            if (transform != null) {
                float[] snapped = applyEdgeSnap(transform, newOffsetX, newOffsetY);
                newOffsetX = snapped[0];
                newOffsetY = snapped[1];
            }
        }

        UITransform transform = draggedEntity.getComponent(UITransform.class);
        if (transform != null) {
            transform.setOffset(newOffsetX, newOffsetY);
            markSceneDirty();
        }
    }

    private float[] applyEdgeSnap(UITransform transform, float newOffsetX, float newOffsetY) {
        float width = transform.getWidth();
        float height = transform.getHeight();
        Vector2f anchor = transform.getAnchor();
        Vector2f pivot = transform.getPivot();

        float canvasWidth = state.getCanvasWidth();
        float canvasHeight = state.getCanvasHeight();

        float anchorX = anchor.x * canvasWidth;
        float anchorY = anchor.y * canvasHeight;
        float elemX = anchorX + newOffsetX - pivot.x * width;
        float elemY = anchorY + newOffsetY - pivot.y * height;
        float elemRight = elemX + width;
        float elemBottom = elemY + height;

        float threshold = UIDesignerState.SNAP_THRESHOLD / state.getZoom();

        if (Math.abs(elemX) < threshold) {
            newOffsetX = pivot.x * width - anchorX;
        }
        if (Math.abs(elemRight - canvasWidth) < threshold) {
            newOffsetX = canvasWidth - width + pivot.x * width - anchorX;
        }
        if (Math.abs(elemY) < threshold) {
            newOffsetY = pivot.y * height - anchorY;
        }
        if (Math.abs(elemBottom - canvasHeight) < threshold) {
            newOffsetY = canvasHeight - height + pivot.y * height - anchorY;
        }

        return new float[]{newOffsetX, newOffsetY};
    }

    private void handleResizeDrag() {
        EditorGameObject draggedEntity = state.getDraggedEntity();
        UIDesignerState.ResizeHandle activeHandle = state.getActiveHandle();
        if (draggedEntity == null || activeHandle == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        Vector2f canvasPos = coords.screenToCanvas(mousePos.x - state.getViewportX(), mousePos.y - state.getViewportY());
        float deltaX = canvasPos.x - state.getDragStartX();
        float deltaY = canvasPos.y - state.getDragStartY();

        UITransform transform = draggedEntity.getComponent(UITransform.class);
        if (transform == null) return;

        Vector2f pivot = transform.getPivot();
        float entityStartWidth = state.getEntityStartWidth();
        float entityStartHeight = state.getEntityStartHeight();
        float entityStartOffsetX = state.getEntityStartOffsetX();
        float entityStartOffsetY = state.getEntityStartOffsetY();

        float newWidth = entityStartWidth;
        float newHeight = entityStartHeight;
        float newOffsetX = entityStartOffsetX;
        float newOffsetY = entityStartOffsetY;

        switch (activeHandle) {
            case TOP_LEFT -> {
                newWidth = Math.max(1, entityStartWidth - deltaX);
                newHeight = Math.max(1, entityStartHeight - deltaY);
                newOffsetX = entityStartOffsetX + deltaX * (1 - pivot.x);
                newOffsetY = entityStartOffsetY + deltaY * (1 - pivot.y);
            }
            case TOP -> {
                newHeight = Math.max(1, entityStartHeight - deltaY);
                newOffsetY = entityStartOffsetY + deltaY * (1 - pivot.y);
            }
            case TOP_RIGHT -> {
                newWidth = Math.max(1, entityStartWidth + deltaX);
                newHeight = Math.max(1, entityStartHeight - deltaY);
                newOffsetX = entityStartOffsetX + deltaX * pivot.x;
                newOffsetY = entityStartOffsetY + deltaY * (1 - pivot.y);
            }
            case LEFT -> {
                newWidth = Math.max(1, entityStartWidth - deltaX);
                newOffsetX = entityStartOffsetX + deltaX * (1 - pivot.x);
            }
            case RIGHT -> {
                newWidth = Math.max(1, entityStartWidth + deltaX);
                newOffsetX = entityStartOffsetX + deltaX * pivot.x;
            }
            case BOTTOM_LEFT -> {
                newWidth = Math.max(1, entityStartWidth - deltaX);
                newHeight = Math.max(1, entityStartHeight + deltaY);
                newOffsetX = entityStartOffsetX + deltaX * (1 - pivot.x);
                newOffsetY = entityStartOffsetY + deltaY * pivot.y;
            }
            case BOTTOM -> {
                newHeight = Math.max(1, entityStartHeight + deltaY);
                newOffsetY = entityStartOffsetY + deltaY * pivot.y;
            }
            case BOTTOM_RIGHT -> {
                newWidth = Math.max(1, entityStartWidth + deltaX);
                newHeight = Math.max(1, entityStartHeight + deltaY);
                newOffsetX = entityStartOffsetX + deltaX * pivot.x;
                newOffsetY = entityStartOffsetY + deltaY * pivot.y;
            }
        }

        transform.setWidth(newWidth);
        transform.setHeight(newHeight);
        transform.setOffset(newOffsetX, newOffsetY);

        // Apply cascading resize to children
        float scaleX = newWidth / entityStartWidth;
        float scaleY = newHeight / entityStartHeight;
        applyCascadingResize(scaleX, scaleY);

        markSceneDirty();
    }

    private void applyCascadingResize(float scaleX, float scaleY) {
        List<UITransformDragCommand.ChildTransformState> childStates = state.getDragChildStates();
        if (childStates == null) return;

        for (UITransformDragCommand.ChildTransformState childState : childStates) {
            UITransform childTransform = childState.entity.getComponent(UITransform.class);
            if (childTransform == null) continue;

            float newWidth = childState.getOldWidth() * scaleX;
            float newHeight = childState.getOldHeight() * scaleY;
            float newOffsetX = childState.getOldOffset().x * scaleX;
            float newOffsetY = childState.getOldOffset().y * scaleY;

            childTransform.setWidth(Math.max(1, newWidth));
            childTransform.setHeight(Math.max(1, newHeight));
            childTransform.setOffset(newOffsetX, newOffsetY);
        }
    }

    private void handleAnchorDrag() {
        EditorGameObject draggedEntity = state.getDraggedEntity();
        if (draggedEntity == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        Vector2f canvasPos = coords.screenToCanvas(mousePos.x - state.getViewportX(), mousePos.y - state.getViewportY());

        // Get parent bounds
        float parentWidth = state.getCanvasWidth();
        float parentHeight = state.getCanvasHeight();
        float parentX = 0;
        float parentY = 0;

        EditorGameObject parent = coords.findParentWithUITransform(draggedEntity);
        if (parent != null) {
            float[] parentBounds = coords.calculateElementBounds(parent);
            if (parentBounds != null) {
                parentX = parentBounds[0];
                parentY = parentBounds[1];
                parentWidth = parentBounds[2];
                parentHeight = parentBounds[3];
            }
        }

        float newAnchorX = (canvasPos.x - parentX) / parentWidth;
        float newAnchorY = (canvasPos.y - parentY) / parentHeight;

        newAnchorX = Math.max(0, Math.min(1, newAnchorX));
        newAnchorY = Math.max(0, Math.min(1, newAnchorY));

        // Snap to common values
        newAnchorX = snapToValue(newAnchorX, 0f, 0.05f);
        newAnchorX = snapToValue(newAnchorX, 0.5f, 0.05f);
        newAnchorX = snapToValue(newAnchorX, 1f, 0.05f);
        newAnchorY = snapToValue(newAnchorY, 0f, 0.05f);
        newAnchorY = snapToValue(newAnchorY, 0.5f, 0.05f);
        newAnchorY = snapToValue(newAnchorY, 1f, 0.05f);

        UITransform transform = draggedEntity.getComponent(UITransform.class);
        if (transform != null) {
            Vector2f oldAnchor = transform.getAnchor();
            Vector2f offset = transform.getOffset();

            // Adjust offset to compensate for anchor movement
            float anchorDeltaX = (newAnchorX - oldAnchor.x) * parentWidth;
            float anchorDeltaY = (newAnchorY - oldAnchor.y) * parentHeight;

            transform.setAnchor(newAnchorX, newAnchorY);
            transform.setOffset(offset.x - anchorDeltaX, offset.y - anchorDeltaY);

            markSceneDirty();
        }
    }

    private void handlePivotDrag() {
        EditorGameObject draggedEntity = state.getDraggedEntity();
        if (draggedEntity == null) return;

        ImVec2 mousePos = ImGui.getMousePos();
        Vector2f canvasPos = coords.screenToCanvas(mousePos.x - state.getViewportX(), mousePos.y - state.getViewportY());

        float[] bounds = coords.calculateElementBounds(draggedEntity);
        if (bounds == null) return;

        UITransform transform = draggedEntity.getComponent(UITransform.class);
        if (transform == null) return;

        float width = transform.getWidth();
        float height = transform.getHeight();
        Vector2f oldPivot = transform.getPivot();
        Vector2f offset = transform.getOffset();
        Vector2f anchor = transform.getAnchor();

        // Get parent bounds for anchor calculation
        float parentWidth = state.getCanvasWidth();
        float parentHeight = state.getCanvasHeight();
        float parentX = 0;
        float parentY = 0;

        EditorGameObject parent = coords.findParentWithUITransform(draggedEntity);
        if (parent != null) {
            float[] parentBounds = coords.calculateElementBounds(parent);
            if (parentBounds != null) {
                parentX = parentBounds[0];
                parentY = parentBounds[1];
                parentWidth = parentBounds[2];
                parentHeight = parentBounds[3];
            }
        }

        float anchorPosX = parentX + anchor.x * parentWidth + offset.x;
        float anchorPosY = parentY + anchor.y * parentHeight + offset.y;

        float newPivotX = (canvasPos.x - anchorPosX + oldPivot.x * width) / width;
        float newPivotY = (canvasPos.y - anchorPosY + oldPivot.y * height) / height;

        newPivotX = Math.max(0, Math.min(1, newPivotX));
        newPivotY = Math.max(0, Math.min(1, newPivotY));

        // Snap to common values
        newPivotX = snapToValue(newPivotX, 0f, 0.05f);
        newPivotX = snapToValue(newPivotX, 0.5f, 0.05f);
        newPivotX = snapToValue(newPivotX, 1f, 0.05f);
        newPivotY = snapToValue(newPivotY, 0f, 0.05f);
        newPivotY = snapToValue(newPivotY, 0.5f, 0.05f);
        newPivotY = snapToValue(newPivotY, 1f, 0.05f);

        // Adjust offset to compensate for pivot movement
        float pivotDeltaX = (newPivotX - oldPivot.x) * width;
        float pivotDeltaY = (newPivotY - oldPivot.y) * height;

        transform.setPivot(newPivotX, newPivotY);
        transform.setOffset(offset.x + pivotDeltaX, offset.y + pivotDeltaY);

        markSceneDirty();
    }

    private float snapToValue(float value, float target, float threshold) {
        if (Math.abs(value - target) < threshold) {
            return target;
        }
        return value;
    }

    // ========================================================================
    // UNDO COMMAND
    // ========================================================================

    private void commitDragCommand() {
        EditorGameObject draggedEntity = state.getDraggedEntity();
        if (draggedEntity == null || state.getDragOldOffset() == null) return;

        UITransform transform = draggedEntity.getComponent(UITransform.class);
        if (transform == null) return;

        Vector2f newOffset = transform.getOffset();
        float newWidth = transform.getWidth();
        float newHeight = transform.getHeight();
        Vector2f newAnchor = transform.getAnchor();
        Vector2f newPivot = transform.getPivot();

        String description;
        if (state.isDraggingHandle()) {
            description = "Resize " + draggedEntity.getName();
        } else if (state.isDraggingAnchor()) {
            description = "Move Anchor " + draggedEntity.getName();
        } else if (state.isDraggingPivot()) {
            description = "Move Pivot " + draggedEntity.getName();
        } else {
            description = "Move " + draggedEntity.getName();
        }

        UITransformDragCommand command = new UITransformDragCommand(
                draggedEntity, transform,
                state.getDragOldOffset(), state.getDragOldWidth(), state.getDragOldHeight(),
                state.getDragOldAnchor(), state.getDragOldPivot(),
                newOffset, newWidth, newHeight, newAnchor, newPivot,
                description
        );

        // Add child states
        List<UITransformDragCommand.ChildTransformState> childStates = state.getDragChildStates();
        if (childStates != null) {
            for (UITransformDragCommand.ChildTransformState childState : childStates) {
                childState.captureNewValues();
                command.addChildState(childState);
            }
        }

        if (command.hasChanges()) {
            command.undo();
            UndoManager.getInstance().execute(command);
            markSceneDirty();
        }

        state.setDragOldOffset(null);
    }

    private void markSceneDirty() {
        EditorScene scene = context.getCurrentScene();
        if (scene != null) {
            scene.markDirty();
        }
    }
}
