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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents a scene being edited in the Scene Editor.
 * <p>
 * Phase 5: Added entity management and camera settings.
 * <p>
 * Manages:
 * - Tilemap layers (TilemapLayer wrappers)
 * - Collision map (CollisionMap)
 * - Entity instances (EditorEntity)
 * - Camera settings (SceneCameraSettings)
 * - Selection state
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
    // ENTITY MANAGEMENT (Phase 5)
    // ========================================================================

    private final List<EditorEntity> entities = new ArrayList<>();

    @Getter
    private EditorEntity selectedEntity = null;

    // ========================================================================
    // CAMERA SETTINGS (Phase 5)
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
    // ENTITY MANAGEMENT (Phase 5)
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
     * Removes an entity from the scene.
     */
    public void removeEntity(EditorEntity entity) {
        if (entity == null) return;

        entities.remove(entity);

        if (selectedEntity == entity) {
            selectedEntity = null;
        }

        markDirty();
    }

    /**
     * Sets the selected entity.
     */
    public void setSelectedEntity(EditorEntity entity) {
        this.selectedEntity = entity;
        if (entity != null) {
            this.selectedObject = null; // Clear other selection
        }
    }

    /**
     * Gets all entities (copy).
     */
    public List<EditorEntity> getEntities() {
        return new ArrayList<>(entities);
    }

    /**
     * Gets entity count.
     */
    public int getEntityCount() {
        return entities.size();
    }

    /**
     * Finds an entity at the given world position.
     * Searches in reverse order (top entities first).
     */
    public EditorEntity findEntityAt(float worldX, float worldY) {
        for (int i = entities.size() - 1; i >= 0; i--) {
            EditorEntity entity = entities.get(i);
            Vector3f pos = entity.getPositionRef();
            Vector2f size = entity.getPreviewSize();

            if (size == null) {
                size = new Vector2f(1f, 1f);
            }

            // Hit test (assuming bottom-center origin)
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

    /**
     * Clears entity selection.
     */
    public void clearEntitySelection() {
        selectedEntity = null;
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
        collisionMap.clear();
        cameraSettings.reset();
        activeLayerIndex = -1;
        selectedObject = null;
        selectedEntity = null;
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

    public int getObjectCount() {
        return layers.size() + entities.size();
    }

    @Override
    public String toString() {
        return String.format("EditorScene[name=%s, layers=%d, entities=%d, collision=%d, dirty=%b]",
                name, layers.size(), entities.size(), collisionMap.getTileCount(), dirty);
    }
}
