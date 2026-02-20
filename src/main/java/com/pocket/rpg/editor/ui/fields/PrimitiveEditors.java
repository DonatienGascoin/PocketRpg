package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.Map;
import java.util.function.*;

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

    /**
     * Clears pending undo start values. Called by {@link FieldUndoTracker#clear()}.
     */
    public static void clearUndoState() {
        undoStartValues.clear();
    }

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

        FieldEditorContext.popOverrideStyle();

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

        FieldEditorContext.popOverrideStyle();

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

        FieldEditorContext.popOverrideStyle();

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

        FieldEditorContext.popOverrideStyle();

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

        FieldEditorContext.popOverrideStyle();

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

        FieldEditorContext.popOverrideStyle();

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

    // ========================================================================
    // GETTER/SETTER VARIANTS (no reflection, uses Consumer for undo)
    // ========================================================================

    /**
     * Draws an int field using getter/setter pattern with undo support.
     *
     * @param label  Display label
     * @param key    Unique key for undo tracking (e.g., "component.field")
     * @param getter Supplier to get current value
     * @param setter Consumer to set new value
     * @return true if value was changed
     */
    public static boolean drawInt(String label, String key, IntSupplier getter, IntConsumer setter) {
        int value = getter.getAsInt();
        intBuffer.set(value);

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.inputInt("##" + key, intBuffer);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, value);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        if (changed[0]) {
            setter.accept(intBuffer.get());
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            int startValue = (Integer) undoStartValues.remove(key);
            int currentValue = intBuffer.get();
            if (startValue != currentValue) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(setter::accept, startValue, currentValue, "Change " + label)
                );
            }
        }

        ImGui.popID();
        return changed[0];
    }

    /**
     * Draws a float drag field using getter/setter pattern with undo support.
     */
    public static boolean drawFloat(String label, String key,
                                    DoubleSupplier getter, DoubleConsumer setter,
                                    float speed) {
        return drawFloat(label, key, getter, setter, speed, 0, 0, "%.3f");
    }

    /**
     * Draws a float drag field using getter/setter pattern with undo support.
     */
    public static boolean drawFloat(String label, String key,
                                    DoubleSupplier getter, DoubleConsumer setter,
                                    float speed, float min, float max, String format) {
        float value = (float) getter.getAsDouble();
        floatBuffer[0] = value;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat("##" + key, floatBuffer, speed, min, max, format);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, value);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        if (changed[0]) {
            setter.accept(floatBuffer[0]);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            float startValue = (Float) undoStartValues.remove(key);
            float currentValue = floatBuffer[0];
            if (startValue != currentValue) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(
                                v -> setter.accept(v),
                                startValue, currentValue,
                                "Change " + label
                        )
                );
            }
        }

        ImGui.popID();
        return changed[0];
    }

    /**
     * Draws a float slider using getter/setter pattern with undo support.
     */
    public static boolean drawFloatSlider(String label, String key,
                                          DoubleSupplier getter, DoubleConsumer setter,
                                          float min, float max) {
        float value = (float) getter.getAsDouble();
        floatBuffer[0] = value;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.sliderFloat("##" + key, floatBuffer, min, max);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, value);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        if (changed[0]) {
            setter.accept(floatBuffer[0]);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            float startValue = (Float) undoStartValues.remove(key);
            float currentValue = floatBuffer[0];
            if (startValue != currentValue) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(
                                v -> setter.accept(v),
                                startValue, currentValue,
                                "Change " + label
                        )
                );
            }
        }

        ImGui.popID();
        return changed[0];
    }

    /**
     * Draws a boolean checkbox using getter/setter pattern with undo support.
     */
    public static boolean drawBoolean(String label, String key,
                                      BooleanSupplier getter, Consumer<Boolean> setter) {
        boolean value = getter.getAsBoolean();

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.checkbox("##" + key, value);
        });

        if (changed[0]) {
            boolean newValue = !value;
            setter.accept(newValue);
            // Boolean is instant - push undo immediately
            UndoManager.getInstance().push(
                    new SetterUndoCommand<>(setter, value, newValue, "Change " + label)
            );
        }

        ImGui.popID();
        return changed[0];
    }

    /**
     * Draws a string input using getter/setter pattern with undo support.
     */
    public static boolean drawString(String label, String key,
                                     Supplier<String> getter, Consumer<String> setter) {
        String value = getter.get() != null ? getter.get() : "";
        stringBuffer.set(value);

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.inputText("##" + key, stringBuffer);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, value);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        if (changed[0]) {
            setter.accept(stringBuffer.get());
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            String startValue = (String) undoStartValues.remove(key);
            String currentValue = stringBuffer.get();
            if (!startValue.equals(currentValue)) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(setter, startValue, currentValue, "Change " + label)
                );
            }
        }

        ImGui.popID();
        return changed[0];
    }

    // ========================================================================
    // DRAG INT2 / FLOAT2 (compound value with undo support)
    // ========================================================================

    private static final int[] int2Buffer = new int[2];
    private static final float[] float2Buffer = new float[2];

    /**
     * Draws a dragInt2 field using getter/setter pattern with undo support.
     * Encapsulates the activation/deactivation tracking pattern for compound values.
     *
     * @param label   Display label
     * @param key     Unique key for undo tracking
     * @param getterX Supplier for the first int value
     * @param getterY Supplier for the second int value
     * @param setter  Consumer accepting int[2] to set both values
     * @param speed   Drag speed
     * @return true if either value was changed
     */
    public static boolean drawDragInt2(String label, String key,
                                        IntSupplier getterX, IntSupplier getterY,
                                        Consumer<int[]> setter,
                                        float speed) {
        return drawDragInt2(label, key, getterX, getterY, setter, speed, 0, 0);
    }

    /**
     * Draws a dragInt2 field with min/max limits using getter/setter pattern with undo support.
     */
    public static boolean drawDragInt2(String label, String key,
                                        IntSupplier getterX, IntSupplier getterY,
                                        Consumer<int[]> setter,
                                        float speed, int min, int max) {
        int startX = getterX.getAsInt();
        int startY = getterY.getAsInt();
        int2Buffer[0] = startX;
        int2Buffer[1] = startY;

        ImGui.pushID(key);

        FieldEditorUtils.inspectorRow(label, () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (min != 0 || max != 0) {
                ImGui.dragInt2("##" + key, int2Buffer, speed, min, max);
            } else {
                ImGui.dragInt2("##" + key, int2Buffer, speed);
            }
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, new int[]{startX, startY});
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        boolean changed = false;
        if (int2Buffer[0] != startX || int2Buffer[1] != startY) {
            setter.accept(new int[]{int2Buffer[0], int2Buffer[1]});
            changed = true;
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            int[] oldValues = (int[]) undoStartValues.remove(key);
            if (oldValues[0] != int2Buffer[0] || oldValues[1] != int2Buffer[1]) {
                UndoManager.getInstance().push(new SetterUndoCommand<>(
                        setter, oldValues, new int[]{int2Buffer[0], int2Buffer[1]},
                        "Change " + label
                ));
            }
        }

        ImGui.popID();
        return changed;
    }

    /**
     * Draws a dragFloat2 field using getter/setter pattern with undo support.
     *
     * @param label   Display label
     * @param key     Unique key for undo tracking
     * @param getterX Supplier for the first float value
     * @param getterY Supplier for the second float value
     * @param setter  Consumer accepting float[2] to set both values
     * @param speed   Drag speed
     * @return true if either value was changed
     */
    public static boolean drawDragFloat2(String label, String key,
                                          DoubleSupplier getterX, DoubleSupplier getterY,
                                          Consumer<float[]> setter,
                                          float speed) {
        return drawDragFloat2(label, key, getterX, getterY, setter, speed, 0, 0, "%.3f");
    }

    /**
     * Draws a dragFloat2 field with min/max limits using getter/setter pattern with undo support.
     */
    public static boolean drawDragFloat2(String label, String key,
                                          DoubleSupplier getterX, DoubleSupplier getterY,
                                          Consumer<float[]> setter,
                                          float speed, float min, float max, String format) {
        float startX = (float) getterX.getAsDouble();
        float startY = (float) getterY.getAsDouble();
        float2Buffer[0] = startX;
        float2Buffer[1] = startY;

        ImGui.pushID(key);

        FieldEditorUtils.inspectorRow(label, () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (min != 0 || max != 0) {
                ImGui.dragFloat2("##" + key, float2Buffer, speed, min, max, format);
            } else {
                ImGui.dragFloat2("##" + key, float2Buffer, speed);
            }
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, new float[]{startX, startY});
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        boolean changed = false;
        if (float2Buffer[0] != startX || float2Buffer[1] != startY) {
            setter.accept(new float[]{float2Buffer[0], float2Buffer[1]});
            changed = true;
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            float[] oldValues = (float[]) undoStartValues.remove(key);
            if (oldValues[0] != float2Buffer[0] || oldValues[1] != float2Buffer[1]) {
                UndoManager.getInstance().push(new SetterUndoCommand<>(
                        setter, oldValues, new float[]{float2Buffer[0], float2Buffer[1]},
                        "Change " + label
                ));
            }
        }

        ImGui.popID();
        return changed;
    }

    // ========================================================================
    // INLINE VARIANTS (for side-by-side layouts)
    // ========================================================================

    /**
     * Draws a compact inline float field with label immediately followed by field.
     * Uses RELATIVE positioning - suitable for side-by-side layouts like "X: [__] Y: [__]".
     * Width must be set via ImGui.setNextItemWidth() BEFORE calling this method.
     *
     * @param label  Short label (e.g., "X", "Y")
     * @param key    Unique key for undo tracking
     * @param getter Supplier to get current value
     * @param setter Consumer to set new value
     * @param speed  Drag speed
     * @return true if value was changed
     */
    public static boolean drawFloatInline(String label, String key,
                                          DoubleSupplier getter, DoubleConsumer setter,
                                          float speed) {
        float value = (float) getter.getAsDouble();
        floatBuffer[0] = value;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inlineField(label, () -> {
            changed[0] = ImGui.dragFloat("##" + key, floatBuffer, speed);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, value);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        if (changed[0]) {
            setter.accept(floatBuffer[0]);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            float startValue = (Float) undoStartValues.remove(key);
            float currentValue = floatBuffer[0];
            if (startValue != currentValue) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(
                                v -> setter.accept(v),
                                startValue, currentValue,
                                "Change " + label
                        )
                );
            }
        }

        ImGui.popID();
        return changed[0];
    }

    /**
     * Draws a compact inline float field with explicit field width.
     * Width is set AFTER the label text, right before the drag field,
     * to avoid ImGui's NextItemData being consumed by the label.
     */
    public static boolean drawFloatInline(String label, String key,
                                          DoubleSupplier getter, DoubleConsumer setter,
                                          float speed, float fieldWidth) {
        float value = (float) getter.getAsDouble();
        floatBuffer[0] = value;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inlineField(label, fieldWidth, () -> {
            changed[0] = ImGui.dragFloat("##" + key, floatBuffer, speed);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, value);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        if (changed[0]) {
            setter.accept(floatBuffer[0]);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            float startValue = (Float) undoStartValues.remove(key);
            float currentValue = floatBuffer[0];
            if (startValue != currentValue) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(
                                v -> setter.accept(v),
                                startValue, currentValue,
                                "Change " + label
                        )
                );
            }
        }

        ImGui.popID();
        return changed[0];
    }

    /**
     * Draws a compact inline float field with min/max limits.
     */
    public static boolean drawFloatInline(String label, String key,
                                          DoubleSupplier getter, DoubleConsumer setter,
                                          float speed, float min, float max, String format) {
        float value = (float) getter.getAsDouble();
        floatBuffer[0] = value;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inlineField(label, () -> {
            changed[0] = ImGui.dragFloat("##" + key, floatBuffer, speed, min, max, format);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, value);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        if (changed[0]) {
            setter.accept(floatBuffer[0]);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            float startValue = (Float) undoStartValues.remove(key);
            float currentValue = floatBuffer[0];
            if (startValue != currentValue) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(
                                v -> setter.accept(v),
                                startValue, currentValue,
                                "Change " + label
                        )
                );
            }
        }

        ImGui.popID();
        return changed[0];
    }

    /**
     * Draws a compact inline float field with min/max limits and explicit field width.
     */
    public static boolean drawFloatInline(String label, String key,
                                          DoubleSupplier getter, DoubleConsumer setter,
                                          float speed, float min, float max, String format,
                                          float fieldWidth) {
        float value = (float) getter.getAsDouble();
        floatBuffer[0] = value;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        FieldEditorUtils.inlineField(label, fieldWidth, () -> {
            changed[0] = ImGui.dragFloat("##" + key, floatBuffer, speed, min, max, format);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, value);
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        if (changed[0]) {
            setter.accept(floatBuffer[0]);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            float startValue = (Float) undoStartValues.remove(key);
            float currentValue = floatBuffer[0];
            if (startValue != currentValue) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(
                                v -> setter.accept(v),
                                startValue, currentValue,
                                "Change " + label
                        )
                );
            }
        }

        ImGui.popID();
        return changed[0];
    }
}
