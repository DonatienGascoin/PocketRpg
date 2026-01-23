package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.ui.inspectors.CustomComponentEditorRegistry;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRefMeta;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
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

    public static boolean drawComponent(Component component, EditorGameObject entity) {
        if (component == null) return false;

        ComponentMeta meta = ComponentReflectionUtils.getMeta(component);
        if (meta == null) {
            ImGui.textDisabled("Unknown component type: " + component.getClass().getSimpleName());
            return false;
        }

        // Check for custom editor first
        if (CustomComponentEditorRegistry.hasCustomEditor(component.getClass().getName())) {
            return CustomComponentEditorRegistry.drawCustomEditor(component, entity);
        }

        // Set up context for @Required and override styling
        FieldEditorContext.begin(entity, component);
        try {
            boolean changed = false;
            for (FieldMeta fieldMeta : meta.fields()) {
                try {
                    changed |= drawField(component, fieldMeta, entity);
                } catch (Exception e) {
                    ImGui.textColored(1f, 0.3f, 0.3f, 1f, fieldMeta.name() + ": Error");
                }
            }
            drawComponentReferences(meta.references(), entity);
            return changed;
        } finally {
            FieldEditorContext.end();
        }
    }

    /**
     * DRAW FIELD: Without Undo (e.g., Prefab Overrides)
     */
    public static boolean drawField(Component component, FieldMeta meta) {
        return drawFieldInternal(component, meta, null);
    }

    /**
     * DRAW FIELD: With Undo logic
     */
    public static boolean drawField(Component component, FieldMeta meta, EditorGameObject entity) {
        String editKey = component.getClass().getName() + "." + meta.name() + "@" + System.identityHashCode(component);
        ImGui.pushID(editKey);
        boolean changed = drawFieldInternal(component, meta, entity);
        ImGui.popID();
        return changed;
    }

    private static boolean drawFieldInternal(Component component, FieldMeta meta, EditorGameObject entity) {
        if (component == null) {
            return false;
        }

        Class<?> type = meta.type();
        String fieldName = meta.name();
        String label = meta.getDisplayName();
        String stateKey = component.getClass().getName() + "." + fieldName + "@" + System.identityHashCode(component);

        boolean wasActive = ImGui.isAnyItemActive();
        boolean fieldChanged = false;

        // Begin row highlight for missing required fields
        boolean requiredHighlight = FieldEditorContext.beginRequiredRowHighlight(fieldName);

        // PRIMITIVES
        if (type == int.class || type == Integer.class) {
            fieldChanged = FieldEditors.drawInt(label, component, fieldName);
        } else if (type == float.class || type == Float.class) {
            fieldChanged = FieldEditors.drawFloat(label, component, fieldName, 0.1f);
        } else if (type == double.class || type == Double.class) {
            float floatValue = ComponentReflectionUtils.getFloat(component, fieldName, 0f);
            float[] buf = {floatValue};
            if (ImGui.dragFloat(label, buf, 0.1f)) {
                ComponentReflectionUtils.setFieldValue(component, fieldName, (double) buf[0]);
                fieldChanged = true;
            }
        } else if (type == boolean.class || type == Boolean.class) {
            fieldChanged = FieldEditors.drawBoolean(label, component, fieldName);
        } else if (type == String.class) {
            fieldChanged = FieldEditors.drawString(label, component, fieldName);
        }

        // VECTORS
        else if (type == Vector2f.class) {
            fieldChanged = FieldEditors.drawVector2f(label, component, fieldName);
        } else if (type == Vector3f.class) {
            fieldChanged = FieldEditors.drawVector3f(label, component, fieldName);
        } else if (type == Vector4f.class) {
            fieldChanged = FieldEditors.drawColor(label, component, fieldName);
        }

        // ENUMS
        else if (type.isEnum()) {
            fieldChanged = FieldEditors.drawEnum(label, component, fieldName, type);
        }

        // AUDIOCLIP (special handling with play/stop button)
        else if (type == AudioClip.class) {
            fieldChanged = FieldEditors.drawAudioClip(label, component, fieldName, entity);
        }

        // ASSETS
        else if (Assets.isAssetType(type)) {
            fieldChanged = FieldEditors.drawAsset(label, component, fieldName, type, entity);
        }

        // LISTS
        else if (meta.isList()) {
            fieldChanged = ListEditor.drawList(label, component, meta, entity);
        }

        // UNKNOWN
        else {
            FieldEditors.drawReadOnly(label, component, fieldName, type.getSimpleName());
        }

        // End row highlight
        FieldEditorContext.endRequiredRowHighlight(requiredHighlight);

        // UNDO LOGIC
        if (entity != null) {
            if (ImGui.isItemActive() && !wasActive) {
                editingOriginalValues.put(stateKey, cloneValue(ComponentReflectionUtils.getFieldValue(component, fieldName)));
            }
            if (ImGui.isItemDeactivatedAfterEdit() && editingOriginalValues.containsKey(stateKey)) {
                Object originalValue = editingOriginalValues.remove(stateKey);
                Object currentValue = ComponentReflectionUtils.getFieldValue(component, fieldName);
                if (!valuesEqual(originalValue, currentValue)) {
                    UndoManager.getInstance().execute(new SetComponentFieldCommand(component, fieldName, originalValue, currentValue, FieldEditorContext.getEntity()));
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

    public static void drawComponentReferences(List<ComponentRefMeta> references, EditorGameObject entity) {
        if (references == null || references.isEmpty()) return;

        for (ComponentRefMeta ref : references) {
            ImGui.pushID(ref.name());

            String label = ref.getDisplayName();
            String description = ref.getEditorDescription();

            // Check if reference can be resolved
            ReferenceStatus status = checkReferenceStatus(ref, entity);

            // Optional indicator
            if (!ref.required()) {
                description += " [optional]";
            }

            // Status indicator
            String statusIcon;
            float r, g, b;

            switch (status) {
                case FOUND -> {
                    statusIcon = MaterialIcons.CheckCircle; // ✓
                    r = 0.3f; g = 0.8f; b = 0.3f; // Green
                }
                case NOT_FOUND -> {
                    statusIcon = MaterialIcons.Error; // ✗
                    if (ref.required()) {
                        r = 1.0f; g = 0.3f; b = 0.3f; // Red
                    } else {
                        r = 0.8f; g = 0.6f; b = 0.2f; // Orange/yellow for optional
                    }
                }
                case UNKNOWN -> {
                    statusIcon = MaterialIcons.Help;
                    r = 0.6f; g = 0.6f; b = 0.6f; // Gray
                }
                default -> {
                    statusIcon = MaterialIcons.RemoveCircle;
                    r = 0.5f; g = 0.5f; b = 0.5f;
                }
            }

            // Render label with status color
            ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, 1.0f);
            ImGui.text("  " + statusIcon + " " + label + ":");
            ImGui.popStyleColor();

            ImGui.sameLine(170);
            ImGui.textDisabled(description);

            // Tooltip with details
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

    private static ReferenceStatus checkReferenceStatus(ComponentRefMeta ref, EditorGameObject entity) {
        if (entity == null) {
            return ReferenceStatus.UNKNOWN;
        }

        return switch (ref.source()) {
            case SELF -> {
                // Check if entity has a component of the required type
                boolean found = hasComponentOfType(entity, ref.componentType());
                yield found ? ReferenceStatus.FOUND : ReferenceStatus.NOT_FOUND;
            }
            case PARENT, CHILDREN, CHILDREN_RECURSIVE -> {
                // Can't verify parent/children in editor without scene hierarchy
                yield ReferenceStatus.UNKNOWN;
            }
        };
    }

    /**
     * Checks if an entity has a component of the given type.
     */
    private static boolean hasComponentOfType(EditorGameObject entity, Class<?> componentType) {
        String targetTypeName = componentType.getName();

        // Check scratch entity components
        for (Component comp : entity.getComponents()) {
            if (componentType.isInstance(comp)) {
                return true;
            }
        }

        // Check if it's Transform (always present)
        if (targetTypeName.equals("com.pocket.rpg.components.Transform")) {
            return true;
        }

        return false;
    }

    /**
     * Reference resolution status.
     */
    private enum ReferenceStatus {
        FOUND,      // Component exists
        NOT_FOUND,  // Component missing
        UNKNOWN     // Can't determine (parent/children reference)
    }

    public static void renderAssetPicker() {
        FieldEditors.renderAssetPicker();
    }
}
