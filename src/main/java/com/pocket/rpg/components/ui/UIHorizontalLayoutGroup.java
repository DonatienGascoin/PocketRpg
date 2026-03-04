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
        float totalSpacing = spacing * Math.max(0, children.size() - 1);

        // Calculate expanded width per child if force-expanding
        float expandedWidth = 0;
        if (childForceExpandWidth) {
            expandedWidth = (contentWidth - totalSpacing) / children.size();
        }

        float x = paddingLeft;

        for (GameObject child : children) {
            UITransform ct = child.getComponent(UITransform.class);
            ct.clearLayoutOverrides();

            // Layout axis (width): resolve percentage against content area minus spacing
            float childWidth;
            if (childForceExpandWidth) {
                childWidth = expandedWidth;
            } else if (ct.getWidthMode() == UITransform.SizeMode.PERCENT) {
                childWidth = (contentWidth - totalSpacing) * ct.getWidthPercent() / 100f;
            } else {
                childWidth = ct.getEffectiveWidth();
            }

            // Cross axis (height): resolve percentage against content height
            float childHeight;
            if (childForceExpandHeight) {
                childHeight = contentHeight;
            } else if (ct.getHeightMode() == UITransform.SizeMode.PERCENT) {
                childHeight = contentHeight * ct.getHeightPercent() / 100f;
            } else {
                childHeight = ct.getEffectiveHeight();
            }

            // Vertical alignment within the row
            float y = switch (childAlignment) {
                case TOP -> paddingTop;
                case MIDDLE -> paddingTop + (contentHeight - childHeight) / 2;
                case BOTTOM -> paddingTop + contentHeight - childHeight;
            };

            ct.setAnchor(0, 0);
            ct.setPivot(0, 0);
            ct.setOffset(x, y);

            // Store the percentage reference bases so the editor can round-trip correctly
            // Horizontal layout: width is layout axis (content - spacing), height is cross axis (content)
            ct.setLayoutPercentReference(contentWidth - totalSpacing, contentHeight);

            // Set layout overrides for percentage or force-expanded children
            // so getEffectiveWidth/Height returns layout-computed values
            if (childForceExpandWidth || ct.getWidthMode() == UITransform.SizeMode.PERCENT) {
                ct.setLayoutOverrideWidth(childWidth);
            }
            if (childForceExpandHeight || ct.getHeightMode() == UITransform.SizeMode.PERCENT) {
                ct.setLayoutOverrideHeight(childHeight);
            }

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
