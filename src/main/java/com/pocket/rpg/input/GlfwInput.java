package com.pocket.rpg.input;

import org.joml.Vector2f;

import java.util.List;

public record GlfwInput(KeyListener keyListener, MouseListener mouseListener) implements InputInterface {

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
        return mouseListener.isMouseButtonPressing(button);
    }

    @Override
    public boolean wasMouseButtonReleasedThisFrame(int button) {
        return mouseListener.isMouseButtonPressing(button);
    }

    @Override
    public Vector2f getScroll() {
        return mouseListener.getScrollDelta();
    }
}
