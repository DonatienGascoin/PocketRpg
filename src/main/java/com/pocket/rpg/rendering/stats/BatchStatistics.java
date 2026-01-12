package com.pocket.rpg.rendering.stats;

import com.pocket.rpg.rendering.batch.SpriteBatch.SortingStrategy;

/**
 * Statistics data for batch rendering.
 * Used by StatisticsReporter to report batch performance.
 */
public record BatchStatistics(int totalSprites, int staticSprites, int dynamicSprites, int drawCalls, SortingStrategy sortingStrategy) {

    public float getSpritesPerCall() {
        return drawCalls > 0 ? (float) totalSprites / drawCalls : 0;
    }

    @Override
    public String toString() {
        return String.format(
                "Batch Stats: %d total sprites (%d static, %d dynamic), %d draw calls (%.1f sprites/call), strategy=%s",
                totalSprites, staticSprites, dynamicSprites, drawCalls, getSpritesPerCall(), sortingStrategy
        );
    }
}