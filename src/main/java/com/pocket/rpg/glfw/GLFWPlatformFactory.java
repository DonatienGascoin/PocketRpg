package com.pocket.rpg.glfw;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.AbstractWindow;
import com.pocket.rpg.core.PlatformFactory;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.rendering.CameraSystem;
import com.pocket.rpg.rendering.renderers.OpenGLRenderer;
import com.pocket.rpg.rendering.renderers.RenderInterface;
import com.pocket.rpg.ui.UIRenderer;

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
    public RenderInterface createRenderer(CameraSystem cameraSystem, RenderingConfig config) {
        return new OpenGLRenderer(cameraSystem, config);
    }

    @Override
    public UIRenderer createUIRenderer() {
        return new OpenGLUIRenderer();
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