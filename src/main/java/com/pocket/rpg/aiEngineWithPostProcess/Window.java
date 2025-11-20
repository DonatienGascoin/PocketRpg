package com.pocket.rpg.aiEngineWithPostProcess;

import com.pocket.rpg.utils.Time;
import com.pocket.rpg.utils.WindowConfig;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;

public abstract class Window {

    protected GlfwManager glfwManager;
    private PostProcessor postProcessor;
    private final List<PostEffect> effects = new ArrayList<>();

    /**
     * Creates a window with the specified internal game resolution.
     *
     * @param config Window configuration
     */
    public Window(WindowConfig config) {
        this.glfwManager = new GlfwManager(config);
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
        glfwManager.init();
        Time.init();

        // Initialize post-processor
        postProcessor = new PostProcessor(getScreenWidth(), getScreenHeight());

        // Let subclass declare which effects to use
//        declareEffects();

        // Add pillarbox effect as the last effect (always required)
//        effects.add(new PillarboxEffect(getScreenWidth(), getScreenHeight(),
//                glfwManager.getWindowHandle()));

        // Add all effects to processor and initialize
        for (PostEffect effect : effects) {
            postProcessor.addEffect(effect);
        }
        postProcessor.init();

        // Initialize game-specific resources
        initGame();

       /* // Set key callbacks or other general callbacks here if needed
        glfwSetKeyCallback(glfwManager.getWindowHandle(), (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true);
        });*/
    }

    /**
     * Declare which post-processing effects to use.
     * Called during initialization, before effects are created.
     * <p>
     * Example:
     * protected void declareEffects() {
     * addEffect(new BlurEffect(2.0f));
     * addEffect(new ColorVignetteEffect(1.5f, 0.5f));
     * }
     */
    protected abstract void declareEffects();

    /**
     * Initialize game-specific resources (textures, sprites, etc.).
     * Called after post-processing is set up.
     */
    protected abstract void initGame();

    /**
     * Adds an effect to the post-processing chain.
     * Call this from declareEffects().
     *
     * @param effect The effect to add
     */
    protected final void addEffect(PostEffect effect) {
        effects.add(effect);
    }

    /**
     * Main rendering loop. Continues until window should close.
     */
    private void loop() {
        while (!glfwManager.shouldClose()) {
            // Begin post-processing capture
            postProcessor.bindFboA();
            // Clear background
            glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Update and render game
            renderGame(Time.deltaTime());

            // Apply post-processing effects (includes pillarbox as final effect)
            postProcessor.applyEffects();

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