package com.pocket.rpg.editor;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.core.EditorWindow;
import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.core.ImGuiLayer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.EditorMenuBar;
import com.pocket.rpg.editor.ui.SceneViewport;
import com.pocket.rpg.editor.ui.StatusBar;
import imgui.ImGui;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import static org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.glClear;
import static org.lwjgl.opengl.GL33.glClearColor;

/**
 * Main application class for the PocketRPG Scene Editor.
 * <p>
 * Phase 1 Implementation:
 * - Fullscreen window with ImGui integration
 * - Free camera with pan/zoom controls
 * - File menu (New/Open/Save/Exit) with NFD dialogs
 * - Scene viewport with grid overlay
 * - Status bar
 * <p>
 * Entry point: {@link #main(String[])}
 */
public class EditorApplication {

    // Core systems
    private EditorConfig config;
    private EditorWindow window;
    private ImGuiLayer imGuiLayer;

    // Camera
    private EditorCamera camera;

    // Current scene
    private EditorScene currentScene;

    // UI Components
    private EditorMenuBar menuBar;
    private SceneViewport sceneViewport;
    private StatusBar statusBar;

    // State
    private boolean running = true;

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("    PocketRPG Scene Editor - Phase 1");
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

        // Load configuration
        config = EditorConfig.createDefault();

        // Create window
        window = new EditorWindow(config);
        window.init();
        window.setOnResize(this::onWindowResize);

        // Initialize ImGui
        imGuiLayer = new ImGuiLayer();
        imGuiLayer.init(window.getWindowHandle(), true);

        // Create camera
        camera = new EditorCamera(config);
        camera.setViewportSize(window.getWidth(), window.getHeight());

        // Create scene
        currentScene = new EditorScene("Untitled");

        // Create UI components
        createUIComponents();

        System.out.println("Scene Editor initialized successfully");
    }

    private void createUIComponents() {
        // Menu bar
        menuBar = new EditorMenuBar();
        menuBar.setCurrentScene(currentScene);
        menuBar.setOnNewScene(this::newScene);
        menuBar.setOnOpenScene(this::openScene);
        menuBar.setOnSaveScene(this::saveScene);
        menuBar.setOnSaveSceneAs(this::saveSceneAs);
        menuBar.setOnExit(this::requestExit);

        // Scene viewport
        sceneViewport = new SceneViewport(camera, config);

        // Status bar
        statusBar = new StatusBar();
        statusBar.setCamera(camera);
        statusBar.setCurrentScene(currentScene);
    }

    private void loop() {
        System.out.println("Entering main loop...");

        while (running && !window.shouldClose()) {
            // Poll events
            window.pollEvents();

            // Skip rendering if minimized
            if (window.isMinimized()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            // Update
            update();

            // Render
            render();

            // Swap buffers
            window.swapBuffers();
        }

        System.out.println("Exited main loop");
    }

    private void update() {
        // Update scene (animations, etc.)
        float deltaTime = ImGui.getIO().getDeltaTime();
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
    }

    private void render() {
        // Clear screen
        glClearColor(
                config.getClearColor().x,
                config.getClearColor().y,
                config.getClearColor().z,
                config.getClearColor().w
        );
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Begin ImGui frame
        imGuiLayer.newFrame();

        // Process keyboard shortcuts
        menuBar.processShortcuts();

        // Setup docking
        setupDocking();

        // Render UI
        renderUI();

        // End ImGui frame
        imGuiLayer.render();
    }

    private void setupDocking() {
        // Create fullscreen docking space
        int windowFlags = ImGuiWindowFlags.MenuBar |
                ImGuiWindowFlags.NoDocking |
                ImGuiWindowFlags.NoTitleBar |
                ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove |
                ImGuiWindowFlags.NoBringToFrontOnFocus |
                ImGuiWindowFlags.NoNavFocus |
                ImGuiWindowFlags.NoBackground;

        ImGui.setNextWindowPos(0, 0);
        ImGui.setNextWindowSize(window.getWidth(), window.getHeight());

        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);

        ImGui.begin("##DockSpace", windowFlags);
        ImGui.popStyleVar(3);

        // Create dockspace
        int dockspaceId = ImGui.getID("EditorDockSpace");
        ImGui.dockSpace(dockspaceId, 0, 0, ImGuiDockNodeFlags.PassthruCentralNode);

        ImGui.end();
    }

    private void renderUI() {
        // Menu bar
        menuBar.render();

        // Scene viewport (central panel)
        sceneViewport.render();

        // Placeholder panels for future phases
        renderPlaceholderPanels();

        // Status bar
        statusBar.render(window.getHeight());

        // Demo window for testing (can be removed)
        // ImGui.showDemoWindow();
    }

    private void renderPlaceholderPanels() {
        // Hierarchy panel (placeholder)
        if (ImGui.begin("Hierarchy")) {
            ImGui.text("Scene Hierarchy");
            ImGui.separator();
            ImGui.textDisabled("(Coming in Phase 2)");

            if (currentScene != null) {
                ImGui.text("Scene: " + currentScene.getName());
                ImGui.text("Objects: " + currentScene.getObjectCount());
            }
        }
        ImGui.end();

        // Inspector panel (placeholder)
        if (ImGui.begin("Inspector")) {
            ImGui.text("Inspector");
            ImGui.separator();
            ImGui.textDisabled("(Coming in Phase 5)");
            ImGui.textDisabled("Select an object to inspect");
        }
        ImGui.end();

        // Tileset panel (placeholder)
        if (ImGui.begin("Tileset Palette")) {
            ImGui.text("Tileset Palette");
            ImGui.separator();
            ImGui.textDisabled("(Coming in Phase 3)");
            ImGui.textDisabled("Load a tileset to begin painting");
        }
        ImGui.end();

        // Layers panel (placeholder)
        if (ImGui.begin("Layers")) {
            ImGui.text("Layers");
            ImGui.separator();
            ImGui.textDisabled("(Coming in Phase 3)");
            ImGui.text("Ground Layer");
            ImGui.text("Objects Layer");
            ImGui.text("Collision Layer");
        }
        ImGui.end();
    }

    // ========================================================================
    // SCENE OPERATIONS
    // ========================================================================

    private void newScene() {
        System.out.println("Creating new scene...");

        if (currentScene != null) {
            currentScene.destroy();
        }

        currentScene = new EditorScene("Untitled");
        menuBar.setCurrentScene(currentScene);
        statusBar.setCurrentScene(currentScene);
        camera.reset();

        statusBar.showMessage("New scene created");
    }

    private void openScene(String path) {
        System.out.println("Opening scene: " + path);

        // TODO: Implement scene loading in Phase 2
        // For now, just create a new scene with the filename

        if (currentScene != null) {
            currentScene.destroy();
        }

        currentScene = new EditorScene();
        currentScene.setFilePath(path);

        // Extract name from path
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = lastSep >= 0 ? path.substring(lastSep + 1) : path;
        if (fileName.endsWith(".scene")) {
            fileName = fileName.substring(0, fileName.length() - 6);
        }
        currentScene.setName(fileName);

        menuBar.setCurrentScene(currentScene);
        statusBar.setCurrentScene(currentScene);
        camera.reset();

        statusBar.showMessage("Opened: " + fileName);
    }

    private void saveScene() {
        if (currentScene == null || currentScene.getFilePath() == null) {
            return;
        }

        System.out.println("Saving scene: " + currentScene.getFilePath());

        // TODO: Implement scene saving in Phase 2

        currentScene.clearDirty();
        statusBar.showMessage("Saved: " + currentScene.getName());
    }

    private void saveSceneAs(String path) {
        if (currentScene == null) {
            return;
        }

        System.out.println("Saving scene as: " + path);

        currentScene.setFilePath(path);

        // Extract name from path
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = lastSep >= 0 ? path.substring(lastSep + 1) : path;
        if (fileName.endsWith(".scene")) {
            fileName = fileName.substring(0, fileName.length() - 6);
        }
        currentScene.setName(fileName);

        // TODO: Implement scene saving in Phase 2

        currentScene.clearDirty();
        menuBar.setCurrentScene(currentScene);
        statusBar.showMessage("Saved: " + fileName);
    }

    private void requestExit() {
        running = false;
    }

    // ========================================================================
    // EVENT HANDLERS
    // ========================================================================

    private void onWindowResize() {
        camera.setViewportSize(window.getWidth(), window.getHeight());
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    private void destroy() {
        System.out.println("Shutting down Scene Editor...");

        if (currentScene != null) {
            currentScene.destroy();
        }

        FileDialogs.cleanup();

        if (imGuiLayer != null) {
            imGuiLayer.destroy();
        }

        if (window != null) {
            window.destroy();
        }

        System.out.println("Scene Editor shut down");
    }
}
