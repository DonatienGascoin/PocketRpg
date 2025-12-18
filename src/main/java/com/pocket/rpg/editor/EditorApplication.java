package com.pocket.rpg.editor;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.core.EditorWindow;
import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.core.ImGuiLayer;
import com.pocket.rpg.editor.panels.CollisionPanel;
import com.pocket.rpg.editor.panels.LayerPanel;
import com.pocket.rpg.editor.panels.TilesetPalettePanel;
import com.pocket.rpg.editor.rendering.CollisionOverlayRenderer;
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
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;

import static org.lwjgl.opengl.GL33.*;

/**
 * Main application class for the PocketRPG Scene Editor.
 * <p>
 * Phase 4 Part 2: Added collision editing tools and UI
 * - Collision brush, eraser, fill, rectangle, picker tools
 * - Collision overlay renderer
 * - Collision panel for type selection
 * - Editor mode switching (Tilemap/Collision)
 * <p>
 * FIXED: All collision tools properly wired, z-level synchronized,
 * picker callback connected, visibility tied to scene state.
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
    private CollisionOverlayRenderer collisionOverlay;

    // Tools
    private ToolManager toolManager;

    // Tilemap tools
    private TileBrushTool brushTool;
    private TileEraserTool eraserTool;
    private TileFillTool fillTool;
    private TileRectangleTool rectangleTool;
    private TilePickerTool pickerTool;

    // Collision tools
    private CollisionBrushTool collisionBrushTool;
    private CollisionEraserTool collisionEraserTool;
    private CollisionFillTool collisionFillTool;
    private CollisionRectangleTool collisionRectangleTool;
    private CollisionPickerTool collisionPickerTool;

    // UI Components
    private EditorMenuBar menuBar;
    private SceneViewport sceneViewport;
    private StatusBar statusBar;
    private LayerPanel layerPanel;
    private TilesetPalettePanel tilesetPalette;
    private CollisionPanel collisionPanel;

    // Editor mode
    private enum EditorMode {
        TILEMAP,
        COLLISION
    }
    private EditorMode currentMode = EditorMode.TILEMAP;

    // UI state - visibility now driven by scene state
    private final ImBoolean showCollisionPanel = new ImBoolean(false);

    // State
    private boolean running = true;
    private boolean firstFrame = true;

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("    PocketRPG Scene Editor - Phase 4");
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

        // Create collision overlay renderer
        collisionOverlay = new CollisionOverlayRenderer();

        // Create tools
        createTools();

        // Create UI components
        createUIComponents();

        System.out.println("Scene Editor initialized successfully");
    }

    private void createTools() {
        toolManager = new ToolManager();

        // Tilemap tools
        brushTool = new TileBrushTool(currentScene);
        toolManager.registerTool(brushTool);

        eraserTool = new TileEraserTool(currentScene);
        toolManager.registerTool(eraserTool);

        fillTool = new TileFillTool(currentScene);
        toolManager.registerTool(fillTool);

        rectangleTool = new TileRectangleTool(currentScene);
        toolManager.registerTool(rectangleTool);

        pickerTool = new TilePickerTool(currentScene);
        toolManager.registerTool(pickerTool);

        // Collision tools
        collisionBrushTool = new CollisionBrushTool(currentScene);
        toolManager.registerTool(collisionBrushTool);

        collisionEraserTool = new CollisionEraserTool(currentScene);
        toolManager.registerTool(collisionEraserTool);

        collisionFillTool = new CollisionFillTool(currentScene);
        toolManager.registerTool(collisionFillTool);

        collisionRectangleTool = new CollisionRectangleTool(currentScene);
        toolManager.registerTool(collisionRectangleTool);

        collisionPickerTool = new CollisionPickerTool(currentScene);
        toolManager.registerTool(collisionPickerTool);

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

        // Collision panel - wire ALL collision tools
        collisionPanel = new CollisionPanel();
        collisionPanel.setScene(currentScene);
        collisionPanel.setBrushTool(collisionBrushTool);
        collisionPanel.setEraserTool(collisionEraserTool);  // FIX: was missing
        collisionPanel.setFillTool(collisionFillTool);
        collisionPanel.setRectangleTool(collisionRectangleTool);
        collisionPanel.setPickerTool(collisionPickerTool);  // FIX: was missing

        // Setup tile picker callback
        pickerTool.setOnTilesPicked(selection -> {
            brushTool.setSelection(selection);
            fillTool.setSelection(selection);
            rectangleTool.setSelection(selection);
            tilesetPalette.setExternalSelection(selection);
            toolManager.setActiveTool(brushTool);
            statusBar.showMessage("Picked tiles - switched to Brush");
        });

        // Setup collision picker callback - FIX: wire to CollisionPanel
        collisionPickerTool.setOnCollisionPicked(type -> {
            collisionPanel.setSelectedType(type);
            toolManager.setActiveTool(collisionBrushTool);  // Switch to brush after picking
            statusBar.showMessage("Picked collision: " + type.getDisplayName() + " - switched to Brush");
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

        // FIX: Update collision overlay from scene state (not separate ImBoolean)
        if (currentScene != null) {
            collisionOverlay.setVisible(currentScene.isCollisionVisible());
            collisionOverlay.setOpacity(currentScene.getCollisionOpacity());
            collisionOverlay.setZLevel(currentScene.getCollisionZLevel());
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
        if (ImGui.getIO().getWantTextInput()) return;

        // Mode switching
        if (ImGui.isKeyPressed(ImGuiKey.M)) {
            switchToTilemapMode();
        }
        if (ImGui.isKeyPressed(ImGuiKey.N)) {
            switchToCollisionMode();
        }

        // Tool shortcuts - depend on current mode
        if (currentMode == EditorMode.TILEMAP) {
            // Tilemap tools (existing shortcuts)
            if (ImGui.isKeyPressed(ImGuiKey.B)) {
                toolManager.setActiveTool("Brush");
                statusBar.showMessage("Tile Brush");
            }
            if (ImGui.isKeyPressed(ImGuiKey.E)) {
                toolManager.setActiveTool("Eraser");
                statusBar.showMessage("Tile Eraser");
            }
            if (ImGui.isKeyPressed(ImGuiKey.F)) {
                toolManager.setActiveTool("Fill");
                statusBar.showMessage("Tile Fill");
            }
            if (ImGui.isKeyPressed(ImGuiKey.R)) {
                toolManager.setActiveTool("Rectangle");
                statusBar.showMessage("Tile Rectangle");
            }
            if (ImGui.isKeyPressed(ImGuiKey.I)) {
                toolManager.setActiveTool("Picker");
                statusBar.showMessage("Tile Picker");
            }

            // Brush size adjustment
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
        } else if (currentMode == EditorMode.COLLISION) {
            // Collision tools (new shortcuts)
            if (ImGui.isKeyPressed(ImGuiKey.C)) {
                toolManager.setActiveTool("Collision Brush");
                statusBar.showMessage("Collision Brush");
            }
            if (ImGui.isKeyPressed(ImGuiKey.X)) {
                toolManager.setActiveTool("Collision Eraser");
                statusBar.showMessage("Collision Eraser");
            }
            if (ImGui.isKeyPressed(ImGuiKey.G)) {
                toolManager.setActiveTool("Collision Fill");
                statusBar.showMessage("Collision Fill");
            }
            if (ImGui.isKeyPressed(ImGuiKey.H)) {
                toolManager.setActiveTool("Collision Rectangle");
                statusBar.showMessage("Collision Rectangle");
            }
            if (ImGui.isKeyPressed(ImGuiKey.V)) {
                toolManager.setActiveTool("Collision Picker");
                statusBar.showMessage("Collision Picker");
            }

            // Brush size adjustment (collision)
            if (ImGui.isKeyPressed(ImGuiKey.Minus) || ImGui.isKeyPressed(ImGuiKey.KeypadSubtract)) {
                int size = collisionBrushTool.getBrushSize();
                if (size > 1) {
                    collisionBrushTool.setBrushSize(size - 1);
                    collisionEraserTool.setEraserSize(size - 1);
                    statusBar.showMessage("Collision Brush Size: " + (size - 1));
                }
            }
            if (ImGui.isKeyPressed(ImGuiKey.Equal) || ImGui.isKeyPressed(ImGuiKey.KeypadAdd)) {
                int size = collisionBrushTool.getBrushSize();
                if (size < 10) {
                    collisionBrushTool.setBrushSize(size + 1);
                    collisionEraserTool.setEraserSize(size + 1);
                    statusBar.showMessage("Collision Brush Size: " + (size + 1));
                }
            }

            // Z-level shortcuts ([ and ])
            if (ImGui.isKeyPressed(ImGuiKey.LeftBracket)) {
                int z = currentScene.getCollisionZLevel();
                if (z > 0) {
                    currentScene.setCollisionZLevel(z - 1);
                    syncToolZLevels();
                    statusBar.showMessage("Z-Level: " + (z - 1));
                }
            }
            if (ImGui.isKeyPressed(ImGuiKey.RightBracket)) {
                int z = currentScene.getCollisionZLevel();
                if (z < 3) {
                    currentScene.setCollisionZLevel(z + 1);
                    syncToolZLevels();
                    statusBar.showMessage("Z-Level: " + (z + 1));
                }
            }
        }
    }

    /**
     * Synchronizes Z-level from scene to all collision tools.
     */
    private void syncToolZLevels() {
        if (currentScene == null) return;
        int z = currentScene.getCollisionZLevel();
        collisionBrushTool.setZLevel(z);
        collisionEraserTool.setZLevel(z);
        collisionFillTool.setZLevel(z);
        collisionRectangleTool.setZLevel(z);
        collisionPickerTool.setZLevel(z);
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

        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);

        ImGui.begin("DockSpace", windowFlags);
        ImGui.popStyleVar(3);

        // DockSpace
        int dockspaceId = ImGui.getID("MainDockSpace");
        ImGui.dockSpace(dockspaceId, 0, 0, ImGuiDockNodeFlags.PassthruCentralNode);

        ImGui.end();
    }

    private void renderUI() {
        // Menu bar (with mode selection)
        renderMenuBar();

        // Scene viewport (central panel)
        sceneViewport.render();

        // Render collision overlay if visible
        if (currentScene != null && currentScene.isCollisionVisible()) {
            renderCollisionOverlay();
        }

        // Tool overlays (rendered after scene, uses ImGui draw lists)
        sceneViewport.renderToolOverlay();

        // Layer panel
        layerPanel.render();

        // Mode-specific panels
        if (currentMode == EditorMode.TILEMAP) {
            tilesetPalette.render();
        } else if (currentMode == EditorMode.COLLISION) {
            collisionPanel.render();
        }

        // Tool settings panel with buttons
        renderToolPanel();

        // Placeholder panels
        renderPlaceholderPanels();

        // Status bar
        statusBar.render(window.getHeight());
    }

    /**
     * Renders the menu bar with mode selection.
     */
    private void renderMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            // File menu (delegate to EditorMenuBar)
            menuBar.renderFileMenu();

            // View menu
            if (ImGui.beginMenu("View")) {
                if (currentMode == EditorMode.COLLISION) {
                    // Sync checkbox with scene state
                    boolean visible = currentScene != null && currentScene.isCollisionVisible();
                    if (ImGui.checkbox("Collision Overlay", visible)) {
                        if (currentScene != null) {
                            currentScene.setCollisionVisible(!visible);
                        }
                    }
                }
                ImGui.endMenu();
            }

            // Mode menu
            if (ImGui.beginMenu("Mode")) {
                boolean isTilemap = currentMode == EditorMode.TILEMAP;
                boolean isCollision = currentMode == EditorMode.COLLISION;

                if (ImGui.menuItem("Tilemap Mode", "M", isTilemap)) {
                    switchToTilemapMode();
                }
                if (ImGui.menuItem("Collision Mode", "N", isCollision)) {
                    switchToCollisionMode();
                }
                ImGui.endMenu();
            }

            // Show current mode indicator in menu bar
            ImGui.spacing();
            ImGui.spacing();
            String modeText = "Mode: " + (currentMode == EditorMode.TILEMAP ? "TILEMAP" : "COLLISION");
            ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, modeText);

            ImGui.endMainMenuBar();
        }
    }

    /**
     * Renders collision overlay on the viewport.
     */
    private void renderCollisionOverlay() {
        if (currentScene == null || currentScene.getCollisionMap() == null) {
            return;
        }

        collisionOverlay.setViewportBounds(
                sceneViewport.getViewportX(),
                sceneViewport.getViewportY(),
                sceneViewport.getViewportWidth(),
                sceneViewport.getViewportHeight()
        );

        collisionOverlay.render(currentScene.getCollisionMap(), camera);
    }

    /**
     * Renders the tool settings panel with tool buttons.
     */
    private void renderToolPanel() {
        if (ImGui.begin("Tools")) {
            // Mode indicator
            ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f,
                    currentMode == EditorMode.TILEMAP ? "TILEMAP MODE" : "COLLISION MODE");
            ImGui.separator();

            // Tool buttons - filter by current mode
            for (var tool : toolManager.getTools()) {
                boolean isTilemapTool = tool.getName().startsWith("Tile") || tool.getName().equals("Brush") ||
                        tool.getName().equals("Eraser") || tool.getName().equals("Fill") ||
                        tool.getName().equals("Rectangle") || tool.getName().equals("Picker");
                boolean isCollisionTool = tool.getName().startsWith("Collision");

                // Skip tools not matching current mode
                if (currentMode == EditorMode.TILEMAP && isCollisionTool) continue;
                if (currentMode == EditorMode.COLLISION && isTilemapTool) continue;

                boolean isActive = toolManager.getActiveTool() == tool;

                // Highlight active tool with different color
                if (isActive) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 1.0f, 1.0f);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 1.0f, 1.0f);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.5f, 0.9f, 1.0f);
                }

                String label = tool.getName();
                String shortcut = tool.getShortcutKey();
                if (shortcut != null) {
                    label += " (" + shortcut + ")";
                }

                if (ImGui.button(label, 150, 0)) {
                    toolManager.setActiveTool(tool);
                }

                if (isActive) {
                    ImGui.popStyleColor(3);
                }
            }

            ImGui.separator();

            // Tool-specific settings
            renderToolSettings();
        }
        ImGui.end();
    }

    /**
     * Renders settings for the active tool.
     */
    private void renderToolSettings() {
        EditorTool activeTool = toolManager.getActiveTool();

        if (currentMode == EditorMode.TILEMAP) {
            if (activeTool == brushTool) {
                ImGui.text("Brush Settings");
                var selection = brushTool.getSelection();
                if (selection == null) {
                    ImGui.textDisabled("No tile selected");
                } else if (selection.isPattern()) {
                    ImGui.text("Pattern: " + selection.getWidth() + "x" + selection.getHeight());
                } else {
                    ImGui.text("Tile: " + selection.getFirstTileIndex());
                    int[] size = {brushTool.getBrushSize()};
                    if (ImGui.sliderInt("Size", size, 1, 10)) {
                        brushTool.setBrushSize(size[0]);
                    }
                }
            } else if (activeTool == eraserTool) {
                ImGui.text("Eraser Settings");
                int[] size = {eraserTool.getEraserSize()};
                if (ImGui.sliderInt("Size", size, 1, 10)) {
                    eraserTool.setEraserSize(size[0]);
                }
            } else if (activeTool == fillTool) {
                ImGui.text("Fill Settings");
                ImGui.textDisabled("Click to flood fill");
            } else if (activeTool == rectangleTool) {
                ImGui.text("Rectangle Settings");
                ImGui.textDisabled("Drag to fill area");
            } else if (activeTool == pickerTool) {
                ImGui.text("Picker Settings");
                ImGui.textDisabled("Click: Pick tile");
                ImGui.textDisabled("Shift+Drag: Pick pattern");
            }

            ImGui.separator();
            ImGui.textDisabled("Shortcuts:");
            ImGui.textDisabled("M - Switch to Tilemap");
            ImGui.textDisabled("B - Brush, E - Eraser");
            ImGui.textDisabled("F - Fill, R - Rectangle");
            ImGui.textDisabled("I - Picker");
        } else if (currentMode == EditorMode.COLLISION) {
            if (activeTool == collisionBrushTool) {
                ImGui.text("Collision Brush");
                ImGui.text("Type: " + collisionPanel.getSelectedType().getDisplayName());
                int[] size = {collisionBrushTool.getBrushSize()};
                if (ImGui.sliderInt("Size", size, 1, 10)) {
                    collisionBrushTool.setBrushSize(size[0]);
                }
            } else if (activeTool == collisionEraserTool) {
                ImGui.text("Collision Eraser");
                int[] size = {collisionEraserTool.getEraserSize()};
                if (ImGui.sliderInt("Size", size, 1, 10)) {
                    collisionEraserTool.setEraserSize(size[0]);
                }
            } else if (activeTool == collisionFillTool) {
                ImGui.text("Collision Fill");
                ImGui.textDisabled("Click to flood fill");
                ImGui.textDisabled("Max: 2000 tiles");
            } else if (activeTool == collisionRectangleTool) {
                ImGui.text("Collision Rectangle");
                ImGui.textDisabled("Drag to fill area");
            } else if (activeTool == collisionPickerTool) {
                ImGui.text("Collision Picker");
                ImGui.textDisabled("Click to pick type");
            }

            ImGui.separator();
            ImGui.text("Z-Level: " + (currentScene != null ? currentScene.getCollisionZLevel() : 0));
            ImGui.textDisabled("[ / ] to change");

            ImGui.separator();
            ImGui.textDisabled("Shortcuts:");
            ImGui.textDisabled("N - Switch to Collision");
            ImGui.textDisabled("C - Brush, X - Eraser");
            ImGui.textDisabled("G - Fill, H - Rectangle");
            ImGui.textDisabled("V - Picker");
        }
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
                ImGui.text("Collision Tiles: " + currentScene.getCollisionMap().getTileCount());
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
    // MODE SWITCHING
    // ========================================================================

    private void switchToTilemapMode() {
        currentMode = EditorMode.TILEMAP;
        toolManager.setActiveTool(brushTool);
        showCollisionPanel.set(false);
        statusBar.showMessage("Switched to Tilemap Mode");
    }

    private void switchToCollisionMode() {
        currentMode = EditorMode.COLLISION;
        toolManager.setActiveTool(collisionBrushTool);
        showCollisionPanel.set(true);
        // Sync tool z-levels when entering collision mode
        syncToolZLevels();
        statusBar.showMessage("Switched to Collision Mode");
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

    private void openScene(String path) {
        System.out.println("Opening scene: " + path);

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
        collisionPanel.setScene(currentScene);

        // Update tilemap tools
        brushTool.setScene(currentScene);
        eraserTool.setScene(currentScene);
        fillTool.setScene(currentScene);
        rectangleTool.setScene(currentScene);
        pickerTool.setScene(currentScene);

        // Update collision tools
        collisionBrushTool.setScene(currentScene);
        collisionEraserTool.setScene(currentScene);
        collisionFillTool.setScene(currentScene);
        collisionRectangleTool.setScene(currentScene);
        collisionPickerTool.setScene(currentScene);

        tilesetPalette.setBrushTool(brushTool);

        // Sync z-levels from new scene
        syncToolZLevels();
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
