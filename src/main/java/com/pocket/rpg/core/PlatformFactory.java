package com.pocket.rpg.core;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.rendering.renderers.RenderInterface;
import com.pocket.rpg.ui.UIRenderer;

/**
 * Factory for creating platform-specific implementations.
 * This allows the engine to be platform-agnostic.
 */
public interface PlatformFactory {

    /**
     * Create a window implementation for this platform.
     */
    AbstractWindow createWindow(
            GameConfig config,
            InputBackend inputBackend,
            InputEventBus callbacks);

    /**
     * Create a renderer implementation for this platform.
     */
    RenderInterface createRenderer(ViewportConfig viewportConfig, RenderingConfig config);

    /**
     * Create a UI renderer for this platform.
     */
    UIRenderer createUIRenderer();

    /**
     * Create an input backend for this platform.
     */
    InputBackend createInputBackend();

    /**
     * Create a post-processor for this platform.
     */
    PostProcessor createPostProcessor(GameConfig config);

    /**
     * Get the platform name (for logging/debugging).
     */
    String getPlatformName();
}