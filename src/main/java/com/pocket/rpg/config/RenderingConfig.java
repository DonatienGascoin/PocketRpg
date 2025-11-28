package com.pocket.rpg.config;

import com.pocket.rpg.rendering.SpriteBatch;
import com.pocket.rpg.rendering.stats.ConsoleStatisticsReporter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderingConfig {

    @Builder.Default
    private final int maxBatchSize = 10000;

    @Builder.Default
    private final SpriteBatch.SortingStrategy sortingStrategy = SpriteBatch.SortingStrategy.BALANCED;

    @Builder.Default
    private final boolean enableStatistics = true;

    @Builder.Default
    private final int statisticsInterval = 300; // frames

    @Builder.Default
    private ConsoleStatisticsReporter reporter = null;
}
