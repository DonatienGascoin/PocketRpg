package com.pocket.rpg.core.window;

import com.pocket.rpg.config.GameConfig;

/**
 * Abstract base class for window implementations.
 * <p>
 * Provides common interface for both game windows (GLFWWindow) and editor windows (EditorWindow).
 */
public abstract class AbstractWindow {

    /**
     * Game configuration. May be null for editor windows.
     */
    protected final GameConfig config;

    /**
     * Creates a window with game configuration.
     * Used by game windows that need access to game settings.
     *
     * @param config Game configuration
     */
    public AbstractWindow(GameConfig config) {
        this.config = config;
    }

    /**
     * Creates a window without game configuration.
     * Used by editor windows that don't require GameConfig.
     */
    public AbstractWindow() {
        this.config = null;
    }

    // Abstract methods that implementations must provide
    public abstract void init();

    public abstract boolean shouldClose();

    public abstract void pollEvents();

    public abstract void swapBuffers();

    public abstract void destroy();

    public abstract long getWindowHandle();

    public abstract int getScreenWidth();

    public abstract int getScreenHeight();

    public abstract boolean isVisible();

    public abstract boolean isFocused();
}