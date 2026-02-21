package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Renders all discovered fields for an arbitrary object using the existing
 * getter/setter field editor overloads with reflection-based get/set.
 * <p>
 * Since UndoManager is redirected to the panel's stacks during rendering,
 * the field editors' built-in undo tracking pushes to the right place automatically.
 */
public final class ReflectionAssetEditor {

    private ReflectionAssetEditor() {}

    /**
     * Draws all editable fields for the given target object.
     *
     * @param target   The object being edited
     * @param fields   Fields discovered by AssetFieldCollector
     * @param idPrefix Unique prefix for ImGui IDs (prevents ID collisions)
     * @return true if any field was modified
     */
    public static boolean drawObject(Object target, List<FieldMeta> fields, String idPrefix) {
        boolean anyChanged = false;

        for (FieldMeta meta : fields) {
            boolean changed = drawField(target, meta, idPrefix);
            anyChanged |= changed;
        }

        return anyChanged;
    }

    @SuppressWarnings("unchecked")
    private static boolean drawField(Object target, FieldMeta meta, String idPrefix) {
        Class<?> type = meta.type();
        String label = meta.getDisplayName();
        String key = idPrefix + "." + meta.name();
        Field field = meta.field();

        // int / Integer
        if (type == int.class || type == Integer.class) {
            return PrimitiveEditors.drawInt(label, key,
                    () -> getInt(field, target),
                    val -> setField(field, target, val));
        }

        // float / Float
        if (type == float.class || type == Float.class) {
            return PrimitiveEditors.drawFloat(label, key,
                    () -> getFloat(field, target),
                    val -> setField(field, target, (float) val),
                    0.1f);
        }

        // double / Double
        if (type == double.class || type == Double.class) {
            return PrimitiveEditors.drawFloat(label, key,
                    () -> getDouble(field, target),
                    val -> setField(field, target, val),
                    0.1f);
        }

        // boolean / Boolean
        if (type == boolean.class || type == Boolean.class) {
            return PrimitiveEditors.drawBoolean(label, key,
                    () -> getBool(field, target),
                    val -> setField(field, target, val));
        }

        // String
        if (type == String.class) {
            return PrimitiveEditors.drawString(label, key,
                    () -> getString(field, target),
                    val -> setField(field, target, val));
        }

        // Enums
        if (type.isEnum()) {
            Class<? extends Enum> enumClass = (Class<? extends Enum>) type;
            return drawEnumField(label, key, field, target, enumClass);
        }

        // Lists with known element types
        if (meta.isList()) {
            return drawListField(label, key, field, target, meta, idPrefix);
        }

        // Unknown types - read-only display
        drawReadOnly(label, field, target);
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> boolean drawEnumField(String label, String key, Field field,
                                                              Object target, Class<?> rawEnumClass) {
        Class<E> enumClass = (Class<E>) rawEnumClass;
        return EnumEditor.drawEnum(label, key,
                () -> (E) getFieldValue(field, target),
                val -> setField(field, target, val),
                enumClass);
    }

    @SuppressWarnings("unchecked")
    private static boolean drawListField(String label, String key, Field field,
                                          Object target, FieldMeta meta, String idPrefix) {
        List<Object> list = (List<Object>) getFieldValue(field, target);
        if (list == null) {
            FieldEditorUtils.inspectorRow(label, () -> ImGui.textDisabled("(null)"));
            return false;
        }

        Class<?> elementType = meta.elementType();
        boolean anyChanged = false;

        if (ImGui.treeNode(key, label + " [" + list.size() + "]")) {
            // Primitive/String/Enum list elements
            if (isSimpleType(elementType)) {
                for (int i = 0; i < list.size(); i++) {
                    String elemKey = key + "[" + i + "]";
                    final int idx = i;

                    if (elementType == String.class) {
                        anyChanged |= PrimitiveEditors.drawString("[" + i + "]", elemKey,
                                () -> (String) list.get(idx),
                                val -> list.set(idx, val));
                    } else if (elementType == int.class || elementType == Integer.class) {
                        anyChanged |= PrimitiveEditors.drawInt("[" + i + "]", elemKey,
                                () -> (Integer) list.get(idx),
                                val -> list.set(idx, val));
                    } else if (elementType == float.class || elementType == Float.class) {
                        anyChanged |= PrimitiveEditors.drawFloat("[" + i + "]", elemKey,
                                () -> ((Number) list.get(idx)).doubleValue(),
                                val -> list.set(idx, (float) val),
                                0.1f);
                    } else if (elementType == boolean.class || elementType == Boolean.class) {
                        anyChanged |= PrimitiveEditors.drawBoolean("[" + i + "]", elemKey,
                                () -> (Boolean) list.get(idx),
                                val -> list.set(idx, val));
                    } else if (elementType.isEnum()) {
                        anyChanged |= drawListEnumElement("[" + i + "]", elemKey, list, idx, elementType);
                    }
                }
            } else {
                // Object list elements - render recursively
                for (int i = 0; i < list.size(); i++) {
                    Object elem = list.get(i);
                    if (elem == null) {
                        ImGui.textDisabled("[" + i + "] (null)");
                        continue;
                    }
                    String elemKey = idPrefix + "." + meta.name() + "[" + i + "]";
                    if (ImGui.treeNode(elemKey, "[" + i + "] " + elem.getClass().getSimpleName())) {
                        List<FieldMeta> elemFields = AssetFieldCollector.getFields(elem.getClass());
                        anyChanged |= drawObject(elem, elemFields, elemKey);
                        ImGui.treePop();
                    }
                }
            }

            ImGui.treePop();
        }

        return anyChanged;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> boolean drawListEnumElement(String label, String key,
                                                                    List<Object> list, int idx,
                                                                    Class<?> rawEnumClass) {
        Class<E> enumClass = (Class<E>) rawEnumClass;
        return EnumEditor.drawEnum(label, key,
                () -> (E) list.get(idx),
                val -> list.set(idx, val),
                enumClass);
    }

    private static void drawReadOnly(String label, Field field, Object target) {
        Object value = getFieldValue(field, target);
        String raw = value != null ? value.toString() : "(null)";
        String display = raw.length() > 60 ? raw.substring(0, 57) + "..." : raw;
        FieldEditorUtils.inspectorRow(label, () -> ImGui.textDisabled(display));
    }

    private static boolean isSimpleType(Class<?> type) {
        return type == String.class
                || type == int.class || type == Integer.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == boolean.class || type == Boolean.class
                || type.isEnum();
    }

    // ========================================================================
    // REFLECTION HELPERS
    // ========================================================================

    private static int getInt(Field field, Object target) {
        try {
            return field.getInt(target);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    private static float getFloat(Field field, Object target) {
        try {
            return field.getFloat(target);
        } catch (IllegalAccessException e) {
            return 0f;
        }
    }

    private static double getDouble(Field field, Object target) {
        try {
            return field.getDouble(target);
        } catch (IllegalAccessException e) {
            return 0.0;
        }
    }

    private static boolean getBool(Field field, Object target) {
        try {
            return field.getBoolean(target);
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static String getString(Field field, Object target) {
        try {
            Object val = field.get(target);
            return val != null ? (String) val : "";
        } catch (IllegalAccessException e) {
            return "";
        }
    }

    private static Object getFieldValue(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static void setField(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            System.err.println("[ReflectionAssetEditor] Failed to set field " + field.getName() + ": " + e.getMessage());
        }
    }
}
