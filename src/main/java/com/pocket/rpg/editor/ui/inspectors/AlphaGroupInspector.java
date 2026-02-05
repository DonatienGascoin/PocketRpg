package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.AlphaGroup;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;

/**
 * Custom editor for AlphaGroup component.
 * Provides a slider for alpha with immediate preview.
 */
@InspectorFor(AlphaGroup.class)
public class AlphaGroupInspector extends CustomComponentInspector<AlphaGroup> {

    private Float alphaEditStartValue;

    @Override
    public boolean draw() {
        boolean changed = false;

        float alpha = component.getAlpha();
        float[] alphaBuf = {alpha};

        ImGui.text("Alpha");
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);

        boolean sliderChanged = ImGui.sliderFloat("##alpha", alphaBuf, 0f, 1f);

        if (ImGui.isItemActivated()) {
            alphaEditStartValue = alpha;
        }

        if (sliderChanged) {
            ComponentReflectionUtils.setFieldValue(component, "alpha", alphaBuf[0]);
            component.applyAlphaInEditor();
            changed = true;
        }

        if (ImGui.isItemDeactivatedAfterEdit() && alphaEditStartValue != null) {
            float newValue = component.getAlpha();
            if (entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "alpha", alphaEditStartValue, newValue, entity)
                );
            }
            alphaEditStartValue = null;
        }

        return changed;
    }
}
