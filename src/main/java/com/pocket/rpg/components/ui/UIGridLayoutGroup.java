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
 *   <li>spacing (inherited) - gap between cells in both axes</li>
 *   <li>padding (inherited) - insets from edges</li>
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
    private Corner startCorner = Corner.UPPER_LEFT;

    @Getter @Setter
    private Axis startAxis = Axis.HORIZONTAL;

    @Getter @Setter
    private Constraint constraint = Constraint.FLEXIBLE;

    @Getter @Setter
    private int constraintCount = 2;

    @Getter @Setter
    private ChildHorizontalAlignment horizontalAlignment = ChildHorizontalAlignment.LEFT;

    @Getter @Setter
    private ChildVerticalAlignment verticalAlignment = ChildVerticalAlignment.TOP;

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
                if (startAxis == Axis.HORIZONTAL) {
                    columns = Math.max(1, (int) ((availableWidth + spacing) / (cellWidth + spacing)));
                    columns = Math.min(columns, children.size());
                    rows = (int) Math.ceil((double) children.size() / columns);
                } else {
                    rows = Math.max(1, (int) ((availableHeight + spacing) / (cellHeight + spacing)));
                    rows = Math.min(rows, children.size());
                    columns = (int) Math.ceil((double) children.size() / rows);
                }
            }
        }

        // Calculate actual cell size (expand to fill if force-expand is on)
        float actualCellWidth = cellWidth;
        float actualCellHeight = cellHeight;
        if (childForceExpandWidth && columns > 0) {
            float totalSpacing = spacing * Math.max(0, columns - 1);
            actualCellWidth = (availableWidth - totalSpacing) / columns;
        }
        if (childForceExpandHeight && rows > 0) {
            float totalSpacing = spacing * Math.max(0, rows - 1);
            actualCellHeight = (availableHeight - totalSpacing) / rows;
        }

        // Calculate grid block size and alignment offsets
        float gridWidth = columns * actualCellWidth + Math.max(0, columns - 1) * spacing;
        float gridHeight = rows * actualCellHeight + Math.max(0, rows - 1) * spacing;

        float baseX = switch (horizontalAlignment) {
            case LEFT -> paddingLeft;
            case CENTER -> paddingLeft + (availableWidth - gridWidth) / 2;
            case RIGHT -> paddingLeft + availableWidth - gridWidth;
        };
        float baseY = switch (verticalAlignment) {
            case TOP -> paddingTop;
            case MIDDLE -> paddingTop + (availableHeight - gridHeight) / 2;
            case BOTTOM -> paddingTop + availableHeight - gridHeight;
        };

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

            float x = baseX + col * (actualCellWidth + spacing);
            float y = baseY + row * (actualCellHeight + spacing);

            UITransform ct = children.get(i).getComponent(UITransform.class);
            ct.setAnchor(0, 0);
            ct.setPivot(0, 0);
            ct.setOffset(x, y);
            ct.setWidth(actualCellWidth);
            ct.setHeight(actualCellHeight);
        }
    }
}
