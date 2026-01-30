package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Undo command for changing the scene camera's initial bounds ID.
 */
public class SetInitialBoundsIdCommand implements EditorCommand {

    private final SceneCameraSettings settings;
    private final String oldId;
    private final String newId;

    public SetInitialBoundsIdCommand(SceneCameraSettings settings, String oldId, String newId) {
        this.settings = settings;
        this.oldId = oldId;
        this.newId = newId;
    }

    @Override
    public void execute() {
        settings.setInitialBoundsId(newId);
    }

    @Override
    public void undo() {
        settings.setInitialBoundsId(oldId);
    }

    @Override
    public String getDescription() {
        return "Set Initial Bounds ID";
    }
}
