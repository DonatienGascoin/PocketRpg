package com.pocket.rpg.input;

import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

public class KeyListener {
    private final boolean[] keyPressed = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keyBeginPress = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keyEndPress = new boolean[GLFW_KEY_LAST + 1];

    public void keyCallback(int key, int scanCode, int action, int mods) {
        if (isInRange(key)) {
            if (action == GLFW_PRESS) {
                keyPressed[key] = true;
                keyBeginPress[key] = true;
            } else if (action == GLFW_RELEASE) {
                keyPressed[key] = false;
                keyBeginPress[key] = false;
                keyEndPress[key] = true;
            }
        }
    }

    public void endFrame() {
        Arrays.fill(keyBeginPress, false);
        Arrays.fill(keyEndPress, false);
    }

    public boolean isKeyPressed(int keyCode) {
        if (isInRange(keyCode)) {
            return keyPressed[keyCode];
        }
        return false;
    }

    public boolean wasPressedThisFrame(int keyCode) {
        if (isInRange(keyCode)) {
            return keyBeginPress[keyCode];
        }
        return false;
    }

    public boolean wasReleasedThisFrame(int keyCode) {
        if (isInRange(keyCode)) {
            return keyEndPress[keyCode];
        }
        return false;
    }

    private static boolean isInRange(int keyCode) {
        return keyCode <= GLFW_KEY_LAST && keyCode >= 0;
    }
}
