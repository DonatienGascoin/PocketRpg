package com.pocket.rpg.editor.shortcut;

import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.EditorSceneController;
import com.pocket.rpg.editor.core.MavenCompiler;
import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.EditorToolController;
import com.pocket.rpg.editor.PrefabEditController;
import com.pocket.rpg.editor.PlayModeController;
import com.pocket.rpg.editor.panels.CollisionPanel;
import com.pocket.rpg.editor.panels.TilesetPalettePanel;
import com.pocket.rpg.editor.panels.hierarchy.EntityCreationService;
import com.pocket.rpg.editor.scene.DirtyTracker;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.CompoundCommand;
import com.pocket.rpg.editor.undo.commands.RemoveEntityCommand;
import com.pocket.rpg.editor.ui.EditorMenuBar;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Implementation of shortcut handlers that wires to the editor systems.
 * This class bridges the shortcut system with the actual editor functionality.
 * Tool shortcuts work based on hierarchy selection state (tilemap layer, collision layer, or entity mode).
 */
public class EditorShortcutHandlersImpl implements EditorShortcutHandlers {

    private final EditorContext context;
    private final EditorToolController toolController;
    private final EditorMenuBar menuBar;

    @Setter
    private com.pocket.rpg.editor.panels.ConfigurationPanel configurationPanel;

    @Setter
    private PlayModeController playModeController;

    @Setter
    private EditorSceneController sceneController;

    // Panels needed for toggle shortcuts (not for tool visibility)
    @Setter
    private TilesetPalettePanel tilesetPalettePanel;

    @Setter
    private CollisionPanel collisionPanel;

    @Setter
    private EntityCreationService entityCreationService;

    @Setter
    private EditorModeManager modeManager;

    @Setter
    private PrefabEditController prefabEditController;

    @Setter
    private DirtyTracker activeDirtyTracker;

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
        if (modeManager != null && modeManager.isPrefabEditMode()) {
            if (prefabEditController != null) {
                prefabEditController.save();
            }
            return;
        }
        if (modeManager != null && !modeManager.isSceneMode()) {
            showMessage("Scene save disabled in current mode");
            return;
        }
        menuBar.triggerSaveScene();
    }

    @Override
    public void onSaveSceneAs() {
        menuBar.triggerSaveSceneAs();
    }

    @Override
    public void onOpenConfiguration() {
        if (configurationPanel != null) {
            configurationPanel.toggle();
        }
    }

    @Override
    public void onReloadScene() {
        if (MavenCompiler.isCompiling()) return;
        if (sceneController == null) return;

        MavenCompiler.compileAsync(
                () -> {
                    showMessage("Compiled, reloading...");
                    sceneController.reloadScene();
                },
                error -> {
                    System.err.println("Compilation failed: " + error);
                    showMessage("Compilation failed");
                }
        );
    }

    // ========================================================================
    // EDIT HANDLERS
    // ========================================================================

    @Override
    public void onUndo() {
        if (modeManager != null && modeManager.isPlayMode()) {
            return;
        }
        if (UndoManager.getInstance().undo()) {
            if (activeDirtyTracker != null) {
                activeDirtyTracker.markDirty();
            }
            showMessage("Undo");
        }
    }

    @Override
    public void onRedo() {
        if (modeManager != null && modeManager.isPlayMode()) {
            return;
        }
        if (UndoManager.getInstance().redo()) {
            if (activeDirtyTracker != null) {
                activeDirtyTracker.markDirty();
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
        if (modeManager != null && !modeManager.isSceneMode()) {
            return;
        }
        onEntityDelete();
    }

    @Override
    public void onSelectAll() {
        // TODO: Implement select all functionality
        showMessage("Select All (not implemented)");
    }

    @Override
    public void onDuplicate() {
        if (modeManager != null && !modeManager.isSceneMode()) {
            return;
        }
        if (entityCreationService == null) {
            return;
        }
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        // Snapshot selection to avoid ConcurrentModificationException
        List<EditorGameObject> selected = new ArrayList<>(scene.getSelectedEntities());
        if (selected.isEmpty()) {
            showMessage("No entity selected");
            return;
        }

        Set<EditorGameObject> copies = new HashSet<>();
        for (EditorGameObject entity : selected) {
            copies.add(entityCreationService.duplicateEntity(entity));
        }
        scene.setSelection(copies);
        showMessage(selected.size() == 1
                ? "Duplicated: " + selected.get(0).getName()
                : "Duplicated " + selected.size() + " entities");
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

    @Override
    public void onFocusSelected() {
        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        Set<EditorGameObject> selected = scene.getSelectedEntities();
        if (selected.isEmpty()) {
            return;
        }

        float sumX = 0, sumY = 0;
        for (EditorGameObject entity : selected) {
            Vector3f pos = entity.getPosition();
            sumX += pos.x;
            sumY += pos.y;
        }
        float centerX = sumX / selected.size();
        float centerY = sumY / selected.size();

        context.getCamera().centerOn(centerX, centerY);

        if (selected.size() == 1) {
            showMessage("Focused on " + selected.iterator().next().getName());
        } else {
            showMessage("Focused on " + selected.size() + " entities");
        }
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
    // PAINT TOOL HANDLERS (keys 1-5, context-dependent: tilemap or collision)
    // ========================================================================

    private boolean isTilemapLayerSelected() {
        EditorSelectionManager selectionManager = context.getSelectionManager();
        return selectionManager != null && selectionManager.isTilemapLayerSelected();
    }

    private boolean isCollisionLayerSelected() {
        EditorSelectionManager selectionManager = context.getSelectionManager();
        return selectionManager != null && selectionManager.isCollisionLayerSelected();
    }

    /**
     * Returns true if tools can be used (scene mode or prefab edit mode, but not play mode).
     */
    private boolean canUseTools() {
        if (modeManager == null) return true;
        return modeManager.isSceneMode() || modeManager.isPrefabEditMode();
    }

    @Override
    public void onToolBrush() {
        if (!canUseTools()) return;
        if (isTilemapLayerSelected()) {
            context.getToolManager().setActiveTool(toolController.getBrushTool());
            showMessage("Tile Brush");
        } else if (isCollisionLayerSelected()) {
            context.getToolManager().setActiveTool(toolController.getCollisionBrushTool());
            showMessage("Collision Brush");
        }
    }

    @Override
    public void onToolEraser() {
        if (!canUseTools()) return;
        if (isTilemapLayerSelected()) {
            context.getToolManager().setActiveTool(toolController.getEraserTool());
            showMessage("Tile Eraser");
        } else if (isCollisionLayerSelected()) {
            context.getToolManager().setActiveTool(toolController.getCollisionEraserTool());
            showMessage("Collision Eraser");
        }
    }

    @Override
    public void onToolFill() {
        if (!canUseTools()) return;
        if (isTilemapLayerSelected()) {
            context.getToolManager().setActiveTool(toolController.getFillTool());
            showMessage("Tile Fill");
        } else if (isCollisionLayerSelected()) {
            context.getToolManager().setActiveTool(toolController.getCollisionFillTool());
            showMessage("Collision Fill");
        }
    }

    @Override
    public void onToolRectangle() {
        if (!canUseTools()) return;
        if (isTilemapLayerSelected()) {
            context.getToolManager().setActiveTool(toolController.getRectangleTool());
            showMessage("Tile Rectangle");
        } else if (isCollisionLayerSelected()) {
            context.getToolManager().setActiveTool(toolController.getCollisionRectangleTool());
            showMessage("Collision Rectangle");
        }
    }

    @Override
    public void onToolPicker() {
        if (!canUseTools()) return;
        if (isTilemapLayerSelected()) {
            context.getToolManager().setActiveTool(toolController.getPickerTool());
            showMessage("Tile Picker");
        } else if (isCollisionLayerSelected()) {
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
        if (scene == null) return;

        // Snapshot selection to avoid ConcurrentModificationException
        List<EditorGameObject> selected = new ArrayList<>(scene.getSelectedEntities());
        if (selected.isEmpty()) return;

        // Filter out children whose parent is also selected (parent removal handles them)
        Set<EditorGameObject> selectedSet = new HashSet<>(selected);
        List<EditorGameObject> roots = selected.stream()
                .filter(e -> !isAncestorSelected(e, selectedSet))
                .toList();

        if (roots.size() == 1) {
            UndoManager.getInstance().execute(new RemoveEntityCommand(scene, roots.get(0)));
            showMessage("Deleted: " + roots.get(0).getName());
        } else {
            List<EditorCommand> commands = roots.stream()
                    .map(e -> (EditorCommand) new RemoveEntityCommand(scene, e))
                    .toList();
            UndoManager.getInstance().execute(new CompoundCommand("Delete " + roots.size() + " entities", commands));
            showMessage("Deleted " + roots.size() + " entities");
        }
    }

    private boolean isAncestorSelected(EditorGameObject entity, Set<EditorGameObject> selectedSet) {
        EditorGameObject parent = entity.getParent();
        while (parent != null) {
            if (selectedSet.contains(parent)) return true;
            parent = parent.getParent();
        }
        return false;
    }

    @Override
    public void onEntityCancel() {
        if (modeManager != null && modeManager.isPrefabEditMode()) {
            return; // Escape handled by PrefabEditController in Plan 5
        }
        // Clear tool selection (brush/fill/rectangle tile selections)
        ToolManager toolManager = context.getToolManager();
        if (toolManager != null) {
            EditorTool tool = toolManager.getActiveTool();
            if (tool instanceof com.pocket.rpg.editor.tools.TileBrushTool brushTool) {
                brushTool.setSelection(null);
            } else if (tool instanceof com.pocket.rpg.editor.tools.TileFillTool fillTool) {
                fillTool.setSelection(null);
            } else if (tool instanceof com.pocket.rpg.editor.tools.TileRectangleTool rectTool) {
                rectTool.setSelection(null);
            }
        }

        // Deselect current entity
        EditorScene scene = context.getCurrentScene();
        if (scene != null && scene.getSelectedEntity() != null) {
            scene.setSelectedEntity(null);
            showMessage("Deselected");
        }
    }

    // ========================================================================
    // TRANSFORM TOOL HANDLERS
    // ========================================================================

    @Override
    public void onToolMove() {
        if (!canUseTools()) return;
        context.getToolManager().setActiveTool(toolController.getMoveTool());
        showMessage("Move Tool");
    }

    @Override
    public void onToolRotate() {
        if (!canUseTools()) return;
        context.getToolManager().setActiveTool(toolController.getRotateTool());
        showMessage("Rotate Tool");
    }

    @Override
    public void onToolScale() {
        if (!canUseTools()) return;
        context.getToolManager().setActiveTool(toolController.getScaleTool());
        showMessage("Scale Tool");
    }

    // ========================================================================
    // BRUSH SIZE HANDLERS
    // ========================================================================

    @Override
    public void onBrushSizeIncrease() {
        // Adjust brush size for whichever panel is open
        if (isTilemapLayerSelected()) {
            int size = toolController.getBrushTool().getBrushSize();
            if (size < 10) {
                int newSize = size + 1;
                toolController.getBrushTool().setBrushSize(newSize);
                toolController.getEraserTool().setEraserSize(newSize);
                showMessage("Brush Size: " + newSize);
            }
        } else if (isCollisionLayerSelected()) {
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
        if (isTilemapLayerSelected()) {
            int size = toolController.getBrushTool().getBrushSize();
            if (size > 1) {
                int newSize = size - 1;
                toolController.getBrushTool().setBrushSize(newSize);
                toolController.getEraserTool().setEraserSize(newSize);
                showMessage("Brush Size: " + newSize);
            }
        } else if (isCollisionLayerSelected()) {
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
        if (!isCollisionLayerSelected()) {
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
        if (!isCollisionLayerSelected()) {
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
        if (modeManager != null && modeManager.isPrefabEditMode()) {
            showMessage("Exit prefab edit mode before entering play mode");
            return;
        }
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

    // NOTE: Configuration and Animator handlers moved to their respective panels

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }
}
