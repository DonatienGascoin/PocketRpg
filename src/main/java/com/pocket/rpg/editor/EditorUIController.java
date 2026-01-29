package com.pocket.rpg.editor;

import com.pocket.rpg.editor.events.*;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.OpenAnimationEditorEvent;
import com.pocket.rpg.editor.events.OpenSpriteEditorEvent;
import com.pocket.rpg.editor.events.TriggerFocusRequestEvent;
import com.pocket.rpg.editor.events.TriggerSelectedEvent;
import com.pocket.rpg.editor.panels.*;
import com.pocket.rpg.editor.rendering.CameraOverlayRenderer;
import com.pocket.rpg.editor.rendering.CollisionOverlayRenderer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.UIEntityFactory;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
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
    private HierarchyPanel hierarchyPanel;

    @Getter
    private UIDesignerPanel uiDesignerPanel;

    @Getter
    private InspectorPanel inspectorPanel;

    @Getter
    private ConfigurationPanel configurationPanel;

    @Getter
    private AssetBrowserPanel assetBrowserPanel;

    @Getter
    private AnimationEditorPanel animationEditorPanel;

    @Getter
    private SpriteEditorPanel spriteEditorPanel;

    @Getter
    private AnimatorEditorPanel animatorEditorPanel;

    @Getter
    private ConsolePanel consolePanel;

    @Getter
    private AudioBrowserPanel audioBrowserPanel;

    @Getter
    private EditorMenuBar menuBar;

    @Getter
    private StatusBar statusBar;

    private CollisionOverlayRenderer collisionOverlay;
    private CameraOverlayRenderer cameraOverlayRenderer;

    // Scene view focus state (SceneViewport doesn't extend EditorPanel)
    private boolean sceneViewFocused = false;

    // Flag to focus Game panel on next frame (after window exists)
    private boolean requestGameFocus = false;

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
        animatorEditorPanel.setStatusCallback(statusBar::showMessage);

        // Register panel handlers for asset double-click
        assetBrowserPanel.registerPanelHandler(
                EditorPanelType.ANIMATION_EDITOR,
                animationEditorPanel::selectAnimationByPath
        );
        assetBrowserPanel.registerPanelHandler(
                EditorPanelType.SPRITE_EDITOR,
                spriteEditorPanel::open
        );
        assetBrowserPanel.registerPanelHandler(
                EditorPanelType.ANIMATOR_EDITOR,
                animatorEditorPanel::selectControllerByPath
        );
    }

    private void createPanels() {
        EditorScene scene = context.getCurrentScene();

        tilesetPalette = new TilesetPalettePanel(context.getToolManager());
        tilesetPalette.initPanel(context.getConfig());
        tilesetPalette.setBrushTool(toolController.getBrushTool());
        tilesetPalette.setEraserTool(toolController.getEraserTool());
        tilesetPalette.setFillTool(toolController.getFillTool());
        tilesetPalette.setRectangleTool(toolController.getRectangleTool());
        tilesetPalette.setScene(scene);
        tilesetPalette.setEditorSelectionManager(context.getSelectionManager());
        tilesetPalette.setHorizontalLayout(true);

        collisionPanel = new CollisionPanel();
        collisionPanel.initPanel(context.getConfig());
        collisionPanel.setScene(scene);
        collisionPanel.setBrushTool(toolController.getCollisionBrushTool());
        collisionPanel.setEraserTool(toolController.getCollisionEraserTool());
        collisionPanel.setFillTool(toolController.getCollisionFillTool());
        collisionPanel.setRectangleTool(toolController.getCollisionRectangleTool());
        collisionPanel.setPickerTool(toolController.getCollisionPickerTool());
        collisionPanel.setEditorSelectionManager(context.getSelectionManager());
        collisionPanel.setHorizontalLayout(true);

        toolController.setCollisionPanel(collisionPanel);

        // Create sprite editor before asset browser (needed for context menu)
        spriteEditorPanel = new SpriteEditorPanel();

        assetBrowserPanel = new AssetBrowserPanel();
        assetBrowserPanel.initPanel(context.getConfig());
        assetBrowserPanel.initialize();
        assetBrowserPanel.setSpriteEditorPanel(spriteEditorPanel);
        assetBrowserPanel.setSelectionManager(context.getSelectionManager());

        hierarchyPanel = new HierarchyPanel();
        hierarchyPanel.initPanel(context.getConfig());
        hierarchyPanel.setScene(scene);
        hierarchyPanel.setToolManager(context.getToolManager());
        hierarchyPanel.setSelectionTool(toolController.getSelectionTool());
        hierarchyPanel.setBrushTool(toolController.getBrushTool());
        hierarchyPanel.setSelectionManager(context.getSelectionManager());
        hierarchyPanel.setUiFactory(new UIEntityFactory(context.getGameConfig()));
        hierarchyPanel.setUiController(this);
        hierarchyPanel.init();

        inspectorPanel = new InspectorPanel();
        inspectorPanel.initPanel(context.getConfig());
        inspectorPanel.setScene(scene);
        inspectorPanel.setSelectionManager(context.getSelectionManager());

        // Subscribe to trigger selection events (from collision panel or scene view)
        EditorEventBus.get().subscribe(TriggerSelectedEvent.class, event -> {
            inspectorPanel.setSelectedTrigger(event.coordinate());
            collisionPanel.setSelectedTrigger(event.coordinate());
        });

        // Subscribe to trigger focus events (double-click in collision panel)
        EditorEventBus.get().subscribe(TriggerFocusRequestEvent.class, event -> {
            if (context.getCamera() != null) {
                context.getCamera().centerOn(event.coordinate().x() + 0.5f, event.coordinate().y() + 0.5f);
            }
        });

        // Subscribe to open sprite editor events (from inspector "Open Sprite Editor" button)
        EditorEventBus.get().subscribe(OpenSpriteEditorEvent.class, event -> {
            if (event.texturePath() != null) {
                spriteEditorPanel.open(event.texturePath());
            } else {
                spriteEditorPanel.open();
            }
        });

        // Subscribe to open animation editor events (from inspector "Open Animation Editor" button)
        EditorEventBus.get().subscribe(OpenAnimationEditorEvent.class, event -> {
            if (event.animationPath() != null) {
                animationEditorPanel.selectAnimationByPath(event.animationPath());
            }
        });

        // Subscribe to animator selection events (from Animator Editor)
        EditorEventBus.get().subscribe(AnimatorStateSelectedEvent.class, event -> {
            inspectorPanel.setAnimatorState(event.state(), event.controller(), event.onModified());
        });
        EditorEventBus.get().subscribe(AnimatorTransitionSelectedEvent.class, event -> {
            inspectorPanel.setAnimatorTransition(event.transition(), event.controller(), event.onModified());
        });
        EditorEventBus.get().subscribe(AnimatorSelectionClearedEvent.class, event -> {
            inspectorPanel.clearAnimatorSelection();
        });

        // Clear animator inspectors and graph selection when selection changes away from animator types
        EditorEventBus.get().subscribe(SelectionChangedEvent.class, event -> {
            boolean wasAnimator = event.previousType() == EditorSelectionManager.SelectionType.ANIMATOR_STATE
                    || event.previousType() == EditorSelectionManager.SelectionType.ANIMATOR_TRANSITION;
            boolean isAnimator = event.selectionType() == EditorSelectionManager.SelectionType.ANIMATOR_STATE
                    || event.selectionType() == EditorSelectionManager.SelectionType.ANIMATOR_TRANSITION;
            if (wasAnimator && !isAnimator) {
                inspectorPanel.clearAnimatorSelection();
                animatorEditorPanel.clearGraphSelection();
            }
        });

        // Wire up collision type selection to switch to brush tool (if not in fill/rectangle)
        collisionPanel.setActiveToolSupplier(() -> context.getToolManager().getActiveTool());
        collisionPanel.setOnSwitchToBrushTool(() ->
                context.getToolManager().setActiveTool(toolController.getCollisionBrushTool())
        );

        // Wire up camera bounds supplier for visibility checking
        collisionPanel.setCameraWorldBoundsSupplier(() -> {
            if (context.getCamera() != null) {
                return context.getCamera().getWorldBounds();
            }
            return null;
        });

        configurationPanel = new ConfigurationPanel(context);
        configurationPanel.initPanel(context.getConfig());

        uiDesignerPanel = new UIDesignerPanel(context);

        animationEditorPanel = new AnimationEditorPanel();
        animationEditorPanel.initialize();

        animatorEditorPanel = new AnimatorEditorPanel();
        animatorEditorPanel.initialize();
        animatorEditorPanel.setSelectionManager(context.getSelectionManager());

        consolePanel = new ConsolePanel();
        consolePanel.initPanel(context.getConfig());

        audioBrowserPanel = new AudioBrowserPanel();
        audioBrowserPanel.initPanel(context.getConfig());
    }

    private void createMenuAndStatus() {
        menuBar = new EditorMenuBar();
        menuBar.setCurrentScene(context.getCurrentScene());
        menuBar.setConfigurationPanel(configurationPanel);
        menuBar.setOnOpenSpriteEditor(() -> spriteEditorPanel.open());
        menuBar.setOnToggleGizmos(() -> {
            sceneViewport.setShowGizmos(menuBar.isGizmosEnabled());
        });

        statusBar = new StatusBar();
        statusBar.initialize();
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
        tilesetPalette.setScene(scene);
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
        float orthoSize = context.getRenderingConfig()
                .getDefaultOrthographicSize(context.getGameConfig().getGameHeight());
        cameraOverlayRenderer.setOrthographicSize(orthoSize);
        cameraOverlayRenderer.render(context.getCamera(), scene);
    }

    private void renderSceneViewport() {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

        // begin() returns false if window is collapsed or hidden (inactive docked tab)
        boolean isVisible = ImGui.begin("Scene", flags);

        // Update SceneViewport's contentVisible based on window visibility
        sceneViewport.setContentVisible(isVisible);

        // Track scene view focus for shortcut context (SceneViewport doesn't extend EditorPanel)
        sceneViewFocused = isVisible && sceneViewport.isFocused();

        if (isVisible) {
            float viewportWidth = ImGui.getContentRegionAvailX();
            sceneToolbar.render(viewportWidth);

            ImGui.separator();

            sceneViewport.setShowGrid(sceneToolbar.isShowGrid());
            sceneViewport.setShowGizmos(sceneToolbar.isShowGizmos());
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

            // Tools menu (from EditorMenuBar) - migration tools, etc.
            menuBar.renderToolsMenu();

            // Window menu for panel visibility
            renderWindowMenu();

            // Play mode controls
            renderPlayModeControls();

            // Scene name on the right
            renderSceneInfo();

            ImGui.endMainMenuBar();
        }

        // Render dialogs from EditorMenuBar (migration dialog, etc.)
        menuBar.renderDialogs();
    }

    private void renderPlayModeControls() {
        if (playModeController == null) return;

        PlayModeController.PlayState state = playModeController.getState();
        boolean isStopped = state == PlayModeController.PlayState.STOPPED;
        boolean isPlaying = state == PlayModeController.PlayState.PLAYING;
        boolean isPaused = state == PlayModeController.PlayState.PAUSED;

        // Calculate button width for centering (3 buttons + spacing)
        float buttonWidth = 28;
        float spacing = 4;
        float totalWidth = buttonWidth * 3 + spacing * 2;

        // Center in menu bar
        float menuBarWidth = ImGui.getWindowWidth();
        float centerX = (menuBarWidth - totalWidth) / 2;
        ImGui.setCursorPosX(centerX);

        // Play button - active styling when playing, disabled when already playing
        if (isPlaying) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.5f, 0.2f, 1f);
        }
        if (isPlaying) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.PlayArrow + "##PlayMenu")) {
            if (isPaused) {
                playModeController.resume();
            } else {
                playModeController.play();
                requestGameFocus = true;
            }
        }
        if (isPlaying) {
            ImGui.endDisabled();
            ImGui.popStyleColor(3);
        }
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(isPlaying ? "Playing" : (isPaused ? "Resume" : "Play"));
        }

        ImGui.sameLine(0, spacing);

        // Pause button - active styling when paused, disabled when stopped or paused
        if (isPaused) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.6f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.6f, 0.5f, 0.2f, 1f);
        }
        if (isStopped || isPaused) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.Pause + "##PauseMenu")) {
            playModeController.pause();
        }
        if (isStopped || isPaused) {
            ImGui.endDisabled();
        }
        if (isPaused) {
            ImGui.popStyleColor(3);
        }
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(isPaused ? "Paused" : "Pause");
        }

        ImGui.sameLine(0, spacing);

        // Stop button - disabled when stopped
        if (isStopped) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.Stop + "##StopMenu")) {
            playModeController.stop();
        }
        if (isStopped) {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip("Stop");
        }
    }

    /**
     * Renders the Window menu for toggling panel visibility.
     */
    private void renderWindowMenu() {
        if (ImGui.beginMenu("Window")) {
            // Core panels
            if (ImGui.menuItem("Hierarchy", "", hierarchyPanel.isOpen())) {
                hierarchyPanel.toggle();
            }
            if (ImGui.menuItem("Inspector", "", inspectorPanel.isOpen())) {
                inspectorPanel.toggle();
            }
            if (ImGui.menuItem("Assets", "", assetBrowserPanel.isOpen())) {
                assetBrowserPanel.toggle();
            }
            if (ImGui.menuItem("Console", "", consolePanel.isOpen())) {
                consolePanel.toggle();
            }
            if (ImGui.menuItem("Audio Browser", "", audioBrowserPanel.isOpen())) {
                audioBrowserPanel.toggle();
            }
            if (ImGui.menuItem("Configuration", "", configurationPanel.isOpen())) {
                configurationPanel.toggle();
            }

            ImGui.separator();

            // Painting panels with shortcut hints
            if (ImGui.menuItem("Tileset Palette", "F1", tilesetPalette.isOpen())) {
                tilesetPalette.toggle();
            }
            if (ImGui.menuItem("Collision Panel", "F2", collisionPanel.isOpen())) {
                collisionPanel.toggle();
            }

            ImGui.separator();

            // Animation panels
            if (ImGui.menuItem("Animation Editor", "", animationEditorPanel.isOpen())) {
                animationEditorPanel.toggle();
            }
            if (ImGui.menuItem("Animator Editor", "", animatorEditorPanel.isOpen())) {
                animatorEditorPanel.toggle();
            }

            ImGui.separator();

            // Reset layout option
            if (ImGui.menuItem("Reset Panel Layout")) {
                resetPanelLayout();
            }

            ImGui.endMenu();
        }
    }

    /**
     * Resets all panels to their default visibility state.
     */
    private void resetPanelLayout() {
        hierarchyPanel.setOpen(true);
        inspectorPanel.setOpen(true);
        assetBrowserPanel.setOpen(true);
        consolePanel.setOpen(true);
        tilesetPalette.setOpen(false);
        collisionPanel.setOpen(false);
        statusBar.showMessage("Panel layout reset to defaults");
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
        if (scene == null) {
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

        // Wire up trigger data for icon rendering and configuration status
        collisionOverlay.setTriggerDataMap(scene.getTriggerDataMap());
        collisionOverlay.setSelectedTrigger(collisionPanel.getSelectedTrigger());

        // Always render triggers (warps, doors, stairs, spawn points)
        collisionOverlay.renderTriggersOnly(scene.getCollisionMap(), context.getCamera());

        // Render non-trigger collision tiles only when toggle is on OR collision mode is active
        boolean collisionModeActive = context.getSelectionManager().isCollisionLayerSelected();
        if (scene.isCollisionVisible() || collisionModeActive) {
            collisionOverlay.render(scene.getCollisionMap(), context.getCamera());
        }
    }

    private void renderPanels() {
        // Core panels (visibility controlled by isOpen())
        hierarchyPanel.render();
        inspectorPanel.render();
        assetBrowserPanel.render();

        // Painting panels (visibility controlled by isOpen())
        tilesetPalette.render();
        collisionPanel.render();

        // Other panels
        configurationPanel.render();
        uiDesignerPanel.render();
        animationEditorPanel.render();
        animatorEditorPanel.render();
        spriteEditorPanel.render();
        consolePanel.render();
        audioBrowserPanel.render();
        if (gameViewPanel != null) {
            gameViewPanel.render();
            // Focus Game panel if requested (must happen after render creates the window)
            if (requestGameFocus) {
                ImGui.setWindowFocus("Game");
                requestGameFocus = false;
            }
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

    /**
     * Opens and focuses the Tileset Palette panel.
     */
    public void openTilesetPalette() {
        if (tilesetPalette != null) {
            tilesetPalette.setOpen(true);
            ImGui.setWindowFocus("Tileset Palette");
        }
    }

    /**
     * Opens and focuses the Collision panel.
     */
    public void openCollisionPanel() {
        if (collisionPanel != null) {
            collisionPanel.setOpen(true);
            ImGui.setWindowFocus("Collision Panel");
        }
    }

    /**
     * Builds a ShortcutContext with current panel focus information.
     * Uses focus state from the previous frame (retained in panel fields).
     * Auto-discovers all EditorPanel instances instead of hardcoding each one.
     */
    public com.pocket.rpg.editor.shortcut.ShortcutContext buildShortcutContext() {
        var builder = com.pocket.rpg.editor.shortcut.ShortcutContext.builder()
                .popupOpen(ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopup))
                .textInputActive(ImGui.getIO().getWantTextInput());

        // Auto-discover focus state from all registered EditorPanel instances
        for (com.pocket.rpg.editor.panels.EditorPanel panel : com.pocket.rpg.editor.panels.EditorPanel.getAllPanels()) {
            if (panel.isFocused()) {
                builder.panelFocused(panel.getPanelId());
            }
        }

        // SceneViewport doesn't extend EditorPanel (different lifecycle) — handle as special case
        if (sceneViewFocused) {
            builder.panelFocused(com.pocket.rpg.editor.shortcut.EditorShortcuts.PanelIds.SCENE_VIEW);
        }

        return builder.build();
    }
}