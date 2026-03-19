package com.pocket.rpg.editor.panels.uidesigner;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Handles entity selection in the UI Designer, including GPU/CPU picking
 * and click-to-cycle through overlapping entities.
 * <p>
 * Selection cycling: when clicking on an already-selected entity without dragging,
 * cycles to the next overlapping entity at that position. This allows selecting
 * parent panels that are behind their children.
 */
public class UIDesignerSelectionHandler {

    private final UIDesignerState state;
    private final UIDesignerCoordinates coords;
    private final EditorContext context;

    /** GPU picking function: (canvasX, canvasY) -> EditorGameObject or null */
    private BiFunction<Float, Float, EditorGameObject> gpuPicker;

    // Cycling state
    private boolean wasAlreadySelected;
    private boolean dragOccurred;
    private float clickCanvasX;
    private float clickCanvasY;
    private float lastCycleCanvasX = Float.NaN;
    private float lastCycleCanvasY = Float.NaN;

    /** Max distance in screen pixels between clicks to continue cycling. */
    private static final float CYCLE_POSITION_THRESHOLD_PX = 5f;

    public UIDesignerSelectionHandler(UIDesignerState state, UIDesignerCoordinates coords, EditorContext context) {
        this.state = state;
        this.coords = coords;
        this.context = context;
    }

    public void setGpuPicker(BiFunction<Float, Float, EditorGameObject> gpuPicker) {
        this.gpuPicker = gpuPicker;
    }

    public boolean isWasAlreadySelected() {
        return wasAlreadySelected;
    }

    public boolean isDragOccurred() {
        return dragOccurred;
    }

    public void setDragOccurred(boolean dragOccurred) {
        this.dragOccurred = dragOccurred;
    }

    // ========================================================================
    // SELECTION DECISION
    // ========================================================================

    /**
     * The possible actions from evaluating a click.
     */
    public enum SelectionAction {
        /** A gizmo (handle, anchor, pivot) was hit — caller should start gizmo drag. */
        GIZMO,
        /** The currently selected entity is under the cursor — keep selection for drag. */
        KEEP_SELECTION,
        /** Pick the topmost entity and select it. */
        PICK_TOPMOST,
        /** Ctrl is held — toggle the picked entity in/out of selection. */
        TOGGLE,
        /** Shift is held — add the picked entity to selection. */
        ADD_TO_SELECTION,
        /** No entity at position — clear selection (unless modifiers held). */
        CLEAR
    }

    /**
     * Pure decision logic for what action to take on a click.
     * Extracted for testability — no ImGui or side effects.
     *
     * @param hasGizmoHit           whether a gizmo (handle/anchor/pivot) was hit
     * @param ctrlHeld              whether Ctrl is held
     * @param shiftHeld             whether Shift is held
     * @param isSelectedUnderCursor whether the single selected entity is under the cursor
     * @param hasEntityAtPosition   whether any entity was found at the click position
     */
    public static SelectionAction decideAction(boolean hasGizmoHit, boolean ctrlHeld, boolean shiftHeld,
                                               boolean isSelectedUnderCursor, boolean hasEntityAtPosition) {
        if (hasGizmoHit) return SelectionAction.GIZMO;
        if (ctrlHeld && hasEntityAtPosition) return SelectionAction.TOGGLE;
        if (shiftHeld && hasEntityAtPosition) return SelectionAction.ADD_TO_SELECTION;
        if (!ctrlHeld && !shiftHeld && isSelectedUnderCursor) return SelectionAction.KEEP_SELECTION;
        if (hasEntityAtPosition) return SelectionAction.PICK_TOPMOST;
        if (!ctrlHeld && !shiftHeld) return SelectionAction.CLEAR;
        return SelectionAction.CLEAR;
    }

    /**
     * Handles the selection part of a mouse-down click.
     * Returns the entity that should be used for drag, or null if no drag should start.
     */
    public EditorGameObject handleMouseDown(EditorScene scene, float canvasX, float canvasY,
                                            boolean ctrlHeld, boolean shiftHeld) {
        this.clickCanvasX = canvasX;
        this.clickCanvasY = canvasY;
        this.dragOccurred = false;

        // Check if the single selected entity is under the cursor (CPU bounds)
        EditorGameObject currentSelected = getSingleSelectedEntity(scene);
        boolean isSelectedUnderCursor = currentSelected != null
                && coords.isPointInElement(currentSelected, canvasX, canvasY);

        // If the mouse moved away from a previous cycle position, reset cycling
        // so the user gets the topmost entity at the new position.
        // NaN means no previous cycle — allow cycling to start.
        if (isSelectedUnderCursor && !Float.isNaN(lastCycleCanvasX)
                && !isNearLastCyclePosition(canvasX, canvasY)) {
            isSelectedUnderCursor = false;
            lastCycleCanvasX = Float.NaN;
            lastCycleCanvasY = Float.NaN;
        }

        // Pick topmost entity (GPU then CPU fallback)
        EditorGameObject topmost = findEntityAtPosition(scene, canvasX, canvasY);

        SelectionAction action = decideAction(false, ctrlHeld, shiftHeld,
                isSelectedUnderCursor, topmost != null);

        var selectionManager = context.getSelectionManager();

        switch (action) {
            case KEEP_SELECTION -> {
                wasAlreadySelected = true;
                return currentSelected;
            }
            case PICK_TOPMOST -> {
                wasAlreadySelected = false;
                selectionManager.selectEntity(topmost);
                return topmost;
            }
            case TOGGLE -> {
                wasAlreadySelected = false;
                selectionManager.toggleEntitySelection(topmost);
                return topmost;
            }
            case ADD_TO_SELECTION -> {
                wasAlreadySelected = false;
                Set<EditorGameObject> selected = new HashSet<>(scene.getSelectedEntities());
                selected.add(topmost);
                selectionManager.selectEntities(selected);
                return topmost;
            }
            case CLEAR -> {
                wasAlreadySelected = false;
                selectionManager.clearSelection();
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Handles the selection part of a mouse-up without drag (cycling).
     * Only cycles if the entity was already selected before this click and no drag occurred.
     */
    public void handleMouseUpWithoutDrag(EditorScene scene, boolean ctrlHeld, boolean shiftHeld) {
        if (!wasAlreadySelected || dragOccurred || ctrlHeld || shiftHeld) return;

        List<EditorGameObject> overlapping = findAllEntitiesAtPosition(scene, clickCanvasX, clickCanvasY);
        if (overlapping.size() <= 1) return;

        EditorGameObject current = getSingleSelectedEntity(scene);
        EditorGameObject next = getNextInCycle(overlapping, current);
        if (next != null && next != current) {
            lastCycleCanvasX = clickCanvasX;
            lastCycleCanvasY = clickCanvasY;
            context.getSelectionManager().selectEntity(next);
        }
    }

    // ========================================================================
    // CYCLING LOGIC
    // ========================================================================

    /**
     * Returns the previous entity in the cycle list (front-to-back order).
     * The list is in scene order (last = front), so cycling backward goes from
     * the frontmost element toward the back — matching user expectation after
     * the initial click selects the frontmost entity.
     * Wraps around. Returns the last (frontmost) entity if current is not in the list.
     * Returns null if the list is empty.
     * <p>
     * Package-private for testing.
     */
    static EditorGameObject getNextInCycle(List<EditorGameObject> entities, EditorGameObject current) {
        if (entities == null || entities.isEmpty()) return null;
        if (current == null) return entities.getLast();

        int index = entities.indexOf(current);
        if (index < 0) return entities.getLast();

        return entities.get((index - 1 + entities.size()) % entities.size());
    }

    // ========================================================================
    // ENTITY PICKING
    // ========================================================================

    /**
     * Finds the topmost entity at a canvas position using GPU picking with CPU fallback.
     */
    public EditorGameObject findEntityAtPosition(EditorScene scene, float canvasX, float canvasY) {
        // GPU picking: pixel-accurate selection that respects transparency
        if (gpuPicker != null) {
            EditorGameObject picked = gpuPicker.apply(canvasX, canvasY);
            if (picked != null) return picked;
        }

        // CPU fallback: bounding box check for non-visual containers
        // (GPU picking only renders visual elements, so containers need CPU fallback)
        var entities = scene.getEntities();
        for (int i = entities.size() - 1; i >= 0; i--) {
            EditorGameObject entity = entities.get(i);
            if (!coords.isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;
            if (!entity.isActiveInHierarchy()) continue;

            if (coords.isPointInElement(entity, canvasX, canvasY)) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Finds ALL entities at a canvas position using CPU bounding box checks.
     * Returns them in render order (depth-first hierarchy traversal, last = rendered on top).
     */
    List<EditorGameObject> findAllEntitiesAtPosition(EditorScene scene, float canvasX, float canvasY) {
        List<EditorGameObject> result = new ArrayList<>();

        // Traverse the UI hierarchy depth-first (matching render/picking order)
        for (EditorGameObject entity : scene.getEntities()) {
            if (!entity.isEnabled()) continue;
            UICanvas canvas = entity.getComponent(UICanvas.class);
            if (canvas != null && entity.getParent() == null) {
                collectEntitiesDepthFirst(entity, canvasX, canvasY, result);
            }
        }
        return result;
    }

    /**
     * Recursively collects entities at a position in depth-first order.
     * Parent is added before children, matching the render paint order.
     */
    private void collectEntitiesDepthFirst(EditorGameObject entity, float canvasX, float canvasY,
                                           List<EditorGameObject> result) {
        // Only include visually rendered entities (skip containers, layout groups, etc.)
        if (!entity.hasComponent(UICanvas.class) && coords.hasVisualContent(entity)
                && entity.isActiveInHierarchy()
                && coords.isPointInElement(entity, canvasX, canvasY)) {
            result.add(entity);
        }

        for (var child : entity.getChildren()) {
            if (child instanceof EditorGameObject editorChild) {
                collectEntitiesDepthFirst(editorChild, canvasX, canvasY, result);
            }
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private EditorGameObject getSingleSelectedEntity(EditorScene scene) {
        var selected = scene.getSelectedEntities();
        if (selected.size() != 1) return null;
        return selected.iterator().next();
    }

    /**
     * Checks if a canvas position is near the last cycle position.
     * Uses screen-pixel distance so the threshold feels consistent regardless of zoom.
     */
    private boolean isNearLastCyclePosition(float canvasX, float canvasY) {
        if (Float.isNaN(lastCycleCanvasX)) return false;
        float dx = (canvasX - lastCycleCanvasX) * state.getZoom();
        float dy = (canvasY - lastCycleCanvasY) * state.getZoom();
        return (dx * dx + dy * dy) <= CYCLE_POSITION_THRESHOLD_PX * CYCLE_POSITION_THRESHOLD_PX;
    }
}
