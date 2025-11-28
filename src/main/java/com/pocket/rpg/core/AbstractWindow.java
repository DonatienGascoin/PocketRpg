package com.pocket.rpg.core;

import com.pocket.rpg.config.WindowConfig;

public abstract class AbstractWindow {

    protected final WindowConfig config;

    public AbstractWindow(WindowConfig config) {
        this.config = config;
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
