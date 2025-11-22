package com.pocket.rpg.utils;

import com.pocket.rpg.rendering.CameraSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;

/**
 * Default callback implementation with CameraSystem integration.
 */
public class DefaultCallback implements ICallback {

    private final List<BiConsumer<Integer, Integer>> resizeCallbacks = new ArrayList<>();

    public DefaultCallback() {
    }

    @Override
    public void mousePosCallback(long window, double xPos, double yPos) {
        // Default implementation
    }

    @Override
    public void mouseButtonCallback(long window, int button, int action, int mods) {
        // Default implementation
    }

    @Override
    public void mouseScrollCallback(long window, double xOffset, double yOffset) {
        // Default implementation
    }

    @Override
    public void keyCallback(long window, int key, int scanCode, int action, int mods) {
        if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
            glfwSetWindowShouldClose(window, true);
        }
    }

    @Override
    public void windowResizeCallback(long window, int width, int height) {
        for (BiConsumer<Integer, Integer> callback : resizeCallbacks) {
            callback.accept(width, height);
        }
    }

    public DefaultCallback registerResizeCallback(BiConsumer<Integer, Integer> onResize) {
        resizeCallbacks.add(onResize);
        return this;
    }
}