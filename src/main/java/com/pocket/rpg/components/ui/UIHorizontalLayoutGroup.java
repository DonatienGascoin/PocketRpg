package com.pocket.rpg.components.ui;

import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.components.ComponentMeta;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Arranges children left-to-right in a horizontal row.
 * <p>
 * Attach to a GameObject with a UITransform. Children are positioned
 * using anchor (0,0) and offset values, preserving the dirty/recalc system.
 * <p>
 * Properties:
 * <ul>
 *   <li>spacing - gap between children in pixels</li>
 *   <li>padding - insets from edges (left, right, top, bottom)</li>
 *   <li>childAlignment - vertical alignment of children (TOP, MIDDLE, BOTTOM)</li>
 *   <li>childForceExpandWidth - distribute available width evenly among children</li>
 *   <li>childForceExpandHeight - stretch children to fill available height</li>
 * </ul>
 */
@ComponentMeta(category = "UI")
public class UIHorizontalLayoutGroup extends LayoutGroup {

    @Getter @Setter
    private ChildVerticalAlignment childAlignment = ChildVerticalAlignment.TOP;

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

        // Calculate expanded width per child if force-expanding
        float expandedWidth = 0;
        if (childForceExpandWidth) {
            float totalSpacing = spacing * Math.max(0, children.size() - 1);
            expandedWidth = (contentWidth - totalSpacing) / children.size();
        }

        float x = paddingLeft;

        for (GameObject child : children) {
            UITransform ct = child.getComponent(UITransform.class);

            float childWidth = childForceExpandWidth ? expandedWidth : ct.getEffectiveWidth();
            float childHeight = childForceExpandHeight ? contentHeight : ct.getEffectiveHeight();

            // Vertical alignment within the row
            float y = switch (childAlignment) {
                case TOP -> paddingTop;
                case MIDDLE -> paddingTop + (contentHeight - childHeight) / 2;
                case BOTTOM -> paddingTop + contentHeight - childHeight;
            };

            ct.setAnchor(0, 0);
            ct.setPivot(0, 0);
            ct.setOffset(x, y);

            if (childForceExpandWidth) {
                ct.setWidth(expandedWidth);
            }
            if (childForceExpandHeight) {
                ct.setHeight(childHeight);
            }

            x += childWidth + spacing;
        }
    }
}
