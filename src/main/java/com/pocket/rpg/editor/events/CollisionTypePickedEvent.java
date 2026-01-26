package com.pocket.rpg.editor.events;

import com.pocket.rpg.collision.CollisionType;

/**
 * Event published when a collision type is picked from the map using the collision picker tool.
 *
 * @param collisionType The picked collision type
 */
public record CollisionTypePickedEvent(CollisionType collisionType) implements EditorEvent {
}
