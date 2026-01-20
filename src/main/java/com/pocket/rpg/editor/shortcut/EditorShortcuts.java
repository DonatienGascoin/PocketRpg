package com.pocket.rpg.editor.shortcut;

import imgui.flag.ImGuiKey;

/**
 * Defines all editor shortcuts with their default bindings.
 * This class serves as the central definition point for all shortcuts.
 *
 * Handlers are bound later via {@link #bindHandlers(ShortcutRegistry, EditorShortcutHandlers)}.
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
    public static final String VIEW_TOGGLE_GRID = "editor.view.toggleGrid";

    // Mode actions
    public static final String MODE_TILEMAP = "editor.mode.tilemap";
    public static final String MODE_COLLISION = "editor.mode.collision";
    public static final String MODE_ENTITY = "editor.mode.entity";

    // Tilemap tool actions
    public static final String TOOL_TILE_BRUSH = "editor.tool.tileBrush";
    public static final String TOOL_TILE_ERASER = "editor.tool.tileEraser";
    public static final String TOOL_TILE_FILL = "editor.tool.tileFill";
    public static final String TOOL_TILE_RECTANGLE = "editor.tool.tileRectangle";
    public static final String TOOL_TILE_PICKER = "editor.tool.tilePicker";

    // Collision tool actions
    public static final String TOOL_COLLISION_BRUSH = "editor.tool.collisionBrush";
    public static final String TOOL_COLLISION_ERASER = "editor.tool.collisionEraser";
    public static final String TOOL_COLLISION_FILL = "editor.tool.collisionFill";
    public static final String TOOL_COLLISION_RECTANGLE = "editor.tool.collisionRectangle";
    public static final String TOOL_COLLISION_PICKER = "editor.tool.collisionPicker";

    // Entity tool actions
    public static final String TOOL_SELECTION = "editor.tool.selection";
    public static final String TOOL_ENTITY_PLACER = "editor.tool.entityPlacer";

    // Brush size actions
    public static final String BRUSH_SIZE_INCREASE = "editor.brush.sizeIncrease";
    public static final String BRUSH_SIZE_DECREASE = "editor.brush.sizeDecrease";

    // Z-level actions (collision mode)
    public static final String Z_LEVEL_INCREASE = "editor.zlevel.increase";
    public static final String Z_LEVEL_DECREASE = "editor.zlevel.decrease";

    // Entity actions
    public static final String ENTITY_DELETE = "editor.entity.delete";
    public static final String ENTITY_CANCEL = "editor.entity.cancel";

    // Play mode
    public static final String PLAY_TOGGLE = "editor.play.toggle";
    public static final String PLAY_STOP = "editor.play.stop";

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
        public static final String ANIMATION_EDITOR = "animationEditor";

        private PanelIds() {
        }
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers all shortcuts with their default bindings.
     * Call this during editor initialization.
     */
    public static void registerDefaults(ShortcutRegistry registry) {
        registerFileShortcuts(registry);
        registerEditShortcuts(registry);
        registerViewShortcuts(registry);
        registerModeShortcuts(registry);
        registerTilemapToolShortcuts(registry);
        registerCollisionToolShortcuts(registry);
        registerEntityToolShortcuts(registry);
        registerBrushShortcuts(registry);
        registerZLevelShortcuts(registry);
        registerPlayShortcuts(registry);
    }

    private static void registerFileShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(FILE_NEW)
                        .displayName("New Scene")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.N))
                        .global()
                        .handler(() -> {})
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
                        .defaultBinding(null)
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerEditShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(EDIT_UNDO)
                        .displayName("Undo")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Z))
                        .global()
                        .allowInTextInput(false)
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
    }

    private static void registerViewShortcuts(ShortcutRegistry registry) {
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
                        .id(VIEW_TOGGLE_GRID)
                        .displayName("Toggle Grid")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.G))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerModeShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(MODE_TILEMAP)
                        .displayName("Tilemap Mode")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.M))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(MODE_COLLISION)
                        .displayName("Collision Mode")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.N))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(MODE_ENTITY)
                        .displayName("Entity Mode")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.E))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerTilemapToolShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(TOOL_TILE_BRUSH)
                        .displayName("Tile Brush")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.B))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_TILE_ERASER)
                        .displayName("Tile Eraser")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.E))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_TILE_FILL)
                        .displayName("Tile Fill")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.F))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_TILE_RECTANGLE)
                        .displayName("Tile Rectangle")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.R))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_TILE_PICKER)
                        .displayName("Tile Picker")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.I))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerCollisionToolShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(TOOL_COLLISION_BRUSH)
                        .displayName("Collision Brush")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.C))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_COLLISION_ERASER)
                        .displayName("Collision Eraser")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.X))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_COLLISION_FILL)
                        .displayName("Collision Fill")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.G))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_COLLISION_RECTANGLE)
                        .displayName("Collision Rectangle")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.H))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_COLLISION_PICKER)
                        .displayName("Collision Picker")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.V))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerEntityToolShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(TOOL_SELECTION)
                        .displayName("Selection Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.V))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_ENTITY_PLACER)
                        .displayName("Entity Placer")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.P))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(ENTITY_DELETE)
                        .displayName("Delete Entity")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Delete))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(ENTITY_CANCEL)
                        .displayName("Cancel/Deselect")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Escape))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerBrushShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(BRUSH_SIZE_INCREASE)
                        .displayName("Increase Brush Size")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Equal))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(BRUSH_SIZE_DECREASE)
                        .displayName("Decrease Brush Size")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.Minus))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerZLevelShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(Z_LEVEL_INCREASE)
                        .displayName("Increase Z-Level")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.RightBracket))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(Z_LEVEL_DECREASE)
                        .displayName("Decrease Z-Level")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.LeftBracket))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerPlayShortcuts(ShortcutRegistry registry) {
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
    }

    // ========================================================================
    // HANDLER BINDING
    // ========================================================================

    /**
     * Binds actual handlers to the registered shortcuts.
     * Call this after registering defaults and after the editor controllers are initialized.
     */
    public static void bindHandlers(ShortcutRegistry registry, EditorShortcutHandlers handlers) {
        // File
        registry.bindHandler(FILE_NEW, handlers::onNewScene);
        registry.bindHandler(FILE_OPEN, handlers::onOpenScene);
        registry.bindHandler(FILE_SAVE, handlers::onSaveScene);
        registry.bindHandler(FILE_SAVE_AS, handlers::onSaveSceneAs);
        registry.bindHandler(FILE_CONFIGURATION, handlers::onOpenConfiguration);

        // Edit
        registry.bindHandler(EDIT_UNDO, handlers::onUndo);
        registry.bindHandler(EDIT_REDO, handlers::onRedo);
        registry.bindHandler(EDIT_CUT, handlers::onCut);
        registry.bindHandler(EDIT_COPY, handlers::onCopy);
        registry.bindHandler(EDIT_PASTE, handlers::onPaste);
        registry.bindHandler(EDIT_DELETE, handlers::onDelete);
        registry.bindHandler(EDIT_SELECT_ALL, handlers::onSelectAll);
        registry.bindHandler(EDIT_DUPLICATE, handlers::onDuplicate);

        // View
        registry.bindHandler(VIEW_ZOOM_IN, handlers::onZoomIn);
        registry.bindHandler(VIEW_ZOOM_OUT, handlers::onZoomOut);
        registry.bindHandler(VIEW_ZOOM_RESET, handlers::onZoomReset);
        registry.bindHandler(VIEW_TOGGLE_GRID, handlers::onToggleGrid);

        // Modes
        registry.bindHandler(MODE_TILEMAP, handlers::onModeTilemap);
        registry.bindHandler(MODE_COLLISION, handlers::onModeCollision);
        registry.bindHandler(MODE_ENTITY, handlers::onModeEntity);

        // Tilemap tools
        registry.bindHandler(TOOL_TILE_BRUSH, handlers::onToolTileBrush);
        registry.bindHandler(TOOL_TILE_ERASER, handlers::onToolTileEraser);
        registry.bindHandler(TOOL_TILE_FILL, handlers::onToolTileFill);
        registry.bindHandler(TOOL_TILE_RECTANGLE, handlers::onToolTileRectangle);
        registry.bindHandler(TOOL_TILE_PICKER, handlers::onToolTilePicker);

        // Collision tools
        registry.bindHandler(TOOL_COLLISION_BRUSH, handlers::onToolCollisionBrush);
        registry.bindHandler(TOOL_COLLISION_ERASER, handlers::onToolCollisionEraser);
        registry.bindHandler(TOOL_COLLISION_FILL, handlers::onToolCollisionFill);
        registry.bindHandler(TOOL_COLLISION_RECTANGLE, handlers::onToolCollisionRectangle);
        registry.bindHandler(TOOL_COLLISION_PICKER, handlers::onToolCollisionPicker);

        // Entity tools
        registry.bindHandler(TOOL_SELECTION, handlers::onToolSelection);
        registry.bindHandler(TOOL_ENTITY_PLACER, handlers::onToolEntityPlacer);
        registry.bindHandler(ENTITY_DELETE, handlers::onEntityDelete);
        registry.bindHandler(ENTITY_CANCEL, handlers::onEntityCancel);

        // Brush size
        registry.bindHandler(BRUSH_SIZE_INCREASE, handlers::onBrushSizeIncrease);
        registry.bindHandler(BRUSH_SIZE_DECREASE, handlers::onBrushSizeDecrease);

        // Z-level
        registry.bindHandler(Z_LEVEL_INCREASE, handlers::onZLevelIncrease);
        registry.bindHandler(Z_LEVEL_DECREASE, handlers::onZLevelDecrease);

        // Play
        registry.bindHandler(PLAY_TOGGLE, handlers::onPlayToggle);
        registry.bindHandler(PLAY_STOP, handlers::onPlayStop);
    }
}
