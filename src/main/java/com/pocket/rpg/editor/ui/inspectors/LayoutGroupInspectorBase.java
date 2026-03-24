package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.LayoutGroup;
import com.pocket.rpg.components.ui.LayoutGroup.ChildSizeMode;
import com.pocket.rpg.components.ui.UIGridLayoutGroup;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.ui.fields.FieldUndoTracker;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * Shared base inspector for all LayoutGroup subclasses.
 * <p>
 * Draws common fields (spacing, padding) and provides helpers for
 * force-expand and child size fields that subclasses include in their
 * {@link #drawSpecificFields()} implementation.
 *
 * @param <T> The concrete LayoutGroup subclass
 */
public abstract class LayoutGroupInspectorBase<T extends LayoutGroup>
        extends CustomComponentInspector<T> {

    private static final ImInt enumBuffer = new ImInt();
    private static final float[] floatBuffer = new float[1];

    /**
     * Draws the spacing field.
     */
    protected boolean drawSpacing() {
        return FieldEditors.drawFloat("Spacing", component, "spacing", 0.5f, 0, Float.MAX_VALUE);
    }

    /**
     * Draws padding as four fields (Left, Right, Top, Bottom).
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
     * Disables each checkbox when the corresponding child size mode is active.
     */
    protected boolean drawForceExpand() {
        boolean changed = false;

        if (component.getChildWidthMode() != ChildSizeMode.NONE) {
            FieldEditors.inspectorRow("Force Width", () ->
                    ImGui.textDisabled("Controlled by Child Width"));
        } else {
            changed |= FieldEditors.drawBoolean("Force Width", component, "childForceExpandWidth");
        }

        if (component.getChildHeightMode() != ChildSizeMode.NONE) {
            FieldEditors.inspectorRow("Force Height", () ->
                    ImGui.textDisabled("Controlled by Child Height"));
        } else {
            changed |= FieldEditors.drawBoolean("Force Height", component, "childForceExpandHeight");
        }

        return changed;
    }

    /**
     * Draws child size mode dropdowns with optional inline value fields.
     * Skipped for grid layout (which has its own cellWidth/cellHeight).
     */
    protected boolean drawChildSize() {
        if (component instanceof UIGridLayoutGroup) return false;

        boolean changed = false;
        changed |= drawChildSizeRow("Child Width", "childWidthMode", "childWidth");
        changed |= drawChildSizeRow("Child Height", "childHeightMode", "childHeight");
        return changed;
    }

    private boolean drawChildSizeRow(String label, String modeField, String valueField) {
        ChildSizeMode mode = (ChildSizeMode) ComponentReflectionUtils.getFieldValue(component, modeField);
        if (mode == null) mode = ChildSizeMode.NONE;

        ChildSizeMode[] constants = ChildSizeMode.values();
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = constants[i].toString();
        }

        int currentIndex = mode.ordinal();
        enumBuffer.set(currentIndex);

        float floatValue = ComponentReflectionUtils.getFloat(component, valueField, 0f);
        floatBuffer[0] = floatValue;

        final boolean[] changed = {false};
        final ChildSizeMode currentMode = mode;

        ImGui.pushID(modeField);
        FieldEditorContext.pushOverrideStyle(modeField);

        FieldEditors.inspectorRow(label, () -> {
            float availWidth = ImGui.getContentRegionAvailX();

            // Enum combo — takes full width when NONE, ~45% when value field is shown
            if (currentMode != ChildSizeMode.NONE) {
                ImGui.setNextItemWidth(availWidth * 0.45f);
            } else {
                ImGui.setNextItemWidth(availWidth);
            }
            boolean comboChanged = ImGui.combo("##" + modeField, enumBuffer, names);
            if (comboChanged) {
                Object oldValue = currentMode;
                Object newValue = constants[enumBuffer.get()];
                ComponentReflectionUtils.setFieldValue(component, modeField, newValue);
                FieldEditorContext.markFieldOverridden(modeField, newValue);
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, modeField, oldValue, newValue,
                                FieldEditorContext.getEntity())
                );
                changed[0] = true;
            }

            // Value float — shown when mode != NONE
            if (currentMode != ChildSizeMode.NONE) {
                ImGui.sameLine();
                ImGui.setNextItemWidth(availWidth * 0.5f);
                String format = (currentMode == ChildSizeMode.PERCENT) ? "%.1f %%" : "%.1f";
                boolean dragChanged = ImGui.dragFloat("##" + valueField, floatBuffer, 0.5f, 0, Float.MAX_VALUE, format);

                // Undo tracking for continuous drag — must be right after dragFloat
                String undoKey = FieldUndoTracker.undoKey(component, valueField);
                FieldUndoTracker.trackReflection(undoKey, floatBuffer[0], component, valueField,
                        FieldEditorContext.getEntity());

                if (dragChanged) {
                    ComponentReflectionUtils.setFieldValue(component, valueField, floatBuffer[0]);
                    FieldEditorContext.markFieldOverridden(valueField, floatBuffer[0]);
                    changed[0] = true;
                }
            }
        });

        FieldEditorContext.popOverrideStyle();

        boolean reset = FieldEditorUtils.drawResetButtonIfNeeded(component, modeField);
        ImGui.popID();

        return changed[0] || reset;
    }

    /**
     * Subclasses draw their type-specific fields here.
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
