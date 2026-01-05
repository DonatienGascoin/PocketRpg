package com.pocket.rpg.editor.scene;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Renderable;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a scene being edited in the Scene Editor.
 * <p>
 * Manages:
 * - Tilemap layers (TilemapLayer wrappers)
 * - Collision map (CollisionMap)
 * - Entity instances (EditorEntity) with parent-child hierarchy
 * - Camera settings (SceneCameraSettings)
 * - Multi-selection state
 */
public class EditorScene {

    @Getter
    @Setter
    private String name = "Untitled";

    @Getter
    @Setter
    private String filePath = null;

    @Getter
    private boolean dirty = false;

    // ========================================================================
    // LAYER MANAGEMENT
    // ========================================================================

    private final List<TilemapLayer> layers = new ArrayList<>();

    @Getter
    private int activeLayerIndex = -1;

    @Getter
    @Setter
    private LayerVisibilityMode visibilityMode = LayerVisibilityMode.ALL;

    @Getter
    @Setter
    private float dimmedOpacity = 0.5f;

    // ========================================================================
    // COLLISION
    // ========================================================================

    @Getter
    private final CollisionMap collisionMap;

    @Getter
    @Setter
    private boolean collisionVisible = true;

    @Getter
    @Setter
    private int collisionZLevel = 0;

    @Getter
    @Setter
    private float collisionOpacity = 1f;

    // ========================================================================
    // ENTITY MANAGEMENT
    // ========================================================================

    private final List<EditorEntity> entities = new ArrayList<>();

    private final Set<EditorEntity> selectedEntities = new HashSet<>();

    // ========================================================================
    // CAMERA SETTINGS
    // ========================================================================

    @Getter
    private final SceneCameraSettings cameraSettings = new SceneCameraSettings();

    // ========================================================================
    // LEGACY SELECTION (for non-entity objects)
    // ========================================================================

    @Getter
    @Setter
    private GameObject selectedObject = null;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public EditorScene() {
        this.collisionMap = new CollisionMap();
    }

    public EditorScene(String name) {
        this.name = name;
        this.collisionMap = new CollisionMap();
    }

    // ========================================================================
    // DIRTY STATE
    // ========================================================================

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    // ========================================================================
    // LAYER MANAGEMENT
    // ========================================================================

    public TilemapLayer addLayer(String layerName) {
        int zIndex = layers.isEmpty() ? 0 : layers.get(layers.size() - 1).getZIndex() + 1;

        TilemapLayer layer = new TilemapLayer(layerName, zIndex);
        layers.add(layer);

        activeLayerIndex = layers.size() - 1;

        markDirty();
        return layer;
    }

    public TilemapLayer addLayer(String layerName, String spritesheetPath, int spriteWidth, int spriteHeight) {
        TilemapLayer layer = addLayer(layerName);

        var sprite = Assets.<Sprite>load(spritesheetPath);
        if (sprite == null) {
            System.err.println("Failed to load sprite: " + spritesheetPath);
            return layer;
        }

        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), spriteWidth, spriteHeight);
        layer.setSpriteSheet(sheet, spritesheetPath, spriteWidth, spriteHeight);

        return layer;
    }

    public void addExistingLayer(TilemapLayer layer) {
        layers.add(layer);
        if (activeLayerIndex == -1) {
            activeLayerIndex = 0;
        }
    }

    public void removeLayer(int index) {
        if (index < 0 || index >= layers.size()) return;

        TilemapLayer layer = layers.remove(index);
        layer.getGameObject().destroy();

        if (activeLayerIndex >= layers.size()) {
            activeLayerIndex = layers.size() - 1;
        }

        markDirty();
    }

    public TilemapLayer getLayer(int index) {
        if (index < 0 || index >= layers.size()) return null;
        return layers.get(index);
    }

    public TilemapLayer getActiveLayer() {
        if (activeLayerIndex < 0 || activeLayerIndex >= layers.size()) return null;
        return layers.get(activeLayerIndex);
    }

    public void setActiveLayer(int index) {
        if (index >= -1 && index < layers.size()) {
            activeLayerIndex = index;
        }
    }

    public List<TilemapLayer> getLayers() {
        return new ArrayList<>(layers);
    }

    public int getLayerCount() {
        return layers.size();
    }

    public void moveLayerUp(int index) {
        if (index < 0 || index >= layers.size() - 1) return;
        swapLayers(index, index + 1);
    }

    public void moveLayerDown(int index) {
        if (index <= 0 || index >= layers.size()) return;
        swapLayers(index, index - 1);
    }

    public void swapLayers(int indexA, int indexB) {
        if (indexA < 0 || indexA >= layers.size()) return;
        if (indexB < 0 || indexB >= layers.size()) return;
        if (indexA == indexB) return;

        TilemapLayer layerA = layers.get(indexA);
        TilemapLayer layerB = layers.get(indexB);

        int tempZ = layerA.getZIndex();
        layerA.setZIndex(layerB.getZIndex());
        layerB.setZIndex(tempZ);

        layers.set(indexA, layerB);
        layers.set(indexB, layerA);

        if (activeLayerIndex == indexA) {
            activeLayerIndex = indexB;
        } else if (activeLayerIndex == indexB) {
            activeLayerIndex = indexA;
        }

        markDirty();
    }

    public void renameLayer(int index, String newName) {
        TilemapLayer layer = getLayer(index);
        if (layer != null) {
            layer.setName(newName);
            layer.getGameObject().setName(newName);
            markDirty();
        }
    }

    // ========================================================================
    // LAYER VISIBILITY
    // ========================================================================

    public boolean isLayerVisible(int index) {
        TilemapLayer layer = getLayer(index);
        if (layer == null || !layer.isVisible()) {
            return false;
        }

        return switch (visibilityMode) {
            case ALL -> true;
            case SELECTED_ONLY -> index == activeLayerIndex;
            case SELECTED_DIMMED -> true;
        };
    }

    public float getLayerOpacity(int index) {
        if (!isLayerVisible(index)) {
            return 0f;
        }

        if (visibilityMode == LayerVisibilityMode.SELECTED_DIMMED && index != activeLayerIndex) {
            return dimmedOpacity;
        }

        return 1.0f;
    }

    // ========================================================================
    // ENTITY MANAGEMENT
    // ========================================================================

    /**
     * Adds an entity to the scene.
     */
    public void addEntity(EditorEntity entity) {
        if (entity == null) return;
        entities.add(entity);
        markDirty();
    }

    /**
     * Removes an entity and all its children from the scene.
     */
    public void removeEntity(EditorEntity entity) {
        if (entity == null) return;

        // Remove children first (copy list to avoid concurrent modification)
        List<EditorEntity> children = new ArrayList<>(entity.getChildren());
        for (EditorEntity child : children) {
            removeEntity(child);
        }

        // Clear from parent
        entity.clearParent();

        // Remove from entities list
        entities.remove(entity);

        // Remove from selection
        selectedEntities.remove(entity);

        markDirty();
    }

    /**
     * Gets all entities (copy).
     */
    public List<EditorEntity> getEntities() {
        return new ArrayList<>(entities);
    }

    /**
     * Finds an entity at the given world position.
     * Searches in reverse order (top entities first).
     */
    public EditorEntity findEntityAt(float worldX, float worldY) {
        for (int i = entities.size() - 1; i >= 0; i--) {
            EditorEntity entity = entities.get(i);
            Vector3f pos = entity.getPositionRef();
            Vector2f size = entity.getCurrentSize();

            if (size == null) {
                size = new Vector2f(1f, 1f);
            }

            float halfW = size.x / 2f;
            float minX = pos.x - halfW;
            float maxX = pos.x + halfW;
            float minY = pos.y;
            float maxY = pos.y + size.y;

            if (worldX >= minX && worldX <= maxX && worldY >= minY && worldY <= maxY) {
                return entity;
            }
        }

        return null;
    }

    // ========================================================================
    // MULTI-SELECTION
    // ========================================================================

    /**
     * Gets all selected entities (unmodifiable).
     */
    public Set<EditorEntity> getSelectedEntities() {
        return Collections.unmodifiableSet(selectedEntities);
    }

    /**
     * Checks if an entity is selected.
     */
    public boolean isSelected(EditorEntity entity) {
        return selectedEntities.contains(entity);
    }

    /**
     * Adds an entity to the selection.
     */
    public void addToSelection(EditorEntity entity) {
        if (entity != null) {
            selectedEntities.add(entity);
        }
    }

    /**
     * Toggles an entity's selection state.
     */
    public void toggleSelection(EditorEntity entity) {
        if (entity == null) return;
        if (selectedEntities.contains(entity)) {
            selectedEntities.remove(entity);
        } else {
            selectedEntities.add(entity);
        }
    }

    /**
     * Sets the selection to a specific set of entities.
     */
    public void setSelection(Set<EditorEntity> entities) {
        selectedEntities.clear();
        if (entities != null) {
            selectedEntities.addAll(entities);
        }
    }

    /**
     * Clears all entity selection.
     */
    public void clearSelection() {
        selectedEntities.clear();
    }

    /**
     * Gets the single selected entity (for backward compatibility).
     * Returns null if 0 or >1 entities selected.
     */
    public EditorEntity getSelectedEntity() {
        if (selectedEntities.size() == 1) {
            return selectedEntities.iterator().next();
        }
        return null;
    }

    /**
     * Sets single entity selection (for backward compatibility).
     */
    public void setSelectedEntity(EditorEntity entity) {
        selectedEntities.clear();
        if (entity != null) {
            selectedEntities.add(entity);
            this.selectedObject = null;
        }
    }

    // ========================================================================
    // HIERARCHY
    // ========================================================================

    /**
     * Gets all root entities (entities without a parent), sorted by order.
     */
    public List<EditorEntity> getRootEntities() {
        List<EditorEntity> roots = new ArrayList<>();
        for (EditorEntity entity : entities) {
            if (entity.getParentId() == null || entity.getParentId().isEmpty()) {
                roots.add(entity);
            }
        }
        roots.sort(Comparator.comparingInt(EditorEntity::getOrder));
        return roots;
    }

    /**
     * Resolves parent-child relationships after loading.
     * Call this after all entities have been added from deserialization.
     */
    public void resolveHierarchy() {
        // Build lookup map
        Map<String, EditorEntity> byId = entities.stream()
                .collect(Collectors.toMap(EditorEntity::getId, Function.identity()));

        // Clear existing transient relationships
        for (EditorEntity entity : entities) {
            entity.getChildrenMutable().clear();
        }

        // Rebuild relationships
        for (EditorEntity entity : entities) {
            String parentId = entity.getParentId();
            if (parentId != null && !parentId.isEmpty()) {
                EditorEntity parent = byId.get(parentId);
                if (parent != null) {
                    entity.setParent(parent);
                } else {
                    System.err.println("Warning: Entity " + entity.getId() +
                            " references missing parent " + parentId);
                    entity.setParentId(null);
                }
            }
        }

        // Ensure proper order indices for all levels
        reindexSiblings(null); // Root entities
        for (EditorEntity entity : entities) {
            if (entity.hasChildren()) {
                reindexSiblings(entity);
            }
        }
    }

    /**
     * Reindexes sibling order after hierarchy changes.
     * Pass null for root-level entities.
     */
    public void reindexSiblings(EditorEntity parent) {
        List<EditorEntity> siblings;
        if (parent == null) {
            siblings = getRootEntities();
        } else {
            siblings = new ArrayList<>(parent.getChildren());
        }

        siblings.sort(Comparator.comparingInt(EditorEntity::getOrder));

        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setOrder(i);
        }
    }

    /**
     * Inserts an entity at a specific position among siblings.
     * Handles reparenting and order assignment correctly.
     *
     * @param entity      Entity to move
     * @param newParent   New parent (null for root)
     * @param insertIndex Position to insert at (0 = first)
     */
    public void insertEntityAtPosition(EditorEntity entity, EditorEntity newParent, int insertIndex) {
        // First, detach from current parent and reindex old siblings
        EditorEntity oldParent = entity.getParent();
        if (oldParent != null) {
            oldParent.getChildrenMutable().remove(entity);
            // Reindex old siblings
            List<EditorEntity> oldSiblings = new ArrayList<>(oldParent.getChildrenMutable());
            oldSiblings.sort(Comparator.comparingInt(EditorEntity::getOrder));
            for (int i = 0; i < oldSiblings.size(); i++) {
                oldSiblings.get(i).setOrder(i);
            }
        }
        
        // Clear transient parent first
        entity.setParentDirect(null);
        entity.setParentId(newParent != null ? newParent.getId() : null);
        
        // Get target siblings list (now entity has new parentId set)
        List<EditorEntity> siblings;
        if (newParent == null) {
            // Root level - get all root entities except this one
            siblings = new ArrayList<>();
            for (EditorEntity e : entities) {
                if (e != entity && (e.getParentId() == null || e.getParentId().isEmpty())) {
                    siblings.add(e);
                }
            }
        } else {
            // Child level - get parent's children except this one
            siblings = new ArrayList<>();
            for (EditorEntity child : newParent.getChildrenMutable()) {
                if (child != entity) {
                    siblings.add(child);
                }
            }
        }
        
        // Sort by current order
        siblings.sort(Comparator.comparingInt(EditorEntity::getOrder));
        
        // Clamp insert position
        int idx = Math.max(0, Math.min(insertIndex, siblings.size()));
        
        // Insert entity at position
        siblings.add(idx, entity);
        
        // Reassign orders based on list position
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setOrder(i);
        }
        
        // Update transient parent reference and add to parent's children
        if (newParent != null) {
            entity.setParentDirect(newParent);
            newParent.getChildrenMutable().add(entity);
        }
    }

    // ========================================================================
    // RENDERING SUPPORT
    // ========================================================================

    public List<Renderable> getRenderables() {
        List<Renderable> renderables = new ArrayList<>();

        for (int i = 0; i < layers.size(); i++) {
            if (isLayerVisible(i)) {
                TilemapLayer layer = layers.get(i);
                renderables.add(layer.getTilemap());
            }
        }

        renderables.sort(Comparator.comparingInt(Renderable::getZIndex));
        return renderables;
    }

    public List<GameObject> getGameObjects() {
        List<GameObject> objects = new ArrayList<>();
        for (TilemapLayer layer : layers) {
            objects.add(layer.getGameObject());
        }
        return objects;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    public void update(float deltaTime) {
        for (TilemapLayer layer : layers) {
            if (layer.isVisible() && layer.getGameObject().isEnabled()) {
                layer.getGameObject().update(deltaTime);
            }
        }
    }

    public void clear() {
        for (TilemapLayer layer : new ArrayList<>(layers)) {
            layer.getGameObject().destroy();
        }
        layers.clear();
        entities.clear();
        selectedEntities.clear();
        collisionMap.clear();
        cameraSettings.reset();
        activeLayerIndex = -1;
        selectedObject = null;
        dirty = false;
    }

    public void destroy() {
        clear();
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    public String getDisplayName() {
        String displayName = name;
        if (filePath != null) {
            int lastSep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
            if (lastSep >= 0) {
                displayName = filePath.substring(lastSep + 1);
            } else {
                displayName = filePath;
            }
        }
        return dirty ? displayName + " *" : displayName;
    }

    @Override
    public String toString() {
        return String.format("EditorScene[name=%s, layers=%d, entities=%d, collision=%d, dirty=%b]",
                name, layers.size(), entities.size(), collisionMap.getTileCount(), dirty);
    }
}
