package com.pocket.rpg.editor.components;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders ImGui controls for component fields using reflection.
 * <p>
 * Supports these field types:
 * - Primitives: int, float, boolean, String
 * - Vectors: Vector2f, Vector3f, Vector4f
 * - Enums: Any enum type
 * - Assets: Sprite, Texture (display only for now)
 * <p>
 * Usage:
 * if (ReflectionFieldEditor.drawComponent(myComponent)) {
 * scene.markDirty();
 * }
 */
public class ReflectionFieldEditor {

    // Reusable buffers to avoid allocations
    private static final ImString stringBuffer = new ImString(256);
    private static final ImInt intBuffer = new ImInt();
    private static final float[] floatBuffer = new float[4];

    // Cache for enum values
    private static final Map<Class<?>, Object[]> enumCache = new HashMap<>();

    /**
     * Draws all editable fields of a component.
     *
     * @param component The component to edit
     * @return true if any field was changed
     */
    public static boolean drawComponent(Object component) {
        if (component == null) {
            return false;
        }

        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) {
            ImGui.textDisabled("Unknown component type");
            return false;
        }

        boolean changed = false;

        for (FieldMeta fieldMeta : meta.fields()) {
            try {
                changed |= drawField(component, fieldMeta);
            } catch (Exception e) {
                ImGui.textColored(1f, 0.3f, 0.3f, 1f,
                        fieldMeta.name() + ": Error - " + e.getMessage());
            }
        }

        return changed;
    }

    /**
     * Draws a single field with appropriate control.
     */
    public static boolean drawField(Object obj, FieldMeta meta) throws IllegalAccessException {
        Field field = meta.field();
        field.setAccessible(true);

        Class<?> type = meta.type();
        Object value = field.get(obj);
        String label = meta.getDisplayName();

        boolean changed = false;
        Object newValue = value;

        ImGui.pushID(meta.name());

        // ============================================================
        // PRIMITIVES
        // ============================================================

        if (type == int.class || type == Integer.class) {
            int intValue = value != null ? (int) value : 0;
            intBuffer.set(intValue);
            if (ImGui.inputInt(label, intBuffer)) {
                newValue = intBuffer.get();
                changed = true;
            }
        } else if (type == float.class || type == Float.class) {
            float floatValue = value != null ? (float) value : 0f;
            floatBuffer[0] = floatValue;
            if (ImGui.dragFloat(label, floatBuffer, 0.1f)) {
                newValue = floatBuffer[0];
                changed = true;
            }
        } else if (type == double.class || type == Double.class) {
            double doubleValue = value != null ? (double) value : 0.0;
            floatBuffer[0] = (float) doubleValue;
            if (ImGui.dragFloat(label, floatBuffer, 0.1f)) {
                newValue = (double) floatBuffer[0];
                changed = true;
            }
        } else if (type == boolean.class || type == Boolean.class) {
            boolean boolValue = value != null && (boolean) value;
            if (ImGui.checkbox(label, boolValue)) {
                newValue = !boolValue;
                changed = true;
            }
        } else if (type == String.class) {
            String strValue = value != null ? (String) value : "";
            stringBuffer.set(strValue);
            if (ImGui.inputText(label, stringBuffer)) {
                newValue = stringBuffer.get();
                changed = true;
            }
        }

        // ============================================================
        // VECTORS
        // ============================================================

        else if (type == Vector2f.class) {
            Vector2f vec = value != null ? (Vector2f) value : new Vector2f();
            floatBuffer[0] = vec.x;
            floatBuffer[1] = vec.y;
            if (ImGui.dragFloat2(label, floatBuffer, 0.1f)) {
                if (value == null) {
                    newValue = new Vector2f(floatBuffer[0], floatBuffer[1]);
                } else {
                    vec.set(floatBuffer[0], floatBuffer[1]);
                }
                changed = true;
            }
        } else if (type == Vector3f.class) {
            Vector3f vec = value != null ? (Vector3f) value : new Vector3f();
            floatBuffer[0] = vec.x;
            floatBuffer[1] = vec.y;
            floatBuffer[2] = vec.z;
            if (ImGui.dragFloat3(label, floatBuffer, 0.1f)) {
                if (value == null) {
                    newValue = new Vector3f(floatBuffer[0], floatBuffer[1], floatBuffer[2]);
                } else {
                    vec.set(floatBuffer[0], floatBuffer[1], floatBuffer[2]);
                }
                changed = true;
            }
        } else if (type == Vector4f.class) {
            Vector4f vec = value != null ? (Vector4f) value : new Vector4f();
            floatBuffer[0] = vec.x;
            floatBuffer[1] = vec.y;
            floatBuffer[2] = vec.z;
            floatBuffer[3] = vec.w;
            if (ImGui.colorEdit4(label, floatBuffer)) {
                if (value == null) {
                    newValue = new Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]);
                } else {
                    vec.set(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]);
                }
                changed = true;
            }
        }

        // ============================================================
        // ENUMS
        // ============================================================

        else if (type.isEnum()) {
            Object[] constants = getEnumConstants(type);
            int currentIndex = 0;

            if (value != null) {
                for (int i = 0; i < constants.length; i++) {
                    if (constants[i].equals(value)) {
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
            if (ImGui.combo(label, intBuffer, names)) {
                newValue = constants[intBuffer.get()];
                changed = true;
            }
        }

        // ============================================================
        // ASSETS (display only for now)
        // ============================================================

        else if (type == Sprite.class) {
            Sprite sprite = (Sprite) value;
            String display = sprite != null ? sprite.getName() : "(none)";
            ImGui.labelText(label, display);
            // TODO: Add asset picker button
        } else if (type == Texture.class) {
            Texture texture = (Texture) value;
            String display = texture != null ? texture.getFilePath() : "(none)";
            ImGui.labelText(label, display);
            // TODO: Add asset picker button
        }

        // ============================================================
        // UNKNOWN TYPE
        // ============================================================

        else {
            String display = value != null ? value.toString() : "(null)";
            ImGui.labelText(label, display);
            ImGui.sameLine();
            ImGui.textDisabled("(read-only)");
        }

        ImGui.popID();

        // Apply change
        if (changed && newValue != value) {
            field.set(obj, newValue);
        }

        return changed;
    }

    /**
     * Gets cached enum constants for a type.
     */
    private static Object[] getEnumConstants(Class<?> enumType) {
        return enumCache.computeIfAbsent(enumType, Class::getEnumConstants);
    }
}