package com.pocket.rpg.ui;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Clickable button with built-in visuals (image or solid color).
 * Supports hover tint and click/hover callbacks.
 *
 * Hover tint behavior:
 * - If onHover/onExit callbacks are set: no automatic tint (user handles visuals)
 * - If no callbacks: applies darkening tint (configurable)
 *
 * Requires UICanvas ancestor and UITransform on same GameObject.
 */
public class UIButton extends UIComponent {

    // ========================================
    // Visual Properties
    // ========================================

    @Getter @Setter
    private Sprite image;

    @Getter
    private final Vector4f color = new Vector4f(0.3f, 0.3f, 0.3f, 1f);  // Default gray

    // Hover tint: how much darker on hover (0.1 = 10% darker)
    // null = use GameConfig default
    @Getter @Setter
    private Float hoverTint = null;

    // ========================================
    // Callbacks
    // ========================================

    @Getter @Setter
    private Runnable onClick;

    @Getter @Setter
    private Runnable onHover;

    @Getter @Setter
    private Runnable onExit;

    // ========================================
    // State
    // ========================================

    @Getter
    private boolean hovered = false;

    @Getter
    private boolean pressed = false;

    // Config reference (set by UIInputHandler)
    private GameConfig config;

    // ========================================
    // Constructors
    // ========================================

    public UIButton() {
    }

    public UIButton(Sprite image) {
        this.image = image;
    }

    public UIButton(Texture texture) {
        this.image = new Sprite(texture);
    }

    // ========================================
    // Color Methods
    // ========================================

    public void setColor(float r, float g, float b, float a) {
        color.set(r, g, b, a);
    }

    public void setColor(Vector4f color) {
        this.color.set(color);
    }

    public void setAlpha(float alpha) {
        color.w = alpha;
    }

    // ========================================
    // Config
    // ========================================

    /**
     * Sets the GameConfig reference for default hover tint.
     * Called by UIInputHandler during initialization.
     */
    public void setConfig(GameConfig config) {
        this.config = config;
    }

    /**
     * Gets the effective hover tint value.
     * Uses button override if set, otherwise GameConfig default.
     */
    public float getEffectiveHoverTint() {
        if (hoverTint != null) {
            return hoverTint;
        }
        if (config != null) {
            return config.getUiButtonHoverTint();
        }
        return 0.1f;  // Fallback default
    }

    /**
     * Returns true if automatic hover tint should be applied.
     * False if user has set custom hover/exit callbacks.
     */
    public boolean useAutoHoverTint() {
        return onHover == null && onExit == null;
    }

    // ========================================
    // State Management (called by UIInputHandler)
    // ========================================

    /**
     * Called by UIInputHandler when mouse enters button bounds.
     */
    void setHoveredInternal(boolean hovered) {
        if (this.hovered == hovered) return;

        boolean wasHovered = this.hovered;
        this.hovered = hovered;

        if (hovered && !wasHovered) {
            // Just entered
            if (onHover != null) {
                onHover.run();
            }
        } else if (!hovered && wasHovered) {
            // Just exited
            if (onExit != null) {
                onExit.run();
            }
            pressed = false;  // Reset pressed state on exit
        }
    }

    /**
     * Called by UIInputHandler when mouse is pressed over button.
     */
    void setPressedInternal(boolean pressed) {
        this.pressed = pressed;
    }

    /**
     * Called by UIInputHandler when mouse is released over button (click).
     */
    void triggerClick() {
        if (onClick != null) {
            onClick.run();
        }
    }

    // ========================================
    // Rendering
    // ========================================

    @Override
    public void render(UIRendererBackend backend) {
        UITransform transform = getUITransform();
        if (transform == null) return;

        Vector2f pos = transform.getScreenPosition();
        float w = transform.getWidth();
        float h = transform.getHeight();

        // Calculate render color with hover tint
        Vector4f renderColor = new Vector4f(color);

        if (hovered && useAutoHoverTint()) {
            float tint = getEffectiveHoverTint();
            renderColor.x *= (1f - tint);
            renderColor.y *= (1f - tint);
            renderColor.z *= (1f - tint);
        }

        if (image != null) {
            backend.drawSprite(pos.x, pos.y, w, h, image, renderColor);
        } else {
            backend.drawQuad(pos.x, pos.y, w, h, renderColor);
        }
    }

    // ========================================
    // Hit Testing
    // ========================================

    /**
     * Tests if a point (in game coordinates) is inside this button.
     * Used by UIInputHandler for hover/click detection.
     */
    public boolean containsPoint(float x, float y) {
        UITransform transform = getUITransform();
        if (transform == null) return false;

        Vector2f pos = transform.getScreenPosition();
        float w = transform.getWidth();
        float h = transform.getHeight();

        return x >= pos.x && x <= pos.x + w &&
                y >= pos.y && y <= pos.y + h;
    }

    @Override
    public String toString() {
        return String.format("UIButton[hovered=%s, image=%s]",
                hovered, image != null ? "yes" : "no");
    }
}