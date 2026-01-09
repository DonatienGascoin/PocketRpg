package com.pocket.rpg.editor;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.panels.CollisionPanel;
import com.pocket.rpg.editor.panels.PrefabBrowserPanel;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.*;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.RemoveEntityCommand;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

/**
 * Manages editor tools: creation, shortcuts, and tool panel UI.
 * <p>
 * Handles tool registration, keyboard shortcuts based on current mode,
 * and renders the tool panel with buttons and settings.
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
     * Processes keyboard shortcuts for tools.
     */
    public void processShortcuts() {
        if (ImGui.getIO().getWantTextInput()) return;

        EditorModeManager modeManager = context.getModeManager();
        ToolManager toolManager = context.getToolManager();

        // Mode switching
        if (ImGui.isKeyPressed(ImGuiKey.M)) {
            modeManager.switchToTilemap();
            toolManager.setActiveTool(brushTool);
            showMessage("Switched to Tilemap Mode");
        }
        if (ImGui.isKeyPressed(ImGuiKey.N)) {
            modeManager.switchToCollision();
            toolManager.setActiveTool(collisionBrushTool);
            syncCollisionZLevels();
            showMessage("Switched to Collision Mode");
        }

        if (ImGui.isKeyPressed(ImGuiKey.E)) {
            modeManager.switchToEntity();
            toolManager.setActiveTool(selectionTool);
            showMessage("Switched to Entity Mode");
        }

        // Tool shortcuts based on mode
        if (modeManager.isTilemapMode()) {
            processTilemapShortcuts();
        } else if (modeManager.isCollisionMode()) {
            processCollisionShortcuts();
        } else if (modeManager.isEntityMode()) {
            processEntityShortcuts();
        }
    }

    private void processEntityShortcuts() {
        ToolManager toolManager = context.getToolManager();

        // V - Selection tool
        if (ImGui.isKeyPressed(ImGuiKey.V)) {
            toolManager.setActiveTool(selectionTool);
            showMessage("Selection Tool");
        }

        // P - Entity placer tool
        if (ImGui.isKeyPressed(ImGuiKey.P)) {
            toolManager.setActiveTool(entityPlacerTool);
            showMessage("Entity Placer");
        }

        // Escape - Cancel placement / Deselect
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            EditorTool activeTool = toolManager.getActiveTool();

            // If using placer tool, switch to selection and clear prefab selection
            if (activeTool == entityPlacerTool) {
                toolManager.setActiveTool(selectionTool);
                if (prefabBrowserPanel != null) {
                    prefabBrowserPanel.clearSelection();
                }
                showMessage("Cancelled placement");
            } else {
                // Otherwise just deselect entity
                EditorScene scene = context.getCurrentScene();
                if (scene != null && scene.getSelectedEntity() != null) {
                    scene.setSelectedEntity(null);
                    showMessage("Deselected");
                }
            }
        }

        // Delete - Remove selected entity
        if (ImGui.isKeyPressed(ImGuiKey.Delete) || ImGui.isKeyPressed(ImGuiKey.Backspace)) { // TODO: Move to Inspector binding so delete popup can be shown
            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                EditorGameObject selected = scene.getSelectedEntity();
                if (selected != null) {
                    String name = selected.getName();
                    UndoManager.getInstance().execute(new RemoveEntityCommand(scene, selected));
                    showMessage("Deleted: " + name);
                }
            }
        }
    }

    private void processTilemapShortcuts() {
        ToolManager toolManager = context.getToolManager();

        if (ImGui.isKeyPressed(ImGuiKey.B)) {
            toolManager.setActiveTool(brushTool);
            showMessage("Tile Brush");
        }
        if (ImGui.isKeyPressed(ImGuiKey.E)) {
            toolManager.setActiveTool(eraserTool);
            showMessage("Tile Eraser");
        }
        if (ImGui.isKeyPressed(ImGuiKey.F)) {
            toolManager.setActiveTool(fillTool);
            showMessage("Tile Fill");
        }
        if (ImGui.isKeyPressed(ImGuiKey.R)) {
            toolManager.setActiveTool(rectangleTool);
            showMessage("Tile Rectangle");
        }
        if (ImGui.isKeyPressed(ImGuiKey.I)) {
            toolManager.setActiveTool(pickerTool);
            showMessage("Tile Picker");
        }

        // Brush size
        processBrushSizeShortcuts(brushTool.getBrushSize(), size -> {
            brushTool.setBrushSize(size);
            eraserTool.setEraserSize(size);
        });
    }

    private void processCollisionShortcuts() {
        ToolManager toolManager = context.getToolManager();

        if (ImGui.isKeyPressed(ImGuiKey.C)) {
            toolManager.setActiveTool(collisionBrushTool);
            showMessage("Collision Brush");
        }
        if (ImGui.isKeyPressed(ImGuiKey.X)) {
            toolManager.setActiveTool(collisionEraserTool);
            showMessage("Collision Eraser");
        }
        if (ImGui.isKeyPressed(ImGuiKey.G)) {
            toolManager.setActiveTool(collisionFillTool);
            showMessage("Collision Fill");
        }
        if (ImGui.isKeyPressed(ImGuiKey.H)) {
            toolManager.setActiveTool(collisionRectangleTool);
            showMessage("Collision Rectangle");
        }
        if (ImGui.isKeyPressed(ImGuiKey.V)) {
            toolManager.setActiveTool(collisionPickerTool);
            showMessage("Collision Picker");
        }

        // Brush size
        processBrushSizeShortcuts(collisionBrushTool.getBrushSize(), size -> {
            collisionBrushTool.setBrushSize(size);
            collisionEraserTool.setEraserSize(size);
        });

        // Z-level shortcuts
        processZLevelShortcuts();
    }

    private void processBrushSizeShortcuts(int currentSize, Consumer<Integer> setter) {
        if (ImGui.isKeyPressed(ImGuiKey.Minus) || ImGui.isKeyPressed(ImGuiKey.KeypadSubtract)) {
            if (currentSize > 1) {
                setter.accept(currentSize - 1);
                showMessage("Brush Size: " + (currentSize - 1));
            }
        }
        if (ImGui.isKeyPressed(ImGuiKey.Equal) || ImGui.isKeyPressed(ImGuiKey.KeypadAdd)) {
            if (currentSize < 10) {
                setter.accept(currentSize + 1);
                showMessage("Brush Size: " + (currentSize + 1));
            }
        }
    }

    private void processZLevelShortcuts() {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        if (ImGui.isKeyPressed(ImGuiKey.LeftBracket)) {
            int z = scene.getCollisionZLevel();
            if (z > 0) {
                scene.setCollisionZLevel(z - 1);
                syncCollisionZLevels();
                showMessage("Z-Level: " + (z - 1));
            }
        }
        if (ImGui.isKeyPressed(ImGuiKey.RightBracket)) {
            int z = scene.getCollisionZLevel();
            if (z < 3) {
                scene.setCollisionZLevel(z + 1);
                syncCollisionZLevels();
                showMessage("Z-Level: " + (z + 1));
            }
        }
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
    }

    private void renderCollisionToolSettings(EditorTool activeTool) {
        CollisionType selectedType = collisionPanel != null ? collisionPanel.getSelectedType() : CollisionType.SOLID;

        if (activeTool == collisionBrushTool) {
            ImGui.text("Collision Brush");
            ImGui.text("Type: " + selectedType.getDisplayName());
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

        EditorScene scene = context.getCurrentScene();
        int zLevel = scene != null ? scene.getCollisionZLevel() : 0;

        ImGui.separator();
        ImGui.text("Z-Level: " + zLevel);
        ImGui.textDisabled("[ / ] to change");

        ImGui.separator();
        ImGui.textDisabled("Shortcuts:");
        ImGui.textDisabled("N - Switch to Collision");
        ImGui.textDisabled("C - Brush, X - Eraser");
        ImGui.textDisabled("G - Fill, H - Rectangle");
        ImGui.textDisabled("V - Picker");
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
}