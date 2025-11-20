package com.pocket.rpg.engine;

import com.pocket.rpg.utils.WindowConfig;
import lombok.Getter;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class GlfwManager {

    private final WindowConfig config;

    @Getter
    private long windowHandle;
    @Getter
    private int screenWidth, screenHeight;

    public GlfwManager(WindowConfig config) {
        this.config = config;
    }

    /**
     * Initializes GLFW, creates the window, and sets up the OpenGL context.
     *
     * @throws IllegalStateException if GLFW initialization fails
     * @throws RuntimeException if window creation fails
     */
    public void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        setScreenSize();
        setHints();

        windowHandle = glfwCreateWindow(config.getInitialWidth(),
                config.getInitialHeight(),
                config.getTitle(),
                config.isFullscreen() ? glfwGetPrimaryMonitor() : NULL,
                NULL);

        if (windowHandle == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        setCallbacks();

        glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities(); // Initialize OpenGL capabilities

        // Set resize callback after we make the current context.
        glfwSetWindowSizeCallback(windowHandle, this::resizeCallback);

        glfwSwapInterval(config.isVsync() ? 1 : 0); // Enable v-sync

        glfwShowWindow(windowHandle);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    public void pollEventsAndSwapBuffers() {
        glfwSwapBuffers(windowHandle);
        glfwPollEvents();
    }

    public void destroy() {
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();

        GLFWErrorCallback errorCallback = glfwSetErrorCallback(null);
        if (errorCallback != null) {
            errorCallback.free();
        }
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
        glfwWindowHint(GLFW_SAMPLES, 4); // 4* Antialiasing

        if (config.isFullscreen()) {
            glfwWindowHint(GLFW_DECORATED, GLFW_FALSE); // Remove the border and title bar
        }
    }

    private void setCallbacks() {
        glfwSetCursorPosCallback(windowHandle, config.getCallback()::mousePosCallback);
        glfwSetMouseButtonCallback(windowHandle, config.getCallback()::mouseButtonCallback);
        glfwSetScrollCallback(windowHandle, config.getCallback()::mouseScrollCallback);
        glfwSetKeyCallback(windowHandle, config.getCallback()::keyCallback);
    }

    private void setScreenSize() {
        screenWidth = config.getInitialWidth();
        screenHeight = config.getInitialHeight();

        if (config.isFullscreen()) {
            IntBuffer wBuffer = BufferUtils.createIntBuffer(1);
            IntBuffer hBuffer = BufferUtils.createIntBuffer(1);
            GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            if (mode == null) {
                throw new IllegalStateException("Video mode is null.");
            }

            this.screenHeight = mode.height();
            this.screenWidth = mode.width();

//            glfwGetMonitorWorkarea(glfwGetPrimaryMonitor(), null, null, wBuffer, hBuffer);
//            this.screenWidth = wBuffer.get(0);
//            this.screenHeight = hBuffer.get(0);
//            glfwGetMonitorWorkarea(glfwGetPrimaryMonitor(), null, null, wBuffer, hBuffer);
        }
    }

    private void resizeCallback(long window, int newWidth, int newHeight) {
        if (newHeight <= 0 || newWidth <= 0) {
            return; // Ignore minimize window
        }
        // Solution 1: force the Window to keep the aspect ratio (does not work when full screen)
//        glfwSetWindowAspectRatio(glfwWindow, config.getInitialWidth(), config.getInitialHeight());
        screenWidth = newWidth;
        screenHeight = newHeight;

        config.getCallback().windowResizeCallback(window, newWidth, newHeight);
    }
}
