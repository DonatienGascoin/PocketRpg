package com.pocket.rpg.input.callbacks;

public interface WindowSizeCallback {
    /**
     * Will be called when the specified window is resized.
     *
     * @param width  the new width, in screen coordinates, of the window
     * @param height the new height, in screen coordinates, of the window
     */
    void invoke(int width, int height);
}
