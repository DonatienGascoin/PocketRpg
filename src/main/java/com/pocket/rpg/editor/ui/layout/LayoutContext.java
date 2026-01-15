package com.pocket.rpg.editor.ui.layout;

import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks layout state for horizontal/vertical layout groups.
 * Used by EditorLayout to manage nested layout contexts.
 */
public class LayoutContext {

    public enum Direction { HORIZONTAL, VERTICAL }

    @Getter
    private final Direction direction;

    @Getter
    private final float startX;

    @Getter
    private final float availableWidth;

    @Getter
    private int widgetCount = 0;

    @Getter @Setter
    private int totalWidgets = -1;

    public LayoutContext(Direction direction, float startX, float availableWidth) {
        this.direction = direction;
        this.startX = startX;
        this.availableWidth = availableWidth;
    }

    /**
     * Gets remaining width from current cursor position.
     */
    public float getRemainingWidth() {
        return availableWidth - (ImGui.getCursorPosX() - startX);
    }

    /**
     * Registers a widget was drawn in this layout context.
     */
    public void registerWidget() {
        widgetCount++;
    }

    public boolean isHorizontal() {
        return direction == Direction.HORIZONTAL;
    }

    public boolean isVertical() {
        return direction == Direction.VERTICAL;
    }
}
