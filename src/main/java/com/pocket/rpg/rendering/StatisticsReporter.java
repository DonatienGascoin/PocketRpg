package com.pocket.rpg.rendering;

/**
 * Interface for reporting culling statistics.
 * Implementations can output to console, UI, logs, etc.
 */
public interface StatisticsReporter {

    /**
     * Reports culling statistics.
     *
     * @param statistics The statistics to report
     */
    void report(CullingStatistics statistics);
}