package com.pocket.rpg.components.ui;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.rendering.ui.UIRendererBackend;
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
    private Sprite sprite;

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

    public UIButton(Sprite sprite) {
        this.sprite = sprite;
    }

    public UIButton(Texture texture) {
        this.sprite = new Sprite(texture);
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
    public void setHoveredInternal(boolean hovered) {
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
    public void setPressedInternal(boolean pressed) {
        this.pressed = pressed;
    }

    /**
     * Called by UIInputHandler when mouse is released over button (click).
     */
    public void triggerClick() {
        if (onClick != null) {
            onClick.run();
        }
    }

    // ========================================
    // Rendering
    // ========================================

    @Override
    public void render(UIRendererBackend backend) {
        RenderBounds bounds = computeRenderBounds();
        if (bounds == null) return;

        // Calculate render color with hover tint
        Vector4f renderColor = new Vector4f(color);
        if (hovered && useAutoHoverTint()) {
            float tint = getEffectiveHoverTint();
            renderColor.x *= (1f - tint);
            renderColor.y *= (1f - tint);
            renderColor.z *= (1f - tint);
        }

        if (sprite != null) {
            if (bounds.rotation() != 0) {
                backend.drawSprite(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                                   bounds.rotation(), bounds.pivotX(), bounds.pivotY(), sprite, renderColor);
            } else {
                backend.drawSprite(bounds.x(), bounds.y(), bounds.width(), bounds.height(), sprite, renderColor);
            }
        } else {
            if (bounds.rotation() != 0) {
                backend.drawQuad(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                                 bounds.rotation(), bounds.pivotX(), bounds.pivotY(), renderColor);
            } else {
                backend.drawQuad(bounds.x(), bounds.y(), bounds.width(), bounds.height(), renderColor);
            }
        }
    }

    // ========================================
    // Hit Testing
    // ========================================

    /**
     * Tests if a point (in game coordinates) is inside this button.
     * Used by UIInputHandler for hover/click detection.
     * Handles rotation by transforming the point into local space.
     */
    public boolean containsPoint(float testX, float testY) {
        UITransform transform = getUITransform();
        if (transform == null) return false;

        // Use matrix-based methods for correct hierarchy handling
        Vector2f pivotWorld = transform.getWorldPivotPosition2D();
        Vector2f scale = transform.getComputedWorldScale2D();
        float w = transform.getEffectiveWidth() * scale.x;
        float h = transform.getEffectiveHeight() * scale.y;
        float rotation = transform.getComputedWorldRotation2D();
        Vector2f pivot = transform.getEffectivePivot();  // Use effective pivot for MATCH_PARENT

        // Calculate top-left position from pivot
        float posX = pivotWorld.x - pivot.x * w;
        float posY = pivotWorld.y - pivot.y * h;

        // If no rotation, use simple AABB check
        if (Math.abs(rotation) < 0.001f) {
            return testX >= posX && testX <= posX + w &&
                    testY >= posY && testY <= posY + h;
        }

        // Transform point into button's local coordinate space
        float pivotX = pivotWorld.x;
        float pivotY = pivotWorld.y;

        // Translate point relative to pivot
        float relX = testX - pivotX;
        float relY = testY - pivotY;

        // Apply inverse rotation (negate the angle)
        float radians = (float) Math.toRadians(-rotation);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        float localX = relX * cos - relY * sin + pivotX;
        float localY = relX * sin + relY * cos + pivotY;

        // Now do AABB check in local space
        return localX >= posX && localX <= posX + w &&
                localY >= posY && localY <= posY + h;
    }

    @Override
    public String toString() {
        return String.format("UIButton[hovered=%s, image=%s]",
                hovered, sprite != null ? "yes" : "no");
    }
}