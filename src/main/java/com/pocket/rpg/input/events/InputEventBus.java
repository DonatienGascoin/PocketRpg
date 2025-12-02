package com.pocket.rpg.input.events;

import com.pocket.rpg.input.GamepadAxis;
import com.pocket.rpg.input.GamepadButton;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import com.pocket.rpg.input.listeners.WindowResizeListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Central event bus for input events.
 * Simplifies the callback architecture by providing a single point
 * where all input events flow through.
 * <p>
 * Architecture:
 * GLFW Callbacks → InputEventBus → [KeyListener, MouseListener, ...]
 */
public class InputEventBus {

    // Listeners for different event types
    private final List<KeyListener> keyListeners = new ArrayList<>();
    private final List<MouseListener> mouseListeners = new ArrayList<>();
    private final List<GamepadListener> gamepadListeners = new ArrayList<>();
    private final List<WindowResizeListener> resizeListeners = new ArrayList<>();

    // ========================================
    // Registration API
    // ========================================

    public void addKeyListener(KeyListener listener) {
        keyListeners.add(listener);
    }

    public void removeKeyListener(KeyListener listener) {
        keyListeners.remove(listener);
    }

    public void addMouseListener(MouseListener listener) {
        mouseListeners.add(listener);
    }

    public void removeMouseListener(MouseListener listener) {
        mouseListeners.remove(listener);
    }

    public void addResizeListener(WindowResizeListener listener) {
        resizeListeners.add(listener);
    }

    public void removeResizeListener(WindowResizeListener listener) {
        resizeListeners.remove(listener);
    }

    public void addGamepadListener(GamepadListener listener) {
        gamepadListeners.add(listener);
    }

    public void removeGamepadListener(GamepadListener listener) {
        gamepadListeners.remove(listener);
    }

    // ========================================
    // Event Dispatch (called by platform layer)
    // ========================================

    public void dispatchKeyEvent(KeyCode key, KeyEvent.Action action) {
        for (KeyListener listener : keyListeners) {
            listener.onKey(key, action);
        }
    }

    public void dispatchMouseButtonEvent(KeyCode button, MouseButtonEvent.Action action) {
        for (MouseListener listener : mouseListeners) {
            listener.onMouseButton(button, action);
        }
    }

    public void dispatchMouseMoveEvent(double x, double y) {
        for (MouseListener listener : mouseListeners) {
            listener.onMouseMove(x, y);
        }
    }

    public void dispatchMouseScrollEvent(double xOffset, double yOffset) {
        for (MouseListener listener : mouseListeners) {
            listener.onMouseScroll(xOffset, yOffset);
        }
    }

    public void dispatchWindowResizeEvent(int width, int height) {
        for (WindowResizeListener listener : resizeListeners) {
            listener.onWindowResize(width, height);
        }
    }

    /**
     * Dispatch gamepad button event.
     *
     * @param button    The button that changed state
     * @param isPressed True if pressed, false if released
     */
    public void dispatchGamepadButtonEvent(GamepadButton button, boolean isPressed) {
        for (GamepadListener listener : gamepadListeners) {
            if (isPressed) {
                listener.onButtonPressed(button);
            } else {
                listener.onButtonReleased(button);
            }
        }
    }

    /**
     * Dispatch gamepad axis event.
     *
     * @param axis  The axis that changed
     * @param value The new axis value
     */
    public void dispatchGamepadAxisEvent(GamepadAxis axis, float value) {
        for (GamepadListener listener : gamepadListeners) {
            listener.onAxisChanged(axis, value);
        }
    }

    // ========================================
    // Utility
    // ========================================

    public void clear() {
        keyListeners.clear();
        mouseListeners.clear();
        resizeListeners.clear();
    }
}

