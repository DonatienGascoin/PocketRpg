package com.pocket.rpg.engine;

import com.pocket.rpg.input.InputManager;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.utils.PerformanceMonitor;
import com.pocket.rpg.utils.Time;
import com.pocket.rpg.utils.WindowConfig;
import lombok.Getter;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;

/**
 * FIXED: Now properly handles window minimization
 */
public abstract class Window {

    @Getter
    protected final WindowConfig config;

    protected GlfwManager glfwManager;
    private PostProcessor postProcessor;
    private PerformanceMonitor performanceMonitor;

    /**
     * Creates a window with the specified internal game resolution.
     *
     * @param config Window configuration
     */
    public Window(WindowConfig config) {
        this.config = config;
    }

    /**
     * Main entry point that runs the complete window lifecycle.
     */
    public void run() {
        init();
        loop();
        destroy();
    }

    /**
     * Initializes the window and OpenGL context.
     * Subclasses can override to add custom initialization.
     */
    protected final void init() {
        Time.init();

        glfwManager = new GlfwManager(config);
        glfwManager.init();

        InputManager.initialize(getWindowHandle());

        initPostProcessing();

        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.setEnabled(true);

        // Initialize game-specific resources
        initGame();
        
        System.out.println("Window initialized successfully");
    }

    private void initPostProcessing() {
        postProcessor = new PostProcessor(config);
        postProcessor.init(this);
    }

    /**
     * Initialize game-specific resources (textures, sprites, etc.).
     * Called after post-processing is set up.
     */
    protected abstract void initGame();

    /**
     * Main rendering loop. Continues until window should close.
     * FIX: Now properly handles minimization
     */
    private void loop() {
        System.out.println("Entering main loop");
        
        while (!glfwManager.shouldClose()) {
            // FIX: Skip rendering when window is minimized
            if (!glfwManager.isVisible()) {
                // Window is minimized - don't render, just poll events and sleep
                glfwManager.pollEvents();
                
                // Sleep to avoid busy-wait and save CPU/battery
                try {
                    Thread.sleep(100); // Sleep 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                Time.update();
                continue; // Skip rendering
            }


            InputManager.poll();

            // Normal rendering when window is visible
            postProcessor.beginCapture();

            // Clear background
            glClearColor(1f, .8f, .8f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Update and render game
            try {
                renderGame(Time.deltaTime());
            } catch (Exception e) {
                System.err.println("ERROR in renderGame: " + e.getMessage());
                e.printStackTrace();
            }

            postProcessor.endCaptureAndApplyEffects();

            // Swap buffers and poll events
            glfwManager.pollEventsAndSwapBuffers();
            Time.update();
            performanceMonitor.update();
        }
        
        System.out.println("Exited main loop");
    }

    /**
     * Render the game scene.
     * Called each frame. Rendering happens to the post-processing FBO.
     *
     * @param deltaTime Time since last frame in seconds
     */
    protected abstract void renderGame(float deltaTime);


    protected void destroy() {
        System.out.println("Destroying window...");
        
        destroyGame();

        InputManager.destroy();
        
        if (postProcessor != null) {
            postProcessor.destroy();
        }
        
        if (glfwManager != null) {
            glfwManager.destroy();
        }
        
        System.out.println("Window destroyed");
    }

    /**
     * Clean up game-specific resources.
     */
    protected abstract void destroyGame();

    public long getWindowHandle() {
        return glfwManager.getWindowHandle();
    }

    public int getScreenWidth() {
        return glfwManager.getScreenWidth();
    }

    public int getScreenHeight() {
        return glfwManager.getScreenHeight();
    }
    
    /**
     * FIX: New method to check if window is visible
     */
    public boolean isVisible() {
        return glfwManager.isVisible();
    }
    
    /**
     * FIX: New method to check if window is focused
     */
    public boolean isFocused() {
        return glfwManager.isFocused();
    }
}
