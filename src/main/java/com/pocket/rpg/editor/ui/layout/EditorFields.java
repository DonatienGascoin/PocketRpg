package com.pocket.rpg.editor.ui.layout;

import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import imgui.ImGui;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Layout-aware field widgets with integrated undo support.
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Horizontal layout: "Label [field]" compact, calculated width</li>
 *   <li>Vertical layout: "Label" at column, field fills remaining</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * EditorLayout.beginHorizontal(2);
 * EditorFields.floatField("X", "offset.x", getter, setter, 1f);
 * EditorFields.floatField("Y", "offset.y", getter, setter, 1f);
 * EditorLayout.endHorizontal();
 * </pre>
 */
public class EditorFields {

    private static final float LABEL_WIDTH = 120f;
    private static final float[] floatBuf = new float[1];
    private static final Map<String, Object> undoStartValues = new HashMap<>();

    // ========================================================================
    // FLOAT FIELDS
    // ========================================================================

    /**
     * Draws a labeled float field with undo support.
     *
     * @param label   Short label (e.g., "X", "Y")
     * @param undoKey Unique key for undo tracking
     * @param getter  Supplier to get current value
     * @param setter  Consumer to set new value
     * @param speed   Drag speed
     * @return true if value changed
     */
    public static boolean floatField(String label, String undoKey,
                                      DoubleSupplier getter, DoubleConsumer setter,
                                      float speed) {
        EditorLayout.beforeWidget();

        ImGui.text(label);

        if (EditorLayout.isHorizontal()) {
            ImGui.sameLine();
            float labelWidth = ImGui.calcTextSize(label).x;
            ImGui.setNextItemWidth(EditorLayout.calculateWidgetWidth(labelWidth));
        } else {
            ImGui.sameLine(LABEL_WIDTH);
            ImGui.setNextItemWidth(-1);
        }

        floatBuf[0] = (float) getter.getAsDouble();
        boolean changed = ImGui.dragFloat("##" + undoKey, floatBuf, speed);

        handleUndo(undoKey, label, floatBuf[0], setter);

        if (changed) {
            setter.accept(floatBuf[0]);
        }

        return changed;
    }

    /**
     * Draws a labeled float field with min/max limits and format.
     */
    public static boolean floatField(String label, String undoKey,
                                      DoubleSupplier getter, DoubleConsumer setter,
                                      float speed, float min, float max, String format) {
        EditorLayout.beforeWidget();

        ImGui.text(label);

        if (EditorLayout.isHorizontal()) {
            ImGui.sameLine();
            float labelWidth = ImGui.calcTextSize(label).x;
            ImGui.setNextItemWidth(EditorLayout.calculateWidgetWidth(labelWidth));
        } else {
            ImGui.sameLine(LABEL_WIDTH);
            ImGui.setNextItemWidth(-1);
        }

        floatBuf[0] = (float) getter.getAsDouble();
        boolean changed = ImGui.dragFloat("##" + undoKey, floatBuf, speed, min, max, format);

        handleUndo(undoKey, label, floatBuf[0], setter);

        if (changed) {
            setter.accept(floatBuf[0]);
        }

        return changed;
    }

    // ========================================================================
    // VECTOR FIELDS
    // ========================================================================

    /**
     * Draws X/Y fields horizontally: "X [____] Y [____]"
     *
     * @param undoKey Base key (will append .x and .y)
     * @param getter  Supplier to get current Vector2f
     * @param setter  BiConsumer to set x, y values
     * @param speed   Drag speed
     * @return true if value changed
     */
    public static boolean vector2Field(String undoKey,
                                        Supplier<Vector2f> getter,
                                        BiConsumer<Float, Float> setter,
                                        float speed) {
        EditorLayout.beginHorizontal(2);
        boolean changed = false;
        Vector2f current = getter.get();

        if (floatField("X", undoKey + ".x", () -> current.x,
                v -> setter.accept((float) v, current.y), speed)) {
            changed = true;
        }
        if (floatField("Y", undoKey + ".y", () -> current.y,
                v -> setter.accept(current.x, (float) v), speed)) {
            changed = true;
        }

        EditorLayout.endHorizontal();
        return changed;
    }

    /**
     * Draws X/Y/Z fields horizontally.
     */
    public static boolean vector3Field(String undoKey,
                                        Supplier<Vector3f> getter,
                                        TriConsumer<Float, Float, Float> setter,
                                        float speed) {
        EditorLayout.beginHorizontal(3);
        boolean changed = false;
        Vector3f current = getter.get();

        if (floatField("X", undoKey + ".x", () -> current.x,
                v -> setter.accept((float) v, current.y, current.z), speed)) {
            changed = true;
        }
        if (floatField("Y", undoKey + ".y", () -> current.y,
                v -> setter.accept(current.x, (float) v, current.z), speed)) {
            changed = true;
        }
        if (floatField("Z", undoKey + ".z", () -> current.z,
                v -> setter.accept(current.x, current.y, (float) v), speed)) {
            changed = true;
        }

        EditorLayout.endHorizontal();
        return changed;
    }

    // ========================================================================
    // UNDO HELPER
    // ========================================================================

    private static void handleUndo(String undoKey, String label, float currentValue, DoubleConsumer setter) {
        if (ImGui.isItemActivated()) {
            undoStartValues.put(undoKey, currentValue);
        }
        if (ImGui.isItemDeactivatedAfterEdit() && undoStartValues.containsKey(undoKey)) {
            float startValue = (Float) undoStartValues.remove(undoKey);
            if (startValue != currentValue) {
                UndoManager.getInstance().push(
                        new SetterUndoCommand<>(v -> setter.accept(v), startValue, currentValue, "Change " + label)
                );
            }
        }
    }

    // ========================================================================
    // FUNCTIONAL INTERFACES
    // ========================================================================

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
