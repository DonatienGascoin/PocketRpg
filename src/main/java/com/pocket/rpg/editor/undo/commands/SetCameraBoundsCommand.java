package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.undo.EditorCommand;
import org.joml.Vector4f;

/**
 * Command for changing the scene camera bounds.
 */
public class SetCameraBoundsCommand implements EditorCommand {

    private final SceneCameraSettings settings;
    private final Vector4f oldBounds;
    private Vector4f newBounds;

    public SetCameraBoundsCommand(SceneCameraSettings settings, Vector4f oldBounds, Vector4f newBounds) {
        this.settings = settings;
        this.oldBounds = new Vector4f(oldBounds);
        this.newBounds = new Vector4f(newBounds);
    }

    @Override
    public void execute() {
        settings.setBounds(newBounds.x, newBounds.y, newBounds.z, newBounds.w);
    }

    @Override
    public void undo() {
        settings.setBounds(oldBounds.x, oldBounds.y, oldBounds.z, oldBounds.w);
    }

    @Override
    public String getDescription() {
        return "Set Camera Bounds";
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        return other instanceof SetCameraBoundsCommand cmd && cmd.settings == this.settings;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof SetCameraBoundsCommand cmd) {
            this.newBounds = new Vector4f(cmd.newBounds);
        }
    }
}
