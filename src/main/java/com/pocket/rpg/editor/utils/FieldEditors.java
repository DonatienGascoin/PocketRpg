package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.serialization.ComponentData;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable field drawing utilities for component editors.
 * <p>
 * All methods work directly with field maps and field names,
 * making them usable by both ReflectionFieldEditor and custom editors.
 * <p>
 * Changes are wrapped in undo commands when ComponentData and entity are provided.
 */
public class FieldEditors {

    // Shared buffers
    private static final ImString stringBuffer = new ImString(256);
    private static final ImInt intBuffer = new ImInt();
    private static final float[] floatBuffer = new float[4];

    private static final Map<Class<?>, Object[]> enumCache = new HashMap<>();

    // Asset picker state
    private static final AssetPickerPopup assetPicker = new AssetPickerPopup();
    private static ComponentData assetPickerTargetData = null;
    private static String assetPickerFieldName = null;
    private static EditorEntity assetPickerTargetEntity = null;

    private static final float LABEL_WIDTH = 120f;

    public static void inspectorRow(String label, Runnable field) {
        // Measure label width
        float textWidth = ImGui.calcTextSize(label).x;

        ImGui.text(label);

        // Show tooltip if label exceeds column width
        if (textWidth > LABEL_WIDTH) {
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(label);
            }
        }

        ImGui.sameLine(LABEL_WIDTH);
        ImGui.setNextItemWidth(-1);
        field.run();
    }


    // ========================================================================
    // PRIMITIVES
    // ========================================================================

    /**
     * Draws an int input field.
     */
    public static boolean drawInt(String label, Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        int intValue = value instanceof Number n ? n.intValue() : 0;
        intBuffer.set(intValue);

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.inputInt("##" + key, intBuffer);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, intBuffer.get());
        }
        return changed[0];
    }


    /**
     * Draws a float drag field.
     */
    public static boolean drawFloat(String label, Map<String, Object> fields, String key, float speed) {
        Object value = fields.get(key);
        floatBuffer[0] = value instanceof Number n ? n.floatValue() : 0f;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat("##" + key, floatBuffer, speed);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, floatBuffer[0]);
        }
        return changed[0];
    }


    /**
     * Draws a float drag field with min/max constraints.
     */
    public static boolean drawFloat(String label, Map<String, Object> fields, String key,
                                    float speed, float min, float max) {
        Object value = fields.get(key);
        floatBuffer[0] = value instanceof Number n ? n.floatValue() : 0f;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat("##" + key, floatBuffer, speed, min, max);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, floatBuffer[0]);
        }
        return changed[0];
    }


    /**
     * Draws a float slider field with min/max constraints.
     */
    public static boolean drawFloatSlider(String label, Map<String, Object> fields, String key,
                                    float min, float max) {
        Object value = fields.get(key);
        floatBuffer[0] = value instanceof Number n ? n.floatValue() : 0f;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.sliderFloat("##" + key, floatBuffer, min, max);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, floatBuffer[0]);
        }
        return changed[0];
    }

    /**
     * Draws a boolean checkbox.
     */
    public static boolean drawBoolean(String label, Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        boolean boolValue = value instanceof Boolean b && b;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.checkbox("##" + key, boolValue);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, floatBuffer[0]);
        }
        return changed[0];
    }

    /**
     * Draws a string input field.
     */
    public static boolean drawString(String label, Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        String strValue = value != null ? value.toString() : "";
        stringBuffer.set(strValue);

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.inputText("##" + key, stringBuffer);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, floatBuffer[0]);
        }
        return changed[0];
    }

    // ========================================================================
    // VECTORS
    // ========================================================================

    /**
     * Draws a Vector2f drag field.
     */
    public static boolean drawVector2f(String label, Map<String, Object> fields, String key) {
        return drawVector2f(label, fields, key, 0.1f);
    }

    /**
     * Draws a Vector2f drag field with custom speed.
     */
    public static boolean drawVector2f(String label, Map<String, Object> fields, String key, float speed) {
        Vector2f vec = getVector2f(fields, key);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat2("##" + key, floatBuffer, speed);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, new Vector2f(floatBuffer[0], floatBuffer[1]));
        }
        return changed[0];
    }

    /**
     * Draws a Vector3f drag field.
     */
    public static boolean drawVector3f(String label, Map<String, Object> fields, String key) {
        return drawVector3f(label, fields, key, 0.1f);
    }

    /**
     * Draws a Vector3f drag field with custom speed.
     */
    public static boolean drawVector3f(String label, Map<String, Object> fields, String key, float speed) {
        Vector3f vec = getVector3f(fields, key);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;
        floatBuffer[2] = vec.z;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat3("##" + key, floatBuffer, speed);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, new Vector3f(floatBuffer[0], floatBuffer[1], floatBuffer[2]));
        }
        return changed[0];
    }

    /**
     * Draws a Vector4f as a color picker.
     */
    public static boolean drawColor(String label, Map<String, Object> fields, String key) {
        Vector4f vec = getVector4f(fields, key);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;
        floatBuffer[2] = vec.z;
        floatBuffer[3] = vec.w;

        // Pushing key here is good for local scope, but the Caller (drawField)
        // must ensure the parent scope is unique per Object.
        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.colorEdit4("##" + key, floatBuffer);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, new Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]));
        }
        return changed[0];
    }

    /**
     * Draws a Vector4f as drag fields (not color).
     */
    public static boolean drawVector4f(String label, Map<String, Object> fields, String key) {
        Vector4f vec = getVector4f(fields, key);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;
        floatBuffer[2] = vec.z;
        floatBuffer[3] = vec.w;

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat4("##" + key, floatBuffer, 0.1f);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, new Vector2f(floatBuffer[0], floatBuffer[1]));
        }
        return changed[0];
    }

    // ========================================================================
    // ENUMS
    // ========================================================================

    /**
     * Draws an enum combo box.
     */
    public static boolean drawEnum(String label, Map<String, Object> fields, String key, Class<?> enumClass) {
        Object value = fields.get(key);
        Object[] constants = getEnumConstants(enumClass);

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

        ImGui.pushID(key);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.combo("##" + key, intBuffer, names);
        });

        ImGui.popID();

        if (changed[0]) {
            fields.put(key, constants[intBuffer.get()].toString());
        }
        return changed[0];
    }


    // ========================================================================
    // ASSETS
    // ========================================================================

    /**
     * Draws an asset picker field.
     */
    public static boolean drawAsset(String label, Map<String, Object> fields, String key,
                                    Class<?> assetType, ComponentData data, EditorEntity entity) {
        Object value = fields.get(key);
        String display = getAssetDisplayName(value, assetType);

        ImGui.pushID(key);

        inspectorRow(label, () -> {

            if (value != null) {
                ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, display);
            } else {
                ImGui.textDisabled(display);
            }

            ImGui.sameLine();
            if (ImGui.smallButton("...")) {
                assetPickerTargetData = data;
                assetPickerFieldName = key;
                assetPickerTargetEntity = entity;
                Object oldValue = fields.get(key);

                String currentPath = null;
                if (oldValue instanceof Sprite sprite && sprite.getTexture() != null) {
                    currentPath = sprite.getTexture().getFilePath();
                } else if (oldValue instanceof Texture texture) {
                    currentPath = texture.getFilePath();
                }

                assetPicker.open(assetType, currentPath, selectedAsset -> {
                    UndoManager.getInstance().execute(
                            new SetComponentFieldCommand(
                                    assetPickerTargetData,
                                    assetPickerFieldName,
                                    oldValue,
                                    selectedAsset
                            )
                    );
                });
            }
        });

        ImGui.popID();
        return false;
    }


    /**
     * Renders the asset picker popup. Call once per frame from InspectorPanel.
     */
    public static void renderAssetPicker() {
        assetPicker.render();
    }

    // ========================================================================
    // READ-ONLY DISPLAY
    // ========================================================================

    /**
     * Displays a read-only label.
     */
    public static void drawReadOnly(String label, Map<String, Object> fields, String key, String typeName) {
        Object value = fields.get(key);
        String display = value != null ? value.toString() : "(null)";

        ImGui.pushID(key);

        inspectorRow(label, () -> {
            ImGui.textDisabled(display);
            ImGui.sameLine();
            ImGui.textDisabled("(read-only: " + typeName + ")");
        });

        ImGui.popID();
    }


    // ========================================================================
    // VECTOR GETTERS (handle multiple storage formats)
    // ========================================================================

    public static Vector2f getVector2f(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value instanceof Vector2f v) return new Vector2f(v);
        if (value instanceof Map<?, ?> m) {
            float x = getFloatFromMap(m, "x", 0f);
            float y = getFloatFromMap(m, "y", 0f);
            return new Vector2f(x, y);
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            float x = ((Number) list.get(0)).floatValue();
            float y = ((Number) list.get(1)).floatValue();
            return new Vector2f(x, y);
        }
        return new Vector2f();
    }

    public static Vector3f getVector3f(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value instanceof Vector3f v) return new Vector3f(v);
        if (value instanceof Map<?, ?> m) {
            float x = getFloatFromMap(m, "x", 0f);
            float y = getFloatFromMap(m, "y", 0f);
            float z = getFloatFromMap(m, "z", 0f);
            return new Vector3f(x, y, z);
        }
        if (value instanceof List<?> list && list.size() >= 3) {
            float x = ((Number) list.get(0)).floatValue();
            float y = ((Number) list.get(1)).floatValue();
            float z = ((Number) list.get(2)).floatValue();
            return new Vector3f(x, y, z);
        }
        return new Vector3f();
    }

    public static Vector4f getVector4f(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value instanceof Vector4f v) return new Vector4f(v);
        if (value instanceof Map<?, ?> m) {
            float x = getFloatFromMap(m, "x", 0f);
            float y = getFloatFromMap(m, "y", 0f);
            float z = getFloatFromMap(m, "z", 0f);
            float w = getFloatFromMap(m, "w", 1f);
            return new Vector4f(x, y, z, w);
        }
        if (value instanceof List<?> list && list.size() >= 4) {
            float x = ((Number) list.get(0)).floatValue();
            float y = ((Number) list.get(1)).floatValue();
            float z = ((Number) list.get(2)).floatValue();
            float w = ((Number) list.get(3)).floatValue();
            return new Vector4f(x, y, z, w);
        }
        return new Vector4f(0, 0, 0, 1);
    }

    public static float getFloat(Map<String, Object> fields, String key, float defaultValue) {
        Object value = fields.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }

    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================

    private static float getFloatFromMap(Map<?, ?> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }

    private static Object[] getEnumConstants(Class<?> enumType) {
        return enumCache.computeIfAbsent(enumType, Class::getEnumConstants);
    }

    private static String getAssetDisplayName(Object value, Class<?> type) {
        if (value == null) return "(none)";
        if (value instanceof Sprite sprite) {
            Texture tex = sprite.getTexture();
            if (tex != null && tex.getFilePath() != null) {
                return getFileName(tex.getFilePath());
            }
            return sprite.getName() != null ? sprite.getName() : "(unnamed sprite)";
        }
        if (value instanceof Texture texture) {
            if (texture.getFilePath() != null) {
                return getFileName(texture.getFilePath());
            }
            return "(unnamed texture)";
        }
        if (value instanceof String s) {
            return s.isEmpty() ? "(none)" : getFileName(s);
        }
        return value.toString();
    }

    private static String getFileName(String path) {
        if (path == null || path.isEmpty()) return "(none)";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
