package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.TransformDriverInfo;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransformDriver;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import com.pocket.rpg.editor.scene.RuntimeGameObjectAdapter;

/**
 * Detects which UITransform fields are driven by parent components.
 * Replaces the inline if/else chains in UITransformInspector.
 */
public final class TransformDriverDetector {

    private TransformDriverDetector() {}

    /**
     * Detects if the entity's UITransform is driven by a parent or sibling component.
     *
     * @param entity the entity to check
     * @return driver info, or null if no driver found
     */
    public static TransformDriverInfo detect(HierarchyItem entity) {
        if (entity == null) return null;

        // 1. UICanvas on the same GO → entirely managed
        if (entity.getComponent(UICanvas.class) != null) {
            return TransformDriverInfo.entirelyDriven("UICanvas");
        }

        // 2. Check parent for UITransformDriver implementations
        HierarchyItem parent = entity.getHierarchyParent();
        if (parent == null) return null;

        GameObject childGo = unwrapGameObject(entity);
        if (childGo == null) return null;

        for (Component comp : parent.getAllComponents()) {
            if (comp instanceof UITransformDriver driver) {
                TransformDriverInfo info = driver.getChildDriverInfo(childGo);
                if (info != null) return info;
            }
        }

        return null;
    }

    private static GameObject unwrapGameObject(HierarchyItem item) {
        if (item instanceof GameObject go) return go;
        if (item instanceof RuntimeGameObjectAdapter adapter) return adapter.getGameObject();
        return null;
    }
}
