package com.pocket.rpg.editor;

import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.editor.panels.CollisionPanel;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.*;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

/**
 * Manages editor tools: creation and registration.
 * <p>
 * Handles tool registration and callback setup.
 * Tool UI is rendered by SceneViewToolbar.
 * Keyboard shortcuts are handled by the centralized shortcut system.
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
    @Getter private SelectionTool selectionTool;

    // Reference to collision panel for type display
    @Setter private CollisionPanel collisionPanel;

    // Message callback
    @Setter private Consumer<String> messageCallback;

    // Trigger selection callback (when clicking trigger tiles in scene view)
    @Setter private Consumer<TileCoord> triggerSelectedCallback;

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
        selectionTool = new SelectionTool();
        selectionTool.setScene(scene);
        selectionTool.setCamera(context.getCamera());
        selectionTool.setSelectionManager(context.getSelectionManager());
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
        selectionTool.setCollisionZLevel(z);
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

        // Trigger selection callback (when clicking on trigger tiles with picker)
        collisionPickerTool.setOnTriggerSelected(coord -> {
            if (triggerSelectedCallback != null) {
                triggerSelectedCallback.accept(coord);
            }
        });

        // Trigger selection callback for SelectionTool (when collision layer is selected)
        selectionTool.setOnTriggerSelected(coord -> {
            if (triggerSelectedCallback != null) {
                triggerSelectedCallback.accept(coord);
            }
        });
    }

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }
}