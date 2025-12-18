package com.pocket.rpg.collision;

import lombok.Getter;

/**
 * Modifiers that affect how entities move through tiles.
 * <p>
 * Applied by TileBehaviors to change movement speed, animation, or mechanics.
 */
@Getter
public enum MovementModifier {
    /**
     * Normal movement - no special effects
     */
    NORMAL(1.0f),

    /**
     * Jump movement - triggered by ledges
     */
    JUMP(0.5f),

    /**
     * Slow movement - sand, mud
     */
    SLOW(0.6f),

    /**
     * Sliding movement - ice
     */
    SLIDE(1.5f),

    /**
     * Swimming - water
     */
    SWIM(0.7f),

    /**
     * Encounter zone - tall grass
     */
    ENCOUNTER(1.0f);

    /**
     * Speed multiplier (1.0 = normal speed)
     */
    private final float speedMultiplier;

    MovementModifier(float speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }
}