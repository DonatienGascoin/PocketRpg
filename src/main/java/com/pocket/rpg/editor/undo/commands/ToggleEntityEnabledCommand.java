package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for toggling an entity's enabled state.
 */
public class ToggleEntityEnabledCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final boolean newEnabled;

    public ToggleEntityEnabledCommand(EditorGameObject entity, boolean newEnabled) {
        this.entity = entity;
        this.newEnabled = newEnabled;
    }

    @Override
    public void execute() {
        entity.setEnabled(newEnabled);
    }

    @Override
    public void undo() {
        entity.setEnabled(!newEnabled);
    }

    @Override
    public String getDescription() {
        return (newEnabled ? "Enable" : "Disable") + " " + entity.getName();
    }
}
