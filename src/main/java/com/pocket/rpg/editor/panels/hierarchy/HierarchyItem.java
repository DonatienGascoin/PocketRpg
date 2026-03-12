package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;

import java.util.List;

/**
 * Interface for game objects that can be displayed in the hierarchy panel.
 * Implemented by EditorGameObject directly and by RuntimeGameObjectAdapter
 * which wraps runtime GameObjects for hierarchy display.
 */
public interface HierarchyItem {
    String getName();
    String getId();
    Transform getTransform();
    <T extends Component> T getComponent(Class<T> type);
    <T extends Component> List<T> getComponents(Class<T> type);
    List<Component> getAllComponents();
    boolean isEnabled();
    boolean isActiveInHierarchy();
    boolean hasChildren();
    boolean isEditor();
    boolean isRuntime();
    HierarchyItem getHierarchyParent();
    List<? extends HierarchyItem> getHierarchyChildren();

    default boolean hasHierarchyChildren() { return !getHierarchyChildren().isEmpty(); }
    default boolean isEditable() { return isEditor(); }
    default <T extends Component> T findComponentInParent(Class<T> type) {
        HierarchyItem parent = getHierarchyParent();
        int depth = 0;
        while (parent != null && depth < 100) {
            T comp = parent.getComponent(type);
            if (comp != null) return comp;
            parent = parent.getHierarchyParent();
            depth++;
        }
        return null;
    }
    default boolean hasComponent(Class<? extends Component> type) { return getComponent(type) != null; }
}
