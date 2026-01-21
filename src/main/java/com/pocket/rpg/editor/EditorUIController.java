package com.pocket.rpg.editor;

import com.pocket.rpg.editor.panels.*;
import com.pocket.rpg.editor.rendering.CameraOverlayRenderer;
import com.pocket.rpg.editor.rendering.CollisionOverlayRenderer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.UIEntityFactory;
import com.pocket.rpg.editor.ui.*;
import imgui.ImGui;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;

/**
 * Orchestrates all UI rendering: docking, viewport, panels, overlays.
 */
public class EditorUIController {

    private final EditorContext context;
    private final EditorToolController toolController;
    private PlayModeController playModeController;

    @Getter
    private SceneViewport sceneViewport;

    @Getter
    private SceneViewToolbar sceneToolbar;

    @Getter
    private GameViewPanel gameViewPanel;

    @Getter
    private TilesetPalettePanel tilesetPalette;

    @Getter
    private CollisionPanel collisionPanel;

    @Getter
    private PrefabBrowserPanel prefabBrowserPanel;

    @Getter
    private HierarchyPanel hierarchyPanel;

    @Getter
    private UIDesignerPanel uiDesignerPanel;

    @Getter
    private InspectorPanel inspectorPanel;

    @Getter
    private ConfigPanel configPanel;

    @Getter
    private AssetBrowserPanel assetBrowserPanel;

    @Getter
    private AnimationEditorPanel animationEditorPanel;

    @Getter
    private SpriteEditorPanel spriteEditorPanel;

    @Getter
    private EditorMenuBar menuBar;

    @Getter
    private StatusBar statusBar;

    private CollisionOverlayRenderer collisionOverlay;
    private CameraOverlayRenderer cameraOverlayRenderer;

    public EditorUIController(EditorContext context, EditorToolController toolController) {
        this.context = context;
        this.toolController = toolController;
    }

    public void init() {
        sceneViewport = new SceneViewport(context.getCamera(), context.getConfig());
        sceneViewport.init(context.getWindow().getWidth(), context.getWindow().getHeight());
        sceneViewport.setToolManager(context.getToolManager());
        sceneViewport.setScene(context.getCurrentScene());

        sceneToolbar = new SceneViewToolbar(context, toolController);

        collisionOverlay = new CollisionOverlayRenderer();
        cameraOverlayRenderer = new CameraOverlayRenderer();

        createPanels();

        cameraOverlayRenderer.setHierarchyPanel(hierarchyPanel);

        createMenuAndStatus();

        sceneToolbar.setMessageCallback(statusBar::showMessage);
        animationEditorPanel.setStatusCallback(statusBar::showMessage);
        spriteEditorPanel.setStatusCallback(statusBar::showMessage);

        // Register panel handlers for asset double-click
        assetBrowserPanel.registerPanelHandler(
                EditorPanel.ANIMATION_EDITOR,
                animationEditorPanel::selectAnimationByPath
        );
        assetBrowserPanel.registerPanelHandler(
                EditorPanel.SPRITE_EDITOR,
                spriteEditorPanel::open
        );
    }

    private void createPanels() {
        EditorScene scene = context.getCurrentScene();

        tilesetPalette = new TilesetPalettePanel(context.getToolManager());
        tilesetPalette.setBrushTool(toolController.getBrushTool());
        tilesetPalette.setFillTool(toolController.getFillTool());
        tilesetPalette.setRectangleTool(toolController.getRectangleTool());
        tilesetPalette.setHorizontalLayout(true);

        collisionPanel = new CollisionPanel();
        collisionPanel.setScene(scene);
        collisionPanel.setModeManager(context.getModeManager());
        collisionPanel.setBrushTool(toolController.getCollisionBrushTool());
        collisionPanel.setEraserTool(toolController.getCollisionEraserTool());
        collisionPanel.setFillTool(toolController.getCollisionFillTool());
        collisionPanel.setRectangleTool(toolController.getCollisionRectangleTool());
        collisionPanel.setPickerTool(toolController.getCollisionPickerTool());
        collisionPanel.setHorizontalLayout(true);

        toolController.setCollisionPanel(collisionPanel);

        prefabBrowserPanel = new PrefabBrowserPanel();
        prefabBrowserPanel.setModeManager(context.getModeManager());
        prefabBrowserPanel.setToolManager(context.getToolManager());
        prefabBrowserPanel.setEntityPlacerTool(toolController.getEntityPlacerTool());

        // Create sprite editor before asset browser (needed for context menu)
        spriteEditorPanel = new SpriteEditorPanel();

        assetBrowserPanel = new AssetBrowserPanel();
        assetBrowserPanel.initialize();
        assetBrowserPanel.setSpriteEditorPanel(spriteEditorPanel);

        toolController.getEntityPlacerTool().setPrefabPanel(prefabBrowserPanel);
        toolController.setPrefabBrowserPanel(prefabBrowserPanel);

        hierarchyPanel = new HierarchyPanel();
        hierarchyPanel.setScene(scene);
        hierarchyPanel.setModeManager(context.getModeManager());
        hierarchyPanel.setToolManager(context.getToolManager());
        hierarchyPanel.setSelectionTool(toolController.getSelectionTool());
        hierarchyPanel.setBrushTool(toolController.getBrushTool());
        hierarchyPanel.setUiFactory(new UIEntityFactory(context.getGameConfig()));
        hierarchyPanel.init();

        inspectorPanel = new InspectorPanel();
        inspectorPanel.setScene(scene);
        inspectorPanel.setHierarchyPanel(hierarchyPanel);

        configPanel = new ConfigPanel(context);

        uiDesignerPanel = new UIDesignerPanel(context);

        animationEditorPanel = new AnimationEditorPanel();
        animationEditorPanel.initialize();
    }

    private void createMenuAndStatus() {
        menuBar = new EditorMenuBar();
        menuBar.setCurrentScene(context.getCurrentScene());
        menuBar.setConfigPanel(configPanel);
        menuBar.setOnOpenSpriteEditor(() -> spriteEditorPanel.open());

        statusBar = new StatusBar();
        statusBar.setCamera(context.getCamera());
        statusBar.setCurrentScene(context.getCurrentScene());
    }

    public void updateSceneReferences(EditorScene scene) {
        collisionPanel.setScene(scene);
        menuBar.setCurrentScene(scene);
        statusBar.setCurrentScene(scene);
        hierarchyPanel.setScene(scene);
        inspectorPanel.setScene(scene);
        sceneViewport.setScene(scene);
    }

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
        ImGui.setNextWindowSize(context.getWindow().getWidth(), context.getWindow().getHeight() - 24);

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
     * Note: Shortcuts are processed by ShortcutRegistry in EditorApplication.update()
     */
    public void renderUI() {
        // Menu bar
        renderMenuBar();

        // Scene viewport with integrated toolbar and overlays
        renderSceneViewport();

        // Panels
        renderPanels();

        // Status bar
        statusBar.render(context.getWindow().getHeight());
    }

    private void renderCameraOverlay() {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        cameraOverlayRenderer.setViewportBounds(
                sceneViewport.getViewportX(),
                sceneViewport.getViewportY(),
                sceneViewport.getViewportWidth(),
                sceneViewport.getViewportHeight()
        );
        cameraOverlayRenderer.render(context.getCamera(), scene);
    }

    private void renderSceneViewport() {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

        // begin() returns false if window is collapsed or hidden (inactive docked tab)
        boolean isVisible = ImGui.begin("Scene", flags);

        // Update SceneViewport's contentVisible based on window visibility
        sceneViewport.setContentVisible(isVisible);

        if (isVisible) {
            float viewportWidth = ImGui.getContentRegionAvailX();
            sceneToolbar.render(viewportWidth);

            ImGui.separator();

            sceneViewport.setShowGrid(sceneToolbar.isShowGrid());
            sceneViewport.renderContent();

            // Render overlays INSIDE the window context (required for getWindowDrawList)
            renderCollisionOverlay();
            renderCameraOverlay();
            sceneViewport.renderToolOverlay();
        }

        ImGui.end();
    }

    /**
     * Renders the menu bar - delegates to EditorMenuBar for File and Edit menus.
     */
    private void renderMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            // File menu (from EditorMenuBar)
            menuBar.renderFileMenu();

            // Edit menu (from EditorMenuBar) - includes working Undo/Redo
            menuBar.renderEditMenu();

            // View menu
            renderViewMenu();

            // Mode menu
            renderModeMenu();

            // Scene name on the right
            renderSceneInfo();

            ImGui.endMainMenuBar();
        }
    }

    private void renderViewMenu() {
        if (ImGui.beginMenu("View")) {
            boolean showGrid = sceneToolbar.isShowGrid();
            if (ImGui.checkbox("Show Grid", showGrid)) {
                sceneToolbar.setShowGrid(!showGrid);
            }

            EditorScene scene = context.getCurrentScene();
            boolean collisionVisible = scene != null && scene.isCollisionVisible();
            if (ImGui.checkbox("Collision Overlay", collisionVisible)) {
                if (scene != null) {
                    scene.setCollisionVisible(!collisionVisible);
                }
            }

            ImGui.separator();

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

    private void renderSceneInfo() {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        String sceneName = scene.getDisplayName();
        float availableWidth = ImGui.getContentRegionAvailX();
        float textWidth = ImGui.calcTextSize(sceneName).x;
        float padding = 20;

        ImGui.setCursorPosX(ImGui.getCursorPosX() + availableWidth - textWidth - padding);

        if (scene.isDirty()) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, sceneName);
        } else {
            ImGui.textDisabled(sceneName);
        }
    }

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

    private void renderModePanels() {
        if (context.getModeManager().isTilemapMode()) {
            tilesetPalette.render();
        } else if (context.getModeManager().isCollisionMode()) {
            collisionPanel.render();
        } else if (context.getModeManager().isEntityMode()) {
            assetBrowserPanel.render();
            prefabBrowserPanel.render();
        }
    }

    private void renderPanels() {
        renderModePanels();
        hierarchyPanel.render();
        inspectorPanel.render();
        configPanel.render();
        uiDesignerPanel.render();
        animationEditorPanel.render();
        spriteEditorPanel.render();
        if (gameViewPanel != null) {
            gameViewPanel.render();
        }
    }

    public void onWindowResize(int width, int height) {
        context.getCamera().setViewportSize(width, height);
    }

    public com.pocket.rpg.editor.rendering.EditorFramebuffer getFramebuffer() {
        return sceneViewport.getFramebuffer();
    }

    public void destroy() {
        if (sceneViewport != null) {
            sceneViewport.destroy();
        }
        if (animationEditorPanel != null) {
            animationEditorPanel.destroy();
        }
    }

    public void setPlayModeController(PlayModeController controller) {
        this.playModeController = controller;
        this.gameViewPanel = new GameViewPanel(
                context,
                playModeController,
                context.getGameConfig()
        );
    }
}