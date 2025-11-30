package com.pocket.rpg.input.callbacks;

import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.input.events.KeyEvent;
import com.pocket.rpg.input.events.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

public class DefaultInputCallback implements InputCallbacks.KeyCallback,
        InputCallbacks.MouseMoveCallback,
        InputCallbacks.MouseButtonCallback,
        InputCallbacks.MouseScrollCallback,
        InputCallbacks.WindowResizeCallback {

    private final List<InputCallbacks.MouseMoveCallback> mousePosCallbacks = new ArrayList<>();
    private final List<InputCallbacks.MouseButtonCallback> mouseButtonCallbacks = new ArrayList<>();
    private final List<InputCallbacks.MouseScrollCallback> mouseScrollCallbacks = new ArrayList<>();
    private final List<InputCallbacks.KeyCallback> keyCallbacks = new ArrayList<>();
    private final List<InputCallbacks.WindowResizeCallback> windowSizeCallbacks = new ArrayList<>();


    @Override
    public void onKey(KeyCode key, KeyEvent.Action action) {
        for (var keyCallback : keyCallbacks) {
            keyCallback.onKey(key, action);
        }
    }

    @Override
    public void onMouseButton(KeyCode button, MouseButtonEvent.Action action) {
        for (var mouseButtonCallback : mouseButtonCallbacks) {
            mouseButtonCallback.onMouseButton(button, action);
        }
    }

    @Override
    public void onMouseMove(double x, double y) {
        for (var mousePosCallback : mousePosCallbacks) {
            mousePosCallback.onMouseMove(x, y);
        }
    }

    @Override
    public void onMouseScroll(double xOffset, double yOffset) {
        for (var mouseScrollCallback : mouseScrollCallbacks) {
            mouseScrollCallback.onMouseScroll(xOffset, yOffset);
        }
    }

    @Override
    public void onWindowResize(int width, int height) {
        for (var windowSizeCallback : windowSizeCallbacks) {
            windowSizeCallback.onWindowResize(width, height);
        }
    }

    // Add/remove methods for window size callbacks
    public void addWindowSizeCallback(InputCallbacks.WindowResizeCallback callback) {
        if (callback != null) {
            windowSizeCallbacks.add(callback);
        }
    }

    public boolean removeWindowSizeCallback(InputCallbacks.WindowResizeCallback callback) {
        return callback != null && windowSizeCallbacks.remove(callback);
    }

    // Add/remove methods for mouse position callbacks
    public void addMousePosCallback(InputCallbacks.MouseMoveCallback callback) {
        if (callback != null) {
            mousePosCallbacks.add(callback);
        }
    }

    public boolean removeMousePosCallback(InputCallbacks.MouseMoveCallback callback) {
        return callback != null && mousePosCallbacks.remove(callback);
    }

    // Add/remove methods for mouse button callbacks
    public void addMouseButtonCallback(InputCallbacks.MouseButtonCallback callback) {
        if (callback != null) {
            mouseButtonCallbacks.add(callback);
        }
    }

    public boolean removeMouseButtonCallback(InputCallbacks.MouseButtonCallback callback) {
        return callback != null && mouseButtonCallbacks.remove(callback);
    }

    // Add/remove methods for mouse scroll callbacks
    public void addMouseScrollCallback(InputCallbacks.MouseScrollCallback callback) {
        if (callback != null) {
            mouseScrollCallbacks.add(callback);
        }
    }

    public boolean removeMouseScrollCallback(InputCallbacks.MouseScrollCallback callback) {
        return callback != null && mouseScrollCallbacks.remove(callback);
    }

    // Add/remove methods for key callbacks
    public void addKeyCallback(InputCallbacks.KeyCallback callback) {
        if (callback != null) {
            keyCallbacks.add(callback);
        }
    }

    public boolean removeKeyCallback(InputCallbacks.KeyCallback callback) {
        return callback != null && keyCallbacks.remove(callback);
    }

}