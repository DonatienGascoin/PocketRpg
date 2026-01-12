package com.pocket.rpg.editor.core;

import com.pocket.rpg.core.window.AbstractWindow;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GLFW window for the Scene Editor.
 * Extends AbstractWindow to allow sharing with runtime systems like PostProcessor.
 * Handles window creation, OpenGL context, and basic input callbacks.
 * Designed to work with ImGuiLayer for UI rendering.
 */
public class EditorWindow extends AbstractWindow {

    private long windowHandle;
    private final EditorConfig editorConfig;

    private int screenWidth;
    private int screenHeight;
    private boolean isMinimized = false;
    private boolean isFocused = true;

    // Input state (updated by callbacks)
    private double mouseX, mouseY;
    private double scrollDeltaX, scrollDeltaY;
    private boolean[] mouseButtons = new boolean[8];
    private boolean[] mouseButtonsJustPressed = new boolean[8];
    private boolean[] mouseButtonsJustReleased = new boolean[8];

    // Callbacks for editor systems
    private Runnable onResize;

    /**
     * Creates an EditorWindow with editor configuration.
     *
     * @param editorConfig Editor-specific configuration (window size, title, etc.)
     */
    public EditorWindow(EditorConfig editorConfig) {
        super();  // No GameConfig needed for editor window
        this.editorConfig = editorConfig;
    }

    /**
     * Initializes the GLFW window and OpenGL context.
     */
    @Override
    public void init() {
        System.out.println("Initializing Editor Window...");

        // Setup error callback
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Determine window size
        determineWindowSize();

        // Configure GLFW hints
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        glfwWindowHint(GLFW_MAXIMIZED, editorConfig.isFullscreen() ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_SAMPLES, 4);

        // Create windowed window
        windowHandle = glfwCreateWindow(screenWidth, screenHeight, editorConfig.getTitle(), NULL, NULL);

        if (windowHandle == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        // Make OpenGL context current
        glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();

        // Setup callbacks
        setupCallbacks();

        // VSync
        glfwSwapInterval(editorConfig.isVsync() ? 1 : 0);

        // Center window if not fullscreen
        if (!editorConfig.isFullscreen()) {
            centerWindow();
        }

        // Show window
        glfwShowWindow(windowHandle);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);

            glfwGetWindowSize(windowHandle, w, h);
            onResizeCallback(w.get(0), h.get(0));
        }

        // Initial OpenGL state
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        System.out.println("Editor Window initialized: " + screenWidth + "x" + screenHeight);
    }

    private void determineWindowSize() {
        if (editorConfig.isFullscreen()) {
            // Don't set screenWidth or screenHeight
            screenWidth = 800;  // placeholder
            screenHeight = 600; // GLFW will override this
        } else {
            screenWidth = editorConfig.getWindowWidth();
            screenHeight = editorConfig.getWindowHeight();
        }
    }

    private void centerWindow() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(windowHandle, pWidth, pHeight);

            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode != null) {
                glfwSetWindowPos(
                        windowHandle,
                        (vidMode.width() - pWidth.get(0)) / 2,
                        (vidMode.height() - pHeight.get(0)) / 2
                );
            }
        }
    }

    private void setupCallbacks() {
        // Window size callback
        glfwSetWindowSizeCallback(windowHandle, (window, width, height) -> onResizeCallback(width, height));

        // Window iconify callback
        glfwSetWindowIconifyCallback(windowHandle, (window, iconified) -> {
            isMinimized = iconified;
        });

        // Window focus callback
        glfwSetWindowFocusCallback(windowHandle, (window, focused) -> {
            isFocused = focused;
        });

        // Mouse position callback
        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
        });

        // Mouse button callback
        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            if (button >= 0 && button < mouseButtons.length) {
                if (action == GLFW_PRESS) {
                    mouseButtons[button] = true;
                    mouseButtonsJustPressed[button] = true;
                } else if (action == GLFW_RELEASE) {
                    mouseButtons[button] = false;
                    mouseButtonsJustReleased[button] = true;
                }
            }
        });

        // Scroll callback
        glfwSetScrollCallback(windowHandle, (window, xoffset, yoffset) -> {
            scrollDeltaX += xoffset;
            scrollDeltaY += yoffset;
        });

        // Key callback
        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            // Editor shortcuts handled by ImGui
        });
    }

    private void onResizeCallback(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        System.out.println("New width: " + width + ", new height: " + height);
        screenWidth = width;
        screenHeight = height;
        glViewport(0, 0, width, height);

        if (onResize != null) {
            onResize.run();
        }
    }

    // ========================================================================
    // AbstractWindow Implementation
    // ========================================================================

    @Override
    public void pollEvents() {
        // Clear per-frame state
        scrollDeltaX = 0;
        scrollDeltaY = 0;
        for (int i = 0; i < mouseButtonsJustPressed.length; i++) {
            mouseButtonsJustPressed[i] = false;
            mouseButtonsJustReleased[i] = false;
        }

        glfwPollEvents();
    }

    @Override
    public void swapBuffers() {
        glfwSwapBuffers(windowHandle);
    }

    @Override
    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    @Override
    public void destroy() {
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();

        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }

        System.out.println("Editor Window destroyed");
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

    // ========================================================================
    // Legacy Getters (for existing editor code)
    // ========================================================================

    /**
     * @deprecated Use {@link #getScreenWidth()} instead
     */
    @Deprecated
    public int getWidth() {
        return screenWidth;
    }

    /**
     * @deprecated Use {@link #getScreenHeight()} instead
     */
    @Deprecated
    public int getHeight() {
        return screenHeight;
    }

    public boolean isMinimized() {
        return isMinimized;
    }

    // ========================================================================
    // Editor-specific Methods
    // ========================================================================

    public void requestClose() {
        glfwSetWindowShouldClose(windowHandle, true);
    }

    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    public double getScrollDeltaX() {
        return scrollDeltaX;
    }

    public double getScrollDeltaY() {
        return scrollDeltaY;
    }

    public boolean isMouseButtonDown(int button) {
        if (button >= 0 && button < mouseButtons.length) {
            return mouseButtons[button];
        }
        return false;
    }

    public boolean isMouseButtonJustPressed(int button) {
        if (button >= 0 && button < mouseButtonsJustPressed.length) {
            return mouseButtonsJustPressed[button];
        }
        return false;
    }

    public boolean isMouseButtonJustReleased(int button) {
        if (button >= 0 && button < mouseButtonsJustReleased.length) {
            return mouseButtonsJustReleased[button];
        }
        return false;
    }

    public boolean isKeyDown(int key) {
        return glfwGetKey(windowHandle, key) == GLFW_PRESS;
    }

    public void setOnResize(Runnable callback) {
        this.onResize = callback;
    }
}