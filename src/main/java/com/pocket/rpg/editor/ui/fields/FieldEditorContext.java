package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
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
    private static EditorScene currentScene = null;

    private static final int REQUIRED_ROW_BG_COLOR = ImGui.colorConvertFloat4ToU32(1f, 0.1f, 0.1f, 0.7f);

    // State for row highlighting
    private static boolean requiredRowHighlightActive = false;
    private static float requiredRowStartY = 0;

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
     * Begins override context with scene for prefab instance editing.
     */
    public static void begin(EditorGameObject entity, Component component, EditorScene scene) {
        FieldEditorContext.entity = entity;
        FieldEditorContext.component = component;
        FieldEditorContext.componentType = component != null ? component.getClass().getName() : null;
        FieldEditorContext.currentScene = scene;
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
     * Sets the current editor scene.
     * Call this when the scene changes.
     */
    public static void setCurrentScene(EditorScene scene) {
        currentScene = scene;
    }

    /**
     * Gets the current editor scene.
     */
    public static EditorScene getCurrentScene() {
        return currentScene;
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

    private static boolean overrideStylePushed = false;

    /**
     * Pushes override text color if field is overridden.
     * Must be paired with {@link #popOverrideStyle()}.
     */
    public static void pushOverrideStyle(String fieldName) {
        overrideStylePushed = isFieldOverridden(fieldName);
        if (overrideStylePushed) {
            ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.OVERRIDE[0], EditorColors.OVERRIDE[1], EditorColors.OVERRIDE[2], EditorColors.OVERRIDE[3]);
        }
    }

    /**
     * Pops override text color if it was pushed.
     * Uses stored state from {@link #pushOverrideStyle(String)} to guarantee push/pop symmetry.
     */
    public static void popOverrideStyle() {
        if (overrideStylePushed) {
            ImGui.popStyleColor();
            overrideStylePushed = false;
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
     * Begins row highlight for a required field if it's missing.
     * Must be paired with endRequiredRowHighlight().
     * Uses draw list channels to render background behind content.
     *
     * @return true if highlighting is active (caller should call endRequiredRowHighlight)
     */
    public static boolean beginRequiredRowHighlight(String fieldName) {
        if (isFieldRequiredAndMissing(fieldName)) {
            requiredRowHighlightActive = true;

            // Save the starting Y position
            ImVec2 cursorPos = ImGui.getCursorScreenPos();
            requiredRowStartY = cursorPos.y;

            // Split draw list: channel 0 = background, channel 1 = foreground
            ImGui.getWindowDrawList().channelsSplit(2);
            ImGui.getWindowDrawList().channelsSetCurrent(1); // Draw content on foreground

            return true;
        }
        return false;
    }

    /**
     * Ends row highlight and draws the background rectangle.
     * Only call if beginRequiredRowHighlight returned true.
     */
    public static void endRequiredRowHighlight(boolean wasActive) {
        if (wasActive && requiredRowHighlightActive) {
            ImDrawList drawList = ImGui.getWindowDrawList();

            // Switch to background channel
            drawList.channelsSetCurrent(0);

            // Calculate row bounds
            float padding = 2f;
            float startX = ImGui.getWindowPos().x + ImGui.getWindowContentRegionMin().x - padding;
            float endX = ImGui.getWindowPos().x + ImGui.getWindowContentRegionMax().x + padding;
            float startY = requiredRowStartY - padding;
            float endY = ImGui.getCursorScreenPos().y /*+ padding*/;

            // Draw background rectangle
            drawList.addRectFilled(startX, startY, endX, endY, REQUIRED_ROW_BG_COLOR);

            // Merge channels back together
            drawList.channelsMerge();

            requiredRowHighlightActive = false;
        }
    }
}
