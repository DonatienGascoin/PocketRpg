package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * Manages override context state for prefab instance editing.
 * <p>
 * When context is active, field editors show override styling and reset buttons.
 * Context is set by ComponentFieldEditor before rendering component fields.
 */
public final class FieldEditorContext {

    private static EditorGameObject entity = null;
    private static Component component = null;
    private static String componentType = null;

    private static final float[] OVERRIDE_COLOR = {1.0f, 0.8f, 0.2f, 1.0f};
    private static final float[] REQUIRED_ERROR_COLOR = {0.9f, 0.2f, 0.2f, 1.0f};
    private static final float[] REQUIRED_ERROR_BG_COLOR = {0.8f, 0.1f, 0.1f, 0.5f};

    private FieldEditorContext() {}

    // ========================================================================
    // CONTEXT MANAGEMENT
    // ========================================================================

    /**
     * Begins override context for prefab instance editing.
     */
    public static void begin(EditorGameObject entity, Component component) {
        FieldEditorContext.entity = entity;
        FieldEditorContext.component = component;
        FieldEditorContext.componentType = component != null ? component.getClass().getName() : null;
    }

    /**
     * Ends override context.
     */
    public static void end() {
        entity = null;
        component = null;
        componentType = null;
    }

    /**
     * Checks if override context is active.
     */
    public static boolean isActive() {
        return entity != null && componentType != null;
    }

    /**
     * Gets the current entity in context.
     */
    public static EditorGameObject getEntity() {
        return entity;
    }

    /**
     * Gets the current component in context.
     */
    public static Component getComponent() {
        return component;
    }

    /**
     * Gets the current component type name.
     */
    public static String getComponentType() {
        return componentType;
    }

    // ========================================================================
    // OVERRIDE CHECKS
    // ========================================================================

    /**
     * Checks if a field is overridden from prefab default.
     */
    public static boolean isFieldOverridden(String fieldName) {
        return isActive() && entity.isFieldOverridden(componentType, fieldName);
    }

    /**
     * Marks a field as overridden with a value.
     */
    public static void markFieldOverridden(String fieldName, Object value) {
        if (isActive()) {
            entity.setFieldValue(componentType, fieldName, value);
        }
    }

    /**
     * Gets the default value for a field from the prefab.
     */
    public static Object getFieldDefault(String fieldName) {
        return isActive() ? entity.getFieldDefault(componentType, fieldName) : null;
    }

    /**
     * Resets a field to its prefab default value.
     * Updates both the component and the entity override tracking.
     *
     * @return The default value that was applied
     */
    public static Object resetFieldToDefault(String fieldName) {
        if (!isActive()) return null;

        Object defaultValue = entity.getFieldDefault(componentType, fieldName);
        if (component != null) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, defaultValue);
        }
        entity.resetFieldToDefault(componentType, fieldName);
        return defaultValue;
    }

    // ========================================================================
    // STYLING
    // ========================================================================

    /**
     * Pushes override text color if field is overridden.
     */
    public static void pushOverrideStyle(String fieldName) {
        if (isFieldOverridden(fieldName)) {
            ImGui.pushStyleColor(ImGuiCol.Text, OVERRIDE_COLOR[0], OVERRIDE_COLOR[1], OVERRIDE_COLOR[2], OVERRIDE_COLOR[3]);
        }
    }

    /**
     * Pops override text color if field is overridden.
     */
    public static void popOverrideStyle(String fieldName) {
        if (isFieldOverridden(fieldName)) {
            ImGui.popStyleColor();
        }
    }

    // ========================================================================
    // REQUIRED FIELD STYLING
    // ========================================================================

    /**
     * Checks if a field is @Required and has no value.
     */
    public static boolean isFieldRequiredAndMissing(String fieldName) {
        return component != null && ComponentReflectionUtils.isRequiredAndMissing(component, fieldName);
    }

    /**
     * Pushes error styling if field is required and missing.
     * Applies red text color and red frame background for high visibility.
     * Returns the number of style colors pushed (caller must pop this many).
     */
    public static int pushRequiredStyle(String fieldName) {
        if (isFieldRequiredAndMissing(fieldName)) {
            // Text label in red
            ImGui.pushStyleColor(ImGuiCol.Text, REQUIRED_ERROR_COLOR[0], REQUIRED_ERROR_COLOR[1], REQUIRED_ERROR_COLOR[2], REQUIRED_ERROR_COLOR[3]);
            // Input frame background in red
            ImGui.pushStyleColor(ImGuiCol.FrameBg, REQUIRED_ERROR_BG_COLOR[0], REQUIRED_ERROR_BG_COLOR[1], REQUIRED_ERROR_BG_COLOR[2], REQUIRED_ERROR_BG_COLOR[3]);
            // Hovered frame also red
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, REQUIRED_ERROR_BG_COLOR[0], REQUIRED_ERROR_BG_COLOR[1], REQUIRED_ERROR_BG_COLOR[2], REQUIRED_ERROR_BG_COLOR[3] + 0.1f);
            return 3;
        }
        return 0;
    }

    /**
     * Pops error styling colors.
     * @param count The return value from pushRequiredStyle
     */
    public static void popRequiredStyle(int count) {
        if (count > 0) {
            ImGui.popStyleColor(count);
        }
    }
}
