package com.pocket.rpg.animation;

/**
 * Single frame in an animation sequence.
 * Uses sprite path format compatible with SpriteReference.
 *
 * @param spritePath Path to sprite (e.g., "spritesheets/player.spritesheet#0")
 * @param duration   Time in seconds to display this frame
 */
public record AnimationFrame(String spritePath, float duration) {

    /**
     * Marker for frames with no sprite selected (empty frame).
     * Used instead of null for clearer semantics.
     */
    public static final String EMPTY_SPRITE = "";

    public AnimationFrame {
        // Allow empty/null spritePath for frames that don't have a sprite yet
        if (spritePath == null) {
            spritePath = EMPTY_SPRITE;
        }
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }

    /**
     * Returns true if this frame has no sprite assigned.
     */
    public boolean hasSprite() {
        return spritePath != null && !spritePath.isEmpty();
    }
}
