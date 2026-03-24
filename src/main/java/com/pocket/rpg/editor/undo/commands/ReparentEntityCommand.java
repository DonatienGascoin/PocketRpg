package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.TransformDriverInfo;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.components.ui.UITransformDriver;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.editor.utils.TransformSwapHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to change an entity's parent and/or position.
 * <p>
 * Automatically swaps Transform/UITransform when entities move into or out of
 * a Canvas hierarchy.
 */
public class ReparentEntityCommand implements EditorCommand {

    private final EditorScene scene;
    private final EditorGameObject entity;
    private final EditorGameObject newParent;
    private final int insertIndex;

    private EditorGameObject oldParent;
    private int oldIndex;

    // Track auto-swapped entities for undo: entity -> wasUITransform (before swap)
    private final Map<EditorGameObject, Boolean> autoSwappedEntities = new HashMap<>();

    public ReparentEntityCommand(EditorScene scene, EditorGameObject entity,
                                 EditorGameObject newParent, int insertIndex) {
        this.scene = scene;
        this.entity = entity;
        this.newParent = newParent;
        this.insertIndex = insertIndex;
    }

    @Override
    public void execute() {
        oldParent = (EditorGameObject) entity.getParent();

        // Save actual position in sibling list (not just order field)
        List<EditorGameObject> oldSiblings;
        if (oldParent == null) {
            oldSiblings = scene.getRootEntities();
        } else {
            oldSiblings = new ArrayList<>(oldParent.getChildren().stream()
                    .map(c -> (EditorGameObject) c).toList());
        }
        oldSiblings.sort(Comparator.comparingInt(EditorGameObject::getOrder));
        oldIndex = oldSiblings.indexOf(entity);
        if (oldIndex == -1) oldIndex = 0;

        // Bake layout-driven values before reparenting so the child keeps its
        // visual appearance when leaving a layout group parent
        bakeLayoutValuesIfNeeded(entity, oldParent);

        // Check canvas context before reparenting
        boolean wasUnderCanvas = TransformSwapHelper.isUnderCanvas(entity);

        // Perform the reparent
        scene.insertEntityAtPosition(entity, newParent, insertIndex);

        // Check canvas context after reparenting
        boolean nowUnderCanvas = TransformSwapHelper.isUnderCanvas(entity);

        // If canvas context changed, auto-swap transforms
        if (wasUnderCanvas != nowUnderCanvas) {
            autoSwappedEntities.clear();
            autoSwapTransformsRecursively(entity, nowUnderCanvas);
        }

        scene.markDirty();
    }

    @Override
    public void undo() {
        // Reverse all auto-swaps first (in reverse order of application)
        List<EditorGameObject> swappedList = new ArrayList<>(autoSwappedEntities.keySet());
        for (int i = swappedList.size() - 1; i >= 0; i--) {
            EditorGameObject swappedEntity = swappedList.get(i);
            boolean wasUITransform = autoSwappedEntities.get(swappedEntity);
            // Swap back to original type
            performTransformSwap(swappedEntity, wasUITransform);
        }
        autoSwappedEntities.clear();

        // Restore parent position
        scene.insertEntityAtPosition(entity, oldParent, oldIndex);
        scene.markDirty();
    }

    /**
     * If the entity is leaving a layout-driven parent, bakes the current effective
     * runtime values (position, size) into FIXED mode fields so the child preserves
     * its visual appearance after reparenting.
     */
    private void bakeLayoutValuesIfNeeded(EditorGameObject target, EditorGameObject parent) {
        if (parent == null) return;

        // Find a UITransformDriver on the old parent
        TransformDriverInfo driverInfo = null;
        for (Component comp : parent.getComponents()) {
            if (comp instanceof UITransformDriver driver) {
                driverInfo = driver.getChildDriverInfo(target);
                if (driverInfo != null) break;
            }
        }
        if (driverInfo == null) return;

        Transform transform = target.getTransform();
        if (!(transform instanceof UITransform ct)) return;

        if (driverInfo.positionDriven()) {
            float effectiveX = ct.getEffectiveOffsetX();
            float effectiveY = ct.getEffectiveOffsetY();
            ct.setAnchor(0, 0);
            ct.setPivot(0, 0);
            ct.setOffsetXMode(UITransform.SizeMode.FIXED);
            ct.setOffsetYMode(UITransform.SizeMode.FIXED);
            ct.setOffset(effectiveX, effectiveY);
        }

        if (driverInfo.widthDriven()) {
            float effectiveW = ct.getEffectiveWidth();
            ct.setWidthMode(UITransform.SizeMode.FIXED);
            ct.setWidth(effectiveW);
        }

        if (driverInfo.heightDriven()) {
            float effectiveH = ct.getEffectiveHeight();
            ct.setHeightMode(UITransform.SizeMode.FIXED);
            ct.setHeight(effectiveH);
        }

        ct.clearLayoutOverrides();
    }

    /**
     * Recursively auto-swaps transforms for entity and all descendants.
     */
    private void autoSwapTransformsRecursively(EditorGameObject target, boolean shouldBeUITransform) {
        autoSwapTransformIfNeeded(target, shouldBeUITransform);

        for (var child : target.getChildren()) {
            autoSwapTransformsRecursively((EditorGameObject) child, shouldBeUITransform);
        }
    }

    /**
     * Auto-swaps transform if needed based on canvas context.
     */
    private void autoSwapTransformIfNeeded(EditorGameObject target, boolean shouldBeUITransform) {
        // Skip Canvas entities - they always use regular Transform
        if (target.hasComponent(UICanvas.class)) {
            return;
        }

        Transform currentTransform = target.getTransform();
        if (currentTransform == null) return;

        boolean isUITransform = currentTransform instanceof UITransform;

        // Check if swap is needed
        if (isUITransform == shouldBeUITransform) {
            return; // Already correct type
        }

        // Check if swap is allowed
        if (!TransformSwapHelper.canSwapTransform(target, shouldBeUITransform)) {
            return; // Cannot swap (e.g., has UI components requiring UITransform)
        }

        // Record for undo before swapping
        autoSwappedEntities.put(target, isUITransform);

        // Perform the swap
        performTransformSwap(target, shouldBeUITransform);
    }

    /**
     * Performs inline transform swap (no nested command).
     */
    private void performTransformSwap(EditorGameObject target, boolean toUITransform) {
        Transform current = target.getTransform();
        if (current == null) return;

        Transform newTransform;
        if (toUITransform) {
            newTransform = TransformSwapHelper.createUITransformFrom(current);
        } else {
            newTransform = TransformSwapHelper.createTransformFrom((UITransform) current);
        }

        // Replace in components list
        target.replaceComponent(current, newTransform);
    }

    @Override
    public String getDescription() {
        if (newParent == null && oldParent != null) {
            return "Unparent " + entity.getName();
        } else if (newParent != null && oldParent != newParent) {
            return "Reparent " + entity.getName() + " to " + newParent.getName();
        }
        return "Reorder " + entity.getName();
    }
}