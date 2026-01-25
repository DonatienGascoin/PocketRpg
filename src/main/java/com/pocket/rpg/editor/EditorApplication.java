package com.pocket.rpg.editor;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.shortcut.EditorShortcutHandlersImpl;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutContext;
import com.pocket.rpg.editor.shortcut.ShortcutRegistry;
import com.pocket.rpg.editor.ui.inspectors.CustomComponentEditorRegistry;
import com.pocket.rpg.rendering.postfx.PostEffectRegistry;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.core.EditorWindow;
import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.core.ImGuiLayer;
import com.pocket.rpg.editor.rendering.EditorSceneRenderer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tileset.TilesetRegistry;
import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.AudioConfig;
import com.pocket.rpg.audio.DefaultAudioContext;
import com.pocket.rpg.audio.backend.OpenALAudioBackend;
import com.pocket.rpg.audio.editor.EditorAudio;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.ErrorMode;
import com.pocket.rpg.serialization.Serializer;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;

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

    // Logging
    private static final Logger LOG = Log.getLogger(EditorApplication.class);

    // Exit confirmation state
    private boolean showExitConfirmation = false;
    private boolean exitRequested = false;

    // FIX: Track first frame for initial focus
    private boolean isFirstFrame = true;

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

        // Log startup info
        LOG.info("PocketRPG Scene Editor starting up");
        LOG.debug("Java version: " + System.getProperty("java.version"));
        LOG.debug("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));

        // Initialize audio system
        initAudio();

        // Initialize PostEffectRegistry before loading configs (needed for deserialization)
        PostEffectRegistry.initialize();

        // Load configuration
        ConfigLoader.loadAllConfigs();
        EditorConfig config = ConfigLoader.getConfig(ConfigLoader.ConfigType.EDITOR);
        RenderingConfig renderingConfig = ConfigLoader.getConfig(ConfigLoader.ConfigType.RENDERING);
        GameConfig gameConfig = ConfigLoader.getConfig(ConfigLoader.ConfigType.GAME);
        InputConfig inputConfig = ConfigLoader.getConfig(ConfigLoader.ConfigType.INPUT);

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
        CustomComponentEditorRegistry.initBuiltInEditors();

        // Create camera
        EditorCamera camera = new EditorCamera(config);
        camera.setViewportSize(window.getWidth(), window.getHeight());

        // Initialize context
        context = new EditorContext();
        context.init(config, renderingConfig, gameConfig, inputConfig, window, camera);

        // Create initial scene (name will be "Untitled" since no filePath)
        EditorScene initialScene = new EditorScene();
        context.setCurrentScene(initialScene);

        // Create controllers (order matters!)
        createControllers();

        // Create scene renderer
        sceneRenderer = new EditorSceneRenderer(uiController.getFramebuffer(), renderingConfig);
        sceneRenderer.init();

        // Wire window resize (screen coordinates - for UI)
        window.setOnResize(() -> {
            uiController.onWindowResize(window.getWidth(), window.getHeight());
        });

        // Wire framebuffer resize (pixel dimensions - for rendering)
        // This handles multi-monitor setups with different DPIs
        window.setOnFramebufferResize(() -> {
            sceneRenderer.onResize(window.getFramebufferWidth(), window.getFramebufferHeight());
        });

        // Wire window move (handles moving between monitors)
        window.setOnWindowMove(() -> {
            uiController.getSceneViewport().invalidate();
        });

        // Initialize default tool
        initializeDefaultTool();

        // Auto-open last scene if available
        openLastScene();

        LOG.info("Scene Editor initialized successfully");
        System.out.println("Scene Editor initialized successfully");
    }

    /**
     * Opens the last opened scene from the recent list, if it exists.
     */
    private void openLastScene() {
        EditorConfig config = context.getConfig();
        String lastScene = config.getLastOpenedScene();

        if (lastScene != null && !lastScene.isEmpty()) {
            java.io.File file = new java.io.File(lastScene);
            if (file.exists()) {
                System.out.println("Auto-opening last scene: " + lastScene);
                sceneController.openScene(lastScene);
            } else {
                System.out.println("Last scene not found, skipping auto-open: " + lastScene);
            }
        }
    }

    private void initAssets() {
        Assets.initialize();
        Assets.configure()
                .setAssetRoot("gameData/assets/")
                .setErrorMode(ErrorMode.THROW_EXCEPTION)
                .apply();

        Serializer.init(Assets.getContext());
    }

    private void initAudio() {
        // Initialize audio backend
        OpenALAudioBackend backend = new OpenALAudioBackend();
        AudioConfig config = new AudioConfig();
        DefaultAudioContext audioContext = new DefaultAudioContext(backend, config);
        Audio.initialize(audioContext);

        // Initialize editor audio for preview functionality
        EditorAudio.initialize(backend);

        System.out.println("Audio system initialized");
    }

    private void createControllers() {
        // Create tool controller
        toolController = new EditorToolController(context);
        toolController.createTools();

        // IMPORTANT: Create PlayModeController BEFORE UIController needs it
        playModeController = new PlayModeController(context, context.getGameConfig(), context.getInputConfig());

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
        uiController.getMenuBar().setOnExit(this::requestExit);

        // Wire recent scenes
        updateMenuRecentScenes();
        sceneController.setOnRecentScenesChanged(this::updateMenuRecentScenes);

        // Wire scene change listener
        context.onSceneChanged(this::onSceneChanged);

        // Wire tileset palette to brush tool
        uiController.getTilesetPalette().setBrushTool(toolController.getBrushTool());

        // Pass viewport to tool manager
        uiController.getSceneViewport().setToolManager(context.getToolManager());

        // Initialize shortcut system
        ShortcutRegistry shortcutRegistry = ShortcutRegistry.getInstance();
        String shortcutConfigPath = "editor/config/editorShortcuts.json";

        // Load config first to get keyboard layout
        KeyboardLayout layout = shortcutRegistry.loadConfigAndGetLayout(shortcutConfigPath);

        // Register defaults with the keyboard layout
        EditorShortcuts.registerDefaults(shortcutRegistry, layout);

        // Apply any custom bindings from config
        shortcutRegistry.applyConfigBindings();

        // Generate complete config file with both QWERTY and AZERTY layouts
        shortcutRegistry.generateCompleteConfig(EditorShortcuts::getDefaultBindings);

        // Create shortcut handlers implementation
        EditorShortcutHandlersImpl handlers = new EditorShortcutHandlersImpl(
                context,
                toolController,
                uiController.getMenuBar()
        );
        handlers.setPlayModeController(playModeController);
        handlers.setMessageCallback(uiController.getStatusBar()::showMessage);
        handlers.setConfigurationPanel(uiController.getConfigurationPanel());
        handlers.setTilesetPalettePanel(uiController.getTilesetPalette());
        handlers.setCollisionPanel(uiController.getCollisionPanel());

        // Bind handlers to shortcuts
        EditorShortcuts.bindHandlers(shortcutRegistry, handlers);
    }

    /**
     * Initialize default tool on startup.
     */
    private void initializeDefaultTool() {
        // Set selection tool as the default
        context.getToolManager().setActiveTool(toolController.getSelectionTool());
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

            // Check if window close was requested
            if (window.shouldClose() && !exitRequested) {
                requestExit();
                // Cancel the window close to show confirmation first
                glfwSetWindowShouldClose(window.getWindowHandle(), false);
            }

            if (window.isMinimized()) {
                sleep(100);
                continue;
            }

            update();
            render();

            window.swapBuffers();

            // FIX: Set Scene focus on first frame
            if (isFirstFrame) {
                isFirstFrame = false;
                // Focus will be set after first render
            }
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

        // Process shortcuts (after newFrame, before ImGui windows)
        ShortcutRegistry.getInstance().processShortcuts(ShortcutContext.current());

        // Setup docking and render UI
        uiController.setupDocking();
        uiController.renderUI();

        // Render exit confirmation popup
        renderExitConfirmation();

        // End ImGui frame
        imGuiLayer.render();

        // FIX: Focus Scene window on first render (after ImGui windows exist)
        if (!isFirstFrame && ImGui.getFrameCount() == 2) {
            ImGui.setWindowFocus("Scene");
        }
    }

    /**
     * Request exit - shows confirmation if scene is dirty
     */
    private void requestExit() {
        EditorScene scene = context.getCurrentScene();
        if (scene != null && scene.isDirty()) {
            showExitConfirmation = true;
            exitRequested = true;
        } else {
            context.requestExit();
        }
    }

    /**
     * Render exit confirmation dialog
     */
    private void renderExitConfirmation() {
        if (!showExitConfirmation) {
            return;
        }

        ImGui.openPopup("Exit Editor?");

        ImGui.setNextWindowSize(400, 0);
        if (ImGui.beginPopupModal("Exit Editor?", ImGuiWindowFlags.AlwaysAutoResize)) {
            EditorScene scene = context.getCurrentScene();
            
            ImGui.text("Scene '" + scene.getName() + "' has unsaved changes.");
            ImGui.spacing();
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "Do you want to save before exiting?");
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            // Save and Exit
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1.0f);
            if (ImGui.button("Save and Exit", 120, 0)) {
                sceneController.saveScene();
                context.requestExit();
                showExitConfirmation = false;
                exitRequested = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.popStyleColor(2);

            ImGui.sameLine();

            // Exit without Saving
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.2f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.3f, 0.3f, 1.0f);
            if (ImGui.button("Exit without Saving", 160, 0)) {
                context.requestExit();
                showExitConfirmation = false;
                exitRequested = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.popStyleColor(2);

            ImGui.sameLine();

            // Cancel
            if (ImGui.button("Cancel", 80, 0)) {
                showExitConfirmation = false;
                exitRequested = false;
                ImGui.closeCurrentPopup();
            }

            // ESC to cancel
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                showExitConfirmation = false;
                exitRequested = false;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Updates the menu bar's recent scenes list from the config.
     * Converts relative paths to full paths for opening.
     */
    private void updateMenuRecentScenes() {
        EditorConfig config = context.getConfig();
        // Convert relative paths to full paths for the menu bar
        String[] recentArray = config.getRecentScenes().stream()
                .map(config::toFullPath)
                .toArray(String[]::new);
        uiController.getMenuBar().setRecentFiles(recentArray);
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

        if (context != null) {
            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                scene.destroy();
            }
        }

        TilesetRegistry.destroy();
        FileDialogs.cleanup();

        // Destroy audio systems
        EditorAudio.destroy();
        Audio.destroy();

        if (imGuiLayer != null) {
            imGuiLayer.destroy();
        }

        if (context != null) {
            EditorWindow window = context.getWindow();
            if (window != null) {
                window.destroy();
            }
        }

        System.out.println("Scene Editor shut down");
    }
}
