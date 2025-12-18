package com.pocket.rpg.editor;

import com.pocket.rpg.editor.panels.CollisionPanel;
import com.pocket.rpg.editor.panels.LayerPanel;
import com.pocket.rpg.editor.panels.TilesetPalettePanel;
import com.pocket.rpg.editor.rendering.CollisionOverlayRenderer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.EditorMenuBar;
import com.pocket.rpg.editor.ui.SceneViewToolbar;
import com.pocket.rpg.editor.ui.SceneViewport;
import com.pocket.rpg.editor.ui.StatusBar;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;

/**
 * Orchestrates all UI rendering: docking, viewport, panels, overlays.
 * <p>
 * Coordinates between different UI components and handles mode-specific
 * panel visibility.
 */
public class EditorUIController {

    private final EditorContext context;
    private final EditorToolController toolController;

    // Viewport and toolbar
    @Getter
    private SceneViewport sceneViewport;

    @Getter
    private SceneViewToolbar sceneToolbar;

    // Panels
    @Getter
    private LayerPanel layerPanel;

    @Getter
    private TilesetPalettePanel tilesetPalette;

    @Getter
    private CollisionPanel collisionPanel;

    // Menu and status
    @Getter
    private EditorMenuBar menuBar;

    @Getter
    private StatusBar statusBar;

    // Renderers
    private CollisionOverlayRenderer collisionOverlay;

    public EditorUIController(EditorContext context, EditorToolController toolController) {
        this.context = context;
        this.toolController = toolController;
    }

    /**
     * Initializes all UI components.
     */
    public void init() {
        // Create viewport
        sceneViewport = new SceneViewport(context.getCamera(), context.getConfig());
        sceneViewport.init(context.getWindow().getWidth(), context.getWindow().getHeight());
        sceneViewport.setToolManager(context.getToolManager());

        // Create toolbar
        sceneToolbar = new SceneViewToolbar(context, toolController);

        // Create collision overlay
        collisionOverlay = new CollisionOverlayRenderer();

        // Create panels
        createPanels();

        // Create menu and status bar
        createMenuAndStatus();

        // Wire toolbar message callback
        sceneToolbar.setMessageCallback(statusBar::showMessage);
    }

    private void createPanels() {
        EditorScene scene = context.getCurrentScene();

        // Layer panel
        layerPanel = new LayerPanel();
        layerPanel.setScene(scene);
        layerPanel.setModeManager(context.getModeManager());

        // Tileset palette
        tilesetPalette = new TilesetPalettePanel(context.getToolManager());
        tilesetPalette.setScene(scene);
        tilesetPalette.setBrushTool(toolController.getBrushTool());
        tilesetPalette.setFillTool(toolController.getFillTool());
        tilesetPalette.setRectangleTool(toolController.getRectangleTool());

        // Collision panel
        collisionPanel = new CollisionPanel();
        collisionPanel.setScene(scene);
        collisionPanel.setModeManager(context.getModeManager());
        collisionPanel.setBrushTool(toolController.getCollisionBrushTool());
        collisionPanel.setEraserTool(toolController.getCollisionEraserTool());
        collisionPanel.setFillTool(toolController.getCollisionFillTool());
        collisionPanel.setRectangleTool(toolController.getCollisionRectangleTool());
        collisionPanel.setPickerTool(toolController.getCollisionPickerTool());

        // Give tool controller reference to collision panel
        toolController.setCollisionPanel(collisionPanel);
    }

    private void createMenuAndStatus() {
        menuBar = new EditorMenuBar();
        menuBar.setCurrentScene(context.getCurrentScene());

        statusBar = new StatusBar();
        statusBar.setCamera(context.getCamera());
        statusBar.setCurrentScene(context.getCurrentScene());
    }

    /**
     * Updates panel references when scene changes.
     */
    public void updateSceneReferences(EditorScene scene) {
        layerPanel.setScene(scene);
        tilesetPalette.setScene(scene);
        collisionPanel.setScene(scene);
        menuBar.setCurrentScene(scene);
        statusBar.setCurrentScene(scene);
    }

    /**
     * Sets up the docking layout.
     */
    public void setupDocking() {
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
        ImGui.setNextWindowSize(context.getWindow().getWidth(), context.getWindow().getHeight());

        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);

        ImGui.begin("DockSpace", windowFlags);
        ImGui.popStyleVar(3);

        int dockspaceId = ImGui.getID("MainDockSpace");
        ImGui.dockSpace(dockspaceId, 0, 0, ImGuiDockNodeFlags.PassthruCentralNode);

        ImGui.end();
    }

    /**
     * Renders all UI components.
     */
    public void renderUI() {
        // Menu bar with mode selection and scene name
        renderMenuBar();

        // Scene viewport with integrated toolbar
        renderSceneViewport();

        // Collision overlay
        renderCollisionOverlay();

        // Tool overlay
        sceneViewport.renderToolOverlay();

        // Layer panel (always visible, but disabled in collision mode)
        layerPanel.render();

        // Mode-specific panels
        renderModePanels();

        // Placeholder panels
        renderPlaceholderPanels();

        // Status bar at bottom
        statusBar.render(context.getWindow().getHeight());
    }

    /**
     * Renders the scene viewport window with integrated toolbar.
     */
    private void renderSceneViewport() {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

        ImGui.begin("Scene", flags);

        // Render toolbar at top of viewport
        float viewportWidth = ImGui.getContentRegionAvailX();
        sceneToolbar.render(viewportWidth);

        ImGui.separator();

        // Update viewport grid visibility from toolbar
        sceneViewport.setShowGrid(sceneToolbar.isShowGrid());

        // Render viewport content (handles its own tracking)
        sceneViewport.renderContent();

        ImGui.end();
    }

    /**
     * Renders the menu bar with mode selection and scene name.
     */
    private void renderMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            // File menu
            menuBar.renderFileMenu();

            // Edit menu (placeholder)
            if (ImGui.beginMenu("Edit")) {
                ImGui.menuItem("Undo", "Ctrl+Z", false, false);
                ImGui.menuItem("Redo", "Ctrl+Y", false, false);
                ImGui.endMenu();
            }

            // View menu
            renderViewMenu();

            // Mode menu
            renderModeMenu();

            // Spacer - push scene name to right
            float availableWidth = ImGui.getContentRegionAvailX();
            EditorScene scene = context.getCurrentScene();
            String sceneName = scene != null ? scene.getDisplayName() : "No Scene";
            float textWidth = ImGui.calcTextSize(sceneName).x;

            // Position scene name on the right side with some padding
            float padding = 20;
            ImGui.setCursorPosX(ImGui.getCursorPosX() + availableWidth - textWidth - padding);

            // Scene name with dirty indicator (already included in getDisplayName())
            if (scene != null && scene.isDirty()) {
                ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, sceneName);
            } else {
                ImGui.textDisabled(sceneName);
            }

            ImGui.endMainMenuBar();
        }
    }

    private void renderViewMenu() {
        if (ImGui.beginMenu("View")) {
            // Grid toggle
            boolean showGrid = sceneToolbar.isShowGrid();
            if (ImGui.checkbox("Show Grid", showGrid)) {
                sceneToolbar.setShowGrid(!showGrid);
            }

            // Collision overlay toggle
            EditorScene scene = context.getCurrentScene();
            boolean collisionVisible = scene != null && scene.isCollisionVisible();
            if (ImGui.checkbox("Collision Overlay", collisionVisible)) {
                if (scene != null) {
                    scene.setCollisionVisible(!collisionVisible);
                }
            }

            ImGui.separator();

            // Reset camera
            if (ImGui.menuItem("Reset Camera", "Home")) {
                context.getCamera().reset();
            }

            ImGui.endMenu();
        }
    }

    private void renderModeMenu() {
        if (ImGui.beginMenu("Mode")) {
            EditorModeManager modeManager = context.getModeManager();
            boolean isTilemap = modeManager.isTilemapMode();
            boolean isCollision = modeManager.isCollisionMode();

            if (ImGui.menuItem("Tilemap Mode", "M", isTilemap)) {
                modeManager.switchToTilemap();
                context.getToolManager().setActiveTool(toolController.getBrushTool());
                statusBar.showMessage("Switched to Tilemap Mode");
            }
            if (ImGui.menuItem("Collision Mode", "N", isCollision)) {
                modeManager.switchToCollision();
                context.getToolManager().setActiveTool(toolController.getCollisionBrushTool());
                toolController.syncCollisionZLevels();
                statusBar.showMessage("Switched to Collision Mode");
            }
            ImGui.endMenu();
        }
    }

    /**
     * Renders collision overlay if visible.
     */
    private void renderCollisionOverlay() {
        EditorScene scene = context.getCurrentScene();

        if (scene == null || (!scene.isCollisionVisible() && !context.getModeManager().isCollisionMode())) {
            return;
        }

        collisionOverlay.setViewportBounds(
                sceneViewport.getViewportX(),
                sceneViewport.getViewportY(),
                sceneViewport.getViewportWidth(),
                sceneViewport.getViewportHeight()
        );
        collisionOverlay.setOpacity(scene.getCollisionOpacity());
        collisionOverlay.setZLevel(scene.getCollisionZLevel());
        collisionOverlay.render(scene.getCollisionMap(), context.getCamera());
    }

    /**
     * Renders panels based on current mode.
     */
    private void renderModePanels() {
        if (context.getModeManager().isTilemapMode()) {
            tilesetPalette.render();
        } else {
            collisionPanel.render();
        }
    }

    /**
     * Renders placeholder panels.
     */
    private void renderPlaceholderPanels() {
        // Hierarchy panel
        if (ImGui.begin("Hierarchy")) {
            ImGui.text("Scene Hierarchy");
            ImGui.separator();
            ImGui.textDisabled("(Coming in Phase 5)");

            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                ImGui.text("Scene: " + scene.getName());
                ImGui.text("Layers: " + scene.getLayerCount());
                ImGui.text("Collision Tiles: " + scene.getCollisionMap().getTileCount());
            }
        }
        ImGui.end();

        // Inspector panel
        if (ImGui.begin("Inspector")) {
            ImGui.text("Inspector");
            ImGui.separator();
            ImGui.textDisabled("(Coming in Phase 5)");
            ImGui.textDisabled("Select an object to inspect");
        }
        ImGui.end();
    }

    /**
     * Handles window resize.
     */
    public void onWindowResize(int width, int height) {
        context.getCamera().setViewportSize(width, height);
    }

    /**
     * Gets the framebuffer from the viewport.
     */
    public com.pocket.rpg.editor.rendering.EditorFramebuffer getFramebuffer() {
        return sceneViewport.getFramebuffer();
    }

    /**
     * Destroys UI resources.
     */
    public void destroy() {
        if (sceneViewport != null) {
            sceneViewport.destroy();
        }
    }
}