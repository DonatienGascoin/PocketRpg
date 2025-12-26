package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.panels.AssetPickerPopup;
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
 * All field changes are wrapped in undo commands.
 */
public class ReflectionFieldEditor {

    private static final ImString stringBuffer = new ImString(256);
    private static final ImInt intBuffer = new ImInt();
    private static final float[] floatBuffer = new float[4];

    private static final Map<Class<?>, Object[]> enumCache = new HashMap<>();

    private static final AssetPickerPopup assetPicker = new AssetPickerPopup();
    private static ComponentData assetPickerTargetData = null;
    private static String assetPickerFieldName = null;
    private static EditorEntity assetPickerTargetEntity = null;

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
        Object value = data.getFields().get(fieldName);
        String label = meta.getDisplayName();

        boolean changed = false;
        Object newValue = value;

        ImGui.pushID(fieldName);

        // PRIMITIVES
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

        // VECTORS
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

        // ENUMS
        else if (type.isEnum()) {
            Object[] constants = getEnumConstants(type);
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
            if (ImGui.combo(label, intBuffer, names)) {
                newValue = constants[intBuffer.get()].toString();
                changed = true;
            }
        }

        // ASSETS
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
                assetPickerTargetEntity = entity;
                Object oldValue = data.getFields().get(fieldName);
                assetPicker.open(type, selectedAsset -> {
                    if (assetPickerTargetData != null && assetPickerFieldName != null) {
                        UndoManager.getInstance().execute(
                                new SetComponentFieldCommand(assetPickerTargetData, assetPickerFieldName,
                                        oldValue, selectedAsset, assetPickerTargetEntity)
                        );
                    }
                });
            }
        }

        // UNKNOWN
        else {
            String display = value != null ? value.toString() : "(null)";
            ImGui.labelText(label, display);
            ImGui.sameLine();
            ImGui.textDisabled("(read-only: " + type.getSimpleName() + ")");
        }

        ImGui.popID();

        // Apply change via undo command
        if (changed && !valuesEqual(value, newValue)) {
            UndoManager.getInstance().execute(
                    new SetComponentFieldCommand(data, fieldName, value, newValue, entity)
            );
        }

        return changed;
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

    private static Object[] getEnumConstants(Class<?> enumType) {
        return enumCache.computeIfAbsent(enumType, Class::getEnumConstants);
    }

    private static Vector2f toVector2f(Object value) {
        if (value instanceof Vector2f v) return v;
        if (value instanceof List<?> list && list.size() >= 2) {
            return new Vector2f(
                    ((Number) list.get(0)).floatValue(),
                    ((Number) list.get(1)).floatValue()
            );
        }
        return new Vector2f();
    }

    private static Vector3f toVector3f(Object value) {
        if (value instanceof Vector3f v) return v;
        if (value instanceof List<?> list && list.size() >= 3) {
            return new Vector3f(
                    ((Number) list.get(0)).floatValue(),
                    ((Number) list.get(1)).floatValue(),
                    ((Number) list.get(2)).floatValue()
            );
        }
        return new Vector3f();
    }

    private static Vector4f toVector4f(Object value) {
        if (value instanceof Vector4f v) return v;
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

    private static String getAssetDisplayName(Object value, Class<?> type) {
        if (value == null) return "(none)";
        if (value instanceof Sprite sprite) {
            return sprite.getName() != null ? sprite.getName() : "(unnamed sprite)";
        }
        if (value instanceof Texture texture) {
            return texture.getFilePath() != null ? texture.getFilePath() : "(unnamed texture)";
        }
        if (value instanceof String s) {
            return s.isEmpty() ? "(none)" : s;
        }
        return value.toString();
    }

    public static void renderAssetPicker() {
        assetPicker.render();
    }
}