package com.pocket.rpg.postProcessing;

import lombok.Setter;

/**
 * Service locator for PostProcessor.
 * Provides a Unity-style static API while maintaining testability.
 *
 * <p>Usage in gameplay code:
 * <pre>{@code
 * PostProcessing.addEffect(new VignetteEffect());
 * PostProcessing.setEnabled(false); // Disable all effects
 * PostProcessing.removeEffect(vignetteEffect);
 * }</pre>
 *
 * <p>Usage:
 * <pre>{@code
 * PostProcessing.setProcessor(mockPostProcessor);
 * }</pre>
 */
public class PostProcessing {

    @Setter
    private static PostProcessor processor;

    /**
     * Initialize the post-processing system with a processor.
     * Called once during application startup.
     *
     * @param postProcessor The post processor instance to use
     */
    public static void initialize(PostProcessor postProcessor) {
        if (postProcessor == null) {
            throw new IllegalArgumentException("PostProcessor cannot be null");
        }
        processor = postProcessor;
        System.out.println("PostProcessing initialized");
    }

    /**
     * Get the current processor (for advanced usage).
     *
     * @return The current PostProcessor instance
     * @throws IllegalStateException if not initialized
     */
    public static PostProcessor getProcessor() {
        if (processor == null) {
            throw new IllegalStateException(
                    "PostProcessing not initialized. Call PostProcessing.initialize() first.");
        }
        return processor;
    }

    /**
     * Destroy the post-processing system and clear the processor.
     */
    public static void destroy() {
        if (processor != null) {
            processor.destroy();
            processor = null;
            System.out.println("PostProcessing destroyed");
        }
    }

    // ========================================
    // STATIC API (Unity-style convenience)
    // ========================================

    /**
     * Adds a post-processing effect to the pipeline.
     * The effect will be applied in the order it was added.
     *
     * @param effect The effect to add
     */
    public static void addEffect(PostEffect effect) {
        getProcessor().addEffect(effect);
    }

    /**
     * Removes a post-processing effect from the pipeline.
     *
     * @param effect The effect to remove
     * @return true if the effect was removed, false if it wasn't in the pipeline
     */
    public static boolean removeEffect(PostEffect effect) {
        return getProcessor().removeEffect(effect);
    }

    /**
     * Removes all post-processing effects from the pipeline.
     */
    public static void clearEffects() {
        getProcessor().clearEffects();
    }

    /**
     * Enables or disables all post-processing effects.
     * When disabled, the scene is rendered directly to the screen without effects.
     *
     * @param enabled true to enable effects, false to disable
     */
    public static void setEnabled(boolean enabled) {
        getProcessor().setEnabled(enabled);
    }

    /**
     * Checks if post-processing is currently enabled.
     *
     * @return true if effects are enabled, false otherwise
     */
    public static boolean isEnabled() {
        return getProcessor().isEnabled();
    }
}