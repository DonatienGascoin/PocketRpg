package com.pocket.rpg.collision;

/**
 * Marker interface for components that block movement when registered with TileEntityMap.
 * <p>
 * Implementing classes:
 * - StaticOccupant (for static blocking objects like chests, pots)
 * - GridMovement (for moving entities like player, NPCs)
 * - Door (when closed)
 * - SecretPassage (when hidden)
 */
public interface BlockingComponent {
    // Marker interface - no methods required
}
