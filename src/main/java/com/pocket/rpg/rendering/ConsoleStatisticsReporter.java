package com.pocket.rpg.rendering;

/**
 * Reports culling statistics to the console at regular intervals.
 */
public class ConsoleStatisticsReporter implements StatisticsReporter {

    private final int reportInterval; // Report every N frames
    private int frameCounter;

    /**
     * Creates a console reporter with default interval (60 frames).
     */
    public ConsoleStatisticsReporter() {
        this(60);
    }

    /**
     * Creates a console reporter with specified interval.
     *
     * @param reportInterval Number of frames between reports
     */
    public ConsoleStatisticsReporter(int reportInterval) {
        this.reportInterval = Math.max(1, reportInterval);
        this.frameCounter = 0;
    }

    @Override
    public void report(CullingStatistics statistics) {
        frameCounter++;

        if (frameCounter >= reportInterval) {
            printStatistics(statistics);
            frameCounter = 0;
        }
    }

    /**
     * Prints formatted statistics to console.
     */
    private void printStatistics(CullingStatistics statistics) {
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
}