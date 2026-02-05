package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.editor.utils.TransformSwapHelper;
import org.joml.Vector2f;
import org.joml.Vector3f;

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
        oldParent = entity.getParent();

        // Save actual position in sibling list (not just order field)
        List<EditorGameObject> oldSiblings;
        if (oldParent == null) {
            oldSiblings = scene.getRootEntities();
        } else {
            oldSiblings = new ArrayList<>(oldParent.getChildren());
        }
        oldSiblings.sort(Comparator.comparingInt(EditorGameObject::getOrder));
        oldIndex = oldSiblings.indexOf(entity);
        if (oldIndex == -1) oldIndex = 0;

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
     * Recursively auto-swaps transforms for entity and all descendants.
     */
    private void autoSwapTransformsRecursively(EditorGameObject target, boolean shouldBeUITransform) {
        autoSwapTransformIfNeeded(target, shouldBeUITransform);

        for (EditorGameObject child : target.getChildren()) {
            autoSwapTransformsRecursively(child, shouldBeUITransform);
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
            newTransform = createUITransformFrom(current);
        } else {
            newTransform = createTransformFrom((UITransform) current);
        }

        // Replace in components list
        List<Component> components = target.getComponents();
        int index = components.indexOf(current);
        if (index >= 0) {
            components.set(index, newTransform);
        }
    }

    /**
     * Creates a UITransform from a regular Transform, preserving values.
     */
    private UITransform createUITransformFrom(Transform transform) {
        UITransform uiTransform = new UITransform();

        // Copy position -> offset
        Vector3f localPos = transform.getLocalPosition();
        uiTransform.setOffset(localPos.x, localPos.y);

        // Copy Z rotation -> rotation2D
        Vector3f localRot = transform.getLocalRotation();
        uiTransform.setRotation2D(localRot.z);

        // Copy scale -> scale2D
        Vector3f localScale = transform.getLocalScale();
        uiTransform.setScale2D(localScale.x, localScale.y);

        // Set default UI-specific values
        uiTransform.setWidth(100f);
        uiTransform.setHeight(100f);
        uiTransform.setAnchor(0f, 0f);
        uiTransform.setPivot(0f, 0f);

        // Set the owner reference
        uiTransform.setOwner(transform.getOwner());

        return uiTransform;
    }

    /**
     * Creates a regular Transform from a UITransform, preserving values.
     */
    private Transform createTransformFrom(UITransform uiTransform) {
        Transform transform = new Transform();

        // Copy offset -> position
        Vector2f offset = uiTransform.getOffset();
        Vector3f localPos = uiTransform.getLocalPosition();
        transform.setLocalPosition(offset.x, offset.y, localPos.z);

        // Copy rotation2D -> Z rotation
        float rotation2D = uiTransform.getLocalRotation2D();
        transform.setLocalRotation(0, 0, rotation2D);

        // Copy scale2D -> scale
        Vector2f scale2D = uiTransform.getLocalScale2D();
        transform.setLocalScale(scale2D.x, scale2D.y, 1f);

        // Set the owner reference
        transform.setOwner(uiTransform.getOwner());

        return transform;
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