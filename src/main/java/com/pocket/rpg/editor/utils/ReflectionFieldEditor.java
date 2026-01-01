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
 * <p>
 * Uses FieldEditors for the actual field rendering.
 * All field changes are wrapped in undo commands when editing completes.
 */
public class ReflectionFieldEditor {

    // Track original values for undo when editing starts
    private static final Map<String, Object> editingOriginalValues = new HashMap<>();

    public static boolean drawComponent(ComponentData component, EditorEntity entity) {
        if (component == null) {
            return false;
        }

        // Check for custom editor first
        if (CustomComponentEditorRegistry.hasCustomEditor(component.getType())) {
            return CustomComponentEditorRegistry.drawCustomEditor(component, entity);
        }

        ComponentMeta meta = ComponentRegistry.getByClassName(component.getType());
        if (meta == null) {
            ImGui.textDisabled("Unknown component type: " + component.getSimpleName());
            return false;
        }

        boolean changed = false;

        for (FieldMeta fieldMeta : meta.fields()) {
            try {
                changed |= drawField(component, fieldMeta, entity);
            } catch (Exception e) {
                ImGui.textColored(1f, 0.3f, 0.3f, 1f,
                        fieldMeta.name() + ": Error - " + e.getMessage());
            }
        }

        drawComponentReferences(meta.references(), entity);

        return changed;
    }

    public static boolean drawComponent(ComponentData component) {
        return drawComponent(component, null);
    }

    public static boolean drawField(ComponentData data, FieldMeta meta) {
        return drawField(data, meta, null);
    }

    public static boolean drawField(ComponentData data, FieldMeta meta, EditorEntity entity) {
        Class<?> type = meta.type();
        String fieldName = meta.name();
        Map<String, Object> fields = data.getFields();
        String label = meta.getDisplayName();

        // Create unique key for tracking this field's edit state
        String editKey = data.getType() + "." + fieldName + "@" + System.identityHashCode(data);

        ImGui.pushID(fieldName);

        boolean fieldChanged = false;
        boolean wasActive = ImGui.isAnyItemActive();

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

        // FIX: Track editing state and only create undo when editing completes
        boolean isActive = ImGui.isItemActive();
        boolean editCompleted = ImGui.isItemDeactivatedAfterEdit();

        // When user starts editing, save original value
        if (isActive && !wasActive) {
            editingOriginalValues.put(editKey, cloneValue(fields.get(fieldName)));
        }

        // When editing completes, create undo command
        if (editCompleted && editingOriginalValues.containsKey(editKey)) {
            Object originalValue = editingOriginalValues.remove(editKey);
            Object currentValue = fields.get(fieldName);

            if (!valuesEqual(originalValue, currentValue)) {
                UndoManager.getInstance().execute(
                        new SetComponentFieldCommand(data, fieldName, originalValue, currentValue, entity)
                );
            }
        }

        // Clean up if editing was cancelled
        if (!isActive && editingOriginalValues.containsKey(editKey)) {
            editingOriginalValues.remove(editKey);
        }

        ImGui.popID();

        return fieldChanged;
    }

    private static Object cloneValue(Object value) {
        if (value == null) return null;
        if (value instanceof Vector2f v) return new Vector2f(v);
        if (value instanceof Vector3f v) return new Vector3f(v);
        if (value instanceof Vector4f v) return new Vector4f(v);
        // Primitives, Strings, enums are immutable
        return value;
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public static void drawComponentReferences(List<ComponentRefMeta> references, EditorEntity entity) {
        if (references == null || references.isEmpty()) {
            return;
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);
        ImGui.text("Component References (runtime)");
        ImGui.popStyleColor();

        for (ComponentRefMeta ref : references) {
            ImGui.pushID(ref.name());

            String label = ref.getDisplayName();
            String description = ref.getEditorDescription();

            ReferenceStatus status = checkReferenceStatus(ref, entity);

            if (!ref.required()) {
                description += " [optional]";
            }

            String statusIcon;
            float r, g, b;

            switch (status) {
                case FOUND -> {
                    statusIcon = "\u2713";
                    r = 0.3f; g = 0.8f; b = 0.3f;
                }
                case NOT_FOUND -> {
                    statusIcon = "\u2717";
                    if (ref.required()) {
                        r = 1.0f; g = 0.3f; b = 0.3f;
                    } else {
                        r = 0.8f; g = 0.6f; b = 0.2f;
                    }
                }
                case UNKNOWN -> {
                    statusIcon = "?";
                    r = 0.6f; g = 0.6f; b = 0.6f;
                }
                default -> {
                    statusIcon = "-";
                    r = 0.5f; g = 0.5f; b = 0.5f;
                }
            }

            ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, 1.0f);
            ImGui.text("  " + statusIcon + " " + label + ":");
            ImGui.popStyleColor();

            ImGui.sameLine(170);
            ImGui.textDisabled(description);

            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                switch (status) {
                    case FOUND -> ImGui.textColored(0.3f, 0.8f, 0.3f, 1f, "Reference will be resolved");
                    case NOT_FOUND -> {
                        if (ref.required()) {
                            ImGui.textColored(1f, 0.3f, 0.3f, 1f, "Required component not found!");
                            ImGui.text("Add " + ref.componentType().getSimpleName() + " to this entity");
                        } else {
                            ImGui.textColored(0.8f, 0.6f, 0.2f, 1f, "Optional component not found");
                        }
                    }
                    case UNKNOWN -> {
                        ImGui.text("Cannot verify - requires parent/children");
                        ImGui.textDisabled("Will be resolved at runtime");
                    }
                }
                ImGui.endTooltip();
            }

            ImGui.popID();
        }
    }

    private enum ReferenceStatus { FOUND, NOT_FOUND, UNKNOWN }

    private static ReferenceStatus checkReferenceStatus(ComponentRefMeta ref, EditorEntity entity) {
        if (entity == null) {
            return ReferenceStatus.UNKNOWN;
        }

        return switch (ref.source()) {
            case SELF -> {
                boolean found = hasComponentOfType(entity, ref.componentType());
                yield found ? ReferenceStatus.FOUND : ReferenceStatus.NOT_FOUND;
            }
            case PARENT, CHILDREN, CHILDREN_RECURSIVE -> ReferenceStatus.UNKNOWN;
        };
    }

    private static boolean hasComponentOfType(EditorEntity entity, Class<?> componentType) {
        String targetTypeName = componentType.getName();

        for (ComponentData comp : entity.getComponents()) {
            if (comp.getType().equals(targetTypeName)) {
                return true;
            }
        }

        if (targetTypeName.endsWith(".Transform")) {
            return true;
        }

        return false;
    }

    /**
     * Renders the asset picker popup. Call once per frame from InspectorPanel.
     */
    public static void renderAssetPicker() {
        FieldEditors.renderAssetPicker();
    }
}
