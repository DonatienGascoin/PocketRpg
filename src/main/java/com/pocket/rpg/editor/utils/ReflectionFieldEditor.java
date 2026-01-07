package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRefMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders ImGui controls for component fields using reflection.
 */
public class ReflectionFieldEditor {

    private static final Map<String, Object> editingOriginalValues = new HashMap<>();

    public static boolean drawComponent(ComponentData component, EditorEntity entity) {
        if (component == null) return false;

        ComponentMeta meta = ComponentRegistry.getByClassName(component.getType());
        if (meta == null) {
            ImGui.textDisabled("Unknown component type: " + component.getSimpleName());
            return false;
        }

        // Check for custom editor first
        if (CustomComponentEditorRegistry.hasCustomEditor(component.getType())) {
            return CustomComponentEditorRegistry.drawCustomEditor(component, entity);
        }

        boolean changed = false;
        for (FieldMeta fieldMeta : meta.fields()) {
            try {
                // When drawing the whole component, we want the integrated Undo logic
                changed |= drawField(component, fieldMeta, entity);
            } catch (Exception e) {
                ImGui.textColored(1f, 0.3f, 0.3f, 1f, fieldMeta.name() + ": Error");
            }
        }
        drawComponentReferences(meta.references(), entity);
        return changed;
    }

    /**
     * DRAW FIELD: Without Undo (e.g., Prefab Overrides)
     * Resets the ID stack to avoid nesting if the caller already pushed one.
     */
    public static boolean drawField(ComponentData data, FieldMeta meta) {
        return drawFieldInternal(data, meta, null);
    }

    /**
     * DRAW FIELD: With Undo logic
     */
    public static boolean drawField(ComponentData data, FieldMeta meta, EditorEntity entity) {
        String editKey = data.getType() + "." + meta.name() + "@" + System.identityHashCode(data);
        ImGui.pushID(editKey);
        boolean changed = drawFieldInternal(data, meta, entity);
        ImGui.popID();
        return changed;
    }

    private static boolean drawFieldInternal(ComponentData data, FieldMeta meta, EditorEntity entity) {
        if (data == null) {
            return false;
        }

        Class<?> type = meta.type();
        String fieldName = meta.name();
        Map<String, Object> fields = data.getFields();
        String label = meta.getDisplayName();
        String stateKey = data.getType() + "." + fieldName + "@" + System.identityHashCode(data);

        boolean wasActive = ImGui.isAnyItemActive();
        boolean fieldChanged = false;

        // PRIMITIVES
        if (type == int.class || type == Integer.class) {
            fieldChanged = FieldEditors.drawInt(label, fields, fieldName);
        } else if (type == float.class || type == Float.class) {
            fieldChanged = FieldEditors.drawFloat(label, fields, fieldName, 0.1f);
        } else if (type == double.class || type == Double.class) {
            // Handle double as float for UI
            Object oldValue = fields.get(fieldName);
            float floatValue = oldValue instanceof Number n ? n.floatValue() : 0f;
            float[] buf = {floatValue};
            if (ImGui.dragFloat(label, buf, 0.1f)) {
                fields.put(fieldName, (double) buf[0]);
                fieldChanged = true;
            }
        } else if (type == boolean.class || type == Boolean.class) {
            fieldChanged = FieldEditors.drawBoolean(label, fields, fieldName);
        } else if (type == String.class) {
            fieldChanged = FieldEditors.drawString(label, fields, fieldName);
        }

        // VECTORS
        else if (type == Vector2f.class) {
            fieldChanged = FieldEditors.drawVector2f(label, fields, fieldName);
        } else if (type == Vector3f.class) {
            fieldChanged = FieldEditors.drawVector3f(label, fields, fieldName);
        } else if (type == Vector4f.class) {
            fieldChanged = FieldEditors.drawColor(label, fields, fieldName);
        }

        // ENUMS
        else if (type.isEnum()) {
            fieldChanged = FieldEditors.drawEnum(label, fields, fieldName, type);
        }

        // ASSETS (handled separately with their own undo)
        else if (type == Sprite.class || type == Texture.class) {
            fieldChanged = FieldEditors.drawAsset(label, fields, fieldName, type, data, entity);
        }

        // UNKNOWN
        else {
            FieldEditors.drawReadOnly(label, fields, fieldName, type.getSimpleName());
        }

        // UNDO LOGIC
        if (entity != null) {
            if (ImGui.isItemActive() && !wasActive) {
                editingOriginalValues.put(stateKey, cloneValue(fields.get(fieldName)));
            }
            if (ImGui.isItemDeactivatedAfterEdit() && editingOriginalValues.containsKey(stateKey)) {
                Object originalValue = editingOriginalValues.remove(stateKey);
                Object currentValue = fields.get(fieldName);
                if (!valuesEqual(originalValue, currentValue)) {
                    UndoManager.getInstance().execute(new SetComponentFieldCommand(data, fieldName, originalValue, currentValue));
                }
            }
        }

        return fieldChanged;
    }

    private static Object cloneValue(Object value) {
        if (value instanceof Vector2f v) return new Vector2f(v);
        if (value instanceof Vector3f v) return new Vector3f(v);
        if (value instanceof Vector4f v) return new Vector4f(v);
        return value;
    }

    private static boolean valuesEqual(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    public static void drawComponentReferences(List<ComponentRefMeta> references, EditorEntity entity) {
        // ... (Reference drawing logic from your file remains unchanged)
    }

    public static void renderAssetPicker() {
        FieldEditors.renderAssetPicker();
    }
}