package com.pocket.rpg.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.OverlayRenderer;
import org.joml.Vector4f;

/**
 * Wipe transition that reveals the new scene with directional wipes.
 * Pokemon-style screen transitions.
 * <p>
 * Timeline:
 * - 0% to 50%: Wipe IN (screen becomes obscured from direction)
 * - 50%: Scene switch occurs
 * - 50% to 100%: Wipe OUT (new scene revealed from direction)
 */
public class WipeTransition extends FadeTransition {

    /**
     * Direction of the wipe effect.
     */
    public enum WipeDirection {
        /** Wipe from left to right */
        LEFT,
        /** Wipe from right to left */
        RIGHT,
        /** Wipe from top to bottom */
        UP,
        /** Wipe from bottom to top */
        DOWN,
        /** Circle expanding from center */
        CIRCLE_IN,
        /** Circle contracting to center */
        CIRCLE_OUT
    }

    private final WipeDirection direction;

    /**
     * Creates a wipe transition from configuration.
     *
     * @param config    transition configuration
     * @param direction wipe direction
     */
    public WipeTransition(TransitionConfig config, WipeDirection direction) {
        super(config);
        this.direction = direction;
    }

    /**
     * Creates a wipe transition with specific parameters.
     *
     * @param fadeOutDuration duration of wipe in
     * @param fadeInDuration  duration of wipe out
     * @param fadeColor       color to wipe with
     * @param direction       wipe direction
     */
    public WipeTransition(float fadeOutDuration, float fadeInDuration,
                          Vector4f fadeColor, WipeDirection direction) {
        super(fadeOutDuration, fadeInDuration, fadeColor);
        this.direction = direction;
    }

    @Override
    public void render(OverlayRenderer overlayRenderer) {
        // Calculate wipe progress (0.0 = no wipe, 1.0 = fully wiped)
        float wipeProgress = calculateWipeProgress();

        if (wipeProgress > 0.001f) {
            // Render the wipe effect based on direction
            switch (direction) {
                case LEFT -> overlayRenderer.drawWipeLeft(fadeColor, wipeProgress);
                case RIGHT -> overlayRenderer.drawWipeRight(fadeColor, wipeProgress);
                case UP -> overlayRenderer.drawWipeUp(fadeColor, wipeProgress);
                case DOWN -> overlayRenderer.drawWipeDown(fadeColor, wipeProgress);
                case CIRCLE_IN -> overlayRenderer.drawCircleWipe(fadeColor, wipeProgress, true);
                case CIRCLE_OUT -> overlayRenderer.drawCircleWipe(fadeColor, wipeProgress, false);
            }
        }
    }

    /**
     * Calculates the wipe progress.
     * During fade out: 0.0 → 1.0 (wipe covers screen)
     * During fade in: 1.0 → 0.0 (wipe reveals screen)
     *
     * @return progress from 0.0 (no wipe) to 1.0 (fully wiped)
     */
    private float calculateWipeProgress() {
        if (currentTime < fadeOutDuration) {
            // Wipe IN phase: progress increases from 0 to 1
            float fadeOutProgress = currentTime / fadeOutDuration;
            return easeInOut(fadeOutProgress);
        } else {
            // Wipe OUT phase: progress decreases from 1 to 0
            float fadeInTime = currentTime - fadeOutDuration;
            if (fadeInTime < fadeInDuration) {
                float fadeInProgress = fadeInTime / fadeInDuration;
                return 1.0f - easeInOut(fadeInProgress);
            } else {
                return 0.0f;
            }
        }
    }

    /**
     * Gets the wipe direction.
     *
     * @return the direction of this wipe
     */
    public WipeDirection getDirection() {
        return direction;
    }
}