package com.pocket.rpg.platform;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.audio.backend.AudioBackend;
import com.pocket.rpg.rendering.postfx.PostProcessor;

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

    /**
     * Create an audio backend for this platform.
     */
    AudioBackend createAudioBackend();
}