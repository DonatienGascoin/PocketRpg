package com.pocket.rpg.engine;

import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.utils.PerformanceMonitor;
import com.pocket.rpg.utils.Time;
import com.pocket.rpg.utils.WindowConfig;
import lombok.Getter;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;

public abstract class Window {

    @Getter
    private final WindowConfig config;

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

        initPostProcessing();

        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.setEnabled(true);

        // Initialize game-specific resources
        initGame();
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
     */
    private void loop() {
        while (!glfwManager.shouldClose()) {
            postProcessor.beginCapture();

            // Clear background
            glClearColor(1f, .8f, .8f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Update and render game
            renderGame(Time.deltaTime());

            postProcessor.endCaptureAndApplyEffects();

            // Swap buffers and poll events
            glfwManager.pollEventsAndSwapBuffers();
            Time.update();
            performanceMonitor.update();
        }
    }

    /**
     * Render the game scene.
     * Called each frame. Rendering happens to the post-processing FBO.
     *
     * @param deltaTime Time since last frame in seconds
     */
    protected abstract void renderGame(float deltaTime);


    protected void destroy() {
        destroyGame();
        glfwManager.destroy();
        postProcessor.destroy();
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
}