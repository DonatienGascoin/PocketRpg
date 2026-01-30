package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.interaction.CameraBoundsZone;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Undo command for changing a CameraBoundsZone's bounds via drag handles.
 */
public class SetBoundsZoneCommand implements EditorCommand {

    private final CameraBoundsZone zone;
    private final float oldMinX, oldMinY, oldMaxX, oldMaxY;
    private float newMinX, newMinY, newMaxX, newMaxY;

    public SetBoundsZoneCommand(CameraBoundsZone zone,
                                float oldMinX, float oldMinY, float oldMaxX, float oldMaxY,
                                float newMinX, float newMinY, float newMaxX, float newMaxY) {
        this.zone = zone;
        this.oldMinX = oldMinX;
        this.oldMinY = oldMinY;
        this.oldMaxX = oldMaxX;
        this.oldMaxY = oldMaxY;
        this.newMinX = newMinX;
        this.newMinY = newMinY;
        this.newMaxX = newMaxX;
        this.newMaxY = newMaxY;
    }

    @Override
    public void execute() {
        zone.setMinX(newMinX);
        zone.setMinY(newMinY);
        zone.setMaxX(newMaxX);
        zone.setMaxY(newMaxY);
    }

    @Override
    public void undo() {
        zone.setMinX(oldMinX);
        zone.setMinY(oldMinY);
        zone.setMaxX(oldMaxX);
        zone.setMaxY(oldMaxY);
    }

    @Override
    public String getDescription() {
        return "Set Bounds Zone";
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        return other instanceof SetBoundsZoneCommand cmd && cmd.zone == this.zone;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof SetBoundsZoneCommand cmd) {
            this.newMinX = cmd.newMinX;
            this.newMinY = cmd.newMinY;
            this.newMaxX = cmd.newMaxX;
            this.newMaxY = cmd.newMaxY;
        }
    }
}
