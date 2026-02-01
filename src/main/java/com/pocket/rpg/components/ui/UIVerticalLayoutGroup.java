package com.pocket.rpg.components.ui;

import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.components.ComponentMeta;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Arranges children top-to-bottom in a vertical column.
 * <p>
 * Attach to a GameObject with a UITransform. Children are positioned
 * using anchor (0,0) and offset values, preserving the dirty/recalc system.
 * <p>
 * Properties:
 * <ul>
 *   <li>spacing - gap between children in pixels</li>
 *   <li>padding - insets from edges (left, right, top, bottom)</li>
 *   <li>childAlignment - horizontal alignment of children (LEFT, CENTER, RIGHT)</li>
 *   <li>childForceExpandWidth - stretch children to fill available width</li>
 *   <li>childForceExpandHeight - distribute available height evenly among children</li>
 * </ul>
 */
@ComponentMeta(category = "UI")
public class UIVerticalLayoutGroup extends LayoutGroup {

    @Getter @Setter
    private ChildHorizontalAlignment childAlignment = ChildHorizontalAlignment.LEFT;

    @Override
    public void applyLayout() {
        UITransform ownTransform = getOwnTransform();
        if (ownTransform == null) return;

        List<GameObject> children = getLayoutChildren();
        if (children.isEmpty()) return;

        float availableWidth = ownTransform.getEffectiveWidth();
        float availableHeight = ownTransform.getEffectiveHeight();
        float contentWidth = availableWidth - paddingLeft - paddingRight;
        float contentHeight = availableHeight - paddingTop - paddingBottom;

        // Calculate expanded height per child if force-expanding
        float expandedHeight = 0;
        if (childForceExpandHeight) {
            float totalSpacing = spacing * Math.max(0, children.size() - 1);
            expandedHeight = (contentHeight - totalSpacing) / children.size();
        }

        float y = paddingTop;

        for (GameObject child : children) {
            UITransform ct = child.getComponent(UITransform.class);

            float childWidth = childForceExpandWidth ? contentWidth : ct.getEffectiveWidth();
            float childHeight = childForceExpandHeight ? expandedHeight : ct.getEffectiveHeight();

            // Horizontal alignment within the column
            float x = switch (childAlignment) {
                case LEFT -> paddingLeft;
                case CENTER -> paddingLeft + (contentWidth - childWidth) / 2;
                case RIGHT -> paddingLeft + contentWidth - childWidth;
            };

            ct.setAnchor(0, 0);
            ct.setOffset(x, y);

            if (childForceExpandWidth) {
                ct.setWidth(childWidth);
            }
            if (childForceExpandHeight) {
                ct.setHeight(expandedHeight);
            }

            y += childHeight + spacing;
        }
    }
}
