package com.pocket.rpg.editor.scene;

import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Renderable;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents a scene being edited in the Scene Editor.
 * <p>
 * Manages:
 * - Tilemap layers (TilemapLayer wrappers)
 * - Layer visibility mode
 * - Active layer selection
 * - Conversion to/from SceneData for serialization
 * - Live Scene for rendering
 * <p>
 * Architecture:
 * - EditorScene holds SceneData (source of truth for saving)
 * - EditorScene maintains a live Scene for rendering
 * - Edits update both SceneData and live Scene
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

    // Layer management
    private final List<TilemapLayer> layers = new ArrayList<>();

    @Getter
    private int activeLayerIndex = -1;

    @Getter
    @Setter
    private LayerVisibilityMode visibilityMode = LayerVisibilityMode.ALL;

    /**
     * Opacity for dimmed layers (0.0 - 1.0)
     */
    @Getter
    @Setter
    private float dimmedOpacity = 0.3f;

    // Editor state
    @Getter
    @Setter
    private GameObject selectedObject = null;

    /**
     * Creates a new empty editor scene.
     */
    public EditorScene() {
    }

    /**
     * Creates an editor scene with a name.
     */
    public EditorScene(String name) {
        this.name = name;
    }

    // ========================================================================
    // DIRTY STATE
    // ========================================================================

    /**
     * Marks the scene as modified (needs saving).
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * Clears the dirty flag (after saving).
     */
    public void clearDirty() {
        this.dirty = false;
    }

    // ========================================================================
    // LAYER MANAGEMENT
    // ========================================================================

    /**
     * Adds a new tilemap layer.
     *
     * @param layerName Display name for the layer
     * @return The created layer
     */
    public TilemapLayer addLayer(String layerName) {
        // Calculate zIndex (each new layer goes on top)
        int zIndex = layers.isEmpty() ? 0 : layers.get(layers.size() - 1).getZIndex() + 1;

        TilemapLayer layer = new TilemapLayer(layerName, zIndex);
        layers.add(layer);

        // Always select the newly created layer
        activeLayerIndex = layers.size() - 1;

        markDirty();
        return layer;
    }

    /**
     * Adds a layer with a specific spritesheet.
     */
    public TilemapLayer addLayer(String layerName, String spritesheetPath, int spriteWidth, int spriteHeight) {
        TilemapLayer layer = addLayer(layerName);

        // Load sprite and create spritesheet
        var sprite = Assets.<Sprite>load(spritesheetPath);

        if (sprite == null) {
            System.err.println("Failed to load sprite: " + spritesheetPath);
            return layer;
        }

        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), spriteWidth, spriteHeight);
        layer.setSpriteSheet(sheet, spritesheetPath, spriteWidth, spriteHeight);

        return layer;
    }

    /**
     * Removes a layer by index.
     */
    public void removeLayer(int index) {
        if (index < 0 || index >= layers.size()) return;

        TilemapLayer layer = layers.remove(index);
        layer.getGameObject().destroy();

        // Adjust active layer index
        if (activeLayerIndex >= layers.size()) {
            activeLayerIndex = layers.size() - 1;
        }

        markDirty();
    }

    /**
     * Gets a layer by index.
     */
    public TilemapLayer getLayer(int index) {
        if (index < 0 || index >= layers.size()) return null;
        return layers.get(index);
    }

    /**
     * Gets the currently active layer.
     */
    public TilemapLayer getActiveLayer() {
        if (activeLayerIndex < 0 || activeLayerIndex >= layers.size()) return null;
        return layers.get(activeLayerIndex);
    }

    /**
     * Sets the active layer by index.
     */
    public void setActiveLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            activeLayerIndex = index;
        }
    }

    /**
     * Gets all layers (copy).
     */
    public List<TilemapLayer> getLayers() {
        return new ArrayList<>(layers);
    }

    /**
     * Gets the number of layers.
     */
    public int getLayerCount() {
        return layers.size();
    }

    /**
     * Moves a layer up in the list (increases zIndex).
     */
    public void moveLayerUp(int index) {
        if (index < 0 || index >= layers.size() - 1) return;

        swapLayers(index, index + 1);
    }

    /**
     * Moves a layer down in the list (decreases zIndex).
     */
    public void moveLayerDown(int index) {
        if (index <= 0 || index >= layers.size()) return;

        swapLayers(index, index - 1);
    }

    /**
     * Swaps two layers and their zIndex values.
     */
    public void swapLayers(int indexA, int indexB) {
        if (indexA < 0 || indexA >= layers.size()) return;
        if (indexB < 0 || indexB >= layers.size()) return;
        if (indexA == indexB) return;

        TilemapLayer layerA = layers.get(indexA);
        TilemapLayer layerB = layers.get(indexB);

        // Swap zIndex
        int tempZ = layerA.getZIndex();
        layerA.setZIndex(layerB.getZIndex());
        layerB.setZIndex(tempZ);

        // Swap in list
        layers.set(indexA, layerB);
        layers.set(indexB, layerA);

        // Update active layer index if needed
        if (activeLayerIndex == indexA) {
            activeLayerIndex = indexB;
        } else if (activeLayerIndex == indexB) {
            activeLayerIndex = indexA;
        }

        markDirty();
    }

    /**
     * Renames a layer.
     */
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

    /**
     * Checks if a layer should be rendered based on visibility mode.
     */
    public boolean isLayerVisible(int index) {
        TilemapLayer layer = getLayer(index);
        if (layer == null || !layer.isVisible()) {
            return false;
        }

        switch (visibilityMode) {
            case ALL:
                return true;
            case SELECTED_ONLY:
                return index == activeLayerIndex;
            case SELECTED_DIMMED:
                return true; // All visible, but non-active are dimmed
            default:
                return true;
        }
    }

    /**
     * Gets the opacity multiplier for a layer based on visibility mode.
     */
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
    // RENDERING SUPPORT
    // ========================================================================

    /**
     * Gets all renderables sorted by zIndex, respecting visibility mode.
     */
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

    /**
     * Gets all GameObjects from layers.
     */
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

    /**
     * Updates the scene (for animations, etc.).
     */
    public void update(float deltaTime) {
        for (TilemapLayer layer : layers) {
            if (layer.isVisible() && layer.getGameObject().isEnabled()) {
                layer.getGameObject().update(deltaTime);
            }
        }
    }

    /**
     * Clears all scene content.
     */
    public void clear() {
        for (TilemapLayer layer : new ArrayList<>(layers)) {
            layer.getGameObject().destroy();
        }
        layers.clear();
        activeLayerIndex = -1;
        selectedObject = null;
        dirty = false;
    }

    /**
     * Destroys the scene and releases resources.
     */
    public void destroy() {
        clear();
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Gets the display name (with dirty indicator).
     */
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

    /**
     * Checks if scene has unsaved changes.
     */
    public boolean hasUnsavedChanges() {
        return dirty;
    }

    /**
     * Gets the number of objects (layers).
     */
    public int getObjectCount() {
        return layers.size();
    }

    @Override
    public String toString() {
        return String.format("EditorScene[name=%s, layers=%d, activeLayer=%d, dirty=%b]",
                name, layers.size(), activeLayerIndex, dirty);
    }
}