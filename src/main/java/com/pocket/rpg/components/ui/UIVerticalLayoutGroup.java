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
        float totalSpacing = spacing * Math.max(0, children.size() - 1);

        // Calculate expanded height per child if force-expanding
        float expandedHeight = 0;
        if (childForceExpandHeight) {
            expandedHeight = (contentHeight - totalSpacing) / children.size();
        }

        float y = paddingTop;

        for (GameObject child : children) {
            UITransform ct = child.getComponent(UITransform.class);
            ct.clearLayoutOverrides();

            // Layout axis (height): enforced size > forceExpand > percent > fixed
            float childHeight;
            if (childHeightMode == ChildSizeMode.FIXED) {
                childHeight = this.childHeight;
            } else if (childHeightMode == ChildSizeMode.PERCENT) {
                childHeight = (contentHeight - totalSpacing) * this.childHeight / 100f;
            } else if (childForceExpandHeight) {
                childHeight = expandedHeight;
            } else if (ct.getHeightMode() == UITransform.SizeMode.PERCENT) {
                childHeight = (contentHeight - totalSpacing) * ct.getHeightPercent() / 100f;
            } else {
                childHeight = ct.getEffectiveHeight();
            }

            // Cross axis (width): enforced size > forceExpand > percent > fixed
            float childWidth;
            if (childWidthMode == ChildSizeMode.FIXED) {
                childWidth = this.childWidth;
            } else if (childWidthMode == ChildSizeMode.PERCENT) {
                childWidth = contentWidth * this.childWidth / 100f;
            } else if (childForceExpandWidth) {
                childWidth = contentWidth;
            } else if (ct.getWidthMode() == UITransform.SizeMode.PERCENT) {
                childWidth = contentWidth * ct.getWidthPercent() / 100f;
            } else {
                childWidth = ct.getEffectiveWidth();
            }

            // Horizontal alignment within the column
            float x = switch (childAlignment) {
                case LEFT -> paddingLeft;
                case CENTER -> paddingLeft + (contentWidth - childWidth) / 2;
                case RIGHT -> paddingLeft + contentWidth - childWidth;
            };

            ct.setAnchor(0, 0);
            ct.setPivot(0, 0);
            ct.setOffset(x, y);

            // Store the percentage reference bases so the editor can round-trip correctly
            // Vertical layout: height is layout axis (content - spacing), width is cross axis (content)
            ct.setLayoutPercentReference(contentWidth, contentHeight - totalSpacing);

            // Set layout overrides so getEffectiveWidth/Height returns layout-computed values
            if (childWidthMode != ChildSizeMode.NONE || childForceExpandWidth || ct.getWidthMode() == UITransform.SizeMode.PERCENT) {
                ct.setLayoutOverrideWidth(childWidth);
            }
            if (childHeightMode != ChildSizeMode.NONE || childForceExpandHeight || ct.getHeightMode() == UITransform.SizeMode.PERCENT) {
                ct.setLayoutOverrideHeight(childHeight);
            }

            // Sync raw fields for graceful fallback when enforcement is removed
            if (childWidthMode != ChildSizeMode.NONE || childForceExpandWidth) {
                ct.setWidth(childWidth);
            }
            if (childHeightMode != ChildSizeMode.NONE || childForceExpandHeight) {
                ct.setHeight(childHeight);
            }

            y += childHeight + spacing;
        }

        // Total extent: last child bottom + bottom padding (remove trailing spacing)
        contentExtentHeight = y - spacing + paddingBottom;
    }
}
