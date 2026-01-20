package com.pocket.rpg.editor.shortcut;

import imgui.flag.ImGuiKey;

/**
 * Defines all editor shortcuts with their default bindings.
 * This class serves as the central definition point for all shortcuts.
 * 
 * Handlers are bound later via {@link #bindHandlers(EditorShortcutHandlers)}.
 */
public final class EditorShortcuts {

    private EditorShortcuts() {
    }

    // ========================================================================
    // ACTION IDS
    // ========================================================================

    // File actions
    public static final String FILE_NEW = "editor.file.new";
    public static final String FILE_OPEN = "editor.file.open";
    public static final String FILE_SAVE = "editor.file.save";
    public static final String FILE_SAVE_AS = "editor.file.saveAs";
    public static final String FILE_CONFIGURATION = "editor.file.configuration";

    // Edit actions
    public static final String EDIT_UNDO = "editor.edit.undo";
    public static final String EDIT_REDO = "editor.edit.redo";
    public static final String EDIT_CUT = "editor.edit.cut";
    public static final String EDIT_COPY = "editor.edit.copy";
    public static final String EDIT_PASTE = "editor.edit.paste";
    public static final String EDIT_DELETE = "editor.edit.delete";
    public static final String EDIT_SELECT_ALL = "editor.edit.selectAll";
    public static final String EDIT_DUPLICATE = "editor.edit.duplicate";

    // View actions
    public static final String VIEW_ZOOM_IN = "editor.view.zoomIn";
    public static final String VIEW_ZOOM_OUT = "editor.view.zoomOut";
    public static final String VIEW_ZOOM_RESET = "editor.view.zoomReset";
    public static final String VIEW_FIT_SCENE = "editor.view.fitScene";
    public static final String VIEW_TOGGLE_GRID = "editor.view.toggleGrid";

    // Tool actions
    public static final String TOOL_SELECT = "editor.tool.select";
    public static final String TOOL_MOVE = "editor.tool.move";
    public static final String TOOL_BRUSH = "editor.tool.brush";
    public static final String TOOL_ERASER = "editor.tool.eraser";
    public static final String TOOL_FILL = "editor.tool.fill";
    public static final String TOOL_PICKER = "editor.tool.picker";

    // Mode actions
    public static final String MODE_TILEMAP = "editor.mode.tilemap";
    public static final String MODE_ENTITY = "editor.mode.entity";
    public static final String MODE_COLLISION = "editor.mode.collision";

    // Play mode
    public static final String PLAY_TOGGLE = "editor.play.toggle";
    public static final String PLAY_STOP = "editor.play.stop";

    // Scene navigation (viewport)
    public static final String NAV_PAN_UP = "editor.nav.panUp";
    public static final String NAV_PAN_DOWN = "editor.nav.panDown";
    public static final String NAV_PAN_LEFT = "editor.nav.panLeft";
    public static final String NAV_PAN_RIGHT = "editor.nav.panRight";

    // Popup-specific
    public static final String POPUP_CONFIRM = "editor.popup.confirm";
    public static final String POPUP_CANCEL = "editor.popup.cancel";

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers all shortcuts with their default bindings.
     * Call this during editor initialization.
     */
    public static void registerDefaults(ShortcutRegistry registry) {
        // File
        registry.registerAll(
                ShortcutAction.builder()
                        .id(FILE_NEW)
                        .displayName("New Scene")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.N))
                        .global()
                        .handler(() -> {}) // Bound later
                        .build(),

                ShortcutAction.builder()
                        .id(FILE_OPEN)
                        .displayName("Open Scene")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.O))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(FILE_SAVE)
                        .displayName("Save Scene")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.S))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(FILE_SAVE_AS)
                        .displayName("Save Scene As")
                        .defaultBinding(ShortcutBinding.ctrlShift(ImGuiKey.S))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(FILE_CONFIGURATION)
                        .displayName("Configuration")
                        .defaultBinding(null) // No default binding
                        .global()
                        .handler(() -> {})
                        .build()
        );

        // Edit
        registry.registerAll(
                ShortcutAction.builder()
                        .id(EDIT_UNDO)
                        .displayName("Undo")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Z))
                        .global()
                        .allowInTextInput(false) // Let text fields handle their own undo
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(EDIT_REDO)
                        .displayName("Redo")
                        .defaultBinding(ShortcutBinding.ctrlShift(ImGuiKey.Z))
                        .global()
                        .allowInTextInput(false)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(EDIT_CUT)
                        .displayName("Cut")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.X))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(EDIT_COPY)
                        .displayName("Copy")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.C))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(EDIT_PASTE)
                        .displayName("Paste")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.V))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(EDIT_DELETE)
                        .displayName("Delete")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Delete))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(EDIT_SELECT_ALL)
                        .displayName("Select All")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.A))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(EDIT_DUPLICATE)
                        .displayName("Duplicate")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.D))
                        .global()
                        .handler(() -> {})
                        .build()
        );

        // View
        registry.registerAll(
                ShortcutAction.builder()
                        .id(VIEW_ZOOM_IN)
                        .displayName("Zoom In")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Equal))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(VIEW_ZOOM_OUT)
                        .displayName("Zoom Out")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Minus))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(VIEW_ZOOM_RESET)
                        .displayName("Reset Zoom")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey._0))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(VIEW_FIT_SCENE)
                        .displayName("Fit Scene")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Home))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(VIEW_TOGGLE_GRID)
                        .displayName("Toggle Grid")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.G))
                        .global()
                        .handler(() -> {})
                        .build()
        );

        // Tools (panel-focused on scene view)
        registry.registerAll(
                ShortcutAction.builder()
                        .id(TOOL_SELECT)
                        .displayName("Select Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.V))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_MOVE)
                        .displayName("Move Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.M))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_BRUSH)
                        .displayName("Brush Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.B))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_ERASER)
                        .displayName("Eraser Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.E))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_FILL)
                        .displayName("Fill Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.G))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_PICKER)
                        .displayName("Picker Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.I))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build()
        );

        // Mode switching
        registry.registerAll(
                ShortcutAction.builder()
                        .id(MODE_TILEMAP)
                        .displayName("Tilemap Mode")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey._1))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(MODE_ENTITY)
                        .displayName("Entity Mode")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey._2))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(MODE_COLLISION)
                        .displayName("Collision Mode")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey._3))
                        .global()
                        .handler(() -> {})
                        .build()
        );

        // Play mode
        registry.registerAll(
                ShortcutAction.builder()
                        .id(PLAY_TOGGLE)
                        .displayName("Play/Pause")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.P))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(PLAY_STOP)
                        .displayName("Stop")
                        .defaultBinding(ShortcutBinding.ctrlShift(ImGuiKey.P))
                        .global()
                        .handler(() -> {})
                        .build()
        );

        // Navigation (scene view focused)
        registry.registerAll(
                ShortcutAction.builder()
                        .id(NAV_PAN_UP)
                        .displayName("Pan Up")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.W))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(NAV_PAN_DOWN)
                        .displayName("Pan Down")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.S))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(NAV_PAN_LEFT)
                        .displayName("Pan Left")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.A))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(NAV_PAN_RIGHT)
                        .displayName("Pan Right")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.D))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build()
        );

        // Popup shortcuts (highest priority)
        registry.registerAll(
                ShortcutAction.builder()
                        .id(POPUP_CONFIRM)
                        .displayName("Confirm")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Enter))
                        .popup()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(POPUP_CANCEL)
                        .displayName("Cancel")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Escape))
                        .popup()
                        .handler(() -> {})
                        .build()
        );
    }

    /**
     * Binds actual handlers to the registered shortcuts.
     * Call this after registering defaults and after the editor controller is initialized.
     */
    public static void bindHandlers(ShortcutRegistry registry, EditorShortcutHandlers handlers) {
        bindHandler(registry, FILE_NEW, handlers::onNewScene);
        bindHandler(registry, FILE_OPEN, handlers::onOpenScene);
        bindHandler(registry, FILE_SAVE, handlers::onSaveScene);
        bindHandler(registry, FILE_SAVE_AS, handlers::onSaveSceneAs);
        bindHandler(registry, FILE_CONFIGURATION, handlers::onOpenConfiguration);

        bindHandler(registry, EDIT_UNDO, handlers::onUndo);
        bindHandler(registry, EDIT_REDO, handlers::onRedo);
        bindHandler(registry, EDIT_CUT, handlers::onCut);
        bindHandler(registry, EDIT_COPY, handlers::onCopy);
        bindHandler(registry, EDIT_PASTE, handlers::onPaste);
        bindHandler(registry, EDIT_DELETE, handlers::onDelete);
        bindHandler(registry, EDIT_SELECT_ALL, handlers::onSelectAll);
        bindHandler(registry, EDIT_DUPLICATE, handlers::onDuplicate);

        bindHandler(registry, VIEW_ZOOM_IN, handlers::onZoomIn);
        bindHandler(registry, VIEW_ZOOM_OUT, handlers::onZoomOut);
        bindHandler(registry, VIEW_ZOOM_RESET, handlers::onZoomReset);
        bindHandler(registry, VIEW_FIT_SCENE, handlers::onFitScene);
        bindHandler(registry, VIEW_TOGGLE_GRID, handlers::onToggleGrid);

        bindHandler(registry, TOOL_SELECT, handlers::onToolSelect);
        bindHandler(registry, TOOL_MOVE, handlers::onToolMove);
        bindHandler(registry, TOOL_BRUSH, handlers::onToolBrush);
        bindHandler(registry, TOOL_ERASER, handlers::onToolEraser);
        bindHandler(registry, TOOL_FILL, handlers::onToolFill);
        bindHandler(registry, TOOL_PICKER, handlers::onToolPicker);

        bindHandler(registry, MODE_TILEMAP, handlers::onModeTilemap);
        bindHandler(registry, MODE_ENTITY, handlers::onModeEntity);
        bindHandler(registry, MODE_COLLISION, handlers::onModeCollision);

        bindHandler(registry, PLAY_TOGGLE, handlers::onPlayToggle);
        bindHandler(registry, PLAY_STOP, handlers::onPlayStop);

        bindHandler(registry, NAV_PAN_UP, handlers::onPanUp);
        bindHandler(registry, NAV_PAN_DOWN, handlers::onPanDown);
        bindHandler(registry, NAV_PAN_LEFT, handlers::onPanLeft);
        bindHandler(registry, NAV_PAN_RIGHT, handlers::onPanRight);

        bindHandler(registry, POPUP_CONFIRM, handlers::onPopupConfirm);
        bindHandler(registry, POPUP_CANCEL, handlers::onPopupCancel);
    }

    private static void bindHandler(ShortcutRegistry registry, String actionId, Runnable handler) {
        // Re-register with actual handler
        ShortcutAction original = registry.getAction(actionId);
        if (original == null) {
            return;
        }

        registry.unregister(actionId);
        registry.register(ShortcutAction.builder()
                .id(original.getId())
                .displayName(original.getDisplayName())
                .defaultBinding(original.getDefaultBinding())
                .scope(original.getScope())
                .panelId(original.getPanelId())
                .allowInTextInput(original.isAllowInTextInput())
                .handler(handler)
                .build());
    }

    // ========================================================================
    // PANEL IDS
    // ========================================================================

    /**
     * Standard panel IDs used for scope binding.
     */
    public static final class PanelIds {
        public static final String SCENE_VIEW = "sceneView";
        public static final String HIERARCHY = "hierarchy";
        public static final String INSPECTOR = "inspector";
        public static final String PALETTE = "palette";
        public static final String PREFABS = "prefabs";
        public static final String CONSOLE = "console";

        private PanelIds() {
        }
    }
}
