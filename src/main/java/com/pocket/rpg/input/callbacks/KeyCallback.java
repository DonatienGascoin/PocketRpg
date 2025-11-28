package com.pocket.rpg.input.callbacks;

import org.lwjgl.glfw.GLFW;

public interface KeyCallback {
    /**
     * Will be called when a key is pressed, repeated or released.
     *
     * @param key      the keyboard key that was pressed or released
     * @param scancode the platform-specific scancode of the key
     * @param action   the key action. One of:<br><table><tr><td>{@link GLFW#GLFW_PRESS PRESS}</td><td>{@link GLFW#GLFW_RELEASE RELEASE}
     *                 </td><td>{@link GLFW#GLFW_REPEAT REPEAT}</td></tr></table>
     * @param mods     bitfield describing which modifiers keys were held down
     */
    void invoke(int key, int scancode, int action, int mods);
}
