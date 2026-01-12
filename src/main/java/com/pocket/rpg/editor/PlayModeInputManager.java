package com.pocket.rpg.editor;

import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.platform.glfw.GLFWInputBackend;
import com.pocket.rpg.input.DefaultInputContext;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.input.events.KeyEvent;
import com.pocket.rpg.input.events.MouseButtonEvent;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Manages Input system setup for Play Mode.
 * <p>
 * Creates and wires:
 * - KeyListener, MouseListener, GamepadListener
 * - InputEventBus with GLFW callbacks
 * - DefaultInputContext
 * - Input service locator initialization
 */
public class PlayModeInputManager {

    private final long windowHandle;
    private final InputConfig inputConfig;

    private InputEventBus eventBus;
    private KeyListener keyListener;
    private MouseListener mouseListener;
    private GamepadListener gamepadListener;
    private DefaultInputContext inputContext;
    private InputBackend inputBackend;

    // Store original callbacks to restore on destroy
    private org.lwjgl.glfw.GLFWKeyCallback previousKeyCallback;
    private org.lwjgl.glfw.GLFWMouseButtonCallback previousMouseButtonCallback;
    private org.lwjgl.glfw.GLFWCursorPosCallback previousCursorPosCallback;
    private org.lwjgl.glfw.GLFWScrollCallback previousScrollCallback;

    private boolean initialized = false;

    /**
     * Creates a PlayModeInputManager.
     *
     * @param windowHandle GLFW window handle for input callbacks
     * @param inputConfig  Input configuration (axis mappings, bindings)
     */
    public PlayModeInputManager(long windowHandle, InputConfig inputConfig) {
        this.windowHandle = windowHandle;
        this.inputConfig = inputConfig;
    }

    /**
     * Initializes the input system for play mode.
     * Sets up GLFW callbacks and initializes the Input service locator.
     */
    public void init() {
        if (initialized) {
            System.out.println("PlayModeInputManager already initialized");
            return;
        }

        System.out.println("Initializing Play Mode input system...");

        // Create input backend
        inputBackend = new GLFWInputBackend();

        // Create listeners
        keyListener = new KeyListener();
        mouseListener = new MouseListener();
        gamepadListener = new GamepadListener();

        // Create event bus and subscribe listeners
        eventBus = new InputEventBus();
        eventBus.addKeyListener(keyListener);
        eventBus.addMouseListener(mouseListener);
        eventBus.addGamepadListener(gamepadListener);

        // Create input context
        inputContext = new DefaultInputContext(inputConfig, keyListener, mouseListener, gamepadListener);

        // Initialize Input service locator
        if (Input.hasContext()) {
            // Input already initialized - just swap context
            Input.setContext(inputContext);
            System.out.println("Input context swapped for Play Mode");
        } else {
            Input.initialize(inputContext);
        }

        // Set up GLFW callbacks (save previous to restore later)
        setupCallbacks();

        // Initialize mouse position
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        glfwGetCursorPos(windowHandle, xpos, ypos);
        mouseListener.setPosition(xpos[0], ypos[0]);
        mouseListener.resetDelta();

        initialized = true;
        System.out.println("Play Mode input system initialized");
    }

    /**
     * Sets up GLFW input callbacks that dispatch to our event bus.
     */
    private void setupCallbacks() {
        // Save previous callbacks
        previousKeyCallback = glfwSetKeyCallback(windowHandle, null);
        previousMouseButtonCallback = glfwSetMouseButtonCallback(windowHandle, null);
        previousCursorPosCallback = glfwSetCursorPosCallback(windowHandle, null);
        previousScrollCallback = glfwSetScrollCallback(windowHandle, null);

        // Set new callbacks that dispatch to event bus
        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            // Convert GLFW key to KeyCode
            KeyCode keyCode = inputBackend.getKeyCode(key);
            KeyEvent.Action keyAction = inputBackend.getKeyAction(action);

            // Debug: uncomment to see key events
            System.out.println("[PlayMode Input] Key event: " + keyCode + " action=" + keyAction);

            // Dispatch to play mode input
            eventBus.dispatchKeyEvent(keyCode, keyAction);

            // Also call previous callback (for ImGui, editor, etc.)
            if (previousKeyCallback != null) {
                previousKeyCallback.invoke(window, key, scancode, action, mods);
            }
        });

        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            // Mouse buttons use negative values in GLFWInputBackend mapping
            KeyCode buttonCode = inputBackend.getKeyCode(-button);
            MouseButtonEvent.Action buttonAction = inputBackend.getMouseButtonAction(action);

            // Dispatch to play mode input
            eventBus.dispatchMouseButtonEvent(buttonCode, buttonAction);

            // Also call previous callback
            if (previousMouseButtonCallback != null) {
                previousMouseButtonCallback.invoke(window, button, action, mods);
            }
        });

        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            // Dispatch to play mode input
            eventBus.dispatchMouseMoveEvent(xpos, ypos);

            // Also call previous callback
            if (previousCursorPosCallback != null) {
                previousCursorPosCallback.invoke(window, xpos, ypos);
            }
        });

        glfwSetScrollCallback(windowHandle, (window, xoffset, yoffset) -> {
            // Dispatch to play mode input
            eventBus.dispatchMouseScrollEvent(xoffset, yoffset);

            // Also call previous callback
            if (previousScrollCallback != null) {
                previousScrollCallback.invoke(window, xoffset, yoffset);
            }
        });

        System.out.println("Play Mode input callbacks installed");
    }

    /**
     * Updates the input system. Call every frame during play mode.
     *
     * @param deltaTime Time since last frame
     */
    public void update(float deltaTime) {
        if (!initialized) return;

        inputContext.update(deltaTime);
    }

    /**
     * Ends the input frame. Call at end of each frame during play mode.
     */
    public void endFrame() {
        if (!initialized) return;

        inputContext.endFrame();
    }

    /**
     * Destroys the input system and restores previous callbacks.
     */
    public void destroy() {
        if (!initialized) {
            return;
        }

        System.out.println("Destroying Play Mode input system...");

        // Restore previous callbacks
        glfwSetKeyCallback(windowHandle, previousKeyCallback);
        glfwSetMouseButtonCallback(windowHandle, previousMouseButtonCallback);
        glfwSetCursorPosCallback(windowHandle, previousCursorPosCallback);
        glfwSetScrollCallback(windowHandle, previousScrollCallback);

        // Clear listeners
        if (keyListener != null) keyListener.clear();
        if (mouseListener != null) mouseListener.clear();
        if (gamepadListener != null) gamepadListener.clear();

        // Don't destroy Input entirely - just clear context
        // The editor might still need input after play mode stops
        if (inputContext != null) {
            inputContext.destroy();
        }

        eventBus = null;
        keyListener = null;
        mouseListener = null;
        gamepadListener = null;
        inputContext = null;

        initialized = false;
        System.out.println("Play Mode input system destroyed");
    }

    /**
     * Gets the input context (for advanced usage).
     */
    public DefaultInputContext getInputContext() {
        return inputContext;
    }

    /**
     * Checks if input is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
}