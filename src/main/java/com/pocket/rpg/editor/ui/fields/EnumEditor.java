package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.HashMap;
import java.util.Map;

/**
 * Field editor for enum types.
 * Includes undo support.
 */
public final class EnumEditor {

    private static final ImInt intBuffer = new ImInt();
    private static final Map<Class<?>, Object[]> enumCache = new HashMap<>();

    private EnumEditor() {}

    public static boolean drawEnum(String label, Component component, String fieldName, Class<?> enumClass) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
        Object[] constants = getEnumConstants(enumClass);

        int currentIndex = 0;
        if (value != null) {
            String valueStr = value instanceof Enum<?> e ? e.name() : value.toString();
            for (int i = 0; i < constants.length; i++) {
                if (constants[i].toString().equals(valueStr)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = constants[i].toString();
        }

        intBuffer.set(currentIndex);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.combo("##" + fieldName, intBuffer, names);
        });

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            Object oldValue = value;
            Object newValue = constants[intBuffer.get()];
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            FieldEditorContext.markFieldOverridden(fieldName, newValue);

            // Enum is instant change - push undo immediately
            UndoManager.getInstance().push(
                    new SetComponentFieldCommand(component, fieldName, oldValue, newValue, FieldEditorContext.getEntity())
            );
        }

        boolean reset = FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    private static Object[] getEnumConstants(Class<?> enumType) {
        return enumCache.computeIfAbsent(enumType, Class::getEnumConstants);
    }
}
