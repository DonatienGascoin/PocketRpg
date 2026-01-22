package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for renaming an entity.
 */
public class RenameEntityCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final String oldName;
    private String newName;

    public RenameEntityCommand(EditorGameObject entity, String oldName, String newName) {
        this.entity = entity;
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public void execute() {
        entity.setName(newName);
    }

    @Override
    public void undo() {
        entity.setName(oldName);
    }

    @Override
    public String getDescription() {
        return "Rename to " + newName;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof RenameEntityCommand cmd)) {
            return false;
        }
        return cmd.entity == this.entity;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof RenameEntityCommand cmd) {
            this.newName = cmd.newName;
        }
    }
}
