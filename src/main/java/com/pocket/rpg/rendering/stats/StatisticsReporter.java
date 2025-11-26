package com.pocket.rpg.rendering.stats;

import com.pocket.rpg.rendering.culling.CullingStatistics;

/**
 * Interface for reporting rendering statistics.
 * Can handle both culling statistics and batch statistics.
 */
public interface StatisticsReporter {

    /**
     * Reports culling statistics.
     *
     * @param statistics The culling statistics to report
     */
    default void report(CullingStatistics statistics) {
        // Default implementation - can be overridden
    }

    /**
     * Reports batch statistics.
     *
     * @param statistics The batch statistics to report
     */
    default void report(BatchStatistics statistics) {
        // Default implementation - can be overridden
    }
}