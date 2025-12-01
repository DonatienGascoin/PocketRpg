package com.pocket.rpg.config;

import com.pocket.rpg.rendering.SpriteBatch;
import com.pocket.rpg.rendering.stats.ConsoleStatisticsReporter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.joml.Vector4f;

@Getter
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

    @Builder.Default
    private final Vector4f clearColor = new Vector4f(1f, 0.8f, 0.8f, 1.0f);
}
