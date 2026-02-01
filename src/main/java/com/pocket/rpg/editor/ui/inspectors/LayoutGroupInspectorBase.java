package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.LayoutGroup;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImGui;

/**
 * Shared base inspector for all LayoutGroup subclasses.
 * <p>
 * Draws common fields (spacing, padding, force-expand) and delegates
 * subclass-specific fields to {@link #drawSpecificFields()}.
 *
 * @param <T> The concrete LayoutGroup subclass
 */
public abstract class LayoutGroupInspectorBase<T extends LayoutGroup>
        extends CustomComponentInspector<T> {

    /**
     * Draws the spacing field.
     *
     * @return true if the field was changed
     */
    protected boolean drawSpacing() {
        return FieldEditors.drawFloat("Spacing", component, "spacing", 0.5f, 0, Float.MAX_VALUE);
    }

    /**
     * Draws padding as four fields (Left, Right, Top, Bottom).
     *
     * @return true if any field was changed
     */
    protected boolean drawPadding() {
        boolean changed = false;
        ImGui.text("Padding");
        changed |= FieldEditors.drawFloat("Left", component, "paddingLeft", 0.5f, 0, Float.MAX_VALUE);
        changed |= FieldEditors.drawFloat("Right", component, "paddingRight", 0.5f, 0, Float.MAX_VALUE);
        changed |= FieldEditors.drawFloat("Top", component, "paddingTop", 0.5f, 0, Float.MAX_VALUE);
        changed |= FieldEditors.drawFloat("Bottom", component, "paddingBottom", 0.5f, 0, Float.MAX_VALUE);
        return changed;
    }

    /**
     * Draws the force-expand width and height checkboxes.
     *
     * @return true if any field was changed
     */
    protected boolean drawForceExpand() {
        boolean changed = false;
        changed |= FieldEditors.drawBoolean("Force Width", component, "childForceExpandWidth");
        changed |= FieldEditors.drawBoolean("Force Height", component, "childForceExpandHeight");
        return changed;
    }

    /**
     * Subclasses draw their type-specific fields here.
     *
     * @return true if any field was changed
     */
    protected abstract boolean drawSpecificFields();

    @Override
    public boolean draw() {
        boolean changed = false;
        changed |= drawSpecificFields();
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        changed |= drawSpacing();
        changed |= drawPadding();
        return changed;
    }
}
