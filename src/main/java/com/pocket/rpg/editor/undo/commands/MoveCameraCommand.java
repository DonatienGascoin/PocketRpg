package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.undo.EditorCommand;
import org.joml.Vector2f;

/**
 * Command for moving the scene camera position.
 */
public class MoveCameraCommand implements EditorCommand {

    private final SceneCameraSettings settings;
    private final Vector2f oldPosition;
    private Vector2f newPosition;

    public MoveCameraCommand(SceneCameraSettings settings, Vector2f oldPosition, Vector2f newPosition) {
        this.settings = settings;
        this.oldPosition = new Vector2f(oldPosition);
        this.newPosition = new Vector2f(newPosition);
    }

    @Override
    public void execute() {
        settings.setPosition(newPosition.x, newPosition.y);
    }

    @Override
    public void undo() {
        settings.setPosition(oldPosition.x, oldPosition.y);
    }

    @Override
    public String getDescription() {
        return "Move Camera";
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        return other instanceof MoveCameraCommand cmd && cmd.settings == this.settings;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof MoveCameraCommand cmd) {
            this.newPosition = new Vector2f(cmd.newPosition);
        }
    }
}
