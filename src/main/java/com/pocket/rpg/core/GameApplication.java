package com.pocket.rpg.core;

import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.WindowConfig;
import com.pocket.rpg.input.GlfwInput;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyListener;
import com.pocket.rpg.input.MouseListener;
import com.pocket.rpg.inputNew.InputManager;
import com.pocket.rpg.inputNew.GLFWInputBackend;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.postProcessing.postEffects.VignetteEffect;
import com.pocket.rpg.rendering.CameraManager;
import com.pocket.rpg.rendering.renderers.OpenGLRenderer;
import com.pocket.rpg.rendering.renderers.RenderInterface;
import com.pocket.rpg.utils.DefaultCallback;
import com.pocket.rpg.utils.PerformanceMonitor;
import com.pocket.rpg.utils.Time;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;

/**
 * Main application class - the composition root.
 * Owns and orchestrates all systems (window, renderer, engine, etc.)
 */
public class GameApplication {

    private static final boolean ENABLE_PERFORMANCE_MONITOR = true;

    // Platform systems
    private AbstractWindow window;
    // Engine
    private GameEngine engine;

    private RenderInterface renderer;
    private Input input;
    //    private AudioInterface audio;
    private PostProcessor postProcessor;
    private CameraManager cameraManager;
    private PerformanceMonitor performanceMonitor;

    private DefaultCallback callbacks;

    // Configuration
    private WindowConfig config;

    /**
     * Initialize all systems.
     */
    private void init() {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                     POCKET RPG ENGINE                         ║");
        System.out.println("║                   Application Starting                        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");

        // 1. Create window configuration
        config = WindowConfig.builder()
                .windowWidth(640)
                .windowHeight(480)
                .gameWidth(640)
                .gameHeight(480)
                .title("Pocket RPG Engine")
                .vsync(true)
                .scalingMode(PostProcessor.ScalingMode.MAINTAIN_ASPECT_RATIO)
                .enablePillarBox(true)
                .postProcessingEffects(List.of(new VignetteEffect(1f, 1.5f)))
                .build();

        callbacks = new DefaultCallback();

        // 2. Create window
        System.out.println("Creating window...");
        window = new GlfwWindow(config, callbacks);
        window.init();

        // 3. Initialize platform systems (need OpenGL context from window)
        System.out.println("Initializing platform systems...");
        initSystems();

        // 4. Create game engine
        System.out.println("Creating game engine...");
        engine = new GameEngine(config);

        // 5. Inject platform systems into engine
        engine.setRenderer(renderer);
        engine.setCameraManager(cameraManager);
//        engine.setInput(input);
//        engine.setAudio(audio);
        engine.initialize();

        System.out.println("Application initialization complete");
    }

    private void initSystems() {
        renderer = new OpenGLRenderer(config);
        // Renderer init happens in engine

        KeyListener keyListener = new KeyListener();
        callbacks.addKeyCallback(keyListener::keyCallback);
        MouseListener mouseListener = new MouseListener();
        callbacks.addMouseButtonCallback(mouseListener::mouseButtonCallback);
        callbacks.addMousePosCallback(mouseListener::mousePosCallback);
        callbacks.addMouseScrollCallback(mouseListener::mouseScrollCallback);

        Input.init(new InputConfig(), new GlfwInput(keyListener, mouseListener)); // Use Glfw to retrieve inputs
        InputManager.initialize(new GLFWInputBackend());

//        audio = new NoOpAudioManager();
//        audio.init();

        // 3. Initialize camera system
        System.out.println("Initializing camera system...");
        cameraManager = CameraManager.initialize(config.getGameWidth(), config.getGameHeight());
        CameraManager.setViewportSize(window.getScreenWidth(), window.getScreenHeight());

        // 4. Initialize post-processor
        postProcessor = new PostProcessor(config);
        postProcessor.init(window);

        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.setEnabled(ENABLE_PERFORMANCE_MONITOR);
    }

    private void loop() {
        System.out.println("Starting main loop...");

        Time.init();

        while (!window.shouldClose()) {
            // Handle minimized window
            if (!window.isVisible()) {
                window.pollEvents();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                Time.update();
                continue;
            }

            // Begin post-processing capture
            postProcessor.beginCapture();

            // Clear screen
            glClearColor(1f, 0.8f, 0.8f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Update and render game
            try {
                engine.update(Time.deltaTime());
                engine.render();
            } catch (Exception e) {
                System.err.println("ERROR in game loop: " + e.getMessage());
                e.printStackTrace();
            }

            // Apply post-processing
            postProcessor.endCaptureAndApplyEffects();

            // Swap buffers and poll events
            window.swapBuffers();
            window.pollEvents();

            Input.endFrame();

            // Update time and performance, last things in loop
            Time.update();
            performanceMonitor.update();
        }

        System.out.println("Exited main loop");
    }

    /**
     * Run the application.
     */
    public void run() {
        try {
            init();
            loop();
            destroy();
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            destroy();
        }
    }

    /**
     * Clean up all systems.
     */
    private void destroy() {
        System.out.println("Destroying application...");

        if (engine != null) {
            engine.destroy();
        }

        if (renderer != null) {
            renderer.destroy();
        }

//        if (input != null) {
//            input.destroy();
//        }
//
//        if (audio != null) {
//            audio.destroy();
//        }

        if (postProcessor != null) {
            postProcessor.destroy();
        }

        CameraManager.destroy();

        if (window != null) {
            window.destroy();
        }

        System.out.println("Application destroyed");
    }
}
