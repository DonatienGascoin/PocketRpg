package com.pocket.rpg.editor.ui.layout;

import imgui.ImGui;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Static layout management for editor UI.
 * Provides Unity-style horizontal/vertical layout groups.
 *
 * <h2>Usage</h2>
 * <pre>
 * // 2 widgets on one line
 * EditorLayout.beginHorizontal(2);
 * EditorFields.floatField("X", "key.x", getter, setter, 1f);
 * EditorFields.floatField("Y", "key.y", getter, setter, 1f);
 * EditorLayout.endHorizontal();
 * </pre>
 */
public class EditorLayout {

    private static final Deque<LayoutContext> layoutStack = new ArrayDeque<>();
    private static final float LABEL_PADDING = 4f;
    private static final float WIDGET_SPACING = 8f;
    private static final float RIGHT_MARGIN = 8f;  // Match ImGui's default frame padding

    /**
     * Begins a horizontal group with automatic width distribution.
     * Use when widget count is unknown - fields share remaining space.
     */
    public static void beginHorizontal() {
        float availableWidth = ImGui.getContentRegionAvailX();
        float startX = ImGui.getCursorPosX();
        layoutStack.push(new LayoutContext(LayoutContext.Direction.HORIZONTAL, startX, availableWidth));
    }

    /**
     * Begins horizontal with known widget count for equal width distribution.
     *
     * @param widgetCount Number of widgets (input fields, not labels).
     *                    Example: For "X [____] Y [____]" widgetCount = 2
     */
    public static void beginHorizontal(int widgetCount) {
        float availableWidth = ImGui.getContentRegionAvailX();
        float startX = ImGui.getCursorPosX();
        LayoutContext ctx = new LayoutContext(LayoutContext.Direction.HORIZONTAL, startX, availableWidth);
        ctx.setTotalWidgets(widgetCount);
        layoutStack.push(ctx);
    }

    /**
     * Ends the current horizontal group.
     */
    public static void endHorizontal() {
        if (!layoutStack.isEmpty()) {
            layoutStack.pop();
        }
    }

    /**
     * Checks if currently in a horizontal layout context.
     */
    public static boolean isHorizontal() {
        LayoutContext ctx = layoutStack.peek();
        return ctx != null && ctx.isHorizontal();
    }

    /**
     * Calculates widget width based on current context.
     *
     * @param labelWidth Width of label preceding this widget
     * @return Width for the widget, or -1 for full width (vertical mode)
     */
    public static float calculateWidgetWidth(float labelWidth) {
        LayoutContext ctx = layoutStack.peek();
        if (ctx == null || ctx.isVertical()) {
            return -1;
        }

        if (ctx.getTotalWidgets() > 0) {
            // Known widget count: equal distribution
            float estimatedLabelWidth = labelWidth * ctx.getTotalWidgets();
            float spacing = WIDGET_SPACING * (ctx.getTotalWidgets() - 1);
            float labelPadding = LABEL_PADDING * ctx.getTotalWidgets();
            return (ctx.getAvailableWidth() - estimatedLabelWidth - spacing - labelPadding - RIGHT_MARGIN) / ctx.getTotalWidgets();
        } else {
            // Unknown: use half of remaining space
            return (ctx.getRemainingWidth() - labelWidth - LABEL_PADDING - WIDGET_SPACING - RIGHT_MARGIN) / 2;
        }
    }

    /**
     * Called before each widget for positioning.
     * In horizontal mode, adds sameLine() after the first widget.
     */
    public static void beforeWidget() {
        LayoutContext ctx = layoutStack.peek();
        if (ctx != null && ctx.isHorizontal() && ctx.getWidgetCount() > 0) {
            ImGui.sameLine();
        }
        if (ctx != null) {
            ctx.registerWidget();
        }
    }
}
