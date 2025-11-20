package com.pocket.rpg.utils;

import com.pocket.rpg.postProcessing.PostEffect;
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
     * Whether to enable pillarboxing/letterboxing for aspect ratio preservation.
     */
    @Builder.Default
    private boolean enablePillarbox = false;

    /**
     * Target aspect ratio for pillarbox (e.g., 16/9 = 1.777, 4/3 = 1.333).
     * Only used if enablePillarbox is true.
     */
    @Builder.Default
    private float pillarboxAspectRatio = 640f / 480f; // 4:3 aspect ratio


    @Builder.Default
    private List<PostEffect> postProcessingEffects = List.of(
//            new BlurEffect(2.0f),
//            new ColorVignetteEffect(1.5f, 0.5f)
    );
}

