package com.pocket.rpg.components.ui;

import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.components.ComponentMeta;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Arranges children in a grid with fixed cell sizes.
 * <p>
 * All children are resized to the cell size. The grid fills from
 * the start corner along the start axis.
 * <p>
 * Properties:
 * <ul>
 *   <li>cellWidth, cellHeight - size of each grid cell in pixels</li>
 *   <li>spacingX, spacingY - gap between cells</li>
 *   <li>startCorner - which corner the grid fills from</li>
 *   <li>startAxis - whether to fill horizontally or vertically first</li>
 *   <li>constraint - how columns/rows are determined</li>
 *   <li>constraintCount - number of columns/rows when using fixed constraint</li>
 * </ul>
 */
@ComponentMeta(category = "UI")
public class UIGridLayoutGroup extends LayoutGroup {

    // ========================================================================
    // ENUMS
    // ========================================================================

    public enum Corner {
        UPPER_LEFT, UPPER_RIGHT, LOWER_LEFT, LOWER_RIGHT
    }

    public enum Axis {
        HORIZONTAL, VERTICAL
    }

    public enum Constraint {
        /** Columns determined automatically from available width */
        FLEXIBLE,
        /** Fixed number of columns */
        FIXED_COLUMN_COUNT,
        /** Fixed number of rows */
        FIXED_ROW_COUNT
    }

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    @Getter @Setter
    private float cellWidth = 64;

    @Getter @Setter
    private float cellHeight = 64;

    @Getter @Setter
    private float spacingX = 4;

    @Getter @Setter
    private float spacingY = 4;

    @Getter @Setter
    private Corner startCorner = Corner.UPPER_LEFT;

    @Getter @Setter
    private Axis startAxis = Axis.HORIZONTAL;

    @Getter @Setter
    private Constraint constraint = Constraint.FLEXIBLE;

    @Getter @Setter
    private int constraintCount = 2;

    // ========================================================================
    // LAYOUT
    // ========================================================================

    @Override
    public void applyLayout() {
        UITransform ownTransform = getOwnTransform();
        if (ownTransform == null) return;

        List<GameObject> children = getLayoutChildren();
        if (children.isEmpty()) return;

        float availableWidth = ownTransform.getEffectiveWidth() - paddingLeft - paddingRight;
        float availableHeight = ownTransform.getEffectiveHeight() - paddingTop - paddingBottom;

        // Calculate columns and rows
        int columns, rows;
        switch (constraint) {
            case FIXED_COLUMN_COUNT -> {
                columns = Math.max(1, constraintCount);
                rows = (int) Math.ceil((double) children.size() / columns);
            }
            case FIXED_ROW_COUNT -> {
                rows = Math.max(1, constraintCount);
                columns = (int) Math.ceil((double) children.size() / rows);
            }
            default -> { // FLEXIBLE
                columns = Math.max(1, (int) ((availableWidth + spacingX) / (cellWidth + spacingX)));
                rows = (int) Math.ceil((double) children.size() / columns);
            }
        }

        for (int i = 0; i < children.size(); i++) {
            int col, row;
            if (startAxis == Axis.HORIZONTAL) {
                col = i % columns;
                row = i / columns;
            } else {
                row = i % rows;
                col = i / rows;
            }

            // Flip for right/bottom start corners
            if (startCorner == Corner.UPPER_RIGHT || startCorner == Corner.LOWER_RIGHT) {
                col = columns - 1 - col;
            }
            if (startCorner == Corner.LOWER_LEFT || startCorner == Corner.LOWER_RIGHT) {
                row = rows - 1 - row;
            }

            float x = paddingLeft + col * (cellWidth + spacingX);
            float y = paddingTop + row * (cellHeight + spacingY);

            UITransform ct = children.get(i).getComponent(UITransform.class);
            ct.setAnchor(0, 0);
            ct.setOffset(x, y);
            ct.setWidth(cellWidth);
            ct.setHeight(cellHeight);
        }
    }
}
