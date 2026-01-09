package com.pocket.rpg.editor.utils.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.Map;

/**
 * Field editors for primitive types: int, float, boolean, string.
 * Includes undo support via capture on activation, push on deactivation.
 */
public final class PrimitiveEditors {

    private static final ImString stringBuffer = new ImString(256);
    private static final ImInt intBuffer = new ImInt();
    private static final float[] floatBuffer = new float[1];

    // Undo state: key = component@field, value = start value
    private static final Map<String, Object> undoStartValues = new HashMap<>();

    private PrimitiveEditors() {}

    private static String undoKey(Component component, String fieldName) {
        return System.identityHashCode(component) + "@" + fieldName;
    }

    // ========================================================================
    // INT
    // ========================================================================

    public static boolean drawInt(String label, Component component, String fieldName) {
        int intValue = ComponentReflectionUtils.getInt(component, fieldName, 0);
        intBuffer.set(intValue);

        String key = undoKey(component, fieldName);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.inputInt("##" + fieldName, intBuffer);
        });

        // Undo tracking
        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, intValue);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, intBuffer.get());
            FieldEditorContext.markFieldOverridden(fieldName, intBuffer.get());
        }

        // Push undo on deactivation
        if (deactivated && undoStartValues.containsKey(key)) {
            Object startValue = undoStartValues.remove(key);
            int currentValue = intBuffer.get();
            if (!startValue.equals(currentValue)) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, fieldName, startValue, currentValue, FieldEditorContext.getEntity())
                );
            }
        }

        boolean reset = FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    // ========================================================================
    // FLOAT
    // ========================================================================

    public static boolean drawFloat(String label, Component component, String fieldName, float speed) {
        float floatValue = ComponentReflectionUtils.getFloat(component, fieldName, 0f);
        floatBuffer[0] = floatValue;

        String key = undoKey(component, fieldName);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat("##" + fieldName, floatBuffer, speed);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, floatValue);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, floatBuffer[0]);
            FieldEditorContext.markFieldOverridden(fieldName, floatBuffer[0]);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            Object startValue = undoStartValues.remove(key);
            float currentValue = floatBuffer[0];
            if (!startValue.equals(currentValue)) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, fieldName, startValue, currentValue, FieldEditorContext.getEntity())
                );
            }
        }

        boolean reset = FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawFloat(String label, Component component, String fieldName,
                                    float speed, float min, float max) {
        float floatValue = ComponentReflectionUtils.getFloat(component, fieldName, 0f);
        floatBuffer[0] = floatValue;

        String key = undoKey(component, fieldName);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat("##" + fieldName, floatBuffer, speed, min, max);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, floatValue);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, floatBuffer[0]);
            FieldEditorContext.markFieldOverridden(fieldName, floatBuffer[0]);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            Object startValue = undoStartValues.remove(key);
            float currentValue = floatBuffer[0];
            if (!startValue.equals(currentValue)) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, fieldName, startValue, currentValue, FieldEditorContext.getEntity())
                );
            }
        }

        boolean reset = FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawFloatSlider(String label, Component component, String fieldName,
                                          float min, float max) {
        float floatValue = ComponentReflectionUtils.getFloat(component, fieldName, 0f);
        floatBuffer[0] = floatValue;

        String key = undoKey(component, fieldName);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.sliderFloat("##" + fieldName, floatBuffer, min, max);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, floatValue);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, floatBuffer[0]);
            FieldEditorContext.markFieldOverridden(fieldName, floatBuffer[0]);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            Object startValue = undoStartValues.remove(key);
            float currentValue = floatBuffer[0];
            if (!startValue.equals(currentValue)) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, fieldName, startValue, currentValue, FieldEditorContext.getEntity())
                );
            }
        }

        boolean reset = FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    // ========================================================================
    // BOOLEAN
    // ========================================================================

    public static boolean drawBoolean(String label, Component component, String fieldName) {
        boolean boolValue = ComponentReflectionUtils.getBoolean(component, fieldName, false);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.checkbox("##" + fieldName, boolValue);
        });

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            boolean newValue = !boolValue;
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            FieldEditorContext.markFieldOverridden(fieldName, newValue);
            
            // Boolean is instant - push undo immediately
            UndoManager.getInstance().push(
                    new SetComponentFieldCommand(component, fieldName, boolValue, newValue, FieldEditorContext.getEntity())
            );
        }

        boolean reset = FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    // ========================================================================
    // STRING
    // ========================================================================

    public static boolean drawString(String label, Component component, String fieldName) {
        String strValue = ComponentReflectionUtils.getString(component, fieldName, "");
        stringBuffer.set(strValue);

        String key = undoKey(component, fieldName);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.inputText("##" + fieldName, stringBuffer);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, strValue);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            String newValue = stringBuffer.get();
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            FieldEditorContext.markFieldOverridden(fieldName, newValue);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            Object startValue = undoStartValues.remove(key);
            String currentValue = stringBuffer.get();
            if (!startValue.equals(currentValue)) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, fieldName, startValue, currentValue, FieldEditorContext.getEntity())
                );
            }
        }

        boolean reset = FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }
}
