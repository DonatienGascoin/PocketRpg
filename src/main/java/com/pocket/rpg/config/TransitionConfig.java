package com.pocket.rpg.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joml.Vector4f;

/**
 * Configuration for scene transitions.
 * Defines how scenes transition from one to another (fade effects, luma wipes, durations, colors).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitionConfig {

    /**
     * Duration of the fade-out/wipe-in phase in seconds.
     */
    @Builder.Default
    private float fadeOutDuration = 0.5f;

    /**
     * Duration of the fade-in/wipe-out phase in seconds.
     */
    @Builder.Default
    private float fadeInDuration = 0.5f;

    /**
     * Color of the fade/wipe overlay (RGBA).
     * Default is black (0, 0, 0, 1).
     */
    @Builder.Default
    private Vector4f fadeColor = new Vector4f(0, 0, 0, 1);

    /**
     * Name of the transition to use from the GameConfig transitions list.
     * Empty string means plain fade (no luma texture).
     * "Random" means pick a random transition from the list.
     */
    @Builder.Default
    private String transitionName = "";

    /**
     * Copy constructor for creating defensive copies.
     *
     * @param other the config to copy
     */
    public TransitionConfig(TransitionConfig other) {
        this.fadeOutDuration = other.fadeOutDuration;
        this.fadeInDuration = other.fadeInDuration;
        this.fadeColor = new Vector4f(other.fadeColor);
        this.transitionName = other.transitionName;
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
    }
}
