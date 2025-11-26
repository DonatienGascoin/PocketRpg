package com.pocket.rpg.rendering.culling;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Tracks culling statistics including frame counters and rolling averages.
 * Provides performance metrics for culling effectiveness.
 */
public class CullingStatistics {

    private static final int ROLLING_WINDOW_SIZE = 60; // 60 frames (1 second at 60 FPS)

    // Current frame counters
    private int totalSprites;
    private int renderedSprites;
    private int culledSprites;

    // Rolling averages
    private Queue<Integer> totalHistory;
    private Queue<Integer> renderedHistory;
    private Queue<Integer> culledHistory;

    // Cached rolling averages
    private float avgTotal;
    private float avgRendered;
    private float avgCulled;
    private float avgCullPercentage;

    public CullingStatistics() {
        this.totalHistory = new ArrayDeque<>(ROLLING_WINDOW_SIZE);
        this.renderedHistory = new ArrayDeque<>(ROLLING_WINDOW_SIZE);
        this.culledHistory = new ArrayDeque<>(ROLLING_WINDOW_SIZE);
        reset();
    }

    /**
     * Starts a new frame, saving previous frame to history.
     */
    public void startFrame() {
        // Add previous frame to history
        if (totalSprites > 0) {
            addToHistory(totalSprites, renderedSprites, culledSprites);
        }

        // Reset current frame counters
        totalSprites = 0;
        renderedSprites = 0;
        culledSprites = 0;

        // Recalculate rolling averages
        updateRollingAverages();
    }

    /**
     * Increments the total sprite counter.
     */
    public void incrementTotal() {
        totalSprites++;
    }

    /**
     * Increments the rendered sprite counter.
     */
    public void incrementRendered() {
        renderedSprites++;
    }

    /**
     * Increments the culled sprite counter.
     */
    public void incrementCulled() {
        culledSprites++;
    }

    /**
     * Adds frame data to rolling history.
     */
    private void addToHistory(int total, int rendered, int culled) {
        // Add to history
        totalHistory.offer(total);
        renderedHistory.offer(rendered);
        culledHistory.offer(culled);

        // Remove oldest if we exceed window size
        if (totalHistory.size() > ROLLING_WINDOW_SIZE) {
            totalHistory.poll();
            renderedHistory.poll();
            culledHistory.poll();
        }
    }

    /**
     * Updates rolling average calculations.
     */
    private void updateRollingAverages() {
        if (totalHistory.isEmpty()) {
            avgTotal = 0;
            avgRendered = 0;
            avgCulled = 0;
            avgCullPercentage = 0;
            return;
        }

        int sumTotal = 0;
        int sumRendered = 0;
        int sumCulled = 0;

        for (int value : totalHistory) sumTotal += value;
        for (int value : renderedHistory) sumRendered += value;
        for (int value : culledHistory) sumCulled += value;

        int count = totalHistory.size();
        avgTotal = (float) sumTotal / count;
        avgRendered = (float) sumRendered / count;
        avgCulled = (float) sumCulled / count;

        if (avgTotal > 0) {
            avgCullPercentage = (avgCulled / avgTotal) * 100.0f;
        } else {
            avgCullPercentage = 0;
        }
    }

    // Getters for current frame

    public int getTotalSprites() {
        return totalSprites;
    }

    public int getRenderedSprites() {
        return renderedSprites;
    }

    public int getCulledSprites() {
        return culledSprites;
    }

    public float getCullPercentage() {
        if (totalSprites == 0) return 0;
        return (culledSprites / (float) totalSprites) * 100.0f;
    }

    // Getters for rolling averages

    public float getAvgTotal() {
        return avgTotal;
    }

    public float getAvgRendered() {
        return avgRendered;
    }

    public float getAvgCulled() {
        return avgCulled;
    }

    public float getAvgCullPercentage() {
        return avgCullPercentage;
    }

    /**
     * Resets all statistics.
     */
    public void reset() {
        totalSprites = 0;
        renderedSprites = 0;
        culledSprites = 0;
        totalHistory.clear();
        renderedHistory.clear();
        culledHistory.clear();
        avgTotal = 0;
        avgRendered = 0;
        avgCulled = 0;
        avgCullPercentage = 0;
    }

    @Override
    public String toString() {
        return String.format(
                "Frame: %d total, %d rendered, %d culled (%.1f%%) | " +
                        "Avg: %.1f total, %.1f rendered, %.1f culled (%.1f%%)",
                totalSprites, renderedSprites, culledSprites, getCullPercentage(),
                avgTotal, avgRendered, avgCulled, avgCullPercentage
        );
    }
}