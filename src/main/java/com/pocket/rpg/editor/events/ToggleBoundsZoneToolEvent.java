package com.pocket.rpg.editor.events;

/**
 * Event published to toggle the BoundsZoneTool on/off.
 * Used by CameraBoundsZoneInspector to let users activate the bounds editing tool.
 *
 * @param activate true to activate, false to deactivate
 */
public record ToggleBoundsZoneToolEvent(boolean activate) implements EditorEvent {
}
