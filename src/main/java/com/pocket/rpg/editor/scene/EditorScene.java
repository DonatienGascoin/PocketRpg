package com.pocket.rpg.editor.scene;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.trigger.TriggerDataMap;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteGrid;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

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
public class EditorScene implements DirtyTracker {

    /**
     * Default name used when no file path is set.
     */
    private static final String DEFAULT_NAME = "Untitled";

    @Getter
    @Setter
    private String filePath = null;

    @Getter
    private boolean dirty = false;

    /**
     * Gets the scene name, derived from the file path.
     * If no file path is set, returns "Untitled".
     */
    public String getName() {
        if (filePath == null || filePath.isEmpty()) {
            return DEFAULT_NAME;
        }
        // Extract filename without extension
        String normalized = filePath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        return fileName.endsWith(".scene")
                ? fileName.substring(0, fileName.length() - 6)
                : fileName;
    }

    /**
     * Incremented when hierarchy changes (entity add/remove/reparent).
     * Used by EditorUIBridge for cache invalidation.
     */
    @Getter
    private int hierarchyVersion = 0;

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
    private final TriggerDataMap triggerDataMap;

    @Getter
    @Setter
    private boolean collisionVisible = false;

    @Getter
    @Setter
    private int collisionZLevel = 0;

    @Getter
    @Setter
    private float collisionOpacity = 1f;

    // ========================================================================
    // ENTITY MANAGEMENT
    // ========================================================================

    private final List<EditorGameObject> entities = new ArrayList<>();

    private final Set<EditorGameObject> selectedEntities = new HashSet<>();

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
        this.triggerDataMap = new TriggerDataMap();
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
        int zIndex = layers.isEmpty() ? 0 : layers.getLast().getZIndex() + 1;

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

        SpriteGrid grid = SpriteGrid.create(sprite.getTexture(), spriteWidth, spriteHeight);
        layer.setSpriteGrid(grid, spritesheetPath, spriteWidth, spriteHeight);

        return layer;
    }

    public void addExistingLayer(TilemapLayer layer) {
        layers.add(layer);
        // Don't auto-select - user must explicitly choose a layer
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
     * Finds entity by ID.
     */
    public EditorGameObject getEntityById(String id) {
        if (id == null) return null;
        for (EditorGameObject entity : entities) {
            if (id.equals(entity.getId())) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Adds an entity to the scene.
     */
    public void addEntity(EditorGameObject entity) {
        if (entity == null) return;

        // Check for duplicate ID and regenerate if needed
        if (getEntityById(entity.getId()) != null) {
            String oldId = entity.getId();
            entity.regenerateId();
            System.out.println("Regenerated duplicate entity ID: " + oldId + " -> " + entity.getId());
        }

        entities.add(entity);
        hierarchyVersion++;
        markDirty();
    }

    /**
     * Removes an entity and all its children from the scene.
     */
    public void removeEntity(EditorGameObject entity) {
        if (entity == null) return;

        // Remove children first (copy list to avoid concurrent modification)
        List<EditorGameObject> children = new ArrayList<>(entity.getChildren());
        for (EditorGameObject child : children) {
            removeEntity(child);
        }

        // Clear from parent
        entity.clearParent();

        // Remove from entities list
        entities.remove(entity);

        // Remove from selection
        selectedEntities.remove(entity);

        hierarchyVersion++;
        markDirty();
    }

    /**
     * Gets all entities (copy).
     */
    public List<EditorGameObject> getEntities() {
        return new ArrayList<>(entities);
    }

    /**
     * Finds an entity at the given world position.
     * Searches in reverse order (top entities first).
     * Accounts for pivot, scale, and rotation.
     */
    public EditorGameObject findEntityAt(float worldX, float worldY) {
        for (int i = entities.size() - 1; i >= 0; i--) {
            EditorGameObject entity = entities.get(i);
            if (isPointInsideEntity(entity, worldX, worldY)) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Checks if a world point is inside an entity's bounds,
     * accounting for pivot, scale, and rotation.
     */
    private boolean isPointInsideEntity(EditorGameObject entity, float worldX, float worldY) {
        Vector3f pos = entity.getPosition();
        Vector3f scale = entity.getScale();
        Vector3f rotation = entity.getRotation();
        Vector2f size = entity.getCurrentSize();

        if (size == null) {
            size = new Vector2f(1f, 1f);
        }

        // Get pivot from sprite (default to center if no sprite)
        float pivotX = 0.5f;
        float pivotY = 0.5f;
        SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
        if (sr != null) {
            Sprite sprite = sr.getSprite();
            if (sprite != null) {
                pivotX = sprite.getPivotX();
                pivotY = sprite.getPivotY();
            }
        }

        // Calculate scaled size
        float width = size.x * scale.x;
        float height = size.y * scale.y;

        // Transform click point to entity's local space
        // First, translate so entity position is at origin
        float localX = worldX - pos.x;
        float localY = worldY - pos.y;

        // Then, apply inverse rotation
        float rotZ = (float) Math.toRadians(-rotation.z); // Negative for inverse
        float cos = (float) Math.cos(rotZ);
        float sin = (float) Math.sin(rotZ);
        float rotatedX = localX * cos - localY * sin;
        float rotatedY = localX * sin + localY * cos;

        // Calculate bounds in local space (based on pivot)
        float left = -pivotX * width;
        float right = (1f - pivotX) * width;
        float bottom = -pivotY * height;
        float top = (1f - pivotY) * height;

        // Check if point is inside bounds
        return rotatedX >= left && rotatedX <= right && rotatedY >= bottom && rotatedY <= top;
    }

    // ========================================================================
    // MULTI-SELECTION
    // ========================================================================

    /**
     * Gets all selected entities (unmodifiable).
     */
    public Set<EditorGameObject> getSelectedEntities() {
        return Collections.unmodifiableSet(selectedEntities);
    }

    /**
     * Checks if an entity is selected.
     */
    public boolean isSelected(EditorGameObject entity) {
        return selectedEntities.contains(entity);
    }

    /**
     * Adds an entity to the selection.
     */
    public void addToSelection(EditorGameObject entity) {
        if (entity != null) {
            selectedEntities.add(entity);
        }
    }

    /**
     * Toggles an entity's selection state.
     */
    public void toggleSelection(EditorGameObject entity) {
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
    public void setSelection(Set<EditorGameObject> entities) {
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
    public EditorGameObject getSelectedEntity() {
        if (selectedEntities.size() == 1) {
            return selectedEntities.iterator().next();
        }
        return null;
    }

    /**
     * Sets single entity selection (for backward compatibility).
     */
    public void setSelectedEntity(EditorGameObject entity) {
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
    public List<EditorGameObject> getRootEntities() {
        List<EditorGameObject> roots = new ArrayList<>();
        for (EditorGameObject entity : entities) {
            if (entity.getParentId() == null || entity.getParentId().isEmpty()) {
                roots.add(entity);
            }
        }
        roots.sort(Comparator.comparingInt(EditorGameObject::getOrder));
        return roots;
    }

    /**
     * Resolves parent-child relationships after loading.
     * Call this after all entities have been added from deserialization.
     */
    public void resolveHierarchy() {
        // Build lookup map
        Map<String, EditorGameObject> byId = entities.stream()
                .collect(Collectors.toMap(EditorGameObject::getId, Function.identity()));

        // Clear existing transient relationships
        for (EditorGameObject entity : entities) {
            entity.getChildrenMutable().clear();
        }

        // Rebuild relationships
        for (EditorGameObject entity : entities) {
            String parentId = entity.getParentId();
            if (parentId != null && !parentId.isEmpty()) {
                EditorGameObject parent = byId.get(parentId);
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
        for (EditorGameObject entity : entities) {
            if (entity.hasChildren()) {
                reindexSiblings(entity);
            }
        }
    }

    /**
     * Reindexes sibling order after hierarchy changes.
     * Pass null for root-level entities.
     */
    public void reindexSiblings(EditorGameObject parent) {
        List<EditorGameObject> siblings;
        if (parent == null) {
            siblings = getRootEntities();
        } else {
            siblings = new ArrayList<>(parent.getChildren());
        }

        siblings.sort(Comparator.comparingInt(EditorGameObject::getOrder));

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
    public void insertEntityAtPosition(EditorGameObject entity, EditorGameObject newParent, int insertIndex) {
        // First, detach from current parent and reindex old siblings
        EditorGameObject oldParent = entity.getParent();
        if (oldParent != null) {
            oldParent.getChildrenMutable().remove(entity);
            // Reindex old siblings
            List<EditorGameObject> oldSiblings = new ArrayList<>(oldParent.getChildrenMutable());
            oldSiblings.sort(Comparator.comparingInt(EditorGameObject::getOrder));
            for (int i = 0; i < oldSiblings.size(); i++) {
                oldSiblings.get(i).setOrder(i);
            }
        } else {
            // Reindex old root siblings (entity was at root level)
            List<EditorGameObject> oldRoots = new ArrayList<>();
            for (EditorGameObject e : entities) {
                if (e != entity && (e.getParentId() == null || e.getParentId().isEmpty())) {
                    oldRoots.add(e);
                }
            }
            oldRoots.sort(Comparator.comparingInt(EditorGameObject::getOrder));
            for (int i = 0; i < oldRoots.size(); i++) {
                oldRoots.get(i).setOrder(i);
            }
        }
        
        // Clear transient parent first
        entity.setParentDirect(null);
        entity.setParentId(newParent != null ? newParent.getId() : null);
        
        // Get target siblings list (now entity has new parentId set)
        List<EditorGameObject> siblings;
        if (newParent == null) {
            // Root level - get all root entities except this one
            siblings = new ArrayList<>();
            for (EditorGameObject e : entities) {
                if (e != entity && (e.getParentId() == null || e.getParentId().isEmpty())) {
                    siblings.add(e);
                }
            }
        } else {
            // Child level - get parent's children except this one
            siblings = new ArrayList<>();
            for (EditorGameObject child : newParent.getChildrenMutable()) {
                if (child != entity) {
                    siblings.add(child);
                }
            }
        }
        
        // Sort by current order
        siblings.sort(Comparator.comparingInt(EditorGameObject::getOrder));
        
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

        hierarchyVersion++;
        markDirty();
    }

    // ========================================================================
    // RENDERING SUPPORT
    // ========================================================================

    /**
     * Returns all renderables for the scene, sorted by z-index.
     * Includes visible tilemap layers and entities.
     *
     * @return Sorted list of renderables (tilemaps + entities)
     */
    public List<Renderable> getRenderables() {
        List<Renderable> renderables = new ArrayList<>();

        // Add visible tilemap layers
        for (int i = 0; i < layers.size(); i++) {
            if (isLayerVisible(i)) {
                TilemapLayer layer = layers.get(i);
                if (layer.getTilemap() != null) {
                    renderables.add(layer.getTilemap());
                }
            }
        }

        // Add visible entities (EditorGameObject implements Renderable)
        for (EditorGameObject entity : entities) {
            if (entity.isRenderVisible()) {
                renderables.add(entity);
            }
        }

        // Sort by z-index (lower values render first/behind)
        renderables.sort(Comparator.comparingInt(Renderable::getZIndex));
        return renderables;
    }

    /**
     * Returns renderables with tint information for editor rendering.
     * Used by EditorSceneRenderer for layer dimming effects.
     *
     * @return List of RenderableWithTint containing renderable and opacity
     */
    public List<RenderableWithTint> getRenderablesWithTint() {
        List<RenderableWithTint> result = new ArrayList<>();

        // Add tilemap layers with opacity from visibility mode
        for (int i = 0; i < layers.size(); i++) {
            if (isLayerVisible(i)) {
                TilemapLayer layer = layers.get(i);
                if (layer.getTilemap() != null) {
                    float opacity = getLayerOpacity(i);
                    Vector4f tint = (opacity >= 1f)
                            ? new Vector4f(1f, 1f, 1f, 1f)
                            : new Vector4f(0.8f, 0.8f, 0.8f, opacity);
                    result.add(new RenderableWithTint(layer.getTilemap(), tint));
                }
            }
        }

        // Add entities with full opacity
        for (EditorGameObject entity : entities) {
            if (entity.isRenderVisible()) {
                result.add(new RenderableWithTint(entity, new Vector4f(1f, 1f, 1f, 1f)));
            }
        }

        // Sort by z-index
        result.sort(Comparator.comparingInt(r -> r.renderable().getZIndex()));
        return result;
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
        triggerDataMap.clear();
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
        String displayName = getName();
        // Add file extension for display if scene has a file
        if (filePath != null) {
            displayName = displayName + ".scene";
        }
        return dirty ? displayName + " *" : displayName;
    }

    @Override
    public String toString() {
        return String.format("EditorScene[name=%s, layers=%d, entities=%d, collision=%d, dirty=%b]",
                getName(), layers.size(), entities.size(), collisionMap.getTileCount(), dirty);
    }

    public record RenderableWithTint(Renderable renderable, Vector4f tint) {}

}
