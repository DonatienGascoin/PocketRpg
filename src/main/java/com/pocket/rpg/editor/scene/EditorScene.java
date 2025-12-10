package com.pocket.rpg.editor.scene;

import com.pocket.rpg.core.Camera;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.rendering.Renderable;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents a scene being edited in the Scene Editor.
 * Contains GameObjects, tilemaps, collision data, and entity placements.
 * 
 * This is a simplified scene representation for editing purposes.
 * The game loads scenes via SceneLoader which creates proper runtime Scenes.
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

    // Scene content
    private final List<GameObject> gameObjects = new ArrayList<>();
    private final List<Renderable> renderables = new ArrayList<>();

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
    // GAME OBJECTS
    // ========================================================================

    /**
     * Adds a GameObject to the scene.
     */
    public void addGameObject(GameObject gameObject) {
        if (gameObject == null) return;
        
        gameObjects.add(gameObject);
        gameObject.setScene(null); // Editor scenes don't have a runtime Scene
        
        // Check if it's a renderable
        for (var component : gameObject.getAllComponents()) {
            if (component instanceof Renderable renderable) {
                renderables.add(renderable);
            }
        }
        
        sortRenderables();
        markDirty();
    }

    /**
     * Removes a GameObject from the scene.
     */
    public void removeGameObject(GameObject gameObject) {
        if (gameObject == null) return;
        
        gameObjects.remove(gameObject);
        
        // Remove associated renderables
        for (var component : gameObject.getAllComponents()) {
            if (component instanceof Renderable renderable) {
                renderables.remove(renderable);
            }
        }
        
        if (selectedObject == gameObject) {
            selectedObject = null;
        }
        
        markDirty();
    }

    /**
     * Gets all GameObjects in the scene.
     */
    public List<GameObject> getGameObjects() {
        return new ArrayList<>(gameObjects);
    }

    /**
     * Finds a GameObject by name.
     */
    public GameObject findGameObject(String name) {
        for (GameObject go : gameObjects) {
            if (go.getName().equals(name)) {
                return go;
            }
        }
        return null;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Gets all renderables sorted by zIndex.
     */
    public List<Renderable> getRenderables() {
        return renderables;
    }

    /**
     * Sorts renderables by zIndex.
     */
    private void sortRenderables() {
        renderables.sort(Comparator.comparingInt(Renderable::getZIndex));
    }

    /**
     * Notifies that a renderable's zIndex changed.
     */
    public void onZIndexChanged() {
        sortRenderables();
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Updates the scene (for animations, etc.).
     */
    public void update(float deltaTime) {
        for (GameObject go : gameObjects) {
            if (go.isEnabled()) {
                go.update(deltaTime);
            }
        }
    }

    /**
     * Clears all scene content.
     */
    public void clear() {
        // Destroy all game objects
        for (GameObject go : new ArrayList<>(gameObjects)) {
            go.destroy();
        }
        
        gameObjects.clear();
        renderables.clear();
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
            // Extract filename from path
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
     * Gets the number of GameObjects.
     */
    public int getObjectCount() {
        return gameObjects.size();
    }

    @Override
    public String toString() {
        return String.format("EditorScene[name=%s, objects=%d, dirty=%b]",
            name, gameObjects.size(), dirty);
    }
}
