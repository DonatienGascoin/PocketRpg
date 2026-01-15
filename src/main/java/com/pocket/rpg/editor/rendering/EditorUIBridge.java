package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UIComponent;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridge between EditorScene (EditorGameObjects) and UIRenderer (GameObjects).
 * <p>
 * UIRenderer expects UICanvas.getGameObject() to return a valid GameObject
 * with proper parent-child hierarchy. EditorGameObjects are not GameObjects,
 * so this class creates temporary wrappers.
 * <p>
 * Caches wrappers and only rebuilds when hierarchy changes (detected via
 * hierarchyVersion counter in EditorScene). This ensures O(1) performance
 * for most frames.
 *
 * @see EditorScene#getHierarchyVersion()
 */
public class EditorUIBridge {

    // Cached wrappers - reused across frames
    private final Map<String, GameObject> wrapperCache = new HashMap<>();
    private final List<UICanvas> cachedCanvases = new ArrayList<>();

    // Track which scene we built wrappers for
    private EditorScene lastScene;
    private int lastHierarchyVersion = -1;

    /**
     * Gets UICanvas list, rebuilding wrappers only when hierarchy changed.
     *
     * @param scene The editor scene to collect canvases from
     * @return List of UICanvas components (cached when possible)
     */
    public List<UICanvas> getUICanvases(EditorScene scene) {
        if (scene == null) return List.of();

        // Check if we need to rebuild
        boolean needsRebuild = (scene != lastScene)
                || (scene.getHierarchyVersion() != lastHierarchyVersion);

        if (needsRebuild) {
            rebuildWrappers(scene);
            lastScene = scene;
            lastHierarchyVersion = scene.getHierarchyVersion();
        }

        return cachedCanvases;
    }

    /**
     * Force rebuild on next access.
     * Call when component fields change that affect rendering.
     */
    public void invalidate() {
        lastHierarchyVersion = -1;
    }

    /**
     * Clears all cached data.
     * Call when the scene is being destroyed or switched.
     */
    public void clear() {
        wrapperCache.clear();
        cachedCanvases.clear();
        lastScene = null;
        lastHierarchyVersion = -1;
    }

    /**
     * Rebuilds all wrapper GameObjects and collects UICanvases.
     * Uses flat list iteration (not hierarchy traversal) because the children
     * field in EditorGameObject is transient and may not be populated.
     */
    private void rebuildWrappers(EditorScene scene) {
        wrapperCache.clear();
        cachedCanvases.clear();

        // First pass: create wrappers for ALL UI entities from flat list
        // (not using getRootEntities + recursion because children may not be linked)
        for (EditorGameObject entity : scene.getEntities()) {
            if (hasUIComponents(entity)) {
                GameObject wrapper = createWrapperGameObject(entity);
                wrapperCache.put(entity.getId(), wrapper);
            }
        }

        // Second pass: set up parent-child relationships
        for (EditorGameObject entity : scene.getEntities()) {
            GameObject wrapper = wrapperCache.get(entity.getId());
            if (wrapper != null) {
                EditorGameObject parentEntity = entity.getParent();
                if (parentEntity != null) {
                    GameObject parentWrapper = wrapperCache.get(parentEntity.getId());
                    if (parentWrapper != null) {
                        wrapper.setParent(parentWrapper);
                    }
                }
            }
        }

        // Collect canvases - only root canvases (those without UI parent)
        for (Map.Entry<String, GameObject> entry : wrapperCache.entrySet()) {
            GameObject wrapper = entry.getValue();
            UICanvas canvas = wrapper.getComponent(UICanvas.class);
            if (canvas != null && wrapper.getParent() == null) {
                cachedCanvases.add(canvas);
            }
        }
    }

    /**
     * Checks if an EditorGameObject has UI components.
     */
    private boolean hasUIComponents(EditorGameObject entity) {
        for (Component comp : entity.getComponents()) {
            if (comp instanceof UIComponent) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a temporary GameObject wrapper for an EditorGameObject.
     * Adds UI components to the wrapper so UIRenderer can access them via getComponent().
     * <p>
     * IMPORTANT: UITransform extends Transform (not UIComponent), so it must be
     * handled separately and added BEFORE other UIComponents so they can access it.
     */
    private GameObject createWrapperGameObject(EditorGameObject entity) {
        GameObject wrapper = new GameObject(entity.getName());

        // Copy transform position/rotation/scale from EditorGameObject
        wrapper.getTransform().setPosition(entity.getTransform().getPosition());
        wrapper.getTransform().setRotation(entity.getTransform().getRotation());
        wrapper.getTransform().setScale(entity.getTransform().getScale());

        // Handle UITransform specially - it extends Transform, not UIComponent
        // Must add BEFORE UIComponents so they can find it via getComponent()
        UITransform uiTransform = entity.getComponent(UITransform.class);
        if (uiTransform != null) {
            // Reassign UITransform to wrapper
            uiTransform.setGameObject(wrapper);
            addComponentToWrapper(wrapper, uiTransform);
        }

        // Add UI components to wrapper
        for (Component comp : entity.getComponents()) {
            if (comp instanceof UIComponent uiComp) {
                // Temporarily reassign the component to the wrapper
                comp.setGameObject(wrapper);
                // Force enable - wrappers bypass onStart() lifecycle
                uiComp.setEnabled(true);
                // Add to wrapper's component list
                addComponentToWrapper(wrapper, comp);
            }
        }

        return wrapper;
    }

    /**
     * Adds a component to a wrapper GameObject without triggering lifecycle methods.
     */
    private void addComponentToWrapper(GameObject wrapper, Component component) {
        try {
            var field = GameObject.class.getDeclaredField("components");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Component> components = (List<Component>) field.get(wrapper);
            components.add(component);
        } catch (Exception e) {
            System.err.println("[EditorUIBridge] Failed to add component to wrapper: " + e.getMessage());
        }
    }
}
