package com.pocket.rpg.editor;

import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.SelectionChangedEvent;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * Centralized manager for editor selection state.
 * Selection is independent of tools and modes - it persists when switching tools or panels.
 */
public class EditorSelectionManager {

    /**
     * Types of items that can be selected in the editor.
     */
    public enum SelectionType {
        NONE,
        ENTITY,
        TILEMAP_LAYER,
        COLLISION_LAYER,
        CAMERA,
        ASSET
    }

    @Getter
    private SelectionType selectionType = SelectionType.NONE;

    @Getter
    private int selectedLayerIndex = -1;

    // Asset selection
    @Getter
    private String selectedAssetPath = null;

    @Getter
    private Class<?> selectedAssetType = null;

    @Setter
    private EditorScene scene;

    // ========================================================================
    // SELECTION METHODS
    // ========================================================================

    /**
     * Selects a single entity.
     */
    public void selectEntity(EditorGameObject entity) {
        if (scene != null) {
            scene.setSelection(Set.of(entity));
        }
        selectedLayerIndex = -1;  // Clear layer selection
        clearAssetSelection();
        setSelectionType(SelectionType.ENTITY);
    }

    /**
     * Selects multiple entities.
     */
    public void selectEntities(Set<EditorGameObject> entities) {
        if (scene != null) {
            scene.setSelection(entities);
        }
        selectedLayerIndex = -1;  // Clear layer selection
        clearAssetSelection();
        setSelectionType(entities.isEmpty() ? SelectionType.NONE : SelectionType.ENTITY);
    }

    /**
     * Toggles selection of an entity (for Ctrl+Click).
     */
    public void toggleEntitySelection(EditorGameObject entity) {
        if (scene != null) {
            scene.toggleSelection(entity);
            if (scene.getSelectedEntities().isEmpty()) {
                setSelectionType(SelectionType.NONE);
            } else {
                selectedLayerIndex = -1;  // Clear layer selection
                clearAssetSelection();
                setSelectionType(SelectionType.ENTITY);
            }
        }
    }

    /**
     * Selects the camera.
     */
    public void selectCamera() {
        clearEntitySelection();
        selectedLayerIndex = -1;
        clearAssetSelection();
        setSelectionType(SelectionType.CAMERA);
    }

    /**
     * Selects a tilemap layer by index.
     */
    public void selectTilemapLayer(int layerIndex) {
        clearEntitySelection();
        this.selectedLayerIndex = layerIndex;
        // Set the active layer on the scene for painting
        if (scene != null) {
            scene.setActiveLayer(layerIndex);
        }
        clearAssetSelection();
        setSelectionType(SelectionType.TILEMAP_LAYER);
    }

    /**
     * Selects the collision layer.
     */
    public void selectCollisionLayer() {
        clearEntitySelection();
        selectedLayerIndex = -1;
        clearAssetSelection();
        setSelectionType(SelectionType.COLLISION_LAYER);
    }

    /**
     * Selects an asset from the asset browser.
     */
    public void selectAsset(String path, Class<?> type) {
        clearEntitySelection();
        selectedLayerIndex = -1;
        this.selectedAssetPath = path;
        this.selectedAssetType = type;
        setSelectionType(SelectionType.ASSET);
    }

    /**
     * Clears all selection.
     */
    public void clearSelection() {
        clearEntitySelection();
        selectedLayerIndex = -1;
        clearAssetSelection();
        setSelectionType(SelectionType.NONE);
    }

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Returns true if any entity is selected.
     */
    public boolean hasEntitySelection() {
        return selectionType == SelectionType.ENTITY &&
               scene != null &&
               !scene.getSelectedEntities().isEmpty();
    }

    /**
     * Returns true if anything is selected.
     */
    public boolean hasSelection() {
        return selectionType != SelectionType.NONE;
    }

    /**
     * Gets the selected entities (empty set if none).
     */
    public Set<EditorGameObject> getSelectedEntities() {
        if (scene == null) return Set.of();
        return scene.getSelectedEntities();
    }

    /**
     * Gets the first selected entity, or null if none.
     */
    public EditorGameObject getFirstSelectedEntity() {
        Set<EditorGameObject> selected = getSelectedEntities();
        return selected.isEmpty() ? null : selected.iterator().next();
    }

    /**
     * Returns true if camera is selected.
     */
    public boolean isCameraSelected() {
        return selectionType == SelectionType.CAMERA;
    }

    /**
     * Returns true if a tilemap layer is selected.
     */
    public boolean isTilemapLayerSelected() {
        return selectionType == SelectionType.TILEMAP_LAYER;
    }

    /**
     * Returns true if collision layer is selected.
     */
    public boolean isCollisionLayerSelected() {
        return selectionType == SelectionType.COLLISION_LAYER;
    }

    /**
     * Returns true if an asset is selected.
     */
    public boolean isAssetSelected() {
        return selectionType == SelectionType.ASSET && selectedAssetPath != null;
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private void setSelectionType(SelectionType type) {
        if (this.selectionType != type) {
            SelectionType previousType = this.selectionType;
            this.selectionType = type;
            publishSelectionEvent(previousType);
        }
    }

    private void clearEntitySelection() {
        if (scene != null) {
            scene.clearSelection();
            scene.setActiveLayer(-1);
        }
    }

    private void clearAssetSelection() {
        selectedAssetPath = null;
        selectedAssetType = null;
    }

    private void publishSelectionEvent(SelectionType previousType) {
        EditorEventBus.get().publish(new SelectionChangedEvent(selectionType, previousType));
    }
}
