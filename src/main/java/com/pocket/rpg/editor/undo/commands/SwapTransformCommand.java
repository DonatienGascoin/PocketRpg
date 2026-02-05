package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.List;

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
            newTransform = createUITransformFrom(current);
        } else {
            newTransform = createTransformFrom((UITransform) current);
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
        uiTransform.setOwner(entity);

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
        transform.setOwner(entity);

        return transform;
    }

    /**
     * Replaces the old transform with the new one in the entity's components list.
     */
    private void replaceTransform(Transform oldTransform, Transform newTransform) {
        List<Component> components = entity.getComponents();
        int index = components.indexOf(oldTransform);
        if (index >= 0) {
            components.set(index, newTransform);
        }
    }
}
