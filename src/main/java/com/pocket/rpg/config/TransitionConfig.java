package com.pocket.rpg.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joml.Vector4f;

/**
 * Configuration for scene transitions.
 * Defines how scenes transition from one to another (fade effects, durations, colors, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitionConfig {

    /**
     * Duration of the fade-out phase in seconds.
     */
    @Builder.Default
    private float fadeOutDuration = 0.5f;

    /**
     * Duration of the fade-in phase in seconds.
     */
    @Builder.Default
    private float fadeInDuration = 0.5f;

    /**
     * Color of the fade overlay (RGBA).
     * Default is black (0, 0, 0, 1).
     */
    @Builder.Default
    private Vector4f fadeColor = new Vector4f(0, 0, 0, 1);

    /**
     * Optional text to display during transition.
     * Empty string means no text is displayed.
     */
    @Builder.Default
    private String transitionText = "";

    /**
     * Type of transition to perform.
     */
    @Builder.Default
    private TransitionType type = TransitionType.FADE;

    /**
     * Types of transitions available.
     */
    public enum TransitionType {
        /**
         * Simple fade to color and back.
         */
        FADE,

        /**
         * Fade with text overlay (e.g., "Loading...").
         */
        FADE_WITH_TEXT

        // Future expansion:
        // SLIDE_LEFT,
        // SLIDE_RIGHT,
        // WIPE,
        // CROSSFADE
    }

    /**
     * Copy constructor for creating defensive copies.
     *
     * @param other the config to copy
     */
    public TransitionConfig(TransitionConfig other) {
        this.fadeOutDuration = other.fadeOutDuration;
        this.fadeInDuration = other.fadeInDuration;
        this.fadeColor = new Vector4f(other.fadeColor);
        this.transitionText = other.transitionText;
        this.type = other.type;
    }

    /**
     * Gets the total duration of the transition.
     *
     * @return total duration in seconds
     */
    public float getTotalDuration() {
        return fadeOutDuration + fadeInDuration;
    }

    /**
     * Validates the configuration.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (fadeOutDuration < 0) {
            throw new IllegalArgumentException("fadeOutDuration cannot be negative");
        }
        if (fadeInDuration < 0) {
            throw new IllegalArgumentException("fadeInDuration cannot be negative");
        }
        if (fadeColor == null) {
            throw new IllegalArgumentException("fadeColor cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
    }
}