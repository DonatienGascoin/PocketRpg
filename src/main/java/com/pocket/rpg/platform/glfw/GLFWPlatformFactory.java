package com.pocket.rpg.platform.glfw;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.platform.PlatformFactory;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.rendering.postfx.PostProcessor;

/**
 * Platform factory for GLFW + OpenGL implementation.
 */
public class GLFWPlatformFactory implements PlatformFactory {

    @Override
    public AbstractWindow createWindow(
            GameConfig config,
            InputBackend inputBackend,
            InputEventBus inputEventBus) {

        return new GLFWWindow(config, inputBackend, inputEventBus);
    }

    @Override
    public InputBackend createInputBackend() {
        return new GLFWInputBackend();
    }

    @Override
    public PostProcessor createPostProcessor(GameConfig config) {
        return new PostProcessor(config);
    }

    @Override
    public String getPlatformName() {
        return "GLFW + OpenGL";
    }
}