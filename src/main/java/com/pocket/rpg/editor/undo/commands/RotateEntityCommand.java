package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import org.joml.Vector3f;

public class RotateEntityCommand implements EditorCommand {
    private final EditorGameObject entity;
    private final Vector3f oldRot;
    private Vector3f newRot;

    public RotateEntityCommand(EditorGameObject entity, Vector3f oldRot, Vector3f newRot) {
        this.entity = entity;
        this.oldRot = new Vector3f(oldRot);
        this.newRot = new Vector3f(newRot);
    }

    @Override
    public void execute() {
        entity.setRotation(newRot);
        syncOverride(newRot);
    }

    @Override
    public void undo() {
        entity.setRotation(oldRot);
        syncOverride(oldRot);
    }

    private void syncOverride(Vector3f value) {
        entity.syncFieldOverride(Transform.class.getName(), "localRotation", value);
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof RotateEntityCommand cmd)) {
            return false;
        }

        return cmd.entity == this.entity;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof RotateEntityCommand cmd) {
            this.newRot = new Vector3f(cmd.newRot);
        }
    }

    @Override
    public String getDescription() {
        return "Rotate " + entity.getName();
    }
}