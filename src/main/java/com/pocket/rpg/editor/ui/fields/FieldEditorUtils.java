package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;

/**
 * Shared utility methods for field editors.
 */
public final class FieldEditorUtils {

    public static final float LABEL_WIDTH = 120f;
    public static final float RESET_BUTTON_WIDTH = 25f;

    // ========================================================================
    // NEXT-FIELD OVERRIDES (consumed after one inspectorRow call)
    // ========================================================================

    private static float nextFieldWidth = -1;           // -1 means "use default"
    private static float nextLabelWidth = -1;           // -1 means "use LABEL_WIDTH"
    private static Runnable nextMiddleContent = null;   // null means "no middle content"
    private static String nextTooltip = null;           // null means "no custom tooltip"

    private static final float[] ACCENT_COLOR = {0.9f, 0.2f, 0.2f, 1.0f};
    private static final float[] ACCENT_HOVER = {1.0f, 0.3f, 0.3f, 1.0f};
    private static final float[] ACCENT_ACTIVE = {0.8f, 0.1f, 0.1f, 1.0f};

    private FieldEditorUtils() {}

    // ========================================================================
    // BUTTONS
    // ========================================================================

    /**
     * Draws a small button with accent (red) styling when active.
     * Handles push/pop symmetry internally.
     *
     * @param active Whether to apply accent styling
     * @param label  Button label (include ##id for uniqueness)
     * @return true if the button was clicked
     */
    public static boolean accentButton(boolean active, String label) {
        if (active) pushAccentColors();
        boolean clicked = ImGui.smallButton(label);
        if (active) ImGui.popStyleColor(3);
        return clicked;
    }

    /**
     * Draws a full-width button with accent (red) styling when active.
     * Uses {@link ImGui#button(String, float, float)} instead of smallButton.
     *
     * @param active Whether to apply accent styling
     * @param label  Button label
     * @param width  Button width (0 for auto)
     * @return true if the button was clicked
     */
    public static boolean accentButton(boolean active, String label, float width) {
        if (active) pushAccentColors();
        boolean clicked = ImGui.button(label, width, 0);
        if (active) ImGui.popStyleColor(3);
        return clicked;
    }

    private static void pushAccentColors() {
        ImGui.pushStyleColor(ImGuiCol.Button, ACCENT_COLOR[0], ACCENT_COLOR[1], ACCENT_COLOR[2], ACCENT_COLOR[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT_HOVER[0], ACCENT_HOVER[1], ACCENT_HOVER[2], ACCENT_HOVER[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ACCENT_ACTIVE[0], ACCENT_ACTIVE[1], ACCENT_ACTIVE[2], ACCENT_ACTIVE[3]);
    }

    // ========================================================================
    // LAYOUT
    // ========================================================================

    /**
     * Sets the width for the next field only.
     * Consumed after the next inspectorRow() call.
     */
    public static void setNextFieldWidth(float width) {
        nextFieldWidth = width;
    }

    /**
     * Sets the label width for the next inspectorRow() call only.
     * Consumed after the next inspectorRow() call.
     */
    public static void setNextLabelWidth(float width) {
        nextLabelWidth = width;
    }

    /**
     * Sets content to draw between label and field for the next inspectorRow() call.
     * Use for buttons/icons that appear after the label but before the field.
     * Consumed after the next inspectorRow() call.
     */
    public static void setNextMiddleContent(Runnable content) {
        nextMiddleContent = content;
    }

    /**
     * Sets a tooltip for the next field label.
     * Shown when the user hovers over the label, replacing the default truncation tooltip.
     * Consumed after the next inspectorRow() call.
     */
    public static void setNextTooltip(String tooltip) {
        nextTooltip = tooltip;
    }

    private static float consumeNextFieldWidth() {
        float width = nextFieldWidth;
        nextFieldWidth = -1;
        return width;
    }

    private static float consumeNextLabelWidth() {
        float width = nextLabelWidth;
        nextLabelWidth = -1;
        return width;
    }

    private static Runnable consumeNextMiddleContent() {
        Runnable content = nextMiddleContent;
        nextMiddleContent = null;
        return content;
    }

    private static String consumeNextTooltip() {
        String tooltip = nextTooltip;
        nextTooltip = null;
        return tooltip;
    }

    /**
     * Draws a compact inline field with label immediately followed by field.
     * Uses RELATIVE positioning (sameLine without offset) - suitable for side-by-side layouts.
     * Does NOT respect setNextFieldWidth - width must be set before calling this.
     *
     * @param label Short label (e.g., "X", "Y")
     * @param field The field widget to draw
     */
    public static void inlineField(String label, Runnable field) {
        ImGui.text(label);
        ImGui.sameLine();  // Relative positioning - stays after label
        field.run();
    }

    /**
     * Draws a compact inline field with label, explicit field width, then field widget.
     * Sets {@code ImGui.setNextItemWidth()} AFTER the label text to avoid
     * NextItemData being consumed by {@code text()}.
     *
     * @param label      Short label (e.g., "X", "Y")
     * @param fieldWidth Width for the input widget
     * @param field      The field widget to draw
     */
    public static void inlineField(String label, float fieldWidth, Runnable field) {
        ImGui.text(label);
        ImGui.sameLine();
        ImGui.setNextItemWidth(fieldWidth);
        field.run();
    }

    /**
     * Draws a standard inspector row with label and field.
     * Checks for width and middle content overrides set via setNextFieldWidth/setNextMiddleContent.
     * Reserves space for reset button when override context is active.
     */
    public static void inspectorRow(String label, Runnable field) {
        String tooltip = consumeNextTooltip();
        float labelWidth = consumeNextLabelWidth();
        if (labelWidth <= 0) labelWidth = LABEL_WIDTH;

        if (!label.startsWith("##")) {
            var currentPos = ImGui.getCursorPosX();
            float textWidth = ImGui.calcTextSize(label).x;
            boolean truncated = textWidth > labelWidth;

            // Clip the label text so it doesn't bleed into the field area
            if (truncated) {
                ImVec2 cursorScreen = ImGui.getCursorScreenPos();
                float lineHeight = ImGui.getTextLineHeight();
                ImGui.pushClipRect(cursorScreen.x, cursorScreen.y,
                        cursorScreen.x + labelWidth, cursorScreen.y + lineHeight, true);
                ImGui.text(label);
                ImGui.popClipRect();
            } else {
                ImGui.text(label);
            }

            if (ImGui.isItemHovered()) {
                if (tooltip != null && truncated) {
                    ImGui.setTooltip(label + "\n\n" + tooltip);
                } else if (tooltip != null) {
                    ImGui.setTooltip(tooltip);
                } else if (truncated) {
                    ImGui.setTooltip(label);
                }
            }

            ImGui.sameLine(currentPos + labelWidth);
        }

        // Draw middle content if set (buttons between label and field)
        Runnable middleContent = consumeNextMiddleContent();
        if (middleContent != null) {
            middleContent.run();
            ImGui.sameLine();
        }

        // Check for width override
        float overrideWidth = consumeNextFieldWidth();
        if (overrideWidth > 0) {
            ImGui.setNextItemWidth(overrideWidth);
        } else {
            float width = FieldEditorContext.isActive() ? -RESET_BUTTON_WIDTH : -1;
            ImGui.setNextItemWidth(width);
        }

        field.run();
    }

    // ========================================================================
    // RESET BUTTON
    // ========================================================================

    /**
     * Draws reset button if field is overridden.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @return true if reset was clicked
     */
    public static boolean drawResetButtonIfNeeded(Component component, String fieldName) {
        if (!FieldEditorContext.isActive()) return false;
        if (!FieldEditorContext.isFieldOverridden(fieldName)) return false;

        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.Undo + "##reset_" + fieldName)) {
            FieldEditorContext.resetFieldToDefault(fieldName);
            return true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reset to prefab default");
        }
        return false;
    }

    // ========================================================================
    // VECTOR GETTERS
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

    private static float getFloatFromMap(Map<?, ?> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }

    // ========================================================================
    // ASSET DISPLAY
    // ========================================================================

    public static String getAssetDisplayName(Object value) {
        if (value == null) return "(none)";
        String path = Assets.getPathForResource(value);
        if (path != null) {
            return getFileName(path);
        }
        return "(unnamed)";
    }

    public static String getFileName(String path) {
        if (path == null || path.isEmpty()) return "(none)";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
