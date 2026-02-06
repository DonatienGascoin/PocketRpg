package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.LayoutGroup.ChildHorizontalAlignment;
import com.pocket.rpg.components.ui.LayoutGroup.ChildVerticalAlignment;
import com.pocket.rpg.components.ui.UIGridLayoutGroup;
import com.pocket.rpg.components.ui.UIGridLayoutGroup.Axis;
import com.pocket.rpg.components.ui.UIGridLayoutGroup.Constraint;
import com.pocket.rpg.components.ui.UIGridLayoutGroup.Corner;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImGui;

/**
 * Custom inspector for {@link UIGridLayoutGroup}.
 * <p>
 * Shows cell size fields (disabled when force-expand overrides them),
 * grid options, and the shared layout group fields.
 */
@InspectorFor(UIGridLayoutGroup.class)
public class UIGridLayoutGroupInspector extends LayoutGroupInspectorBase<UIGridLayoutGroup> {

    @Override
    protected boolean drawSpecificFields() {
        boolean changed = false;

        // Cell Size section
        ImGui.text("Cell Size");
        ImGui.separator();

        changed |= drawForceExpand();

        if (component.isChildForceExpandWidth()) {
            FieldEditors.inspectorRow("Cell Width", () ->
                    ImGui.textDisabled("Controlled by Force Width"));
        } else {
            changed |= FieldEditors.drawFloat("Cell Width", component, "cellWidth", 0.5f, 0, Float.MAX_VALUE);
        }

        if (component.isChildForceExpandHeight()) {
            FieldEditors.inspectorRow("Cell Height", () ->
                    ImGui.textDisabled("Controlled by Force Height"));
        } else {
            changed |= FieldEditors.drawFloat("Cell Height", component, "cellHeight", 0.5f, 0, Float.MAX_VALUE);
        }

        ImGui.spacing();

        // Grid Options section
        ImGui.text("Grid Options");
        ImGui.separator();

        changed |= FieldEditors.drawEnum("Start Corner", component, "startCorner", Corner.class);
        changed |= FieldEditors.drawEnum("Start Axis", component, "startAxis", Axis.class);
        changed |= FieldEditors.drawEnum("Constraint", component, "constraint", Constraint.class);

        if (component.getConstraint() != Constraint.FLEXIBLE) {
            changed |= FieldEditors.drawInt("Count", component, "constraintCount");
        }

        ImGui.spacing();

        // Alignment section
        ImGui.text("Alignment");
        ImGui.separator();

        if (component.isChildForceExpandWidth()) {
            FieldEditors.inspectorRow("Horizontal", () ->
                    ImGui.textDisabled("No effect (Force Width)"));
        } else {
            changed |= FieldEditors.drawEnum("Horizontal", component, "horizontalAlignment", ChildHorizontalAlignment.class);
        }

        if (component.isChildForceExpandHeight()) {
            FieldEditors.inspectorRow("Vertical", () ->
                    ImGui.textDisabled("No effect (Force Height)"));
        } else {
            changed |= FieldEditors.drawEnum("Vertical", component, "verticalAlignment", ChildVerticalAlignment.class);
        }

        return changed;
    }
}
