package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.LayoutGroup.ChildHorizontalAlignment;
import com.pocket.rpg.components.ui.UIVerticalLayoutGroup;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImGui;

/**
 * Custom inspector for {@link UIVerticalLayoutGroup}.
 * <p>
 * Shows horizontal child alignment, child size controls, and the shared layout group fields.
 */
@InspectorFor(UIVerticalLayoutGroup.class)
public class UIVerticalLayoutGroupInspector extends LayoutGroupInspectorBase<UIVerticalLayoutGroup> {

    @Override
    protected boolean drawSpecificFields() {
        boolean changed = false;
        changed |= FieldEditors.drawEnum("Alignment", component, "childAlignment", ChildHorizontalAlignment.class);

        ImGui.spacing();
        ImGui.text("Child Size");
        ImGui.separator();
        changed |= drawForceExpand();
        changed |= drawChildSize();

        return changed;
    }
}
