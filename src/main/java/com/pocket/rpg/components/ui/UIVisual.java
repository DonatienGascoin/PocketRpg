package com.pocket.rpg.components.ui;

import lombok.Getter;
import org.joml.Vector4f;

/**
 * Base class for UI components that have visual output (color, alpha).
 * Subclasses: UIPanel, UIImage, UIText.
 * <p>
 * Non-visual UI components (UIButton, UICanvas, UIMask, UIScrollView, UIScrollbar)
 * extend UIComponent directly.
 */
public abstract class UIVisual extends UIComponent {

    @Getter
    private final Vector4f color;

    protected UIVisual(float r, float g, float b, float a) {
        this.color = new Vector4f(r, g, b, a);
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
}
