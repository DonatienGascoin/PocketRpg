package com.pocket.rpg.engine;

import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.postProcessing.PostProcessor;
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
    private final boolean usePostProcessing;

    protected GlfwManager glfwManager;
    private PostProcessor postProcessor;

    /**
     * Creates a window with the specified internal game resolution.
     *
     * @param config Window configuration
     */
    public Window(WindowConfig config) {
        this.config = config;
        usePostProcessing = config.getPostProcessingEffects() != null && !config.getPostProcessingEffects().isEmpty();
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

        // Initialize game-specific resources
        initGame();
    }

    private void initPostProcessing() {
        if (usePostProcessing) {
            postProcessor = new PostProcessor(getScreenWidth(), getScreenHeight());

            // Add effects from config
            for (PostEffect effect : config.getPostProcessingEffects()) {
                postProcessor.addEffect(effect);
            }

            postProcessor.init(this);
        }
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
            if (usePostProcessing) {
                // Render to post-processing FBO
                postProcessor.bindFboA();
            }

            // Clear background
            glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Update and render game
            renderGame(Time.deltaTime());

            if (usePostProcessing) {
                // Apply post-processing effects
                postProcessor.applyEffects();
            }

            // Swap buffers and poll events
            glfwManager.pollEventsAndSwapBuffers();
            Time.update();
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
        if (usePostProcessing && postProcessor != null) {
            postProcessor.destroy();
        }
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