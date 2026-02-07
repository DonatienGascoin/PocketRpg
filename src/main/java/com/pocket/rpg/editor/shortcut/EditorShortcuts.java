package com.pocket.rpg.editor.shortcut;

import imgui.flag.ImGuiKey;

import java.util.LinkedHashMap;
import java.util.Map;

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
    public static final String FILE_RELOAD = "editor.file.reloadScene";

    // Edit actions
    public static final String EDIT_UNDO = "editor.edit.undo";
    public static final String EDIT_REDO = "editor.edit.redo";
    public static final String EDIT_REDO_ALT = "editor.edit.redoAlt"; // Ctrl+Y alternative
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
    public static final String VIEW_FOCUS_SELECTED = "editor.view.focusSelected";

    // Panel toggle actions
    public static final String PANEL_TILESET_TOGGLE = "editor.panel.tilesetToggle";
    public static final String PANEL_COLLISION_TOGGLE = "editor.panel.collisionToggle";

    // Paint tool actions (context-dependent: tilemap when tilemap layer selected, collision when collision layer selected)
    public static final String TOOL_BRUSH = "editor.tool.brush";
    public static final String TOOL_ERASER = "editor.tool.eraser";
    public static final String TOOL_FILL = "editor.tool.fill";
    public static final String TOOL_RECTANGLE = "editor.tool.rectangle";
    public static final String TOOL_PICKER = "editor.tool.picker";

    // Entity tool actions
    public static final String TOOL_SELECTION = "editor.tool.selection";
    public static final String TOOL_ENTITY_PLACER = "editor.tool.entityPlacer";

    // Transform tool actions
    public static final String TOOL_MOVE = "editor.tool.move";
    public static final String TOOL_ROTATE = "editor.tool.rotate";
    public static final String TOOL_SCALE = "editor.tool.scale";

    // Brush size actions
    public static final String BRUSH_SIZE_INCREASE = "editor.brush.sizeIncrease";
    public static final String BRUSH_SIZE_DECREASE = "editor.brush.sizeDecrease";

    // Z-level actions (collision mode)
    public static final String Z_LEVEL_INCREASE = "editor.zlevel.increase";
    public static final String Z_LEVEL_DECREASE = "editor.zlevel.decrease";

    // Entity actions
    public static final String ENTITY_DELETE = "editor.entity.delete";
    public static final String ENTITY_CANCEL = "editor.entity.cancel";
    public static final String ENTITY_TOGGLE_ENABLED = "editor.entity.toggleEnabled";

    // Camera pan actions (held keys) — WASD
    public static final String CAMERA_PAN_UP = "editor.camera.panUp";
    public static final String CAMERA_PAN_DOWN = "editor.camera.panDown";
    public static final String CAMERA_PAN_LEFT = "editor.camera.panLeft";
    public static final String CAMERA_PAN_RIGHT = "editor.camera.panRight";

    // Camera pan actions (held keys) — Arrow keys
    public static final String CAMERA_PAN_UP_ARROW = "editor.camera.panUpArrow";
    public static final String CAMERA_PAN_DOWN_ARROW = "editor.camera.panDownArrow";
    public static final String CAMERA_PAN_LEFT_ARROW = "editor.camera.panLeftArrow";
    public static final String CAMERA_PAN_RIGHT_ARROW = "editor.camera.panRightArrow";

    // Play mode
    public static final String PLAY_TOGGLE = "editor.play.toggle";
    public static final String PLAY_STOP = "editor.play.stop";

    // NOTE: Configuration shortcuts (editor.config.*) moved to ConfigurationPanel.provideShortcuts()
    // NOTE: Animator shortcuts (editor.animator.*) moved to AnimatorEditorPanel.provideShortcuts()

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
        public static final String ANIMATOR_EDITOR = "animatorEditor";
        public static final String CONFIGURATION = "configuration";

        private PanelIds() {
        }
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers all shortcuts with their default bindings.
     * Call this during editor initialization.
     *
     * @param registry The shortcut registry
     * @param layout   The keyboard layout (affects Undo/Redo bindings)
     */
    public static void registerDefaults(ShortcutRegistry registry, KeyboardLayout layout) {
        registerFileShortcuts(registry);
        registerEditShortcuts(registry, layout);
        registerViewShortcuts(registry);
        registerPanelShortcuts(registry);
        registerPaintToolShortcuts(registry);
        registerEntityToolShortcuts(registry);
        registerTransformToolShortcuts(registry);
        registerBrushShortcuts(registry);
        registerZLevelShortcuts(registry);
        registerCameraPanShortcuts(registry);
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
                        .allowInInput(true)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(FILE_SAVE_AS)
                        .displayName("Save Scene As")
                        .defaultBinding(ShortcutBinding.ctrlShift(ImGuiKey.S))
                        .global()
                        .allowInInput(true)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(FILE_CONFIGURATION)
                        .displayName("Configuration")
                        .defaultBinding(null)
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(FILE_RELOAD)
                        .displayName("Reload Scene")
                        .defaultBinding(ShortcutBinding.ctrlShift(ImGuiKey.R))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerEditShortcuts(ShortcutRegistry registry, KeyboardLayout layout) {
        // Undo/Redo bindings depend on keyboard layout
        ShortcutBinding undoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrl(ImGuiKey.W)
                : ShortcutBinding.ctrl(ImGuiKey.Z);

        ShortcutBinding redoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrlShift(ImGuiKey.W)
                : ShortcutBinding.ctrlShift(ImGuiKey.Z);

        registry.registerAll(
                ShortcutAction.builder()
                        .id(EDIT_UNDO)
                        .displayName("Undo")
                        .defaultBinding(undoBinding)
                        .global()
                        .allowInInput(true)
                        .allowInPopup(true)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(EDIT_REDO)
                        .displayName("Redo")
                        .defaultBinding(redoBinding)
                        .global()
                        .allowInInput(true)
                        .allowInPopup(true)
                        .handler(() -> {})
                        .build(),

                // Alternative redo binding (Ctrl+Y) - same for all layouts
                ShortcutAction.builder()
                        .id(EDIT_REDO_ALT)
                        .displayName("Redo (Alt)")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Y))
                        .global()
                        .allowInInput(true)
                        .allowInPopup(true)
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
                        .build(),

                ShortcutAction.builder()
                        .id(VIEW_FOCUS_SELECTED)
                        .displayName("Focus Selected")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.F))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerPanelShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(PANEL_TILESET_TOGGLE)
                        .displayName("Toggle Tileset Palette")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.F1))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(PANEL_COLLISION_TOGGLE)
                        .displayName("Toggle Collision Panel")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.F2))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerPaintToolShortcuts(ShortcutRegistry registry) {
        // Paint tools use keys 1-5, context-dependent:
        // tilemap layer selected → tilemap tools, collision layer selected → collision tools
        registry.registerAll(
                ShortcutAction.builder()
                        .id(TOOL_BRUSH)
                        .displayName("Brush")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey._1))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_ERASER)
                        .displayName("Eraser")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey._2))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_FILL)
                        .displayName("Fill")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey._3))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_RECTANGLE)
                        .displayName("Rectangle")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey._4))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_PICKER)
                        .displayName("Picker")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey._5))
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
                        .build(),

                ShortcutAction.builder()
                        .id(ENTITY_TOGGLE_ENABLED)
                        .displayName("Toggle Entity Enabled")
                        .defaultBinding(ShortcutBinding.ctrlShift(ImGuiKey.A))
                        .global()
                        .handler(() -> {})
                        .build()
        );
    }

    private static void registerTransformToolShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                ShortcutAction.builder()
                        .id(TOOL_MOVE)
                        .displayName("Move Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.W))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_ROTATE)
                        .displayName("Rotate Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.E))
                        .global()
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(TOOL_SCALE)
                        .displayName("Scale Tool")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.R))
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

    private static void registerCameraPanShortcuts(ShortcutRegistry registry) {
        registry.registerAll(
                // WASD
                ShortcutAction.builder()
                        .id(CAMERA_PAN_UP)
                        .displayName("Camera Pan Up")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.W))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(CAMERA_PAN_DOWN)
                        .displayName("Camera Pan Down")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.S))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(CAMERA_PAN_LEFT)
                        .displayName("Camera Pan Left")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.A))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(CAMERA_PAN_RIGHT)
                        .displayName("Camera Pan Right")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.D))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                // Arrow keys
                ShortcutAction.builder()
                        .id(CAMERA_PAN_UP_ARROW)
                        .displayName("Camera Pan Up (Arrow)")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.UpArrow))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(CAMERA_PAN_DOWN_ARROW)
                        .displayName("Camera Pan Down (Arrow)")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.DownArrow))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(CAMERA_PAN_LEFT_ARROW)
                        .displayName("Camera Pan Left (Arrow)")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.LeftArrow))
                        .panelFocused(PanelIds.SCENE_VIEW)
                        .handler(() -> {})
                        .build(),

                ShortcutAction.builder()
                        .id(CAMERA_PAN_RIGHT_ARROW)
                        .displayName("Camera Pan Right (Arrow)")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.RightArrow))
                        .panelFocused(PanelIds.SCENE_VIEW)
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
    // DEFAULT BINDINGS BY LAYOUT
    // ========================================================================

    /**
     * Gets the default bindings for a specific keyboard layout.
     * Used for generating config files with both QWERTY and AZERTY defaults.
     *
     * @param layout The keyboard layout
     * @return Map of action ID to default binding
     */
    public static Map<String, ShortcutBinding> getDefaultBindings(KeyboardLayout layout) {
        Map<String, ShortcutBinding> bindings = new LinkedHashMap<>();

        // File shortcuts (same for all layouts)
        bindings.put(FILE_NEW, ShortcutBinding.ctrl(ImGuiKey.N));
        bindings.put(FILE_OPEN, ShortcutBinding.ctrl(ImGuiKey.O));
        bindings.put(FILE_SAVE, ShortcutBinding.ctrl(ImGuiKey.S));
        bindings.put(FILE_SAVE_AS, ShortcutBinding.ctrlShift(ImGuiKey.S));
        bindings.put(FILE_CONFIGURATION, null);
        bindings.put(FILE_RELOAD, ShortcutBinding.ctrlShift(ImGuiKey.R));

        // Edit shortcuts (Undo/Redo differ by layout)
        if (layout == KeyboardLayout.AZERTY) {
            bindings.put(EDIT_UNDO, ShortcutBinding.ctrl(ImGuiKey.W));
            bindings.put(EDIT_REDO, ShortcutBinding.ctrlShift(ImGuiKey.W));
        } else {
            bindings.put(EDIT_UNDO, ShortcutBinding.ctrl(ImGuiKey.Z));
            bindings.put(EDIT_REDO, ShortcutBinding.ctrlShift(ImGuiKey.Z));
        }
        // Alternative redo (Ctrl+Y) - same for all layouts
        bindings.put(EDIT_REDO_ALT, ShortcutBinding.ctrl(ImGuiKey.Y));
        bindings.put(EDIT_CUT, ShortcutBinding.ctrl(ImGuiKey.X));
        bindings.put(EDIT_COPY, ShortcutBinding.ctrl(ImGuiKey.C));
        bindings.put(EDIT_PASTE, ShortcutBinding.ctrl(ImGuiKey.V));
        bindings.put(EDIT_DELETE, ShortcutBinding.key(ImGuiKey.Delete));
        bindings.put(EDIT_SELECT_ALL, ShortcutBinding.ctrl(ImGuiKey.A));
        bindings.put(EDIT_DUPLICATE, ShortcutBinding.ctrl(ImGuiKey.D));

        // View shortcuts (same for all layouts)
        bindings.put(VIEW_ZOOM_IN, ShortcutBinding.ctrl(ImGuiKey.Equal));
        bindings.put(VIEW_ZOOM_OUT, ShortcutBinding.ctrl(ImGuiKey.Minus));
        bindings.put(VIEW_ZOOM_RESET, ShortcutBinding.ctrl(ImGuiKey._0));
        bindings.put(VIEW_TOGGLE_GRID, ShortcutBinding.ctrl(ImGuiKey.G));
        bindings.put(VIEW_FOCUS_SELECTED, ShortcutBinding.key(ImGuiKey.F));

        // Panel toggle shortcuts (same for all layouts)
        bindings.put(PANEL_TILESET_TOGGLE, ShortcutBinding.key(ImGuiKey.F1));
        bindings.put(PANEL_COLLISION_TOGGLE, ShortcutBinding.key(ImGuiKey.F2));

        // Paint tool shortcuts (context-dependent: tilemap or collision based on layer selection)
        bindings.put(TOOL_BRUSH, ShortcutBinding.key(ImGuiKey._1));
        bindings.put(TOOL_ERASER, ShortcutBinding.key(ImGuiKey._2));
        bindings.put(TOOL_FILL, ShortcutBinding.key(ImGuiKey._3));
        bindings.put(TOOL_RECTANGLE, ShortcutBinding.key(ImGuiKey._4));
        bindings.put(TOOL_PICKER, ShortcutBinding.key(ImGuiKey._5));

        // Entity tool shortcuts
        bindings.put(TOOL_SELECTION, ShortcutBinding.key(ImGuiKey.V));
        bindings.put(TOOL_ENTITY_PLACER, ShortcutBinding.key(ImGuiKey.P));
        bindings.put(ENTITY_DELETE, ShortcutBinding.key(ImGuiKey.Delete));
        bindings.put(ENTITY_CANCEL, ShortcutBinding.key(ImGuiKey.Escape));
        bindings.put(ENTITY_TOGGLE_ENABLED, ShortcutBinding.ctrlShift(ImGuiKey.A));

        // Transform tool shortcuts
        bindings.put(TOOL_MOVE, ShortcutBinding.key(ImGuiKey.W));
        bindings.put(TOOL_ROTATE, ShortcutBinding.key(ImGuiKey.E));
        bindings.put(TOOL_SCALE, ShortcutBinding.key(ImGuiKey.R));

        // Brush shortcuts
        bindings.put(BRUSH_SIZE_INCREASE, ShortcutBinding.key(ImGuiKey.Equal));
        bindings.put(BRUSH_SIZE_DECREASE, ShortcutBinding.key(ImGuiKey.Minus));

        // Z-level shortcuts
        bindings.put(Z_LEVEL_INCREASE, ShortcutBinding.key(ImGuiKey.RightBracket));
        bindings.put(Z_LEVEL_DECREASE, ShortcutBinding.key(ImGuiKey.LeftBracket));

        // Camera pan shortcuts (WASD)
        bindings.put(CAMERA_PAN_UP, ShortcutBinding.key(ImGuiKey.W));
        bindings.put(CAMERA_PAN_DOWN, ShortcutBinding.key(ImGuiKey.S));
        bindings.put(CAMERA_PAN_LEFT, ShortcutBinding.key(ImGuiKey.A));
        bindings.put(CAMERA_PAN_RIGHT, ShortcutBinding.key(ImGuiKey.D));

        // Camera pan shortcuts (Arrow keys)
        bindings.put(CAMERA_PAN_UP_ARROW, ShortcutBinding.key(ImGuiKey.UpArrow));
        bindings.put(CAMERA_PAN_DOWN_ARROW, ShortcutBinding.key(ImGuiKey.DownArrow));
        bindings.put(CAMERA_PAN_LEFT_ARROW, ShortcutBinding.key(ImGuiKey.LeftArrow));
        bindings.put(CAMERA_PAN_RIGHT_ARROW, ShortcutBinding.key(ImGuiKey.RightArrow));

        // Play shortcuts
        bindings.put(PLAY_TOGGLE, ShortcutBinding.ctrl(ImGuiKey.P));
        bindings.put(PLAY_STOP, ShortcutBinding.ctrlShift(ImGuiKey.P));

        // NOTE: Configuration and Animator shortcuts are provided by their panels via provideShortcuts()

        return bindings;
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
        registry.bindHandler(FILE_RELOAD, handlers::onReloadScene);

        // Edit
        registry.bindHandler(EDIT_UNDO, handlers::onUndo);
        registry.bindHandler(EDIT_REDO, handlers::onRedo);
        registry.bindHandler(EDIT_REDO_ALT, handlers::onRedo); // Same handler as primary redo
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
        registry.bindHandler(VIEW_FOCUS_SELECTED, handlers::onFocusSelected);

        // Panel toggles
        registry.bindHandler(PANEL_TILESET_TOGGLE, handlers::onPanelTilesetToggle);
        registry.bindHandler(PANEL_COLLISION_TOGGLE, handlers::onPanelCollisionToggle);

        // Paint tools (context-dependent: tilemap or collision)
        registry.bindHandler(TOOL_BRUSH, handlers::onToolBrush);
        registry.bindHandler(TOOL_ERASER, handlers::onToolEraser);
        registry.bindHandler(TOOL_FILL, handlers::onToolFill);
        registry.bindHandler(TOOL_RECTANGLE, handlers::onToolRectangle);
        registry.bindHandler(TOOL_PICKER, handlers::onToolPicker);

        // Entity tools
        registry.bindHandler(TOOL_SELECTION, handlers::onToolSelection);
        registry.bindHandler(TOOL_ENTITY_PLACER, handlers::onToolEntityPlacer);
        registry.bindHandler(ENTITY_DELETE, handlers::onEntityDelete);
        registry.bindHandler(ENTITY_CANCEL, handlers::onEntityCancel);
        registry.bindHandler(ENTITY_TOGGLE_ENABLED, handlers::onEntityToggleEnabled);

        // Transform tools
        registry.bindHandler(TOOL_MOVE, handlers::onToolMove);
        registry.bindHandler(TOOL_ROTATE, handlers::onToolRotate);
        registry.bindHandler(TOOL_SCALE, handlers::onToolScale);

        // Brush size
        registry.bindHandler(BRUSH_SIZE_INCREASE, handlers::onBrushSizeIncrease);
        registry.bindHandler(BRUSH_SIZE_DECREASE, handlers::onBrushSizeDecrease);

        // Z-level
        registry.bindHandler(Z_LEVEL_INCREASE, handlers::onZLevelIncrease);
        registry.bindHandler(Z_LEVEL_DECREASE, handlers::onZLevelDecrease);

        // Play
        registry.bindHandler(PLAY_TOGGLE, handlers::onPlayToggle);
        registry.bindHandler(PLAY_STOP, handlers::onPlayStop);

        // NOTE: Configuration and Animator handlers are bound directly in panel provideShortcuts()
    }
}
