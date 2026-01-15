package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Undo command for rotating UITransform components.
 * Supports command merging for smooth drag operations.
 */
public class UIRotationCommand implements EditorCommand {
    private final EditorGameObject entity;
    private final UITransform transform;
    private final float oldRotation;
    private float newRotation;

    public UIRotationCommand(EditorGameObject entity, UITransform transform,
                             float oldRotation, float newRotation) {
        this.entity = entity;
        this.transform = transform;
        this.oldRotation = oldRotation;
        this.newRotation = newRotation;
    }

    @Override
    public void execute() {
        transform.setRotation2D(newRotation);
    }

    @Override
    public void undo() {
        transform.setRotation2D(oldRotation);
    }

    @Override
    public String getDescription() {
        return "Rotate " + entity.getName();
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof UIRotationCommand cmd)) {
            return false;
        }
        return cmd.entity == this.entity && cmd.transform == this.transform;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof UIRotationCommand cmd) {
            this.newRotation = cmd.newRotation;
        }
    }
}
