package com.pocket.rpg.editor.utils.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Field editors for vector types: Vector2f, Vector3f, Vector4f, and colors.
 * Includes undo support via capture on activation, push on deactivation.
 */
public final class VectorEditors {

    private static final float[] floatBuffer = new float[4];

    // Undo state
    private static final Map<String, Object> undoStartValues = new HashMap<>();

    private VectorEditors() {}

    private static String undoKey(Component component, String fieldName) {
        return System.identityHashCode(component) + "@" + fieldName;
    }

    // ========================================================================
    // VECTOR2F
    // ========================================================================

    public static boolean drawVector2f(String label, Component component, String fieldName) {
        return drawVector2f(label, component, fieldName, 0.1f);
    }

    public static boolean drawVector2f(String label, Component component, String fieldName, float speed) {
        Vector2f vec = FieldEditorUtils.getVector2f(component, fieldName);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;

        String key = undoKey(component, fieldName);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat2("##" + fieldName, floatBuffer, speed);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, new Vector2f(vec));
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            Vector2f newValue = new Vector2f(floatBuffer[0], floatBuffer[1]);
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            FieldEditorContext.markFieldOverridden(fieldName, newValue);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            Vector2f startValue = (Vector2f) undoStartValues.remove(key);
            Vector2f currentValue = new Vector2f(floatBuffer[0], floatBuffer[1]);
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
    // VECTOR3F
    // ========================================================================

    public static boolean drawVector3f(String label, Component component, String fieldName) {
        return drawVector3f(label, component, fieldName, 0.1f);
    }

    public static boolean drawVector3f(String label, Component component, String fieldName, float speed) {
        Vector3f vec = FieldEditorUtils.getVector3f(component, fieldName);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;
        floatBuffer[2] = vec.z;

        String key = undoKey(component, fieldName);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat3("##" + fieldName, floatBuffer, speed);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, new Vector3f(vec));
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            Vector3f newValue = new Vector3f(floatBuffer[0], floatBuffer[1], floatBuffer[2]);
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            FieldEditorContext.markFieldOverridden(fieldName, newValue);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            Vector3f startValue = (Vector3f) undoStartValues.remove(key);
            Vector3f currentValue = new Vector3f(floatBuffer[0], floatBuffer[1], floatBuffer[2]);
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
    // VECTOR4F
    // ========================================================================

    public static boolean drawVector4f(String label, Component component, String fieldName) {
        return drawVector4f(label, component, fieldName, 0.1f);
    }

    public static boolean drawVector4f(String label, Component component, String fieldName, float speed) {
        Vector4f vec = FieldEditorUtils.getVector4f(component, fieldName);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;
        floatBuffer[2] = vec.z;
        floatBuffer[3] = vec.w;

        String key = undoKey(component, fieldName);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat4("##" + fieldName, floatBuffer, speed);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, new Vector4f(vec));
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            Vector4f newValue = new Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]);
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            FieldEditorContext.markFieldOverridden(fieldName, newValue);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            Vector4f startValue = (Vector4f) undoStartValues.remove(key);
            Vector4f currentValue = new Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]);
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
    // COLOR (Vector4f with color picker)
    // ========================================================================

    public static boolean drawColor(String label, Component component, String fieldName) {
        Vector4f vec = FieldEditorUtils.getVector4f(component, fieldName);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;
        floatBuffer[2] = vec.z;
        floatBuffer[3] = vec.w;

        String key = undoKey(component, fieldName);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        FieldEditorUtils.inspectorRow(label, () -> {
            changed[0] = ImGui.colorEdit4("##" + fieldName, floatBuffer);
        });

        if (ImGui.isItemActivated()) {
            undoStartValues.put(key, new Vector4f(vec));
        }
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();

        FieldEditorContext.popOverrideStyle(fieldName);

        if (changed[0]) {
            Vector4f newValue = new Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]);
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            FieldEditorContext.markFieldOverridden(fieldName, newValue);
        }

        if (deactivated && undoStartValues.containsKey(key)) {
            Vector4f startValue = (Vector4f) undoStartValues.remove(key);
            Vector4f currentValue = new Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]);
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
