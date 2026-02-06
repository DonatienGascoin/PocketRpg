package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

        FieldEditorContext.popOverrideStyle();

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

    // ========================================================================
    // GETTER/SETTER VARIANT (no reflection, uses Consumer for undo)
    // ========================================================================

    /**
     * Draws an enum combo using getter/setter pattern with undo support.
     *
     * @param label     Display label
     * @param key       Unique key for undo tracking
     * @param getter    Supplier to get current enum value
     * @param setter    Consumer to set new enum value
     * @param enumClass The enum class
     * @param <E>       Enum type
     * @return true if value was changed
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> boolean drawEnum(String label, String key,
                                                        Supplier<E> getter, Consumer<E> setter,
                                                        Class<E> enumClass) {
        E value = getter.get();
        E[] constants = enumClass.getEnumConstants();

        int currentIndex = 0;
        if (value != null) {
            for (int i = 0; i < constants.length; i++) {
                if (constants[i] == value) {
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

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.combo("##" + key, intBuffer, names);
        });

        if (changed[0]) {
            E oldValue = value;
            E newValue = constants[intBuffer.get()];
            setter.accept(newValue);

            // Enum is instant change - push undo immediately
            UndoManager.getInstance().push(
                    new SetterUndoCommand<>(setter, oldValue, newValue, "Change " + label)
            );
        }

        ImGui.popID();
        return changed[0];
    }
}
