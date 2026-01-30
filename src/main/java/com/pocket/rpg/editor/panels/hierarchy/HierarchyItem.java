package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.core.IGameObject;

import java.util.List;

/**
 * Extended interface for game objects that can be displayed in the hierarchy panel.
 * Extends {@link IGameObject} to add hierarchy navigation and editability.
 * <p>
 * Implemented by {@code EditorGameObject} directly and by {@code RuntimeGameObjectAdapter}
 * which wraps runtime {@code GameObject}s for hierarchy display.
 */
public interface HierarchyItem extends IGameObject {

    /**
     * Returns child items for hierarchy tree display.
     */
    List<? extends HierarchyItem> getHierarchyChildren();

    /**
     * Returns whether this item has children to display.
     */
    default boolean hasHierarchyChildren() {
        return !getHierarchyChildren().isEmpty();
    }

    /**
     * Returns whether this item can be edited (rename, delete, reparent, etc.)
     * Returns true for editor objects, false for runtime objects during play mode.
     */
    default boolean isEditable() {
        return isEditor();
    }
}
