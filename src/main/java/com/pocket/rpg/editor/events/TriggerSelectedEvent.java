package com.pocket.rpg.editor.events;

import com.pocket.rpg.collision.trigger.TileCoord;

/**
 * Event published when a trigger tile is selected.
 * <p>
 * This can occur from:
 * <ul>
 *   <li>Clicking a trigger in the CollisionPanel trigger list</li>
 *   <li>Clicking a trigger tile in the scene view with picker/selection tool</li>
 * </ul>
 *
 * @param coordinate The selected trigger coordinate, or null to clear selection
 */
public record TriggerSelectedEvent(TileCoord coordinate) implements EditorEvent {
}
