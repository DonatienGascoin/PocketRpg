package com.pocket.rpg.editor.shortcut;

import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.EditorToolController;
import com.pocket.rpg.editor.PlayModeController;
import com.pocket.rpg.editor.panels.CollisionPanel;
import com.pocket.rpg.editor.panels.ConfigPanel;
import com.pocket.rpg.editor.panels.TilesetPalettePanel;
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
 * Tool shortcuts only work when their corresponding panel is open.
 */
public class EditorShortcutHandlersImpl implements EditorShortcutHandlers {

    private final EditorContext context;
    private final EditorToolController toolController;
    private final EditorMenuBar menuBar;

    @Setter
    private ConfigPanel configPanel;

    @Setter
    private PlayModeController playModeController;

    @Setter
    private TilesetPalettePanel tilesetPalettePanel;

    @Setter
    private CollisionPanel collisionPanel;

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
        menuBar.triggerNewScene();
    }

    @Override
    public void onOpenScene() {
        menuBar.triggerOpenScene();
    }

    @Override
    public void onSaveScene() {
        menuBar.triggerSaveScene();
    }

    @Override
    public void onSaveSceneAs() {
        menuBar.triggerSaveSceneAs();
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
        // Entity delete works anytime (only affects selected entity)
        onEntityDelete();
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
    // PANEL TOGGLE HANDLERS
    // ========================================================================

    @Override
    public void onPanelTilesetToggle() {
        if (tilesetPalettePanel != null) {
            tilesetPalettePanel.toggle();
            showMessage(tilesetPalettePanel.isOpen() ? "Tileset Palette opened" : "Tileset Palette closed");
        }
    }

    @Override
    public void onPanelCollisionToggle() {
        if (collisionPanel != null) {
            collisionPanel.toggle();
            showMessage(collisionPanel.isOpen() ? "Collision Panel opened" : "Collision Panel closed");
        }
    }

    // ========================================================================
    // TILEMAP TOOL HANDLERS (only work when Tileset Palette is open)
    // ========================================================================

    private boolean isTilesetPaletteVisible() {
        return tilesetPalettePanel != null && tilesetPalettePanel.isContentVisible();
    }

    @Override
    public void onToolTileBrush() {
        if (isTilesetPaletteVisible()) {
            context.getToolManager().setActiveTool(toolController.getBrushTool());
            showMessage("Tile Brush");
        }
    }

    @Override
    public void onToolTileEraser() {
        if (isTilesetPaletteVisible()) {
            context.getToolManager().setActiveTool(toolController.getEraserTool());
            showMessage("Tile Eraser");
        }
    }

    @Override
    public void onToolTileFill() {
        if (isTilesetPaletteVisible()) {
            context.getToolManager().setActiveTool(toolController.getFillTool());
            showMessage("Tile Fill");
        }
    }

    @Override
    public void onToolTileRectangle() {
        if (isTilesetPaletteVisible()) {
            context.getToolManager().setActiveTool(toolController.getRectangleTool());
            showMessage("Tile Rectangle");
        }
    }

    @Override
    public void onToolTilePicker() {
        if (isTilesetPaletteVisible()) {
            context.getToolManager().setActiveTool(toolController.getPickerTool());
            showMessage("Tile Picker");
        }
    }

    // ========================================================================
    // COLLISION TOOL HANDLERS (only work when Collision Panel is open)
    // ========================================================================

    private boolean isCollisionPanelVisible() {
        return collisionPanel != null && collisionPanel.isContentVisible();
    }

    @Override
    public void onToolCollisionBrush() {
        if (isCollisionPanelVisible()) {
            context.getToolManager().setActiveTool(toolController.getCollisionBrushTool());
            showMessage("Collision Brush");
        }
    }

    @Override
    public void onToolCollisionEraser() {
        if (isCollisionPanelVisible()) {
            context.getToolManager().setActiveTool(toolController.getCollisionEraserTool());
            showMessage("Collision Eraser");
        }
    }

    @Override
    public void onToolCollisionFill() {
        if (isCollisionPanelVisible()) {
            context.getToolManager().setActiveTool(toolController.getCollisionFillTool());
            showMessage("Collision Fill");
        }
    }

    @Override
    public void onToolCollisionRectangle() {
        if (isCollisionPanelVisible()) {
            context.getToolManager().setActiveTool(toolController.getCollisionRectangleTool());
            showMessage("Collision Rectangle");
        }
    }

    @Override
    public void onToolCollisionPicker() {
        if (isCollisionPanelVisible()) {
            context.getToolManager().setActiveTool(toolController.getCollisionPickerTool());
            showMessage("Collision Picker");
        }
    }

    // ========================================================================
    // ENTITY TOOL HANDLERS
    // ========================================================================

    @Override
    public void onToolSelection() {
        // Selection tool is always available
        context.getToolManager().setActiveTool(toolController.getSelectionTool());
        showMessage("Selection Tool");
    }

    @Override
    public void onToolEntityPlacer() {
        // EntityPlacerTool removed - drag from Asset Browser instead
    }

    @Override
    public void onEntityDelete() {
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
        // Deselect current entity
        EditorScene scene = context.getCurrentScene();
        if (scene != null && scene.getSelectedEntity() != null) {
            scene.setSelectedEntity(null);
            showMessage("Deselected");
        }
    }

    // ========================================================================
    // BRUSH SIZE HANDLERS
    // ========================================================================

    @Override
    public void onBrushSizeIncrease() {
        // Adjust brush size for whichever panel is open
        if (isTilesetPaletteVisible()) {
            int size = toolController.getBrushTool().getBrushSize();
            if (size < 10) {
                int newSize = size + 1;
                toolController.getBrushTool().setBrushSize(newSize);
                toolController.getEraserTool().setEraserSize(newSize);
                showMessage("Brush Size: " + newSize);
            }
        } else if (isCollisionPanelVisible()) {
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
        // Adjust brush size for whichever panel is open
        if (isTilesetPaletteVisible()) {
            int size = toolController.getBrushTool().getBrushSize();
            if (size > 1) {
                int newSize = size - 1;
                toolController.getBrushTool().setBrushSize(newSize);
                toolController.getEraserTool().setEraserSize(newSize);
                showMessage("Brush Size: " + newSize);
            }
        } else if (isCollisionPanelVisible()) {
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
        if (!isCollisionPanelVisible()) {
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
        if (!isCollisionPanelVisible()) {
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
        if (playModeController == null) {
            showMessage("Play mode not available");
            return;
        }

        switch (playModeController.getState()) {
            case STOPPED -> playModeController.play();
            case PLAYING -> playModeController.pause();
            case PAUSED -> playModeController.resume();
        }
    }

    @Override
    public void onPlayStop() {
        if (playModeController == null) {
            showMessage("Play mode not available");
            return;
        }

        if (playModeController.isActive()) {
            playModeController.stop();
        }
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
