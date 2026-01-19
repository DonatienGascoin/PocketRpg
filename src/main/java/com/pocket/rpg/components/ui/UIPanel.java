package com.pocket.rpg.components.ui;

import com.pocket.rpg.rendering.ui.UIRendererBackend;
import lombok.Getter;
import org.joml.Vector4f;

/**
 * Renders a solid color rectangle in screen space.
 * Requires UICanvas ancestor and UITransform on same GameObject.
 *
 * UIPanel vs UIImage:
 * - UIImage: Requires a sprite/texture
 * - UIPanel: Solid color, no texture needed
 */
public class UIPanel extends UIComponent {

    @Getter
    private final Vector4f color = new Vector4f(0.2f, 0.2f, 0.2f, 1f);  // Dark gray default

    public UIPanel() {
    }

    public UIPanel(Vector4f color) {
        this.color.set(color);
    }

    public void setColor(float r, float g, float b, float a) {
        color.set(r, g, b, a);
    }

    public void setColor(Vector4f color) {
        this.color.set(color);
    }

    public void setAlpha(float alpha) {
        color.w = alpha;
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
    public void render(UIRendererBackend backend) {
        RenderBounds bounds = computeRenderBounds();
        if (bounds == null) return;

        backend.drawQuad(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                         bounds.rotation(), bounds.pivotX(), bounds.pivotY(), color);
    }

    @Override
    public String toString() {
        return String.format("UIPanel[color=(%.2f,%.2f,%.2f,%.2f)]",
                color.x, color.y, color.z, color.w);
    }
}