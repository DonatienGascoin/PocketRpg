package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRefMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders ImGui controls for component fields using reflection.
 * <p>
 * Supports these field types:
 * - Primitives: int, float, boolean, String
 * - Vectors: Vector2f, Vector3f, Vector4f
 * - Enums: Any enum type
 * - Assets: Sprite, Texture (with picker)
 * <p>
 * Also displays @ComponentRef fields as read-only information,
 * with validation showing if references can be resolved.
 */
public class ReflectionFieldEditor {

    // Reusable buffers to avoid allocations
    private static final ImString stringBuffer = new ImString(256);
    private static final ImInt intBuffer = new ImInt();
    private static final float[] floatBuffer = new float[4];

    // Cache for enum values
    private static final Map<Class<?>, Object[]> enumCache = new HashMap<>();

    // Asset picker
    private static final AssetPickerPopup assetPicker = new AssetPickerPopup();
    private static ComponentData assetPickerTargetData = null;
    private static String assetPickerFieldName = null;

    /**
     * Draws all editable fields of a component, plus read-only references.
     *
     * @param component The component data to edit
     * @param entity    The entity containing this component (for reference validation)
     * @return true if any field was changed
     */
    public static boolean drawComponent(ComponentData component, EditorEntity entity) {
        if (component == null) {
            return false;
        }

        ComponentMeta meta = ComponentRegistry.getByClassName(component.getType());
        if (meta == null) {
            ImGui.textDisabled("Unknown component type: " + component.getSimpleName());
            return false;
        }

        boolean changed = false;

        // Draw editable fields (in order from ComponentMeta)
        for (FieldMeta fieldMeta : meta.fields()) {
            try {
                changed |= drawField(component, fieldMeta);
            } catch (Exception e) {
                ImGui.textColored(1f, 0.3f, 0.3f, 1f,
                        fieldMeta.name() + ": Error - " + e.getMessage());
            }
        }

        // Draw read-only component references with validation
        drawComponentReferences(meta.references(), entity);

        return changed;
    }

    /**
     * Draws all editable fields without reference validation.
     * Use when entity context is not available.
     */
    public static boolean drawComponent(ComponentData component) {
        return drawComponent(component, null);
    }

    /**
     * Draws a single field with appropriate control.
     * Updates ComponentData.fields map directly.
     */
    public static boolean drawField(ComponentData data, FieldMeta meta) {
        Class<?> type = meta.type();
        String fieldName = meta.name();
        Object value = data.getFields().get(fieldName);
        String label = meta.getDisplayName();

        boolean changed = false;
        Object newValue = value;

        ImGui.pushID(fieldName);

        // ============================================================
        // PRIMITIVES
        // ============================================================

        if (type == int.class || type == Integer.class) {
            int intValue = value instanceof Number n ? n.intValue() : 0;
            intBuffer.set(intValue);
            if (ImGui.inputInt(label, intBuffer)) {
                newValue = intBuffer.get();
                changed = true;
            }
        } else if (type == float.class || type == Float.class) {
            float floatValue = value instanceof Number n ? n.floatValue() : 0f;
            floatBuffer[0] = floatValue;
            if (ImGui.dragFloat(label, floatBuffer, 0.1f)) {
                newValue = floatBuffer[0];
                changed = true;
            }
        } else if (type == double.class || type == Double.class) {
            double doubleValue = value instanceof Number n ? n.doubleValue() : 0.0;
            floatBuffer[0] = (float) doubleValue;
            if (ImGui.dragFloat(label, floatBuffer, 0.1f)) {
                newValue = (double) floatBuffer[0];
                changed = true;
            }
        } else if (type == boolean.class || type == Boolean.class) {
            boolean boolValue = value instanceof Boolean b && b;
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
            Vector2f vec = toVector2f(value);
            floatBuffer[0] = vec.x;
            floatBuffer[1] = vec.y;
            if (ImGui.dragFloat2(label, floatBuffer, 0.1f)) {
                newValue = new Vector2f(floatBuffer[0], floatBuffer[1]);
                changed = true;
            }
        } else if (type == Vector3f.class) {
            Vector3f vec = toVector3f(value);
            floatBuffer[0] = vec.x;
            floatBuffer[1] = vec.y;
            floatBuffer[2] = vec.z;
            if (ImGui.dragFloat3(label, floatBuffer, 0.1f)) {
                newValue = new Vector3f(floatBuffer[0], floatBuffer[1], floatBuffer[2]);
                changed = true;
            }
        } else if (type == Vector4f.class) {
            Vector4f vec = toVector4f(value);
            floatBuffer[0] = vec.x;
            floatBuffer[1] = vec.y;
            floatBuffer[2] = vec.z;
            floatBuffer[3] = vec.w;
            if (ImGui.colorEdit4(label, floatBuffer)) {
                newValue = new Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]);
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
                // Value might be stored as String (from JSON) or Enum
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
            if (ImGui.combo(label, intBuffer, names)) {
                // Store as enum name string for serialization compatibility
                newValue = constants[intBuffer.get()].toString();
                changed = true;
            }
        }

        // ============================================================
        // ASSETS (with picker)
        // ============================================================

        else if (type == Sprite.class || type == Texture.class) {
            String display = getAssetDisplayName(value, type);

            ImGui.text(label);
            ImGui.sameLine(130);
            ImGui.setNextItemWidth(-60);
            ImGui.inputText("##" + fieldName, new ImString(display), imgui.flag.ImGuiInputTextFlags.ReadOnly);
            ImGui.sameLine();

            if (ImGui.smallButton("...##" + fieldName)) {
                assetPickerTargetData = data;
                assetPickerFieldName = fieldName;
                assetPicker.open(type, selectedAsset -> {
                    if (assetPickerTargetData != null && assetPickerFieldName != null) {
                        assetPickerTargetData.getFields().put(assetPickerFieldName, selectedAsset);
                    }
                });
            }
        }

        // ============================================================
        // UNKNOWN TYPE
        // ============================================================

        else {
            String display = value != null ? value.toString() : "(null)";
            ImGui.labelText(label, display);
            ImGui.sameLine();
            ImGui.textDisabled("(read-only: " + type.getSimpleName() + ")");
        }

        ImGui.popID();

        // Apply change to ComponentData fields map
        if (changed) {
            data.getFields().put(fieldName, newValue);
        }

        return changed;
    }

    /**
     * Renders @ComponentRef fields as read-only labels with validation.
     * Shows in red if the reference cannot be resolved on this entity.
     */
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
                    statusIcon = FontAwesomeIcons.CheckCircle; // ✓
                    r = 0.3f; g = 0.8f; b = 0.3f; // Green
                }
                case NOT_FOUND -> {
                    statusIcon = FontAwesomeIcons.ExclamationCircle; // ✗
                    if (ref.required()) {
                        r = 1.0f; g = 0.3f; b = 0.3f; // Red
                    } else {
                        r = 0.8f; g = 0.6f; b = 0.2f; // Orange/yellow for optional
                    }
                }
                case UNKNOWN -> {
                    statusIcon = FontAwesomeIcons.QuestionCircle;
                    r = 0.6f; g = 0.6f; b = 0.6f; // Gray
                }
                default -> {
                    statusIcon = FontAwesomeIcons.MinusCircle;
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

    /**
     * Reference resolution status.
     */
    private enum ReferenceStatus {
        FOUND,      // Component exists
        NOT_FOUND,  // Component missing
        UNKNOWN     // Can't determine (parent/children reference)
    }

    /**
     * Checks if a component reference can be resolved on the given entity.
     */
    private static ReferenceStatus checkReferenceStatus(ComponentRefMeta ref, EditorEntity entity) {
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
    private static boolean hasComponentOfType(EditorEntity entity, Class<?> componentType) {
        String targetTypeName = componentType.getName();

        // Check scratch entity components
        for (ComponentData comp : entity.getComponents()) {
            if (comp.getType().equals(targetTypeName)) {
                return true;
            }
        }

        // Check if it's Transform (always present)
        if (targetTypeName.endsWith(".Transform")) {
            return true;
        }

        // TODO: For prefab instances, check prefab's components too
        // if (entity.isPrefabInstance()) { ... }

        return false;
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Gets cached enum constants for a type.
     */
    private static Object[] getEnumConstants(Class<?> enumType) {
        return enumCache.computeIfAbsent(enumType, Class::getEnumConstants);
    }

    /**
     * Converts stored value to Vector2f.
     * Handles both Vector2f and List (from JSON deserialization).
     */
    private static Vector2f toVector2f(Object value) {
        if (value instanceof Vector2f v) {
            return v;
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            return new Vector2f(
                    ((Number) list.get(0)).floatValue(),
                    ((Number) list.get(1)).floatValue()
            );
        }
        return new Vector2f();
    }

    /**
     * Converts stored value to Vector3f.
     */
    private static Vector3f toVector3f(Object value) {
        if (value instanceof Vector3f v) {
            return v;
        }
        if (value instanceof List<?> list && list.size() >= 3) {
            return new Vector3f(
                    ((Number) list.get(0)).floatValue(),
                    ((Number) list.get(1)).floatValue(),
                    ((Number) list.get(2)).floatValue()
            );
        }
        return new Vector3f();
    }

    /**
     * Converts stored value to Vector4f.
     */
    private static Vector4f toVector4f(Object value) {
        if (value instanceof Vector4f v) {
            return v;
        }
        if (value instanceof List<?> list && list.size() >= 4) {
            return new Vector4f(
                    ((Number) list.get(0)).floatValue(),
                    ((Number) list.get(1)).floatValue(),
                    ((Number) list.get(2)).floatValue(),
                    ((Number) list.get(3)).floatValue()
            );
        }
        return new Vector4f();
    }

    /**
     * Gets display name for an asset field value.
     */
    private static String getAssetDisplayName(Object value, Class<?> type) {
        if (value == null) {
            return "(none)";
        }
        if (value instanceof Sprite sprite) {
            return sprite.getName() != null ? sprite.getName() : "(unnamed sprite)";
        }
        if (value instanceof Texture texture) {
            return texture.getFilePath() != null ? texture.getFilePath() : "(unnamed texture)";
        }
        // Might be stored as string path
        if (value instanceof String s) {
            return s.isEmpty() ? "(none)" : s;
        }
        return value.toString();
    }

    /**
     * Renders any open asset picker popup.
     * Call once per frame from a central location (e.g., InspectorPanel).
     */
    public static void renderAssetPicker() {
        assetPicker.render();
    }
}