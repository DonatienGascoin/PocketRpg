package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.LayoutGroup;
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
    private int lastComponentCount = -1;
    private int lastEnabledHash = -1;

    /**
     * Gets UICanvas list, rebuilding wrappers when hierarchy or components changed.
     *
     * @param scene The editor scene to collect canvases from
     * @return List of UICanvas components (cached when possible)
     */
    public List<UICanvas> getUICanvases(EditorScene scene) {
        if (scene == null) return List.of();

        // Check if we need to rebuild
        int currentComponentCount = countTotalComponents(scene);
        int currentEnabledHash = computeEnabledHash(scene);
        boolean needsRebuild = (scene != lastScene)
                || (scene.getHierarchyVersion() != lastHierarchyVersion)
                || (currentComponentCount != lastComponentCount)
                || (currentEnabledHash != lastEnabledHash);

        if (needsRebuild) {
            rebuildWrappers(scene);
            lastScene = scene;
            lastHierarchyVersion = scene.getHierarchyVersion();
            lastComponentCount = currentComponentCount;
            lastEnabledHash = currentEnabledHash;
        }

        return cachedCanvases;
    }

    /**
     * Counts total components across all entities for change detection.
     * Detects component additions/removals that don't trigger hierarchy version change.
     */
    private int countTotalComponents(EditorScene scene) {
        int count = 0;
        for (EditorGameObject entity : scene.getEntities()) {
            count += entity.getComponents().size();
        }
        return count;
    }

    /**
     * Computes a hash of all entity enabled states for change detection.
     * Detects enable/disable toggles that don't trigger hierarchy version change.
     */
    private int computeEnabledHash(EditorScene scene) {
        int hash = 0;
        int bit = 1;
        for (EditorGameObject entity : scene.getEntities()) {
            if (entity.isOwnEnabled()) {
                hash ^= bit;
            }
            bit = Integer.rotateLeft(bit, 1);
        }
        return hash;
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
        lastEnabledHash = -1;
    }

    /**
     * Rebuilds all wrapper GameObjects and collects UICanvases.
     * Uses flat list iteration (not hierarchy traversal) because the children
     * field in EditorGameObject is transient and may not be populated.
     */
    private void rebuildWrappers(EditorScene scene) {
        wrapperCache.clear();
        cachedCanvases.clear();

        // First pass: create wrappers for UI entities from flat list
        // (not using getRootEntities + recursion because children may not be linked)
        // Skip disabled entities — they should not render in the editor preview.
        Map<String, EditorGameObject> entityById = new HashMap<>();
        for (EditorGameObject entity : scene.getEntities()) {
            if (!entity.isEnabled()) continue;
            if (hasUIComponents(entity)) {
                GameObject wrapper = createWrapperGameObject(entity);
                wrapperCache.put(entity.getId(), wrapper);
                entityById.put(entity.getId(), entity);
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

        // Third pass: sort children by EditorGameObject order field
        // Build reverse lookup: wrapper -> entity ID
        Map<GameObject, String> wrapperToId = new HashMap<>();
        for (Map.Entry<String, GameObject> entry : wrapperCache.entrySet()) {
            wrapperToId.put(entry.getValue(), entry.getKey());
        }
        for (GameObject wrapper : wrapperCache.values()) {
            List<GameObject> children = wrapper.getChildren();
            if (children.size() > 1) {
                List<GameObject> sorted = new ArrayList<>(children);
                sorted.sort((a, b) -> {
                    EditorGameObject egoA = entityById.get(wrapperToId.get(a));
                    EditorGameObject egoB = entityById.get(wrapperToId.get(b));
                    int orderA = egoA != null ? egoA.getOrder() : 0;
                    int orderB = egoB != null ? egoB.getOrder() : 0;
                    return Integer.compare(orderA, orderB);
                });
                for (GameObject child : sorted) {
                    child.setParent(null);
                }
                for (GameObject child : sorted) {
                    child.setParent(wrapper);
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

        // Sort by sortOrder (ascending) - higher sortOrder renders on top
        cachedCanvases.sort(java.util.Comparator.comparingInt(UICanvas::getSortOrder));
    }

    /**
     * Checks if an EditorGameObject has UI components.
     */
    private boolean hasUIComponents(EditorGameObject entity) {
        for (Component comp : entity.getComponents()) {
            if (comp instanceof UIComponent || comp instanceof UITransform || comp instanceof LayoutGroup) {
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

        // Handle UITransform specially - it extends Transform, not UIComponent
        // Must add BEFORE UIComponents so they can find it via getComponent()
        UITransform uiTransform = entity.getComponent(UITransform.class);
        if (uiTransform != null) {
            // Remove the auto-created Transform before adding UITransform
            // (addComponentToWrapper bypasses the normal addComponent logic that would do this)
            removeAutoCreatedTransform(wrapper);

            // Reassign UITransform to wrapper
            uiTransform.setOwner(wrapper);
            addComponentToWrapper(wrapper, uiTransform);

            // Update wrapper's transform reference to point to UITransform
            setWrapperTransform(wrapper, uiTransform);
        } else {
            // No UITransform - copy position from EditorGameObject's transform
            Transform entityTransform = entity.getTransform();
            if (entityTransform != null) {
                wrapper.getTransform().setPosition(entityTransform.getPosition());
                wrapper.getTransform().setRotation(entityTransform.getRotation());
                wrapper.getTransform().setScale(entityTransform.getScale());
            }
        }

        // Add UI components and LayoutGroup to wrapper
        for (Component comp : entity.getComponents()) {
            if (comp instanceof UIComponent uiComp) {
                // Temporarily reassign the component to the wrapper
                comp.setOwner(wrapper);
                // Only force enable if the component's own state is enabled.
                // Wrappers bypass onStart() lifecycle, so we need this to set
                // internal flags. But respect intentionally disabled components.
                if (uiComp.isOwnEnabled()) {
                    uiComp.setEnabled(true);
                }
                // Add to wrapper's component list
                addComponentToWrapper(wrapper, comp);
            } else if (comp instanceof LayoutGroup) {
                // LayoutGroup extends Component (not UIComponent) but needs to be
                // on the wrapper for UIRenderer to find it via getComponent()
                comp.setOwner(wrapper);
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

    /**
     * Removes the auto-created Transform from wrapper's component list.
     * Called before adding UITransform to avoid duplicate transforms.
     */
    private void removeAutoCreatedTransform(GameObject wrapper) {
        try {
            var field = GameObject.class.getDeclaredField("components");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Component> components = (List<Component>) field.get(wrapper);
            components.removeIf(c -> c == wrapper.getTransform());
        } catch (Exception e) {
            System.err.println("[EditorUIBridge] Failed to remove auto-created transform: " + e.getMessage());
        }
    }

    /**
     * Sets the wrapper's transform field to the UITransform.
     * This ensures wrapper.getTransform() returns the UITransform.
     */
    private void setWrapperTransform(GameObject wrapper, UITransform uiTransform) {
        try {
            var field = GameObject.class.getDeclaredField("transform");
            field.setAccessible(true);
            field.set(wrapper, uiTransform);
        } catch (Exception e) {
            System.err.println("[EditorUIBridge] Failed to set wrapper transform: " + e.getMessage());
        }
    }
}
