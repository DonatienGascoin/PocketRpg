package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.Tooltip;
import com.pocket.rpg.core.IGameObject;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.ui.inspectors.ComponentKeyField;
import com.pocket.rpg.editor.ui.inspectors.CustomComponentEditorRegistry;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentReferenceMeta;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;
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

    public static boolean drawComponent(Component component, HierarchyItem hierarchyEntity) {
        if (component == null) return false;

        // Extract EditorGameObject for undo/override paths (null in play mode)
        EditorGameObject editorEntity = hierarchyEntity instanceof EditorGameObject ego ? ego : null;

        ComponentMeta meta = ComponentReflectionUtils.getMeta(component);
        if (meta == null) {
            ImGui.textDisabled("Unknown component type: " + component.getClass().getSimpleName());
            return false;
        }

        // Check for custom editor first
        if (CustomComponentEditorRegistry.hasCustomEditor(component.getClass().getName())) {
            boolean changed = CustomComponentEditorRegistry.drawCustomEditor(component, hierarchyEntity);
            // Still draw component references after custom editor
            drawComponentReferences(meta.hierarchyReferences(), hierarchyEntity);
            return changed;
        }

        // Set up context for @Required and override styling
        FieldEditorContext.begin(editorEntity, component);
        try {
            boolean changed = false;

            // Draw componentKey field at top of every inspector
            changed |= ComponentKeyField.draw(component);

            for (FieldMeta fieldMeta : meta.fields()) {
                try {
                    changed |= drawField(component, fieldMeta, editorEntity);
                } catch (Exception e) {
                    EditorColors.textColored(EditorColors.DANGER, fieldMeta.name() + ": Error");
                }
            }
            drawComponentReferences(meta.hierarchyReferences(), hierarchyEntity);
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

        // Set tooltip from @Tooltip annotation if present
        Tooltip tooltipAnnotation = meta.field().getAnnotation(Tooltip.class);
        if (tooltipAnnotation != null) {
            FieldEditorUtils.setNextTooltip(tooltipAnnotation.value());
        }

        // Begin row highlight for missing required fields
        boolean requiredHighlight = FieldEditorContext.beginRequiredRowHighlight(fieldName);

        // @ComponentReference(source=KEY) fields — intercept before type dispatch
        ComponentReferenceMeta keyRef = findKeyRefForField(component, fieldName);
        if (keyRef != null) {
            fieldChanged = ComponentKeyReferenceEditor.draw(label, component, fieldName, keyRef);

            // End row highlight
            FieldEditorContext.endRequiredRowHighlight(requiredHighlight);
            return fieldChanged;
        }

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

    /**
     * Checks if a field is a @ComponentReference(source=KEY) field on this component.
     */
    private static ComponentReferenceMeta findKeyRefForField(Component component, String fieldName) {
        ComponentMeta meta = ComponentReflectionUtils.getMeta(component);
        if (meta == null || meta.componentReferences().isEmpty()) {
            return null;
        }
        for (ComponentReferenceMeta ref : meta.componentReferences()) {
            if (ref.isKeySource() && ref.fieldName().equals(fieldName)) {
                return ref;
            }
        }
        return null;
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

    public static void drawComponentReferences(List<ComponentReferenceMeta> references, HierarchyItem entity) {
        if (references == null || references.isEmpty()) return;

        for (ComponentReferenceMeta ref : references) {
            ImGui.pushID(ref.fieldName());

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
            float[] statusColor;

            switch (status) {
                case FOUND -> {
                    statusIcon = MaterialIcons.CheckCircle; // ✓
                    statusColor = EditorColors.SUCCESS;
                }
                case NOT_FOUND -> {
                    statusIcon = MaterialIcons.Error; // ✗
                    statusColor = ref.required() ? EditorColors.DANGER : EditorColors.WARNING;
                }
                case UNKNOWN -> {
                    statusIcon = MaterialIcons.Help;
                    statusColor = EditorColors.DISABLED_TEXT;
                }
                default -> {
                    statusIcon = MaterialIcons.RemoveCircle;
                    statusColor = EditorColors.DISABLED_TEXT;
                }
            }

            // Render label with status color
            EditorColors.textColored(statusColor, "  " + statusIcon + " " + label + ":");

            ImGui.sameLine(170);
            ImGui.textDisabled(description);

            // Tooltip with details
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                switch (status) {
                    case FOUND -> EditorColors.textColored(EditorColors.SUCCESS, "Reference will be resolved");
                    case NOT_FOUND -> {
                        if (ref.required()) {
                            EditorColors.textColored(EditorColors.DANGER, "Required component not found!");
                            ImGui.text("Add " + ref.componentType().getSimpleName() + " to this entity");
                        } else {
                            EditorColors.textColored(EditorColors.WARNING, "Optional component not found");
                        }
                    }
                    case UNKNOWN -> {
                        ImGui.textDisabled("Will be resolved at runtime");
                    }
                }
                ImGui.endTooltip();
            }

            ImGui.popID();
        }
    }

    private static ReferenceStatus checkReferenceStatus(ComponentReferenceMeta ref, HierarchyItem entity) {
        if (entity == null) {
            return ReferenceStatus.UNKNOWN;
        }

        return switch (ref.source()) {
            case SELF -> {
                boolean found = hasComponentOfType(entity, ref.componentType());
                yield found ? ReferenceStatus.FOUND : ReferenceStatus.NOT_FOUND;
            }
            case PARENT -> {
                HierarchyItem parent = entity.getHierarchyParent();
                if (parent == null) {
                    yield ReferenceStatus.NOT_FOUND;
                }
                boolean found = hasComponentOfType(parent, ref.componentType());
                yield found ? ReferenceStatus.FOUND : ReferenceStatus.NOT_FOUND;
            }
            case CHILDREN -> {
                boolean found = false;
                for (HierarchyItem child : entity.getHierarchyChildren()) {
                    if (hasComponentOfType(child, ref.componentType())) {
                        found = true;
                        break;
                    }
                }
                yield found ? ReferenceStatus.FOUND : ReferenceStatus.NOT_FOUND;
            }
            case CHILDREN_RECURSIVE -> {
                boolean found = hasComponentOfTypeRecursive(entity, ref.componentType());
                yield found ? ReferenceStatus.FOUND : ReferenceStatus.NOT_FOUND;
            }
            case KEY -> {
                // KEY references are displayed as dropdowns, not status indicators
                yield ReferenceStatus.UNKNOWN;
            }
        };
    }

    /**
     * Checks if an entity has a component of the given type.
     */
    private static boolean hasComponentOfType(IGameObject entity, Class<?> componentType) {
        for (Component comp : entity.getAllComponents()) {
            if (componentType.isInstance(comp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively checks children for a component of the given type.
     */
    private static boolean hasComponentOfTypeRecursive(HierarchyItem entity, Class<?> componentType) {
        for (HierarchyItem child : entity.getHierarchyChildren()) {
            if (hasComponentOfType(child, componentType)) {
                return true;
            }
            if (hasComponentOfTypeRecursive(child, componentType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reference resolution status.
     */
    private enum ReferenceStatus {
        FOUND,      // Component exists
        NOT_FOUND,  // Component missing
        UNKNOWN     // Can't determine (e.g. KEY reference)
    }

    public static void renderAssetPicker() {
        FieldEditors.renderAssetPicker();
    }
}
