package com.pocket.rpg.editor;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.panels.CollisionPanel;
import com.pocket.rpg.editor.panels.PrefabBrowserPanel;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.ShortcutRegistry;
import com.pocket.rpg.editor.tools.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

/**
 * Manages editor tools: creation and tool panel UI.
 * <p>
 * Handles tool registration and renders the tool panel with buttons and settings.
 * Keyboard shortcuts are now handled by the centralized shortcut system.
 *
 * @see com.pocket.rpg.editor.shortcut.ShortcutRegistry
 */
public class EditorToolController {

    private final EditorContext context;

    // Tilemap tools
    @Getter private TileBrushTool brushTool;
    @Getter private TileEraserTool eraserTool;
    @Getter private TileFillTool fillTool;
    @Getter private TileRectangleTool rectangleTool;
    @Getter private TilePickerTool pickerTool;

    // Collision tools
    @Getter private CollisionBrushTool collisionBrushTool;
    @Getter private CollisionEraserTool collisionEraserTool;
    @Getter private CollisionFillTool collisionFillTool;
    @Getter private CollisionRectangleTool collisionRectangleTool;
    @Getter private CollisionPickerTool collisionPickerTool;
    // Entity tools
    @Getter private EntityPlacerTool entityPlacerTool;
    @Getter private SelectionTool selectionTool;

    // Reference to collision panel for type display
    @Setter private CollisionPanel collisionPanel;
    @Setter private PrefabBrowserPanel prefabBrowserPanel;

    // Message callback
    @Setter private Consumer<String> messageCallback;

    public EditorToolController(EditorContext context) {
        this.context = context;
    }

    /**
     * Creates and registers all tools.
     */
    public void createTools() {
        EditorScene scene = context.getCurrentScene();
        ToolManager toolManager = context.getToolManager();

        // Tilemap tools
        brushTool = new TileBrushTool(scene);
        toolManager.registerTool(brushTool);

        eraserTool = new TileEraserTool(scene);
        toolManager.registerTool(eraserTool);

        fillTool = new TileFillTool(scene);
        toolManager.registerTool(fillTool);

        rectangleTool = new TileRectangleTool(scene);
        toolManager.registerTool(rectangleTool);

        pickerTool = new TilePickerTool(scene);
        toolManager.registerTool(pickerTool);

        // Collision tools
        collisionBrushTool = new CollisionBrushTool(scene);
        toolManager.registerTool(collisionBrushTool);

        collisionEraserTool = new CollisionEraserTool(scene);
        toolManager.registerTool(collisionEraserTool);

        collisionFillTool = new CollisionFillTool(scene);
        toolManager.registerTool(collisionFillTool);

        collisionRectangleTool = new CollisionRectangleTool(scene);
        toolManager.registerTool(collisionRectangleTool);

        collisionPickerTool = new CollisionPickerTool(scene);
        toolManager.registerTool(collisionPickerTool);

        // Entity tools
        entityPlacerTool = new EntityPlacerTool();
        entityPlacerTool.setScene(scene);
        toolManager.registerTool(entityPlacerTool);

        selectionTool = new SelectionTool();
        selectionTool.setScene(scene);
        selectionTool.setCamera(context.getCamera());
        toolManager.registerTool(selectionTool);

        // Setup callbacks
        setupCallbacks();
    }

    /**
     * Updates tool scene references when scene changes.
     */
    public void updateSceneReferences(EditorScene scene) {
        // Tilemap tools
        brushTool.setScene(scene);
        eraserTool.setScene(scene);
        fillTool.setScene(scene);
        rectangleTool.setScene(scene);
        pickerTool.setScene(scene);

        // Collision tools
        collisionBrushTool.setScene(scene);
        collisionEraserTool.setScene(scene);
        collisionFillTool.setScene(scene);
        collisionRectangleTool.setScene(scene);
        collisionPickerTool.setScene(scene);

        // Entity tools
        entityPlacerTool.setScene(scene);
        selectionTool.setScene(scene);

        // Sync z-levels
        syncCollisionZLevels();
    }

    /**
     * Synchronizes Z-level from scene to all collision tools.
     */
    public void syncCollisionZLevels() {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        int z = scene.getCollisionZLevel();
        collisionBrushTool.setZLevel(z);
        collisionEraserTool.setZLevel(z);
        collisionFillTool.setZLevel(z);
        collisionRectangleTool.setZLevel(z);
        collisionPickerTool.setZLevel(z);
    }

    /**
     * Renders the tool panel with buttons and settings.
     */
    public void renderToolPanel() {
        if (ImGui.begin("Tools")) {
            EditorModeManager modeManager = context.getModeManager();

            // Mode indicator
            ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, modeManager.isTilemapMode() ? "TILEMAP MODE" : "COLLISION MODE");
            ImGui.separator();

            // Tool buttons
            renderToolButtons();

            ImGui.separator();

            // Tool settings
            renderToolSettings();
        }
        ImGui.end();
    }

    private void renderToolButtons() {
        ToolManager toolManager = context.getToolManager();
        EditorModeManager modeManager = context.getModeManager();

        for (var tool : toolManager.getTools()) {
            // Filter tools by mode
            boolean isTilemapTool = !tool.getName().startsWith("Collision");
            boolean isCollisionTool = tool.getName().startsWith("Collision");

            if (modeManager.isTilemapMode() && isCollisionTool) continue;
            if (modeManager.isCollisionMode() && isTilemapTool) continue;

            boolean isActive = toolManager.getActiveTool() == tool;

            // Highlight active tool
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
    }

    private void renderToolSettings() {
        EditorTool activeTool = context.getToolManager().getActiveTool();
        EditorModeManager modeManager = context.getModeManager();

        if (modeManager.isTilemapMode()) {
            renderTilemapToolSettings(activeTool);
        } else {
            renderCollisionToolSettings(activeTool);
        }
    }

    private void renderTilemapToolSettings(EditorTool activeTool) {
        // Tool-specific info
        if (activeTool == brushTool) {
            ImGui.text("Brush Settings");
            var selection = brushTool.getSelection();
            if (selection == null) {
                ImGui.textDisabled("No tile selected");
            } else if (selection.isPattern()) {
                ImGui.text("Pattern: " + selection.getWidth() + "x" + selection.getHeight());
            } else {
                ImGui.text("Tile: " + selection.getFirstTileIndex());
            }
        } else if (activeTool == eraserTool) {
            ImGui.text("Eraser Settings");
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

        // Unified tool size for brush and eraser (skip for pattern selections)
        boolean showSize = (activeTool == brushTool || activeTool == eraserTool);
        boolean isPattern = activeTool == brushTool && brushTool.getSelection() != null && brushTool.getSelection().isPattern();
        if (showSize && !isPattern) {
            int[] size = {brushTool.getBrushSize()};
            if (ImGui.sliderInt("Tool Size", size, 1, 10)) {
                brushTool.setBrushSize(size[0]);
                eraserTool.setEraserSize(size[0]);
            }
        }

        ImGui.separator();
        ImGui.textDisabled("Shortcuts:");
        ImGui.textDisabled(shortcut(EditorShortcuts.MODE_TILEMAP) + " - Switch to Tilemap");
        ImGui.textDisabled(shortcut(EditorShortcuts.TOOL_TILE_BRUSH) + " - Brush, " +
                shortcut(EditorShortcuts.TOOL_TILE_ERASER) + " - Eraser");
        ImGui.textDisabled(shortcut(EditorShortcuts.TOOL_TILE_FILL) + " - Fill, " +
                shortcut(EditorShortcuts.TOOL_TILE_RECTANGLE) + " - Rectangle");
        ImGui.textDisabled(shortcut(EditorShortcuts.TOOL_TILE_PICKER) + " - Picker");
    }

    private void renderCollisionToolSettings(EditorTool activeTool) {
        CollisionType selectedType = collisionPanel != null ? collisionPanel.getSelectedType() : CollisionType.SOLID;

        // Tool-specific info
        if (activeTool == collisionBrushTool) {
            ImGui.text("Collision Brush");
            ImGui.text("Type: " + selectedType.getDisplayName());
        } else if (activeTool == collisionEraserTool) {
            ImGui.text("Collision Eraser");
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

        // Unified tool size for brush and eraser
        if (activeTool == collisionBrushTool || activeTool == collisionEraserTool) {
            int[] size = {collisionBrushTool.getBrushSize()};
            if (ImGui.sliderInt("Tool Size", size, 1, 10)) {
                collisionBrushTool.setBrushSize(size[0]);
                collisionEraserTool.setEraserSize(size[0]);
            }
        }

        EditorScene scene = context.getCurrentScene();
        int zLevel = scene != null ? scene.getCollisionZLevel() : 0;

        ImGui.separator();
        ImGui.text("Z-Level: " + zLevel);
        ImGui.textDisabled("[ / ] to change");

        ImGui.separator();
        ImGui.textDisabled("Shortcuts:");
        ImGui.textDisabled(shortcut(EditorShortcuts.MODE_COLLISION) + " - Switch to Collision");
        ImGui.textDisabled(shortcut(EditorShortcuts.TOOL_COLLISION_BRUSH) + " - Brush, " +
                shortcut(EditorShortcuts.TOOL_COLLISION_ERASER) + " - Eraser");
        ImGui.textDisabled(shortcut(EditorShortcuts.TOOL_COLLISION_FILL) + " - Fill, " +
                shortcut(EditorShortcuts.TOOL_COLLISION_RECTANGLE) + " - Rectangle");
        ImGui.textDisabled(shortcut(EditorShortcuts.TOOL_COLLISION_PICKER) + " - Picker");
    }

    private void setupCallbacks() {
        ToolManager toolManager = context.getToolManager();

        // Tile picker callback
        pickerTool.setOnTilesPicked(selection -> {
            brushTool.setSelection(selection);
            fillTool.setSelection(selection);
            rectangleTool.setSelection(selection);
            toolManager.setActiveTool(brushTool);
            showMessage("Picked tiles - switched to Brush");
        });

        // Collision picker callback
        collisionPickerTool.setOnCollisionPicked(type -> {
            if (collisionPanel != null) {
                collisionPanel.setSelectedType(type);
            }
            toolManager.setActiveTool(collisionBrushTool);
            showMessage("Picked collision: " + type.getDisplayName() + " - switched to Brush");
        });
    }

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    /**
     * Gets the display string for a shortcut action from the registry.
     */
    private String shortcut(String actionId) {
        return ShortcutRegistry.getInstance().getBindingDisplay(actionId);
    }
}