package com.pocket.rpg.ui;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

/**
 * Renders a sprite/texture in screen space.
 * Must be on a GameObject with a UICanvas ancestor to render.
 */
public class UIImage extends UIComponent {

    @Getter @Setter
    private Sprite sprite;

    @Getter
    private final Vector4f color = new Vector4f(1, 1, 1, 1);  // RGBA tint

    @Getter @Setter
    private float width = 100;

    @Getter @Setter
    private float height = 100;

    public UIImage() {
    }

    public UIImage(Sprite sprite) {
        this.sprite = sprite;
        if (sprite != null) {
            this.width = sprite.getWidth();
            this.height = sprite.getHeight();
        }
    }

    public UIImage(Texture texture) {
        this.sprite = new Sprite(texture);
        this.width = texture.getWidth();
        this.height = texture.getHeight();
    }

    public UIImage(Sprite sprite, float width, float height) {
        this.sprite = sprite;
        this.width = width;
        this.height = height;
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
        backend.drawSprite(x, y, width, height, sprite, color);
    }

    @Override
    public String toString() {
        return String.format("UIImage[size=%.0fx%.0f, sprite=%s]",
                width, height, sprite != null ? sprite.getName() : "null");
    }
}