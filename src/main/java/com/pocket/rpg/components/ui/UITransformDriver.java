package com.pocket.rpg.components.ui;

import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;

/**
 * Interface for components that drive child UITransform values.
 * Implementors report which fields they control for a given child,
 * so the inspector can disable those fields.
 */
public interface UITransformDriver {

    /**
     * Returns driver info describing which UITransform fields are controlled
     * for the given child, or null if this driver does not affect the child.
     */
    TransformDriverInfo getChildDriverInfo(HierarchyItem child);
}
