package com.pocket.rpg.core;

import com.pocket.rpg.config.WindowConfig;
import com.pocket.rpg.input.callbacks.InputCallback;
import com.pocket.rpg.utils.LogUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class GlfwWindow extends AbstractWindow {

    private long windowHandle;

    private final InputCallback callback;
    private int screenWidth, screenHeight;
    private boolean isMinimized = false;
    private boolean isFocused = true;


    public GlfwWindow(WindowConfig config, InputCallback callback) {
        super(config);
        this.callback = callback;
    }

    @Override
    public void init() {
        System.out.println(LogUtils.buildBox("Initializing GLFW window..."));

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        setScreenSize();
        setHints();

        windowHandle = glfwCreateWindow(config.getWindowWidth(),
                config.getWindowHeight(),
                config.getTitle(),
                config.isFullscreen() ? glfwGetPrimaryMonitor() : NULL,
                NULL);

        if (windowHandle == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities(); // Initialize OpenGL capabilities

        setCallbacks();

        glfwSwapInterval(config.isVsync() ? 1 : 0); // Enable v-sync

        glfwShowWindow(windowHandle);

        System.out.println("GLFW window initialized: " + screenWidth + "x" + screenHeight);
    }

    @Override
    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    @Override
    public void pollEvents() {
        glfwPollEvents();
    }

    @Override
    public void swapBuffers() {
        glfwSwapBuffers(windowHandle);
    }

    @Override
    public void destroy() {
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();

        GLFWErrorCallback errorCallback = glfwSetErrorCallback(null);
        if (errorCallback != null) {
            errorCallback.free();
        }

        System.out.println("GLFW destroyed");
    }

    @Override
    public long getWindowHandle() {
        return windowHandle;
    }

    @Override
    public int getScreenWidth() {
        return screenWidth;
    }

    @Override
    public int getScreenHeight() {
        return screenHeight;
    }

    @Override
    public boolean isVisible() {
        return !isMinimized;
    }

    @Override
    public boolean isFocused() {
        return isFocused;
    }

    private void setHints() {
        // Configure GLFW hints
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL33.GL_TRUE);
        glfwWindowHint(GLFW_MAXIMIZED, config.isFullscreen() ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_SAMPLES, 4); // 4x Antialiasing

        if (config.isFullscreen()) {
            glfwWindowHint(GLFW_DECORATED, GLFW_FALSE); // Remove the border and title bar
        }
    }

    private void setCallbacks() {
        glfwSetCursorPosCallback(windowHandle, (w, x, y) -> callback.mousePosCallback(x, y));
        glfwSetMouseButtonCallback(windowHandle, (w, b, a, m) -> callback.mouseButtonCallback(b, a, m));
        glfwSetScrollCallback(windowHandle, (w, x, y) -> callback.mouseScrollCallback(x, y));
        glfwSetKeyCallback(windowHandle, (w, k, s, a, m) -> callback.keyCallback(k, s, a, m));

        // Set resize callback after we make the current context
        glfwSetWindowSizeCallback(windowHandle, this::resizeCallback);

        // FIX: Set iconify (minimize) callback
        glfwSetWindowIconifyCallback(windowHandle, this::iconifyCallback);

        // FIX: Set focus callback
        glfwSetWindowFocusCallback(windowHandle, this::focusCallback);
    }

    private void setScreenSize() {
        screenWidth = config.getWindowWidth();
        screenHeight = config.getWindowHeight();

        if (config.isFullscreen()) {
            GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            if (mode == null) {
                throw new IllegalStateException("Video mode is null.");
            }

            this.screenHeight = mode.height();
            this.screenWidth = mode.width();
        }
    }

    private void resizeCallback(long window, int newWidth, int newHeight) {
        if (newHeight <= 0 || newWidth <= 0) {
            System.out.println("Window minimized or invalid size: " + newWidth + "x" + newHeight);
            return;
        }

        // Ignore tiny sizes that might cause issues
        if (newWidth < 100 || newHeight < 100) {
            System.out.println("WARNING: Window size too small, ignoring: " + newWidth + "x" + newHeight);
            return;
        }

        screenWidth = newWidth;
        screenHeight = newHeight;

        System.out.println("Window resized: " + newWidth + "x" + newHeight);
        callback.windowResizeCallback(newWidth, newHeight);
    }

    private void iconifyCallback(long window, boolean iconified) {
        isMinimized = iconified;

        if (iconified) {
            System.out.println("Window minimized - pausing rendering");
        } else {
            System.out.println("Window restored - resuming rendering");
        }
    }

    private void focusCallback(long window, boolean focused) {
        isFocused = focused;

        if (!focused) {
            System.out.println("Window lost focus");
        } else {
            System.out.println("Window gained focus");
        }
    }
}
