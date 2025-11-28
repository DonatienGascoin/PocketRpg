package com.pocket.rpg.utils;

import com.pocket.rpg.input.callbacks.*;

import java.util.ArrayList;
import java.util.List;

public class DefaultCallback implements InputCallback {

    private final List<MousePosCallback> mousePosCallbacks = new ArrayList<>();
    private final List<MouseButtonCallback> mouseButtonCallbacks = new ArrayList<>();
    private final List<MouseScrollCallback> mouseScrollCallbacks = new ArrayList<>();
    private final List<KeyCallback> keyCallbacks = new ArrayList<>();
    private final List<WindowSizeCallback> windowSizeCallbacks = new ArrayList<>();


    @Override
    public void mousePosCallback(double xPos, double yPos) {
        for (var mousePosCallback : mousePosCallbacks) {
            mousePosCallback.invoke(xPos, yPos);
        }
    }

    @Override
    public void mouseButtonCallback(int button, int action, int mods) {
        for (var mouseButtonCallback : mouseButtonCallbacks) {
            mouseButtonCallback.invoke(button, action, mods);
        }
    }

    @Override
    public void mouseScrollCallback(double xOffset, double yOffset) {
        for (var mouseScrollCallback : mouseScrollCallbacks) {
            mouseScrollCallback.invoke(xOffset, yOffset);
        }
    }

    @Override
    public void keyCallback(int key, int scanCode, int action, int mods) {
        for (var keyCallback : keyCallbacks) {
            keyCallback.invoke(key, scanCode, action, mods);
        }
    }

    @Override
    public void windowResizeCallback(int width, int height) {
        for (var windowSizeCallback : windowSizeCallbacks) {
            windowSizeCallback.invoke(width, height);
        }
    }

    // Add/remove methods for window size callbacks
    public void addWindowSizeCallback(WindowSizeCallback callback) {
        if (callback != null) {
            windowSizeCallbacks.add(callback);
        }
    }

    public boolean removeWindowSizeCallback(WindowSizeCallback callback) {
        return callback != null && windowSizeCallbacks.remove(callback);
    }

    // Add/remove methods for mouse position callbacks
    public void addMousePosCallback(MousePosCallback callback) {
        if (callback != null) {
            mousePosCallbacks.add(callback);
        }
    }

    public boolean removeMousePosCallback(MousePosCallback callback) {
        return callback != null && mousePosCallbacks.remove(callback);
    }

    // Add/remove methods for mouse button callbacks
    public void addMouseButtonCallback(MouseButtonCallback callback) {
        if (callback != null) {
            mouseButtonCallbacks.add(callback);
        }
    }

    public boolean removeMouseButtonCallback(MouseButtonCallback callback) {
        return callback != null && mouseButtonCallbacks.remove(callback);
    }

    // Add/remove methods for mouse scroll callbacks
    public void addMouseScrollCallback(MouseScrollCallback callback) {
        if (callback != null) {
            mouseScrollCallbacks.add(callback);
        }
    }

    public boolean removeMouseScrollCallback(MouseScrollCallback callback) {
        return callback != null && mouseScrollCallbacks.remove(callback);
    }

    // Add/remove methods for key callbacks
    public void addKeyCallback(KeyCallback callback) {
        if (callback != null) {
            keyCallbacks.add(callback);
        }
    }

    public boolean removeKeyCallback(KeyCallback callback) {
        return callback != null && keyCallbacks.remove(callback);
    }
}