package com.pocket.rpg.editor;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.core.EditorWindow;
import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.core.ImGuiLayer;
import com.pocket.rpg.editor.panels.LayerPanel;
import com.pocket.rpg.editor.panels.TilesetPalettePanel;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.rendering.EditorSceneRenderer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.editor.tileset.TilesetRegistry;
import com.pocket.rpg.editor.tools.*;
import com.pocket.rpg.editor.ui.EditorMenuBar;
import com.pocket.rpg.editor.ui.SceneViewport;
import com.pocket.rpg.editor.ui.StatusBar;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.ErrorMode;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.serialization.Serializer;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImInt;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.glClear;
import static org.lwjgl.opengl.GL33.glClearColor;

/**
 * Main application class for the PocketRPG Scene Editor.
 * <p>
 * Phase 3c Implementation:
 * - TileFillTool for flood fill (2000 tile limit)
 * - TileRectangleTool for rectangle fill
 * - TilePickerTool for eyedropper (single + pattern picking)
 * - Tool shortcuts: B, E, F, R, I
 */
public class EditorApplication {

    // Core systems
    private EditorConfig config;
    private RenderingConfig renderingConfig;
    private EditorWindow window;
    private ImGuiLayer imGuiLayer;

    // Camera
    private EditorCamera camera;

    // Scene and rendering
    private EditorScene currentScene;
    private EditorSceneRenderer sceneRenderer;

    // Tools
    private ToolManager toolManager;
    private TileBrushTool brushTool;
    private TileEraserTool eraserTool;
    private TileFillTool fillTool;
    private TileRectangleTool rectangleTool;
    private TilePickerTool pickerTool;

    // UI Components
    private EditorMenuBar menuBar;
    private SceneViewport sceneViewport;
    private StatusBar statusBar;
    private LayerPanel layerPanel;
    private TilesetPalettePanel tilesetPalette;

    // State
    private boolean running = true;

    private boolean firstFrame = true;

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("    PocketRPG Scene Editor - Phase 3c");
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
        // Initialize Assets system
        Assets.initialize();
        Assets.configure()
                .setAssetRoot("gameData/assets/")
                .setErrorMode(ErrorMode.THROW_EXCEPTION)
                .apply();
        // Initialize serialization
        Serializer.init(Assets.getContext());

        // Load configuration
        config = EditorConfig.createDefault();
        ConfigLoader.loadAllConfigs();
        renderingConfig = ConfigLoader.getConfig(ConfigLoader.ConfigType.RENDERING);

        // Create window
        window = new EditorWindow(config);
        window.init();
        window.setOnResize(this::onWindowResize);

        // Initialize ImGui
        imGuiLayer = new ImGuiLayer();
        imGuiLayer.init(window.getWindowHandle(), true);


        // Initialize TilesetRegistry and load all .spritesheet files
        TilesetRegistry.initialize();
        TilesetRegistry.getInstance().scanAndLoad();

        // Create camera
        camera = new EditorCamera(config);
        camera.setViewportSize(window.getWidth(), window.getHeight());

        // Create scene
        currentScene = new EditorScene("Untitled");

        // Create viewport and framebuffer
        sceneViewport = new SceneViewport(camera, config);
        sceneViewport.init(window.getWidth(), window.getHeight());

        // Create scene renderer
        EditorFramebuffer framebuffer = sceneViewport.getFramebuffer();
        sceneRenderer = new EditorSceneRenderer(framebuffer, renderingConfig);
        sceneRenderer.init();

        // Create tools
        createTools();

        // Create UI components
        createUIComponents();

        System.out.println("Scene Editor initialized successfully");
    }

    private void createTools() {
        toolManager = new ToolManager();

        // Brush tool
        brushTool = new TileBrushTool(currentScene);
        toolManager.registerTool(brushTool);

        // Eraser tool
        eraserTool = new TileEraserTool(currentScene);
        toolManager.registerTool(eraserTool);

        // Fill tool
        fillTool = new TileFillTool(currentScene);
        toolManager.registerTool(fillTool);

        // Rectangle tool
        rectangleTool = new TileRectangleTool(currentScene);
        toolManager.registerTool(rectangleTool);

        // Picker tool
        pickerTool = new TilePickerTool(currentScene);
        toolManager.registerTool(pickerTool);

        // Set viewport's tool manager
        sceneViewport.setToolManager(toolManager);
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

        // Status bar
        statusBar = new StatusBar();
        statusBar.setCamera(camera);
        statusBar.setCurrentScene(currentScene);

        // Layer panel
        layerPanel = new LayerPanel();
        layerPanel.setScene(currentScene);

        // Tileset palette
        tilesetPalette = new TilesetPalettePanel();
        tilesetPalette.setScene(currentScene);
        tilesetPalette.setBrushTool(brushTool);
        tilesetPalette.setFillTool(fillTool);
        tilesetPalette.setRectangleTool(rectangleTool);

        // Setup picker callback to update brush tool and sync to other tools
        pickerTool.setOnTilesPicked(selection -> {
            brushTool.setSelection(selection);
            fillTool.setSelection(selection);
            rectangleTool.setSelection(selection);
            tilesetPalette.setExternalSelection(selection);
            toolManager.setActiveTool(brushTool);
            statusBar.showMessage("Picked tiles - switched to Brush");
        });
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
        float deltaTime = ImGui.getIO().getDeltaTime();

        // Update scene
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }

        // Update tools
        if (toolManager != null) {
            toolManager.update(deltaTime);
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

        // Render scene to framebuffer
        if (sceneRenderer != null && currentScene != null) {
            sceneRenderer.render(currentScene, camera);
        }

        // Begin ImGui frame
        imGuiLayer.newFrame();

        // Process keyboard shortcuts
        menuBar.processShortcuts();
        processToolShortcuts();

        // Setup docking
        setupDocking();

        // Render UI
        renderUI();

        // End ImGui frame
        imGuiLayer.render();
    }

    /**
     * Process tool switching shortcuts.
     */
    private void processToolShortcuts() {
        if (!sceneViewport.isFocused()) return;

        // Tool shortcuts
        if (ImGui.isKeyPressed(ImGuiKey.B)) {
            toolManager.setActiveTool("Brush");
            statusBar.showMessage("Brush Tool");
        }
        if (ImGui.isKeyPressed(ImGuiKey.E)) {
            toolManager.setActiveTool("Eraser");
            statusBar.showMessage("Eraser Tool");
        }
        if (ImGui.isKeyPressed(ImGuiKey.F)) {
            toolManager.setActiveTool("Fill");
            statusBar.showMessage("Fill Tool");
        }
        if (ImGui.isKeyPressed(ImGuiKey.R)) {
            toolManager.setActiveTool("Rectangle");
            statusBar.showMessage("Rectangle Tool");
        }
        if (ImGui.isKeyPressed(ImGuiKey.I)) {
            toolManager.setActiveTool("Picker");
            statusBar.showMessage("Picker Tool");
        }

        // Brush size adjustment with - and = (+ without shift)
        if (ImGui.isKeyPressed(ImGuiKey.Minus) || ImGui.isKeyPressed(ImGuiKey.KeypadSubtract)) {
            int size = brushTool.getBrushSize();
            if (size > 1) {
                brushTool.setBrushSize(size - 1);
                eraserTool.setEraserSize(size - 1);
                statusBar.showMessage("Brush Size: " + (size - 1));
            }
        }
        if (ImGui.isKeyPressed(ImGuiKey.Equal) || ImGui.isKeyPressed(ImGuiKey.KeypadAdd)) {
            int size = brushTool.getBrushSize();
            if (size < 10) {
                brushTool.setBrushSize(size + 1);
                eraserTool.setEraserSize(size + 1);
                statusBar.showMessage("Brush Size: " + (size + 1));
            }
        }
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

        if (firstFrame && !Files.exists(Path.of("editor/editor_layout.ini"))) {
            buildDefaultLayout(dockspaceId);
        }
        firstFrame = false;

        ImGui.end();


    }

    private void buildDefaultLayout(int dockspaceId) {
        System.out.println("No layout found, creating default");
        imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
        imgui.internal.ImGui.dockBuilderAddNode(
                dockspaceId,
                imgui.internal.flag.ImGuiDockNodeFlags.DockSpace
        );
        imgui.internal.ImGui.dockBuilderSetNodeSize(
                dockspaceId,
                window.getWidth(),
                window.getHeight()
        );

        // ─────────────────────────────
        // Split Left | Center+Right
        // ─────────────────────────────
        ImInt leftId = new ImInt();
        ImInt centerRightId = new ImInt();

        imgui.internal.ImGui.dockBuilderSplitNode(
                dockspaceId,
                ImGuiDir.Left,
                0.20f,
                leftId,
                centerRightId
        );

        // ─────────────────────────────
        // Split Center | Right
        // ─────────────────────────────
        ImInt rightId = new ImInt();
        ImInt centerId = new ImInt();

        imgui.internal.ImGui.dockBuilderSplitNode(
                centerRightId.get(),
                ImGuiDir.Right,
                0.35f,
                rightId,
                centerId
        );

        // ─────────────────────────────
        // LEFT: Hierarchy (top) / Inspector (bottom)
        // ─────────────────────────────
        ImInt leftTopId = new ImInt();
        ImInt leftBottomId = new ImInt();

        imgui.internal.ImGui.dockBuilderSplitNode(
                leftId.get(),
                ImGuiDir.Up,
                0.5f,
                leftTopId,
                leftBottomId
        );

        // ─────────────────────────────
        // RIGHT: Layers (top 25%) / Tileset (bottom)
        // ─────────────────────────────
        ImInt rightTopId = new ImInt();
        ImInt rightBottomId = new ImInt();

        imgui.internal.ImGui.dockBuilderSplitNode(
                rightId.get(),
                ImGuiDir.Up,
                0.25f,
                rightTopId,
                rightBottomId
        );

        // ─────────────────────────────
        // Dock windows
        // ─────────────────────────────
        imgui.internal.ImGui.dockBuilderDockWindow("Hierarchy", leftTopId.get());
        imgui.internal.ImGui.dockBuilderDockWindow("Inspector", leftBottomId.get());

        imgui.internal.ImGui.dockBuilderDockWindow("Scene", centerId.get());

        imgui.internal.ImGui.dockBuilderDockWindow("Layers", rightTopId.get());
        imgui.internal.ImGui.dockBuilderDockWindow("Tileset", rightBottomId.get());

        // Tools intentionally NOT docked

        imgui.internal.ImGui.dockBuilderFinish(dockspaceId);
    }


    private void renderUI() {
        // Menu bar
        menuBar.render();

        // Scene viewport (central panel)
        sceneViewport.render();

        // Tool overlays (rendered after scene, uses ImGui draw lists)
        sceneViewport.renderToolOverlay();

        // Layer panel
        layerPanel.render();

        // Tileset palette
        tilesetPalette.render();

        // Tool settings panel
        renderToolPanel();

        // Placeholder panels
        renderPlaceholderPanels();

        // Status bar
        statusBar.render(window.getHeight());
    }

    /**
     * Renders the tool settings panel.
     */
    private void renderToolPanel() {
        if (ImGui.begin("Tools")) {
            // Tool buttons
            for (var tool : toolManager.getTools()) {
                boolean isActive = toolManager.getActiveTool() == tool;

                if (isActive) {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.3f, 0.6f, 1.0f, 1.0f);
                }

                String label = tool.getName();
                String shortcut = tool.getShortcutKey();
                if (shortcut != null) {
                    label += " (" + shortcut + ")";
                }

                if (ImGui.button(label, 100, 0)) {
                    toolManager.setActiveTool(tool);
                }

                if (isActive) {
                    ImGui.popStyleColor();
                }
            }

            ImGui.separator();

            // Tool-specific settings
            if (toolManager.getActiveTool() == brushTool) {
                ImGui.text("Brush Settings");

                // Show selection info
                var selection = brushTool.getSelection();
                if (selection == null) {
                    ImGui.textDisabled("No tile selected");
                } else if (selection.isPattern()) {
                    ImGui.text("Pattern: " + selection.getWidth() + "x" + selection.getHeight());
                    ImGui.textDisabled("Click to stamp");
                } else {
                    ImGui.text("Tile: " + selection.getFirstTileIndex());

                    // Brush size only for single tile
                    int[] size = {brushTool.getBrushSize()};
                    if (ImGui.sliderInt("Size", size, 1, 10)) {
                        brushTool.setBrushSize(size[0]);
                    }
                }
            } else if (toolManager.getActiveTool() == eraserTool) {
                ImGui.text("Eraser Settings");

                int[] size = {eraserTool.getEraserSize()};
                if (ImGui.sliderInt("Size", size, 1, 10)) {
                    eraserTool.setEraserSize(size[0]);
                }
            } else if (toolManager.getActiveTool() == fillTool) {
                ImGui.text("Fill Settings");
                ImGui.textDisabled("Click to flood fill");
                ImGui.textDisabled("Max: 2000 tiles");
            } else if (toolManager.getActiveTool() == rectangleTool) {
                ImGui.text("Rectangle Settings");
                ImGui.textDisabled("Drag to define area");
                ImGui.textDisabled("Release to fill");
            } else if (toolManager.getActiveTool() == pickerTool) {
                ImGui.text("Picker Settings");
                ImGui.textDisabled("Click: Pick tile");
                ImGui.textDisabled("Shift+Drag: Pick pattern");
            }

            ImGui.separator();
            ImGui.textDisabled("Shortcuts:");
            ImGui.textDisabled("-/+ - Brush size");
            ImGui.textDisabled("B - Brush");
            ImGui.textDisabled("E - Eraser");
            ImGui.textDisabled("F - Fill");
            ImGui.textDisabled("R - Rectangle");
            ImGui.textDisabled("I - Picker");
        }
        ImGui.end();
    }

    private void renderPlaceholderPanels() {
        // Hierarchy panel (placeholder)
        if (ImGui.begin("Hierarchy")) {
            ImGui.text("Scene Hierarchy");
            ImGui.separator();
            ImGui.textDisabled("(Coming in Phase 5)");

            if (currentScene != null) {
                ImGui.text("Scene: " + currentScene.getName());
                ImGui.text("Layers: " + currentScene.getLayerCount());
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

        // Update references
        updateSceneReferences();

        camera.reset();
        statusBar.showMessage("New scene created");
    }

    /**
     * Called by the menuBar. It already handles dirty scene and empty path
     *
     * @param path Scene path to open
     */
    private void openScene(String path) {
        System.out.println("Opening scene: " + path);

        // TODO: Implement scene loading in Phase 2
        // For now, just create a new scene with the filename

        if (currentScene != null) {
            currentScene.destroy();
        }
        SceneData scene = Assets.load(path);
        currentScene = EditorSceneSerializer.fromSceneData(scene, path);

        // Update references
        updateSceneReferences();

        camera.reset();
        statusBar.showMessage("Opened: " + scene.getName());
    }

    private void saveScene() {
        if (currentScene == null || currentScene.getFilePath() == null) {
            return;
        }

        System.out.println("Saving scene: " + currentScene.getFilePath());

        try {
            SceneData data = EditorSceneSerializer.toSceneData(currentScene);
            Assets.persist(data, currentScene.getFilePath());

            currentScene.clearDirty();
            statusBar.showMessage("Saved: " + currentScene.getName());
        } catch (Exception e) {
            statusBar.showMessage("Error saving: " + e.getMessage());
            e.printStackTrace();
        }
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

        saveScene();
    }

    /**
     * Updates all components that reference the current scene.
     */
    private void updateSceneReferences() {
        menuBar.setCurrentScene(currentScene);
        statusBar.setCurrentScene(currentScene);
        layerPanel.setScene(currentScene);
        tilesetPalette.setScene(currentScene);

        // Update tools with new scene (don't recreate them)
        brushTool.setScene(currentScene);
        eraserTool.setScene(currentScene);
        fillTool.setScene(currentScene);
        rectangleTool.setScene(currentScene);
        pickerTool.setScene(currentScene);

        tilesetPalette.setBrushTool(brushTool);
    }

    private void requestExit() {
        running = false;
    }

    // ========================================================================
    // EVENT HANDLERS
    // ========================================================================

    private void onWindowResize() {
        camera.setViewportSize(window.getWidth(), window.getHeight());

        if (sceneRenderer != null) {
            sceneRenderer.onResize(window.getWidth(), window.getHeight());
        }
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    private void destroy() {
        System.out.println("Shutting down Scene Editor...");

        if (sceneRenderer != null) {
            sceneRenderer.destroy();
        }

        if (sceneViewport != null) {
            sceneViewport.destroy();
        }

        if (currentScene != null) {
            currentScene.destroy();
        }

        TilesetRegistry.destroy();
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