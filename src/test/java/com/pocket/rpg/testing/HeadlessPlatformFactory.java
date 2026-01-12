package com.pocket.rpg.testing;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.platform.PlatformFactory;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.input.events.KeyEvent;
import com.pocket.rpg.input.events.MouseButtonEvent;
import com.pocket.rpg.rendering.postfx.PostProcessor;

/**
 * Headless platform factory for testing without a real window.
 */
public class HeadlessPlatformFactory implements PlatformFactory {

    @Override
    public AbstractWindow createWindow(
            GameConfig config,
            InputBackend inputBackend,
            InputEventBus inputEventBus) {

        return new MockWindow(config);
    }

    @Override
    public InputBackend createInputBackend() {
        return new MockInputBackend();
    }

    @Override
    public PostProcessor createPostProcessor(GameConfig config) {
        return new NoOpPostProcessor();
    }

    @Override
    public String getPlatformName() {
        return "Headless (Testing)";
    }
}

// Mock implementations
class MockWindow extends AbstractWindow {
    private boolean shouldClose = false;

    public MockWindow(GameConfig config) {
        super(config);
    }

    @Override
    public void init() {
        System.out.println("MockWindow initialized");
    }

    @Override
    public boolean shouldClose() {
        return shouldClose;
    }

    public void setShouldClose(boolean shouldClose) {
        this.shouldClose = shouldClose;
    }

    @Override
    public void pollEvents() {
        // No-op
    }

    @Override
    public void swapBuffers() {
        // No-op
    }

    @Override
    public void destroy() {
        System.out.println("MockWindow destroyed");
    }

    @Override
    public long getWindowHandle() {
        return 0;
    }

    @Override
    public int getScreenWidth() {
        return config.getWindowWidth();
    }

    @Override
    public int getScreenHeight() {
        return config.getWindowHeight();
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public boolean isFocused() {
        return true;
    }
}

class MockInputBackend implements InputBackend {
    @Override
    public KeyCode getKeyCode(int backendKeyCode) {
        return KeyCode.UNKNOWN;
    }

    @Override
    public int mapToBackend(KeyCode keyCode) {
        return -1;
    }

    @Override
    public String getKeyName(KeyCode keyCode) {
        return "Mock";
    }

    @Override
    public KeyEvent.Action getKeyAction(int action) {
        return KeyEvent.Action.PRESS;
    }

    @Override
    public MouseButtonEvent.Action getMouseButtonAction(int action) {
        return MouseButtonEvent.Action.PRESS;
    }
}

class NoOpPostProcessor extends PostProcessor {
    public NoOpPostProcessor() {
        super(GameConfig.builder().build());
    }

    @Override
    public void init(AbstractWindow window) {
        System.out.println("NoOpPostProcessor initialized");
    }

    @Override
    public void beginCapture() {
        // No-op
    }

    @Override
    public void endCaptureAndApplyEffects() {
        // No-op
    }

    @Override
    public void destroy() {
        System.out.println("NoOpPostProcessor destroyed");
    }
}