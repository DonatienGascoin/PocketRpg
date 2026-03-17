package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.DrivableBy;

/**
 * Renders a solid color rectangle in screen space.
 * Requires UICanvas ancestor and UITransform on same GameObject.
 *
 * UIPanel vs UIImage:
 * - UIImage: Requires a sprite/texture
 * - UIPanel: Solid color, no texture needed
 */
@DrivableBy(UIButton.class)
public class UIPanel extends UIVisual {

    public UIPanel() {
        super(0.2f, 0.2f, 0.2f, 1f);  // Dark gray default
    }

    public UIPanel(org.joml.Vector4f color) {
        super(color.x, color.y, color.z, color.w);
    }

    /**
     * Convenience method to set size on UITransform.
     */
    public void setSize(float width, float height) {
        UITransform t = getUITransform();
        if (t != null) {
            t.setSize(width, height);
        }
    }

    @Override
    public String toString() {
        var c = getColor();
        return String.format("UIPanel[color=(%.2f,%.2f,%.2f,%.2f)]",
                c.x, c.y, c.z, c.w);
    }
}
