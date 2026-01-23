package com.pocket.rpg.collision.trigger;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.scenes.Scene;

/**
 * Context passed to trigger handlers when a trigger activates.
 * <p>
 * Contains all information about the trigger event including the entity
 * that triggered it, the tile location, and the trigger configuration.
 *
 * @param entity        The GameObject that triggered the event
 * @param tileX         X coordinate of the trigger tile
 * @param tileY         Y coordinate of the trigger tile
 * @param tileElevation Elevation of the trigger tile
 * @param data          The trigger configuration data
 * @param exitDirection The direction the entity is exiting (null for ON_ENTER/ON_INTERACT)
 */
public record TriggerContext(
        GameObject entity,
        int tileX,
        int tileY,
        int tileElevation,
        TriggerData data,
        Direction exitDirection
) {
    /**
     * Creates a context without exit direction (for ON_ENTER and ON_INTERACT triggers).
     */
    public TriggerContext(GameObject entity, int tileX, int tileY, int tileElevation, TriggerData data) {
        this(entity, tileX, tileY, tileElevation, data, null);
    }
    /**
     * Casts data to specific trigger type.
     * <p>
     * Use when you know the exact type from the handler registration.
     *
     * @param <T> The trigger data type
     * @return The typed trigger data
     */
    @SuppressWarnings("unchecked")
    public <T extends TriggerData> T getData() {
        return (T) data;
    }

    /**
     * Gets the scene the entity is in.
     *
     * @return The scene, or null if entity has no scene
     */
    public Scene getScene() {
        return entity != null ? entity.getScene() : null;
    }

    /**
     * Creates a TileCoord from this context's tile position.
     */
    public TileCoord getTileCoord() {
        return new TileCoord(tileX, tileY, tileElevation);
    }

    /**
     * Returns true if this trigger was activated by exiting a tile.
     * When true, {@link #exitDirection()} will be non-null.
     */
    public boolean isExitTrigger() {
        return exitDirection != null;
    }
}
