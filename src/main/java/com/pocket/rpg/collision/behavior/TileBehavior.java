package com.pocket.rpg.collision.behavior;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MoveResult;

/**
 * Defines behavior for a collision type.
 * <p>
 * Behaviors handle:
 * - Can the entity enter this tile?
 * - What movement modifier applies?
 * - Any special effects on entry/exit?
 * <p>
 * This makes the collision system extensible - new behaviors
 * can be added without modifying core classes.
 */
public interface TileBehavior {

    /**
     * Checks if movement into this tile is allowed.
     *
     * @param fromX     Starting tile X
     * @param fromY     Starting tile Y
     * @param fromZ     Starting Z-level
     * @param toX       Target tile X
     * @param toY       Target tile Y
     * @param toZ       Target Z-level
     * @param direction Direction of movement
     * @param context   Additional context (entity type, state, etc.)
     * @return MoveResult with allowed flag, modifier, and reason
     */
    MoveResult checkMove(int fromX, int fromY, int fromZ,
                         int toX, int toY, int toZ,
                         Direction direction,
                         MoveContext context);

    /**
     * Called when entity enters this tile.
     * Used for triggers, effects, etc.
     *
     * @param tileX   Tile X
     * @param tileY   Tile Y
     * @param tileZ   Z-level
     * @param context Move context
     */
    default void onEnter(int tileX, int tileY, int tileZ, MoveContext context) {
        // Default: do nothing
    }

    /**
     * Called when entity exits this tile.
     *
     * @param tileX   Tile X
     * @param tileY   Tile Y
     * @param tileZ   Z-level
     * @param context Move context
     */
    default void onExit(int tileX, int tileY, int tileZ, MoveContext context) {
        // Default: do nothing
    }

    /**
     * Gets the collision type this behavior handles.
     */
    CollisionType getType();

    // ========================================================================
    // CONTEXT
    // ========================================================================

    /**
     * Context passed to behavior methods.
     * Contains entity state and additional info needed for collision checks.
     */
    record MoveContext(
            Object entity,           // The moving entity (GameObject, etc.)
            boolean canSwim,         // Can entity swim in water?
            boolean canFly,          // Can entity fly over obstacles?
            boolean triggersEncounters // Does movement trigger wild encounters?
    ) {
        /**
         * Default context for basic ground-based movement.
         */
        public static MoveContext defaultContext(Object entity) {
            return new MoveContext(entity, false, false, true);
        }
    }
}