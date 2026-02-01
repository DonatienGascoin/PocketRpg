// Time.java
package com.pocket.rpg.time;

import lombok.Setter;

/**
 * Service locator for TimeContext.
 * Provides Unity-style static API while maintaining testability.
 * <p>
 * Usage in gameplay code:
 * float dt = Time.deltaTime();
 * if (Time.time() > 10.0f) { ... }
 * <p>
 * Usage in tests:
 * Time.setContext(new MockTimeContext());
 */
public class Time {

    /**
     * Set the time context (mainly for testing).
     */
    @Setter
    private static TimeContext context;

    /**
     * Initialize the time system with a context.
     * Called once during application startup.
     */
    public static void initialize(TimeContext timeContext) {
        if (timeContext == null) {
            throw new IllegalArgumentException("Time context cannot be null");
        }
        context = timeContext;
        context.init();
        System.out.println("Time initialized");
    }

    /**
     * Returns true if the time system has been initialized.
     */
    public static boolean isInitialized() {
        return context != null;
    }

    /**
     * Get the current context (for advanced usage).
     */
    public static TimeContext getContext() {
        if (context == null) {
            throw new IllegalStateException(
                    "Time not initialized. Call Time.initialize() first.");
        }
        return context;
    }

    // ========================================
    // STATIC API (Unity-style convenience)
    // ========================================

    /**
     * Update the time system.
     * Called once per frame by the application.
     */
    public static void update() {
        getContext().update();
    }

    /**
     * Get the time elapsed since the last frame (in seconds).
     * This is what you should use for most time-based updates.
     * <p>
     * Example: position += velocity * Time.deltaTime();
     */
    public static float deltaTime() {
        return getContext().getDeltaTime();
    }

    /**
     * Get the unscaled delta time (not affected by time scale).
     * Useful for UI animations that should continue even when game is paused.
     */
    public static float unscaledDeltaTime() {
        return getContext().getUnscaledDeltaTime();
    }

    /**
     * Get the total time since the application started (in seconds).
     */
    public static float time() {
        return getContext().getTime();
    }

    /**
     * Get the current frame count.
     */
    public static long frameCount() {
        return getContext().getFrameCount();
    }

    /**
     * Get the current time scale.
     * 1.0 = normal speed, 0.5 = half speed, 2.0 = double speed, 0.0 = paused
     */
    public static float timeScale() {
        return getContext().getTimeScale();
    }

    /**
     * Set the time scale.
     * 1.0 = normal speed, 0.5 = half speed (slow-mo), 2.0 = double speed, 0.0 = pause
     */
    public static void setTimeScale(float scale) {
        getContext().setTimeScale(scale);
    }

    /**
     * Get the current FPS (frames per second).
     */
    public static float fps() {
        return getContext().getFPS();
    }

    /**
     * Get the last frame time in milliseconds.
     * Used by PerformanceMonitor for detailed frame timing.
     */
    public static float frameTimeMs() {
        return getContext().getFrameTimeMs();
    }

    /**
     * Get the average frame time over recent frames (in milliseconds).
     * Used by PerformanceMonitor for smoothed performance metrics.
     */
    public static float avgFrameTimeMs() {
        return getContext().getAvgFrameTimeMs();
    }

    /**
     * Reset the time system.
     */
    public static void reset() {
        getContext().reset();
    }
}