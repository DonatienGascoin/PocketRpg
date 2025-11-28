package com.pocket.rpg.input;

import lombok.Getter;
import org.joml.Vector2f;

import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

public class MouseListener {

    private static final int NB_MOUSE_BUTTON = 9;
    @Getter
    private double scrollX, scrollY;
    @Getter
    private double xPos, yPos, lastX, lastY;
    private final boolean[] mouseButtonDown = new boolean[NB_MOUSE_BUTTON];
    private final boolean[] mouseButtonUp = new boolean[NB_MOUSE_BUTTON];
    private final boolean[] mouseButtonHeld = new boolean[NB_MOUSE_BUTTON];

    public void mousePosCallback( double xPos, double yPos) {
        // Store last positions
        lastX = xPos;
        lastY = yPos;

        // Store new positions
        this.xPos = xPos;
        this.yPos = yPos;
    }

    public void mouseButtonCallback(int button, int action, int mods) {
        if (action == GLFW_PRESS) {
            if (button < NB_MOUSE_BUTTON) {
                mouseButtonDown[button] = true;
                mouseButtonHeld[button] = true;
            }
        } else if (action == GLFW_RELEASE) {
            if (button < NB_MOUSE_BUTTON) {
                mouseButtonDown[button] = false;
                mouseButtonHeld[button] = false;

                mouseButtonUp[button] = true;
            }
        }
    }

    public void mouseScrollCallback( double xOffset, double yOffset) {
        scrollX = xOffset;
        scrollY = yOffset;
    }

    public void endFrame() {
        scrollX = 0;
        scrollY = 0;

        // Remove all released buttons
        Arrays.fill(mouseButtonUp, false);
        // Remove all pressed buttons
        Arrays.fill(mouseButtonDown, false);
    }

    public Vector2f getMousePosition() {
        return new Vector2f((float) xPos, (float) yPos);
    }

    public Vector2f getMouseDelta() {
        return new Vector2f((float) (xPos - lastX), (float) (yPos - lastY));
    }

    public Vector2f getScrollDelta() {
        return new Vector2f((float) scrollX, (float) scrollY);
    }


    public boolean isMouseButtonPressed(int button) {
        if (button < mouseButtonHeld.length) {
            return mouseButtonHeld[button];
        }
        return false;
    }

    public boolean isMouseButtonDown(int button) {
        if (button < mouseButtonDown.length) {
            return mouseButtonDown[button];
        }
        return false;
    }

    public boolean isMouseButtonUp(int button) {
        if (button < mouseButtonUp.length) {
            return mouseButtonUp[button];
        }
        return false;
    }

    public boolean isDragging(int button) {
        return isMouseButtonDown(button)
                && lastX != xPos
                || lastY != yPos;
    }
}
