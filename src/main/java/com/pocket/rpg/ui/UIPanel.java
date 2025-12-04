package com.pocket.rpg.ui;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

/**
 * UIPanel vs UIImage:
 * - UIImage: Requires a sprite/texture, used for icons, backgrounds with images
 * - UIPanel: Renders as solid color rectangle, no texture needed
 *
 * Use UIPanel for:
 * - Solid color backgrounds
 * - Debug rectangles
 * - Simple UI frames without texture
 * - Any case where you just need a colored rectangle
 */
public class UIPanel extends UIComponent {

    @Getter
    private final Vector4f color = new Vector4f(0.2f, 0.2f, 0.2f, 1f);  // Dark gray default

    @Getter @Setter
    private float width = 100;

    @Getter @Setter
    private float height = 100;

    public UIPanel() {
    }

    public UIPanel(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public UIPanel(float width, float height, Vector4f color) {
        this.width = width;
        this.height = height;
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

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void render(UIRendererBackend backend) {
        float x = gameObject.getTransform().getPosition().x;
        float y = gameObject.getTransform().getPosition().y;
        backend.drawQuad(x, y, width, height, color);
    }

    @Override
    public String toString() {
        return String.format("UIPanel[size=%.0fx%.0f, color=(%.2f,%.2f,%.2f,%.2f)]",
                width, height, color.x, color.y, color.z, color.w);
    }
}