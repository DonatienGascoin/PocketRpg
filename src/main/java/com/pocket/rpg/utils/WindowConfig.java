package com.pocket.rpg.utils;

import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.postProcessing.postEffects.VignetteEffect;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

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

    @NonNull
    private ICallback callback;

    /**
     * List of post-processing effects to apply.
     * Warning: Order matters! Effects are applied in the order listed.
     */
    @Builder.Default
    private List<PostEffect> postProcessingEffects = List.of(
            // Retro CRT Style
//            new ScanlinesEffect(0.3f, 300.0f),
//            new DesaturationEffect(0.7f),
//            new ChromaticAberrationEffect(0.003f),
//            new VignetteEffect(1.0f, 0.5f)

            // Dramatic Combat
//            new MotionBlurEffect(1.0f, 0.0f, 0.03f, 10),
//            new ChromaticAberrationEffect(0.008f),
//            new DisplacementEffect(0.01f, 0.002f, 0.0f)

            // Magical/Ethereal Scene
//            new BloomEffect(0.7f, 2.0f),
//            new ColorGradingEffect(0.8f, 0.9f, 1.0f, 0.3f), // Slight blue tint
//            new VignetteEffect(1.2f, 0.4f)
            // Low Health Warning
//            new ColorGradingEffect(1.0f, 0.3f, 0.3f, 0.5f), // Red tint
//            new DesaturationEffect(0.3f),
//            new VignetteEffect(1.5f, 0.8f)
            // Cel-Shaded/Comic Style
//            new EdgeDetectionEffect(0.15f, 0.0f, 0.0f, 0.0f),
//            new DesaturationEffect(0.2f)
            // Speed/Dash Effect
//            new MotionBlurEffect(playerVelocity.x, playerVelocity.y, 0.04f, 12),
//            new RadialBlurEffect(0.5f, 0.5f, 0.02f, 10),
//            new ChromaticAberrationEffect(0.006f));

            // General Purpose Effects
//            new BlurEffect(2.0f),
//            new DesaturationEffect(1f),
//            new BloomEffect(0.8f, 2f),
//            new ChromaticAberrationEffect(0.02f),
//            new ScanlinesEffect(0.3f, 300.0f),
//            new FilmGrainEffect(0.2f),
//            new ColorGradingEffect(0.3f, 0.5f, 1.0f, 0.3f),
//            new ColorGradingEffect(1.0f, 0.3f, 0.3f, 0.5f),
//            new PixelationEffect(0.005f),
//            new MotionBlurEffect(1.0f, 0.0f, 0.02f, 8),
//            new MotionBlurEffect(0.0f, 1.0f, 0.03f, 10),
//            new EdgeDetectionEffect(0.1f, 0.0f, 0.0f, 0.0f),
//            new EdgeDetectionEffect(0.1f, 1.0f, 0.5f, 0.0f),
//            new RadialBlurEffect(0.5f, 0.5f, 0.03f, 10),
//            new RadialBlurEffect(0.3f, 0.7f, 0.05f, 12),
//            new DisplacementEffect(0.005f),
//            new DisplacementEffect(0.01f, 0.002f, 0.002f),
            new VignetteEffect(1f, 1.5f)
    );

    /**
     * Scaling mode when pillarbox is disabled.
     * MAINTAIN_ASPECT_RATIO: Keeps aspect ratio with black bars (like pillarbox)
     * STRETCH: Stretches image to fill window (may distort)
     */
    @Builder.Default
    private PostProcessor.ScalingMode scalingMode = PostProcessor.ScalingMode.MAINTAIN_ASPECT_RATIO;

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