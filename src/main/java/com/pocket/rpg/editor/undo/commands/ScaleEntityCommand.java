package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import org.joml.Vector3f;

public class ScaleEntityCommand implements EditorCommand {
    private final EditorGameObject entity;
    private final Vector3f oldScale;
    private Vector3f newScale;

    public ScaleEntityCommand(EditorGameObject entity, Vector3f oldScale, Vector3f newScale) {
        this.entity = entity;
        this.oldScale = new Vector3f(oldScale);
        this.newScale = new Vector3f(newScale);
    }

    @Override
    public void execute() {
        entity.setScale(newScale.x, newScale.y);
    }

    @Override
    public void undo() {
        entity.setScale(oldScale.x, oldScale.y);
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof ScaleEntityCommand cmd)) {
            return false;
        }

        return cmd.entity == this.entity;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof ScaleEntityCommand cmd) {
            this.newScale = cmd.newScale;
        }
    }

    @Override
    public String getDescription() {
        return "Scale " + entity.getName();
    }
}
