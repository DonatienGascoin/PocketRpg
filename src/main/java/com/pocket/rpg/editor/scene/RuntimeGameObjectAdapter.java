package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adapts a runtime {@link GameObject} to the {@link HierarchyItem} interface
 * for display in the hierarchy panel during play mode.
 * <p>
 * Uses a static cache to avoid recreating adapters for the same GameObject.
 * The {@link WeakHashMap} ensures adapters for destroyed GameObjects are garbage collected.
 */
public class RuntimeGameObjectAdapter implements HierarchyItem {

    private static final Map<GameObject, RuntimeGameObjectAdapter> adapterCache = new WeakHashMap<>();

    @Getter
    private final GameObject gameObject;

    private RuntimeGameObjectAdapter(GameObject gameObject) {
        this.gameObject = gameObject;
    }

    /**
     * Gets or creates an adapter for the given GameObject.
     */
    public static RuntimeGameObjectAdapter of(GameObject gameObject) {
        return adapterCache.computeIfAbsent(gameObject, RuntimeGameObjectAdapter::new);
    }

    /**
     * Clears the adapter cache. Call when play mode ends.
     */
    public static void clearCache() {
        adapterCache.clear();
    }

    // ========================================================================
    // IGameObject delegation
    // ========================================================================

    @Override
    public String getName() {
        return gameObject.getName();
    }

    @Override
    public String getId() {
        return "runtime_" + gameObject.getId();
    }

    @Override
    public Transform getTransform() {
        return gameObject.getTransform();
    }

    @Override
    public <T extends Component> T getComponent(Class<T> type) {
        return gameObject.getComponent(type);
    }

    @Override
    public <T extends Component> List<T> getComponents(Class<T> type) {
        return gameObject.getComponents(type);
    }

    @Override
    public List<Component> getAllComponents() {
        return gameObject.getAllComponents();
    }

    @Override
    public boolean isEnabled() {
        return gameObject.isEnabled();
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    // ========================================================================
    // HierarchyItem
    // ========================================================================

    @Override
    public List<? extends HierarchyItem> getHierarchyChildren() {
        List<GameObject> children = gameObject.getChildren();
        if (children.isEmpty()) {
            return List.of();
        }
        List<HierarchyItem> wrapped = new ArrayList<>(children.size());
        for (GameObject child : children) {
            wrapped.add(RuntimeGameObjectAdapter.of(child));
        }
        return wrapped;
    }

    @Override
    public boolean isEditable() {
        return false;
    }
}
