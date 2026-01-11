package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.ui.fields.FieldEditors;

/**
 * Custom editor for Transform component.
 * <p>
 * Uses TransformEditors which handles all complexity internally:
 * - Override detection and styling
 * - Reset button with proper buffer updates
 * - Undo capture/push
 * - XYZ axis coloring
 */
public class TransformInspector implements CustomComponentInspector {

    @Override
    public boolean draw(Component component, EditorGameObject entity) {
        boolean changed = false;

        changed |= FieldEditors.drawPosition("Position", entity);
        changed |= FieldEditors.drawScale("Scale", entity);
        changed |= FieldEditors.drawRotation("Rotation", entity);

        return changed;
    }
}
