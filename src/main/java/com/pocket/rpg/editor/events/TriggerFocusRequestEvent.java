package com.pocket.rpg.editor.events;

import com.pocket.rpg.collision.trigger.TileCoord;

/**
 * Event published when the camera should focus on a trigger location.
 * <p>
 * Typically fired when double-clicking a trigger in the CollisionPanel list.
 *
 * @param coordinate The trigger coordinate to focus on
 */
public record TriggerFocusRequestEvent(TileCoord coordinate) implements EditorEvent {
}
