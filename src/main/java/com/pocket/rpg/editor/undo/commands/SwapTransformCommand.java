package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.editor.utils.TransformSwapHelper;

/**
 * Command for swapping between Transform and UITransform.
 * Preserves position, rotation, and scale values during the swap.
 */
public class SwapTransformCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final boolean toUITransform;

    // Saved values for undo
    private Transform oldTransform;

    public SwapTransformCommand(EditorGameObject entity, boolean toUITransform) {
        this.entity = entity;
        this.toUITransform = toUITransform;
    }

    @Override
    public void execute() {
        Transform current = entity.getTransform();
        if (current == null) return;

        // Save old transform for undo
        oldTransform = current;

        // Create new transform
        Transform newTransform;
        if (toUITransform) {
            newTransform = TransformSwapHelper.createUITransformFrom(current);
        } else {
            newTransform = TransformSwapHelper.createTransformFrom((UITransform) current);
        }

        // Replace in components list
        replaceTransform(current, newTransform);
    }

    @Override
    public void undo() {
        if (oldTransform == null) return;

        Transform current = entity.getTransform();
        if (current != null) {
            replaceTransform(current, oldTransform);
        }
    }

    @Override
    public String getDescription() {
        if (toUITransform) {
            return "Swap Transform to UITransform";
        } else {
            return "Swap UITransform to Transform";
        }
    }

    /**
     * Replaces the old transform with the new one in the entity's components list.
     */
    private void replaceTransform(Transform oldTransform, Transform newTransform) {
        entity.replaceComponent(oldTransform, newTransform);
    }
}
