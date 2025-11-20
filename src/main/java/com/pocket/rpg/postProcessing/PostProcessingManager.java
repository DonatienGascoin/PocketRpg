package com.pocket.rpg.postProcessing;

import lombok.Getter;

/**
 * Manages post-processing effects independently from the game window.
 * This class encapsulates all post-processing logic so GameWindow can focus on game content.
 */
public class PostProcessingManager {

    private static final float DEFAULT_BLUR_STRENGTH = 2.0f;
    private static final float DEFAULT_VIGNETTE_INTENSITY = 1.5f;
    private static final float DEFAULT_DESATURATION = 0.5f;

    @Getter
    private final PostProcessor postProcessor;
    private final long windowHandle;

    /**
     * Creates a post-processing manager.
     *
     * @param screenWidth  Internal rendering width
     * @param screenHeight Internal rendering height
     * @param windowHandle GLFW window handle for final blit
     */
    public PostProcessingManager(int screenWidth, int screenHeight, long windowHandle) {
        this.windowHandle = windowHandle;
        this.postProcessor = new PostProcessor(screenWidth, screenHeight);
    }

    /**
     * Initializes the post-processor with default effects.
     * Call this after OpenGL context is created.
     */
    public void init() {
        initWithDefaultEffects();
    }

    /**
     * Initializes with default blur and vignette effects.
     */
    private void initWithDefaultEffects() {
        postProcessor.addEffect(new BlurEffect(DEFAULT_BLUR_STRENGTH));
        postProcessor.addEffect(new ColorVignetteEffect(DEFAULT_VIGNETTE_INTENSITY, DEFAULT_DESATURATION));
        postProcessor.init();
    }

    /**
     * Initializes with custom effects.
     *
     * @param effects The effects to apply in order
     */
    public void initWithEffects(PostEffect... effects) {
        for (PostEffect effect : effects) {
            postProcessor.addEffect(effect);
        }
        postProcessor.init();
    }

    /**
     * Begins rendering to the post-processing FBO.
     * All subsequent rendering will be captured for post-processing.
     */
    public void beginCapture() {
        postProcessor.bindFboA();
    }

    /**
     * Applies all post-processing effects and blits the result to the screen.
     */
    public void endCaptureAndRender() {
        postProcessor.applyEffects();
    }

    /**
     * Cleans up post-processing resources.
     */
    public void destroy() {
        postProcessor.destroy();
    }

}