package com.pocket.rpg.input;

import org.joml.Vector2f;

import java.util.List;
import java.util.Objects;

public final class GlfwInput implements InputInterface {
    private final KeyListener keyListener;
    private final MouseListener mouseListener;

    public GlfwInput(KeyListener keyListener, MouseListener mouseListener) {
        this.keyListener = keyListener;
        this.mouseListener = mouseListener;
    }

    @Override
    public void endFrame() {
        keyListener.endFrame();
        mouseListener.endFrame();
    }

    @Override
    public boolean wasPressedThisFrame(List<Integer> bindings) {
        for (Integer i : bindings) {
            if (keyListener.wasPressedThisFrame(i)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPressed(List<Integer> bindings) {
        for (Integer i : bindings) {
            if (keyListener.isKeyPressed(i)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean wasReleasedThisFrame(List<Integer> bindings) {
        for (Integer i : bindings) {
            if (keyListener.wasReleasedThisFrame(i)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Vector2f getMousePosition() {
        return mouseListener.getMousePosition();
    }

    @Override
    public boolean wasMouseButtonPressedThisFrame(int button) {
        return mouseListener.isMouseButtonDown(button);
    }

    @Override
    public boolean isMouseButtonPressed(int button) {
        return mouseListener.isMouseButtonPressed(button);
    }

    @Override
    public boolean wasMouseButtonReleasedThisFrame(int button) {
        return mouseListener.isMouseButtonUp(button);
    }

    @Override
    public Vector2f getScroll() {
        return mouseListener.getScrollDelta();
    }

    public KeyListener keyListener() {
        return keyListener;
    }

    public MouseListener mouseListener() {
        return mouseListener;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (GlfwInput) obj;
        return Objects.equals(this.keyListener, that.keyListener) &&
                Objects.equals(this.mouseListener, that.mouseListener);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyListener, mouseListener);
    }

    @Override
    public String toString() {
        return "GlfwInput[" +
                "keyListener=" + keyListener + ", " +
                "mouseListener=" + mouseListener + ']';
    }

}
