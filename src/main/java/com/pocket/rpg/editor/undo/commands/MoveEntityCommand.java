package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.undo.EditorCommand;
import org.joml.Vector3f;

/**
 * Command for moving an entity.
 */
public class MoveEntityCommand implements EditorCommand {

    private final EditorEntity entity;
    private final Vector3f oldPosition;
    private Vector3f newPosition;

    public MoveEntityCommand(EditorEntity entity, Vector3f oldPosition, Vector3f newPosition) {
        this.entity = entity;
        this.oldPosition = new Vector3f(oldPosition);
        this.newPosition = new Vector3f(newPosition);
    }

    @Override
    public void execute() {
        entity.setPosition(newPosition);
    }

    @Override
    public void undo() {
        entity.setPosition(oldPosition);
    }

    @Override
    public String getDescription() {
        return "Move " + entity.getName();
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof MoveEntityCommand cmd)) {
            return false;
        }
        return cmd.entity == this.entity;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof MoveEntityCommand cmd) {
            this.newPosition = new Vector3f(cmd.newPosition);
        }
    }
}