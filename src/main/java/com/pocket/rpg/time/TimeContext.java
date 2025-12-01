// TimeContext.java
package com.pocket.rpg.time;

/**
 * Interface for time context implementations.
 * Allows different implementations (real, mock, scaled, etc.)
 */
public interface TimeContext {

    /**
     * Initialize the time context.
     * Called once during startup.
     */
    void init();

    /**
     * Update the time context.
     * Called once per frame.
     */
    void update();

    /**
     * Get the time elapsed since the last frame (in seconds).
     * This is the value you should use for all time-based updates.
     */
    float getDeltaTime();

    /**
     * Get the unscaled delta time (not affected by time scale).
     * Useful for UI animations that should continue even when game is paused.
     */
    float getUnscaledDeltaTime();

    /**
     * Get the total time since the application started (in seconds).
     */
    float getTime();

    /**
     * Get the current frame count.
     */
    long getFrameCount();

    /**
     * Get the current time scale.
     * 1.0 = normal speed, 0.5 = half speed, 2.0 = double speed, 0.0 = paused
     */
    float getTimeScale();

    /**
     * Set the time scale.
     * 1.0 = normal speed, 0.5 = half speed (slow-mo), 2.0 = double speed, 0.0 = pause
     */
    void setTimeScale(float scale);

    /**
     * Get the current FPS (frames per second).
     */
    float getFPS();

    /**
     * Get the last frame time in milliseconds.
     * Used by PerformanceMonitor for detailed frame timing.
     */
    float getFrameTimeMs();

    /**
     * Get the average frame time over recent frames (in milliseconds).
     * Used by PerformanceMonitor for smoothed performance metrics.
     */
    float getAvgFrameTimeMs();

    /**
     * Reset the time context.
     */
    void reset();
}