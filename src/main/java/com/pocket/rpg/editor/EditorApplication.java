package com.pocket.rpg.editor;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.core.EditorWindow;
import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.core.ImGuiLayer;
import com.pocket.rpg.editor.rendering.EditorSceneRenderer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tileset.TilesetRegistry;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.ErrorMode;
import com.pocket.rpg.serialization.Serializer;
import imgui.ImGui;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/**
 * Main application class for the PocketRPG Scene Editor.
 * <p>
 * Responsibilities:
 * - Application lifecycle (init, loop, destroy)
 * - Wiring controllers together
 * - Main render loop orchestration
 * <p>
 * Delegates to:
 * - EditorContext: Shared state
 * - EditorSceneController: Scene operations
 * - EditorToolController: Tool management
 * - EditorUIController: UI rendering
 * - PlayModeController: Play mode management
 */
public class EditorApplication {

    // Core context (shared state)
    private EditorContext context;

    // Controllers
    private EditorSceneController sceneController;
    private EditorToolController toolController;
    private EditorUIController uiController;
    private PlayModeController playModeController;

    // Rendering
    private ImGuiLayer imGuiLayer;
    private EditorSceneRenderer sceneRenderer;

    // Escape key state for edge detection
    private boolean escapeWasPressed = false;

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("    PocketRPG Scene Editor - Phase 7");
        System.out.println("         (with Play Mode support)");
        System.out.println("===========================================");

        EditorApplication app = new EditorApplication();
        app.run();
    }

    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            destroy();
        }
    }

    private void init() {
        System.out.println("Initializing Scene Editor...");

        // Initialize asset system
        initAssets();

        // Load configuration
        EditorConfig config = EditorConfig.createDefault();
        ConfigLoader.loadAllConfigs();
        RenderingConfig renderingConfig = ConfigLoader.getConfig(ConfigLoader.ConfigType.RENDERING);
        GameConfig gameConfig = ConfigLoader.getConfig(ConfigLoader.ConfigType.GAME);

        // Create window
        EditorWindow window = new EditorWindow(config);
        window.init();

        // Initialize ImGui
        imGuiLayer = new ImGuiLayer();
        imGuiLayer.init(window.getWindowHandle(), true);

        // Initialize registries
        ComponentRegistry.initialize();
        TilesetRegistry.initialize();
        TilesetRegistry.getInstance().scanAndLoad();
        PrefabRegistry.initialize();

        // Create camera
        EditorCamera camera = new EditorCamera(config);
        camera.setViewportSize(window.getWidth(), window.getHeight());

        // Initialize context
        context = new EditorContext();
        context.init(config, renderingConfig, gameConfig, window, camera);

        // Create initial scene
        EditorScene initialScene = new EditorScene("Untitled");
        context.setCurrentScene(initialScene);

        // Create controllers (order matters!)
        createControllers();

        // Create scene renderer
        sceneRenderer = new EditorSceneRenderer(uiController.getFramebuffer(), renderingConfig);
        sceneRenderer.init();

        // Wire window resize
        window.setOnResize(() -> {
            uiController.onWindowResize(window.getWidth(), window.getHeight());
            sceneRenderer.onResize(window.getWidth(), window.getHeight());
        });

        System.out.println("Scene Editor initialized successfully");
    }

    private void initAssets() {
        Assets.initialize();
        Assets.configure()
                .setAssetRoot("gameData/assets/")
                .setErrorMode(ErrorMode.THROW_EXCEPTION)
                .apply();

        Serializer.init(Assets.getContext());
    }

    private void createControllers() {
        // Create tool controller
        toolController = new EditorToolController(context);
        toolController.createTools();

        // IMPORTANT: Create PlayModeController BEFORE UIController needs it
        playModeController = new PlayModeController(context, context.getGameConfig());

        // Create UI controller and pass playModeController
        uiController = new EditorUIController(context, toolController);
        uiController.init();
        
        // Now set play mode controller (after init, so statusBar exists)
        uiController.setPlayModeController(playModeController);
        playModeController.setMessageCallback(uiController.getStatusBar()::showMessage);

        // Create scene controller
        sceneController = new EditorSceneController(context);

        // Wire message callbacks
        toolController.setMessageCallback(uiController.getStatusBar()::showMessage);
        sceneController.setMessageCallback(uiController.getStatusBar()::showMessage);

        // Wire menu bar actions
        uiController.getMenuBar().setOnNewScene(sceneController::newScene);
        uiController.getMenuBar().setOnOpenScene(sceneController::openScene);
        uiController.getMenuBar().setOnSaveScene(sceneController::saveScene);
        uiController.getMenuBar().setOnSaveSceneAs(sceneController::saveSceneAs);
        uiController.getMenuBar().setOnExit(context::requestExit);

        // Wire scene change listener
        context.onSceneChanged(this::onSceneChanged);

        // Wire tileset palette to brush tool
        uiController.getTilesetPalette().setBrushTool(toolController.getBrushTool());

        // Pass viewport to tool manager
        uiController.getSceneViewport().setToolManager(context.getToolManager());
    }

    private void onSceneChanged(EditorScene scene) {
        toolController.updateSceneReferences(scene);
        uiController.updateSceneReferences(scene);
    }

    private void loop() {
        System.out.println("Entering main loop...");

        EditorWindow window = context.getWindow();

        while (context.isRunning() && !window.shouldClose()) {
            window.pollEvents();

            if (window.isMinimized()) {
                sleep(100);
                continue;
            }

            update();
            render();

            window.swapBuffers();
        }

        System.out.println("Exited main loop");
    }

    private void update() {
        float deltaTime = ImGui.getIO().getDeltaTime();

        // Handle Escape key to stop play mode (edge-triggered)
        if (playModeController.isActive()) {
            boolean escapePressed = isEscapePressed();
            if (escapePressed && !escapeWasPressed) {
                playModeController.stop();
                escapeWasPressed = true;
                return;
            }
            escapeWasPressed = escapePressed;

            // Update play mode
            playModeController.update(deltaTime);
            return; // Skip editor updates while playing
        }

        escapeWasPressed = isEscapePressed();

        // Update scene
        EditorScene scene = context.getCurrentScene();
        if (scene != null) {
            scene.update(deltaTime);
        }

        // Update tools
        context.getToolManager().update(deltaTime);

        // Process shortcuts
        uiController.getMenuBar().processShortcuts();
        toolController.processShortcuts();
    }

    private boolean isEscapePressed() {
        return glfwGetKey(context.getWindow().getWindowHandle(), GLFW_KEY_ESCAPE) == GLFW_PRESS;
    }

    private void render() {
        EditorConfig config = context.getConfig();

        // Clear screen
        glClearColor(
                config.getClearColor().x,
                config.getClearColor().y,
                config.getClearColor().z,
                config.getClearColor().w
        );
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Render play mode if active
        if (playModeController.isActive()) {
            playModeController.render();
        }

        // Render editor scene to framebuffer (only when NOT in play mode)
        EditorScene scene = context.getCurrentScene();
        if (sceneRenderer != null && scene != null && !playModeController.isActive()) {
            sceneRenderer.render(scene, context.getCamera());
        }

        // Begin ImGui frame
        imGuiLayer.newFrame();

        // Setup docking and render UI
        uiController.setupDocking();
        uiController.renderUI();

        // End ImGui frame
        imGuiLayer.render();
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void destroy() {
        System.out.println("Shutting down Scene Editor...");

        // Stop play mode first
        if (playModeController != null && playModeController.isActive()) {
            playModeController.stop();
        }

        if (sceneRenderer != null) {
            sceneRenderer.destroy();
        }

        if (uiController != null) {
            uiController.destroy();
        }

        EditorScene scene = context.getCurrentScene();
        if (scene != null) {
            scene.destroy();
        }

        TilesetRegistry.destroy();
        FileDialogs.cleanup();

        if (imGuiLayer != null) {
            imGuiLayer.destroy();
        }

        EditorWindow window = context.getWindow();
        if (window != null) {
            window.destroy();
        }

        System.out.println("Scene Editor shut down");
    }
}
