package com.pocket.rpg.rendering.stats;

import com.pocket.rpg.rendering.culling.CullingStatistics;

/**
 * Reports culling statistics to the console at regular intervals.
 */
public class ConsoleStatisticsReporter implements StatisticsReporter {

    private final int reportInterval; // Report every N frames
    private int cullingFrameCounter;
    private int batchFrameCounter;

    /**
     * Creates a console reporter with specified interval.
     *
     * @param reportInterval Number of frames between reports
     */
    public ConsoleStatisticsReporter(int reportInterval) {
        this.reportInterval = Math.max(1, reportInterval);
        this.cullingFrameCounter = 0;
        this.batchFrameCounter = 0;
    }

    @Override
    public void report(CullingStatistics statistics) {
        cullingFrameCounter++;

        if (cullingFrameCounter >= reportInterval) {
            printCullingStatistics(statistics);
            cullingFrameCounter = 0;
        }
    }

    @Override
    public void report(BatchStatistics statistics) {
        batchFrameCounter++;

        if (batchFrameCounter >= reportInterval) {
            printBatchStatistics(statistics);
            batchFrameCounter = 0;
        }
    }

    /**
     * Prints formatted culling statistics to console.
     */
    private void printCullingStatistics(CullingStatistics statistics) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              CULLING PERFORMANCE STATISTICS                  ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        // Current frame
        System.out.printf("║ Current Frame:                                               ║%n");
        System.out.printf("║   Total Sprites:     %-5d                                   ║%n",
                statistics.getTotalSprites());
        System.out.printf("║   Rendered:          %-5d                                   ║%n",
                statistics.getRenderedSprites());
        System.out.printf("║   Culled:            %-5d (%.1f%%)                          ║%n",
                statistics.getCulledSprites(),
                statistics.getCullPercentage());

        System.out.println("║                                                              ║");

        // Rolling averages
        System.out.printf("║ Rolling Average (60 frames):                                 ║%n");
        System.out.printf("║   Avg Total:         %-6.1f                                 ║%n",
                statistics.getAvgTotal());
        System.out.printf("║   Avg Rendered:      %-6.1f                                 ║%n",
                statistics.getAvgRendered());
        System.out.printf("║   Avg Culled:        %-6.1f (%.1f%%)                        ║%n",
                statistics.getAvgCulled(),
                statistics.getAvgCullPercentage());

        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * Prints formatted batch statistics to console.
     */
    private void printBatchStatistics(BatchStatistics statistics) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              BATCH RENDERING STATISTICS                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        System.out.printf("║ Total Sprites:       %-5d                                   ║%n",
                statistics.totalSprites());
        System.out.printf("║   Static:            %-5d                                   ║%n",
                statistics.staticSprites());
        System.out.printf("║   Dynamic:           %-5d                                   ║%n",
                statistics.dynamicSprites());

        System.out.println("║                                                              ║");

        System.out.printf("║ Draw Calls:          %-5d                                   ║%n",
                statistics.drawCalls());
        System.out.printf("║ Sprites per Call:    %-6.1f                                 ║%n",
                statistics.getSpritesPerCall());
        System.out.printf("║ Sorting Strategy:    %-30s      ║%n",
                statistics.sortingStrategy().toString());

        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}