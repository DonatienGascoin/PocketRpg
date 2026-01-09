package com.pocket.rpg.editor.utils;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.utils.fields.*;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Facade for all field editor utilities.
 * <p>
 * Delegates to specialized editor classes:
 * <ul>
 *   <li>{@link PrimitiveEditors} - int, float, boolean, string</li>
 *   <li>{@link VectorEditors} - Vector2f, Vector3f, Vector4f, color</li>
 *   <li>{@link EnumEditor} - Enum combo boxes</li>
 *   <li>{@link AssetEditor} - Asset picker</li>
 *   <li>{@link TransformEditors} - Position/Rotation/Scale with undo</li>
 * </ul>
 * <p>
 * Override context is managed via {@link FieldEditorContext}.
 * <p>
 * Usage in custom editors:
 * <pre>
 * // Context is set by ComponentFieldEditor, just call draw methods:
 * boolean changed = FieldEditors.drawFloat("Speed", component, "speed", 0.1f);
 * changed |= FieldEditors.drawSprite("Sprite", component, "sprite", entity);
 * </pre>
 */
public final class FieldEditors {

    private FieldEditors() {}

    // ========================================================================
    // OVERRIDE CONTEXT
    // ========================================================================

    /**
     * Begins override context for prefab instance editing.
     * When active, fields show override styling and reset buttons.
     */
    public static void beginOverrideContext(EditorGameObject entity, Component component) {
        FieldEditorContext.begin(entity, component);
    }

    /**
     * Ends override context.
     */
    public static void endOverrideContext() {
        FieldEditorContext.end();
    }

    // ========================================================================
    // LAYOUT
    // ========================================================================

    /**
     * Draws a standard inspector row with label and field.
     */
    public static void inspectorRow(String label, Runnable field) {
        FieldEditorUtils.inspectorRow(label, field);
    }

    // ========================================================================
    // PRIMITIVES
    // ========================================================================

    public static boolean drawInt(String label, Component component, String fieldName) {
        return PrimitiveEditors.drawInt(label, component, fieldName);
    }

    public static boolean drawFloat(String label, Component component, String fieldName, float speed) {
        return PrimitiveEditors.drawFloat(label, component, fieldName, speed);
    }

    public static boolean drawFloat(String label, Component component, String fieldName,
                                    float speed, float min, float max) {
        return PrimitiveEditors.drawFloat(label, component, fieldName, speed, min, max);
    }

    public static boolean drawFloatSlider(String label, Component component, String fieldName,
                                          float min, float max) {
        return PrimitiveEditors.drawFloatSlider(label, component, fieldName, min, max);
    }

    public static boolean drawBoolean(String label, Component component, String fieldName) {
        return PrimitiveEditors.drawBoolean(label, component, fieldName);
    }

    public static boolean drawString(String label, Component component, String fieldName) {
        return PrimitiveEditors.drawString(label, component, fieldName);
    }

    // ========================================================================
    // VECTORS
    // ========================================================================

    public static boolean drawVector2f(String label, Component component, String fieldName) {
        return VectorEditors.drawVector2f(label, component, fieldName);
    }

    public static boolean drawVector2f(String label, Component component, String fieldName, float speed) {
        return VectorEditors.drawVector2f(label, component, fieldName, speed);
    }

    public static boolean drawVector3f(String label, Component component, String fieldName) {
        return VectorEditors.drawVector3f(label, component, fieldName);
    }

    public static boolean drawVector3f(String label, Component component, String fieldName, float speed) {
        return VectorEditors.drawVector3f(label, component, fieldName, speed);
    }

    public static boolean drawVector4f(String label, Component component, String fieldName) {
        return VectorEditors.drawVector4f(label, component, fieldName);
    }

    public static boolean drawVector4f(String label, Component component, String fieldName, float speed) {
        return VectorEditors.drawVector4f(label, component, fieldName, speed);
    }

    public static boolean drawColor(String label, Component component, String fieldName) {
        return VectorEditors.drawColor(label, component, fieldName);
    }

    // ========================================================================
    // ENUMS
    // ========================================================================

    public static boolean drawEnum(String label, Component component, String fieldName, Class<?> enumClass) {
        return EnumEditor.drawEnum(label, component, fieldName, enumClass);
    }

    // ========================================================================
    // ASSETS
    // ========================================================================

    public static boolean drawAsset(String label, Component component, String fieldName,
                                    Class<?> assetType, EditorGameObject entity) {
        return AssetEditor.drawAsset(label, component, fieldName, assetType, entity);
    }

    public static void renderAssetPicker() {
        AssetEditor.renderAssetPicker();
    }

    // ========================================================================
    // TRANSFORM (Entity-level)
    // ========================================================================

    /**
     * Draws position editor with XY fields, override styling, reset button, and undo.
     */
    public static boolean drawPosition(String label, EditorGameObject entity) {
        return TransformEditors.drawPosition(label, entity);
    }

    /**
     * Draws rotation editor with Z field, override styling, reset button, and undo.
     */
    public static boolean drawRotation(String label, EditorGameObject entity) {
        return TransformEditors.drawRotation(label, entity);
    }

    /**
     * Draws scale editor with XY fields, override styling, reset button, and undo.
     */
    public static boolean drawScale(String label, EditorGameObject entity) {
        return TransformEditors.drawScale(label, entity);
    }

    // ========================================================================
    // READ-ONLY
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
    // VECTOR GETTERS (for custom editors)
    // ========================================================================

    public static Vector2f getVector2f(Component component, String fieldName) {
        return FieldEditorUtils.getVector2f(component, fieldName);
    }

    public static Vector3f getVector3f(Component component, String fieldName) {
        return FieldEditorUtils.getVector3f(component, fieldName);
    }

    public static Vector4f getVector4f(Component component, String fieldName) {
        return FieldEditorUtils.getVector4f(component, fieldName);
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
}
