package com.pocket.rpg.editor.utils;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;

/**
 * Custom editor for Transform component.
 * <p>
 * Uses TransformEditors which handles all complexity internally:
 * - Override detection and styling
 * - Reset button with proper buffer updates
 * - Undo capture/push
 * - XYZ axis coloring
 */
public class TransformEditor implements CustomComponentEditor {

    @Override
    public boolean draw(Component component, EditorGameObject entity) {
        boolean changed = false;

        changed |= FieldEditors.drawPosition("Position", entity);
        changed |= FieldEditors.drawScale("Scale", entity);
        changed |= FieldEditors.drawRotation("Rotation", entity);

        return changed;
    }
}
