package com.pocket.rpg.editor.shortcut;

import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.EditorToolController;
import com.pocket.rpg.editor.panels.ConfigPanel;
import com.pocket.rpg.editor.panels.PrefabBrowserPanel;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.RemoveEntityCommand;
import com.pocket.rpg.editor.ui.EditorMenuBar;
import lombok.Setter;

import java.util.function.Consumer;

/**
 * Implementation of shortcut handlers that wires to the editor systems.
 * This class bridges the shortcut system with the actual editor functionality.
 */
public class EditorShortcutHandlersImpl implements EditorShortcutHandlers {

    private final EditorContext context;
    private final EditorToolController toolController;
    private final EditorMenuBar menuBar;

    @Setter
    private ConfigPanel configPanel;

    @Setter
    private PrefabBrowserPanel prefabBrowserPanel;

    @Setter
    private Consumer<String> messageCallback;

    public EditorShortcutHandlersImpl(EditorContext context,
                                       EditorToolController toolController,
                                       EditorMenuBar menuBar) {
        this.context = context;
        this.toolController = toolController;
        this.menuBar = menuBar;
    }

    // ========================================================================
    // FILE HANDLERS
    // ========================================================================

    @Override
    public void onNewScene() {
        // Delegate to menu bar which handles unsaved changes dialog
        menuBar.renderFileMenu(); // This triggers the menu item logic
        // Note: In Phase 4, we'll wire this properly via menu bar methods
    }

    @Override
    public void onOpenScene() {
        menuBar.renderFileMenu();
    }

    @Override
    public void onSaveScene() {
        menuBar.renderFileMenu();
    }

    @Override
    public void onSaveSceneAs() {
        menuBar.renderFileMenu();
    }

    @Override
    public void onOpenConfiguration() {
        if (configPanel != null) {
            configPanel.openModal();
        }
    }

    // ========================================================================
    // EDIT HANDLERS
    // ========================================================================

    @Override
    public void onUndo() {
        if (UndoManager.getInstance().undo()) {
            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                scene.markDirty();
            }
            showMessage("Undo");
        }
    }

    @Override
    public void onRedo() {
        if (UndoManager.getInstance().redo()) {
            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                scene.markDirty();
            }
            showMessage("Redo");
        }
    }

    @Override
    public void onCut() {
        // TODO: Implement cut functionality
        showMessage("Cut (not implemented)");
    }

    @Override
    public void onCopy() {
        // TODO: Implement copy functionality
        showMessage("Copy (not implemented)");
    }

    @Override
    public void onPaste() {
        // TODO: Implement paste functionality
        showMessage("Paste (not implemented)");
    }

    @Override
    public void onDelete() {
        // Delegate to entity delete if in entity mode
        EditorModeManager modeManager = context.getModeManager();
        if (modeManager.isEntityMode()) {
            onEntityDelete();
        }
    }

    @Override
    public void onSelectAll() {
        // TODO: Implement select all functionality
        showMessage("Select All (not implemented)");
    }

    @Override
    public void onDuplicate() {
        // TODO: Implement duplicate functionality
        showMessage("Duplicate (not implemented)");
    }

    // ========================================================================
    // VIEW HANDLERS
    // ========================================================================

    @Override
    public void onZoomIn() {
        context.getCamera().adjustZoom(1.0f);
        showMessage("Zoom In");
    }

    @Override
    public void onZoomOut() {
        context.getCamera().adjustZoom(-1.0f);
        showMessage("Zoom Out");
    }

    @Override
    public void onZoomReset() {
        context.getCamera().resetZoom();
        showMessage("Reset Zoom");
    }

    @Override
    public void onToggleGrid() {
        // TODO: Wire to grid visibility toggle
        showMessage("Toggle Grid (not implemented)");
    }

    // ========================================================================
    // MODE HANDLERS
    // ========================================================================

    @Override
    public void onModeTilemap() {
        EditorModeManager modeManager = context.getModeManager();
        if (!modeManager.isTilemapMode()) {
            context.switchToTilemapMode();
            context.getToolManager().setActiveTool(toolController.getBrushTool());
            showMessage("Switched to Tilemap Mode");
        }
    }

    @Override
    public void onModeCollision() {
        EditorModeManager modeManager = context.getModeManager();
        if (!modeManager.isCollisionMode()) {
            context.switchToCollisionMode();
            context.getToolManager().setActiveTool(toolController.getCollisionBrushTool());
            toolController.syncCollisionZLevels();
            showMessage("Switched to Collision Mode");
        }
    }

    @Override
    public void onModeEntity() {
        EditorModeManager modeManager = context.getModeManager();
        if (!modeManager.isEntityMode()) {
            context.switchToEntityMode();
            context.getToolManager().setActiveTool(toolController.getSelectionTool());
            showMessage("Switched to Entity Mode");
        }
    }

    // ========================================================================
    // TILEMAP TOOL HANDLERS
    // ========================================================================

    @Override
    public void onToolTileBrush() {
        if (context.getModeManager().isTilemapMode()) {
            context.getToolManager().setActiveTool(toolController.getBrushTool());
            showMessage("Tile Brush");
        }
    }

    @Override
    public void onToolTileEraser() {
        if (context.getModeManager().isTilemapMode()) {
            context.getToolManager().setActiveTool(toolController.getEraserTool());
            showMessage("Tile Eraser");
        }
    }

    @Override
    public void onToolTileFill() {
        if (context.getModeManager().isTilemapMode()) {
            context.getToolManager().setActiveTool(toolController.getFillTool());
            showMessage("Tile Fill");
        }
    }

    @Override
    public void onToolTileRectangle() {
        if (context.getModeManager().isTilemapMode()) {
            context.getToolManager().setActiveTool(toolController.getRectangleTool());
            showMessage("Tile Rectangle");
        }
    }

    @Override
    public void onToolTilePicker() {
        if (context.getModeManager().isTilemapMode()) {
            context.getToolManager().setActiveTool(toolController.getPickerTool());
            showMessage("Tile Picker");
        }
    }

    // ========================================================================
    // COLLISION TOOL HANDLERS
    // ========================================================================

    @Override
    public void onToolCollisionBrush() {
        if (context.getModeManager().isCollisionMode()) {
            context.getToolManager().setActiveTool(toolController.getCollisionBrushTool());
            showMessage("Collision Brush");
        }
    }

    @Override
    public void onToolCollisionEraser() {
        if (context.getModeManager().isCollisionMode()) {
            context.getToolManager().setActiveTool(toolController.getCollisionEraserTool());
            showMessage("Collision Eraser");
        }
    }

    @Override
    public void onToolCollisionFill() {
        if (context.getModeManager().isCollisionMode()) {
            context.getToolManager().setActiveTool(toolController.getCollisionFillTool());
            showMessage("Collision Fill");
        }
    }

    @Override
    public void onToolCollisionRectangle() {
        if (context.getModeManager().isCollisionMode()) {
            context.getToolManager().setActiveTool(toolController.getCollisionRectangleTool());
            showMessage("Collision Rectangle");
        }
    }

    @Override
    public void onToolCollisionPicker() {
        if (context.getModeManager().isCollisionMode()) {
            context.getToolManager().setActiveTool(toolController.getCollisionPickerTool());
            showMessage("Collision Picker");
        }
    }

    // ========================================================================
    // ENTITY TOOL HANDLERS
    // ========================================================================

    @Override
    public void onToolSelection() {
        if (context.getModeManager().isEntityMode()) {
            context.getToolManager().setActiveTool(toolController.getSelectionTool());
            showMessage("Selection Tool");
        }
    }

    @Override
    public void onToolEntityPlacer() {
        if (context.getModeManager().isEntityMode()) {
            context.getToolManager().setActiveTool(toolController.getEntityPlacerTool());
            showMessage("Entity Placer");
        }
    }

    @Override
    public void onEntityDelete() {
        if (!context.getModeManager().isEntityMode()) {
            return;
        }

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

    @Override
    public void onEntityCancel() {
        if (!context.getModeManager().isEntityMode()) {
            return;
        }

        EditorTool activeTool = context.getToolManager().getActiveTool();

        // If using placer tool, switch to selection and clear prefab selection
        if (activeTool == toolController.getEntityPlacerTool()) {
            context.getToolManager().setActiveTool(toolController.getSelectionTool());
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

    // ========================================================================
    // BRUSH SIZE HANDLERS
    // ========================================================================

    @Override
    public void onBrushSizeIncrease() {
        EditorModeManager modeManager = context.getModeManager();

        if (modeManager.isTilemapMode()) {
            int size = toolController.getBrushTool().getBrushSize();
            if (size < 10) {
                int newSize = size + 1;
                toolController.getBrushTool().setBrushSize(newSize);
                toolController.getEraserTool().setEraserSize(newSize);
                showMessage("Brush Size: " + newSize);
            }
        } else if (modeManager.isCollisionMode()) {
            int size = toolController.getCollisionBrushTool().getBrushSize();
            if (size < 10) {
                int newSize = size + 1;
                toolController.getCollisionBrushTool().setBrushSize(newSize);
                toolController.getCollisionEraserTool().setEraserSize(newSize);
                showMessage("Brush Size: " + newSize);
            }
        }
    }

    @Override
    public void onBrushSizeDecrease() {
        EditorModeManager modeManager = context.getModeManager();

        if (modeManager.isTilemapMode()) {
            int size = toolController.getBrushTool().getBrushSize();
            if (size > 1) {
                int newSize = size - 1;
                toolController.getBrushTool().setBrushSize(newSize);
                toolController.getEraserTool().setEraserSize(newSize);
                showMessage("Brush Size: " + newSize);
            }
        } else if (modeManager.isCollisionMode()) {
            int size = toolController.getCollisionBrushTool().getBrushSize();
            if (size > 1) {
                int newSize = size - 1;
                toolController.getCollisionBrushTool().setBrushSize(newSize);
                toolController.getCollisionEraserTool().setEraserSize(newSize);
                showMessage("Brush Size: " + newSize);
            }
        }
    }

    // ========================================================================
    // Z-LEVEL HANDLERS
    // ========================================================================

    @Override
    public void onZLevelIncrease() {
        if (!context.getModeManager().isCollisionMode()) {
            return;
        }

        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        int z = scene.getCollisionZLevel();
        if (z < 3) {
            scene.setCollisionZLevel(z + 1);
            toolController.syncCollisionZLevels();
            showMessage("Z-Level: " + (z + 1));
        }
    }

    @Override
    public void onZLevelDecrease() {
        if (!context.getModeManager().isCollisionMode()) {
            return;
        }

        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        int z = scene.getCollisionZLevel();
        if (z > 0) {
            scene.setCollisionZLevel(z - 1);
            toolController.syncCollisionZLevels();
            showMessage("Z-Level: " + (z - 1));
        }
    }

    // ========================================================================
    // PLAY MODE HANDLERS
    // ========================================================================

    @Override
    public void onPlayToggle() {
        // TODO: Wire to play mode controller
        showMessage("Play/Pause (not implemented)");
    }

    @Override
    public void onPlayStop() {
        // TODO: Wire to play mode controller
        showMessage("Stop (not implemented)");
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }
}
