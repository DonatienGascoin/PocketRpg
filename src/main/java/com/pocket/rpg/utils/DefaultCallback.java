package com.pocket.rpg.utils;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;

public class DefaultCallback implements ICallback {
    @Override
    public void mousePosCallback(long window, double xPos, double yPos) {

    }

    @Override
    public void mouseButtonCallback(long window, int button, int action, int mods) {

    }

    @Override
    public void mouseScrollCallback(long window, double xOffset, double yOffset) {

    }

    @Override
    public void keyCallback(long window, int key, int scanCode, int action, int mods) {
        if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
            glfwSetWindowShouldClose(window, true);
        }
    }

    @Override
    public void windowResizeCallback(long window, int width, int height) {
        // Default implementation does nothing
        // Post-processor handles aspect ratio preservation automatically
    }
}
