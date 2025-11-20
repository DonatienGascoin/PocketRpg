package com.pocket.rpg.utils;

import com.pocket.rpg.postProcessing.BlurEffect;
import com.pocket.rpg.postProcessing.ColorVignetteEffect;
import com.pocket.rpg.postProcessing.PillarboxEffect;
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
    
    @Builder.Default
    private List<PostEffect> postProcessingEffects = List.of(
            new BlurEffect(2.0f),
            new ColorVignetteEffect(1.5f, 0.5f),
            new PillarboxEffect(640 / 480f)
    );
}

