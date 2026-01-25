package com.pocket.rpg.collision;

/**
 * Interface for components that can block movement when registered with TileEntityMap.
 * <p>
 * Implementing classes:
 * - StaticOccupant (always blocks)
 * - GridMovement (always blocks)
 * - Door (blocks when closed)
 * - SecretPassage (blocks when hidden)
 */
public interface BlockingComponent {

    /**
     * Checks if this component is currently blocking movement.
     * <p>
     * Override to implement conditional blocking (e.g., Door returns false when open).
     * Default returns true (always blocking).
     *
     * @return true if currently blocking movement
     */
    default boolean isBlocking() {
        return true;
    }
}
