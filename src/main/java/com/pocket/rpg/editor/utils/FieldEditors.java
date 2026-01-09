package com.pocket.rpg.editor.utils;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
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
 * Reusable field drawing utilities for component editors.
 * <p>
 * All methods work directly with Component instances using reflection,
 * making them usable by both ReflectionFieldEditor and custom editors.
 * <p>
 * Supports prefab override tracking via beginOverrideContext/endOverrideContext.
 */
public class FieldEditors {

    // Shared buffers
    private static final ImString stringBuffer = new ImString(256);
    private static final ImInt intBuffer = new ImInt();
    private static final float[] floatBuffer = new float[4];

    private static final Map<Class<?>, Object[]> enumCache = new HashMap<>();

    // Asset picker state
    private static final AssetPickerPopup assetPicker = new AssetPickerPopup();
    private static Component assetPickerTargetComponent = null;
    private static String assetPickerFieldName = null;
    private static EditorGameObject assetPickerTargetEntity = null;

    private static final float LABEL_WIDTH = 120f;
    private static final float RESET_BUTTON_WIDTH = 25f;

    // ========================================================================
    // OVERRIDE CONTEXT
    // ========================================================================

    private static EditorGameObject overrideEntity = null;
    private static String overrideComponentType = null;

    /**
     * Begins override context for prefab instance editing.
     * When active, fields show override styling and reset buttons.
     */
    public static void beginOverrideContext(EditorGameObject entity, Component component) {
        overrideEntity = entity;
        overrideComponentType = component.getClass().getName();
    }

    /**
     * Ends override context.
     */
    public static void endOverrideContext() {
        overrideEntity = null;
        overrideComponentType = null;
    }

    private static boolean isOverrideContextActive() {
        return overrideEntity != null && overrideComponentType != null;
    }

    private static boolean isFieldOverridden(String fieldName) {
        return isOverrideContextActive() && overrideEntity.isFieldOverridden(overrideComponentType, fieldName);
    }

    private static void markFieldOverridden(String fieldName, Object value) {
        if (isOverrideContextActive()) {
            overrideEntity.setFieldValue(overrideComponentType, fieldName, value);
        }
    }

    /**
     * Draws reset button if field is overridden. Returns true if reset was clicked.
     */
    private static boolean drawResetButtonIfNeeded(Component component, String fieldName) {
        if (!isOverrideContextActive()) return false;
        if (!isFieldOverridden(fieldName)) return false;

        ImGui.sameLine();
        if (ImGui.smallButton(FontAwesomeIcons.Undo + "##reset_" + fieldName)) {
            Object defaultValue = overrideEntity.getFieldDefault(overrideComponentType, fieldName);
            ComponentReflectionUtils.setFieldValue(component, fieldName, defaultValue);
            overrideEntity.resetFieldToDefault(overrideComponentType, fieldName);
            return true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reset to prefab default");
        }
        return false;
    }

    private static void pushOverrideStyle(String fieldName) {
        if (isFieldOverridden(fieldName)) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.8f, 1.0f, 1.0f);
        }
    }

    private static void popOverrideStyle(String fieldName) {
        if (isFieldOverridden(fieldName)) {
            ImGui.popStyleColor();
        }
    }

    // ========================================================================
    // LAYOUT
    // ========================================================================

    public static void inspectorRow(String label, Runnable field) {
        float textWidth = ImGui.calcTextSize(label).x;

        ImGui.text(label);

        if (textWidth > LABEL_WIDTH) {
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(label);
            }
        }

        ImGui.sameLine(LABEL_WIDTH);

        // Reserve space for reset button when override context active
        float width = isOverrideContextActive() ? -RESET_BUTTON_WIDTH : -1;
        ImGui.setNextItemWidth(width);

        field.run();
    }

    // ========================================================================
    // PRIMITIVES
    // ========================================================================

    public static boolean drawInt(String label, Component component, String fieldName) {
        int intValue = ComponentReflectionUtils.getInt(component, fieldName, 0);
        intBuffer.set(intValue);

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.inputInt("##" + fieldName, intBuffer);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, intBuffer.get());
            markFieldOverridden(fieldName, intBuffer.get());
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawFloat(String label, Component component, String fieldName, float speed) {
        floatBuffer[0] = ComponentReflectionUtils.getFloat(component, fieldName, 0f);

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat("##" + fieldName, floatBuffer, speed);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, floatBuffer[0]);
            markFieldOverridden(fieldName, floatBuffer[0]);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawFloat(String label, Component component, String fieldName,
                                    float speed, float min, float max) {
        floatBuffer[0] = ComponentReflectionUtils.getFloat(component, fieldName, 0f);

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat("##" + fieldName, floatBuffer, speed, min, max);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, floatBuffer[0]);
            markFieldOverridden(fieldName, floatBuffer[0]);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawFloatSlider(String label, Component component, String fieldName,
                                          float min, float max) {
        floatBuffer[0] = ComponentReflectionUtils.getFloat(component, fieldName, 0f);

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.sliderFloat("##" + fieldName, floatBuffer, min, max);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, floatBuffer[0]);
            markFieldOverridden(fieldName, floatBuffer[0]);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawBoolean(String label, Component component, String fieldName) {
        boolean boolValue = ComponentReflectionUtils.getBoolean(component, fieldName, false);

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.checkbox("##" + fieldName, boolValue);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            boolean newValue = !boolValue;
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            markFieldOverridden(fieldName, newValue);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawString(String label, Component component, String fieldName) {
        String strValue = ComponentReflectionUtils.getString(component, fieldName, "");
        stringBuffer.set(strValue);

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.inputText("##" + fieldName, stringBuffer);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            String newValue = stringBuffer.get();
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            markFieldOverridden(fieldName, newValue);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    // ========================================================================
    // VECTORS
    // ========================================================================

    public static boolean drawVector2f(String label, Component component, String fieldName) {
        return drawVector2f(label, component, fieldName, 0.1f);
    }

    public static boolean drawVector2f(String label, Component component, String fieldName, float speed) {
        Vector2f vec = getVector2f(component, fieldName);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat2("##" + fieldName, floatBuffer, speed);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            Vector2f newValue = new Vector2f(floatBuffer[0], floatBuffer[1]);
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            markFieldOverridden(fieldName, newValue);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawVector3f(String label, Component component, String fieldName) {
        return drawVector3f(label, component, fieldName, 0.1f);
    }

    public static boolean drawVector3f(String label, Component component, String fieldName, float speed) {
        Vector3f vec = getVector3f(component, fieldName);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;
        floatBuffer[2] = vec.z;

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat3("##" + fieldName, floatBuffer, speed);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            Vector3f newValue = new Vector3f(floatBuffer[0], floatBuffer[1], floatBuffer[2]);
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            markFieldOverridden(fieldName, newValue);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawColor(String label, Component component, String fieldName) {
        Vector4f vec = getVector4f(component, fieldName);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;
        floatBuffer[2] = vec.z;
        floatBuffer[3] = vec.w;

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.colorEdit4("##" + fieldName, floatBuffer);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            Vector4f newValue = new Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]);
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            markFieldOverridden(fieldName, newValue);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    public static boolean drawVector4f(String label, Component component, String fieldName) {
        Vector4f vec = getVector4f(component, fieldName);
        floatBuffer[0] = vec.x;
        floatBuffer[1] = vec.y;
        floatBuffer[2] = vec.z;
        floatBuffer[3] = vec.w;

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.dragFloat4("##" + fieldName, floatBuffer, 0.1f);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            Vector4f newValue = new Vector4f(floatBuffer[0], floatBuffer[1], floatBuffer[2], floatBuffer[3]);
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            markFieldOverridden(fieldName, newValue);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    // ========================================================================
    // ENUMS
    // ========================================================================

    public static boolean drawEnum(String label, Component component, String fieldName, Class<?> enumClass) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
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

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        final boolean[] changed = {false};
        inspectorRow(label, () -> {
            changed[0] = ImGui.combo("##" + fieldName, intBuffer, names);
        });

        popOverrideStyle(fieldName);

        if (changed[0]) {
            Object newValue = constants[intBuffer.get()];
            ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
            markFieldOverridden(fieldName, newValue);
        }

        boolean reset = drawResetButtonIfNeeded(component, fieldName);
        ImGui.popID();

        return changed[0] || reset;
    }

    // ========================================================================
    // ASSETS
    // ========================================================================

    public static boolean drawAsset(String label, Component component, String fieldName,
                                    Class<?> assetType, EditorGameObject entity) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
        String display = getAssetDisplayName(value);

        ImGui.pushID(fieldName);
        pushOverrideStyle(fieldName);

        try {
            inspectorRow(label, () -> {
                if (value != null) {
                    ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, display);
                } else {
                    ImGui.textDisabled(display);
                }

                ImGui.sameLine();
                if (ImGui.smallButton("...")) {
                    assetPickerTargetComponent = component;
                    assetPickerFieldName = fieldName;
                    assetPickerTargetEntity = entity;
                    Object oldValue = ComponentReflectionUtils.getFieldValue(component, fieldName);
                    String currentPath = oldValue != null ? Assets.getPathForResource(oldValue) : null;

                    assetPicker.open(assetType, currentPath, selectedAsset -> {
                        UndoManager.getInstance().execute(
                                new SetComponentFieldCommand(
                                        assetPickerTargetComponent,
                                        assetPickerFieldName,
                                        oldValue,
                                        selectedAsset
                                )
                        );
                        markFieldOverridden(assetPickerFieldName, selectedAsset);
                    });
                }
            });

            popOverrideStyle(fieldName);
            drawResetButtonIfNeeded(component, fieldName);

        } finally {
            ImGui.popID();
        }
        return false;
    }

    public static void renderAssetPicker() {
        assetPicker.render();
    }

    // ========================================================================
    // READ-ONLY DISPLAY
    // ========================================================================

    public static void drawReadOnly(String label, Component component, String fieldName, String typeName) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
        String display = value != null ? value.toString() : "(null)";

        ImGui.pushID(fieldName);

        inspectorRow(label, () -> {
            ImGui.textDisabled(display);
            ImGui.sameLine();
            ImGui.textDisabled("(read-only: " + typeName + ")");
        });

        ImGui.popID();
    }

    // ========================================================================
    // VECTOR GETTERS (unchanged)
    // ========================================================================

    public static Vector2f getVector2f(Component component, String fieldName) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
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

    public static Vector3f getVector3f(Component component, String fieldName) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
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

    public static Vector4f getVector4f(Component component, String fieldName) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
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

    public static float getFloat(Component component, String fieldName, float defaultValue) {
        return ComponentReflectionUtils.getFloat(component, fieldName, defaultValue);
    }

    public static int getInt(Component component, String fieldName, int defaultValue) {
        return ComponentReflectionUtils.getInt(component, fieldName, defaultValue);
    }

    public static boolean getBoolean(Component component, String fieldName, boolean defaultValue) {
        return ComponentReflectionUtils.getBoolean(component, fieldName, defaultValue);
    }

    public static String getString(Component component, String fieldName, String defaultValue) {
        return ComponentReflectionUtils.getString(component, fieldName, defaultValue);
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

    private static String getAssetDisplayName(Object value) {
        if (value == null) return "(none)";
        String path = Assets.getPathForResource(value);
        if (path != null) {
            return getFileName(path);
        }
        return "(unnamed)";
    }

    private static String getFileName(String path) {
        if (path == null || path.isEmpty()) return "(none)";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}