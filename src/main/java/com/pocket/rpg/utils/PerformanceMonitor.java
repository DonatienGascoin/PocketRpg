package com.pocket.rpg.utils;

import lombok.Getter;
import lombok.Setter;

/**
 * Monitors and reports performance metrics such as FPS and frame time.
 * Updates and prints statistics at a configurable interval.
 */
public class PerformanceMonitor {

    private double lastReportTime;
    private final double reportInterval;

    @Setter
    @Getter
    private boolean enabled;

    /**
     * Creates a performance monitor with default 1-second reporting interval.
     */
    public PerformanceMonitor() {
        this(1.0);
    }

    /**
     * Creates a performance monitor with custom reporting interval.
     *
     * @param reportInterval Time in seconds between performance reports
     */
    public PerformanceMonitor(double reportInterval) {
        this.reportInterval = reportInterval;
        this.enabled = true;
        this.lastReportTime = Time.getTime();
    }

    /**
     * Call this every frame to update and potentially print performance metrics.
     * Automatically prints stats when the report interval has elapsed.
     */
    public void update() {
        if (!enabled) {
            return;
        }

        double currentTime = Time.getTime();
        if (currentTime - lastReportTime >= reportInterval) {
//            printStats();
            lastReportTime = currentTime;
        }
    }

    /**
     * Prints current performance statistics to console.
     */
    public void printStats() {
        System.out.printf("FPS: %.0f | Frame Time: %.2f ms (avg: %.2f ms)%n",
                Time.fps(),
                Time.frameTimeMs(),
                Time.avgFrameTimeMs());
    }

    /**
     * Prints detailed performance statistics including delta time.
     */
    public void printDetailedStats() {
        System.out.printf("FPS: %.0f | Frame Time: %.2f ms (avg: %.2f ms) | Delta: %.4f s%n",
                Time.fps(),
                Time.frameTimeMs(),
                Time.avgFrameTimeMs(),
                Time.deltaTime());
    }

    /**
     * Reset the report timer. Useful if you want to force a report soon.
     */
    public void resetTimer() {
        this.lastReportTime = Time.getTime();
    }
}