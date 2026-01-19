package com.pocket.rpg.components.ui;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.rendering.ui.UIRendererBackend;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

/**
 * Renders a sprite/texture in screen space.
 * Requires UICanvas ancestor and UITransform on same GameObject.
 */
public class UIImage extends UIComponent {

    @Getter @Setter
    private Sprite sprite;

    @Getter
    private final Vector4f color = new Vector4f(1, 1, 1, 1);  // RGBA tint

    public UIImage() {
    }

    public UIImage(Sprite sprite) {
        this.sprite = sprite;
    }

    public UIImage(Texture texture) {
        this.sprite = new Sprite(texture);
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

        backend.drawSprite(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                           bounds.rotation(), bounds.pivotX(), bounds.pivotY(), sprite, color);
    }

    @Override
    public String toString() {
        return String.format("UIImage[sprite=%s]",
                sprite != null ? sprite.getName() : "null");
    }
}