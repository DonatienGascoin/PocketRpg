package com.pocket.rpg.utils;

import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.postProcessing.PostProcessor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WindowConfig {

    @Builder.Default
    private String title = "Pocket Rpg";

    @Builder.Default
    private int initialWidth = 640;

    @Builder.Default
    private int initialHeight = 480;

    @Builder.Default
    private boolean fullscreen = false;

    @Builder.Default
    private boolean vsync = false;

    @Builder.Default
    private ICallback callback = new DefaultCallback();

    /**
     * List of post-processing effects to apply.
     * Warning: Order matters! Effects are applied in the order listed.
     */
    @Builder.Default
    private List<PostEffect> postProcessingEffects = List.of(
//            new BlurEffect(2.0f),
//            new ColorVignetteEffect(0.5f, 0.5f)
    );

    /**
     * Scaling mode when pillarbox is disabled.
     * MAINTAIN_ASPECT_RATIO: Keeps aspect ratio with black bars (like pillarbox)
     * STRETCH: Stretches image to fill window (may distort)
     */
    @Builder.Default
    private PostProcessor.ScalingMode scalingMode = PostProcessor.ScalingMode.STRETCH;

    /**
     * Whether to enable pillarboxing/letterboxing for aspect ratio preservation.
     */
    @Builder.Default
    private boolean enablePillarBox = false;

    /**
     * Target aspect ratio for pillarbox (e.g., 16/9 = 1.777, 4/3 = 1.333).
     * Only used if enablePillarbox is true.
     */
    @Builder.Default
    private float pillarboxAspectRatio = 640f / 480f; // 4:3 aspect ratio
}