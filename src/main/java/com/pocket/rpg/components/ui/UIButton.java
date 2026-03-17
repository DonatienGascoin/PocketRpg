package com.pocket.rpg.components.ui;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Interaction-only button component that manages a sibling UIVisual for rendering.
 * <p>
 * UIButton does NOT render itself. Instead, it manages a sibling UIPanel (COLOR_TINT mode)
 * or UIImage (SPRITE_SWAP mode) on the same GameObject, pushing hover/press state to it.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>On start, UIButton finds or creates the correct sibling UIVisual</li>
 *   <li>On hover/press state changes, UIButton pushes color/sprite to the managed visual</li>
 *   <li>UIRenderer renders the sibling UIPanel/UIImage normally (UIButton is not in dispatch)</li>
 * </ol>
 * <p>
 * Requires UICanvas ancestor and UITransform on same GameObject.
 */
public class UIButton extends UIComponent {

    // ========================================
    // Transition Mode
    // ========================================

    public enum TransitionMode { COLOR_TINT, SPRITE_SWAP }

    // ========================================
    // Visual Properties
    // ========================================

    @Getter @Setter
    private TransitionMode transitionMode = TransitionMode.COLOR_TINT;

    @Getter @Setter
    private Sprite sprite;

    @Getter
    private final Vector4f color = new Vector4f(0.3f, 0.3f, 0.3f, 1f);  // Default gray

    // Hover tint: how much darker on hover (0.1 = 10% darker)
    // null = use GameConfig default
    @Getter @Setter
    private Float hoverTint = null;

    // Pressed tint: how much darker on press (0.2 = 20% darker)
    // null = use GameConfig default
    @Getter @Setter
    private Float pressedTint = null;

    // Optional color overrides for hover/pressed states (full RGBA replacement).
    @Getter
    private Vector4f hoveredColor = new Vector4f(0.3f, 0.3f, 0.3f, 0.3f);

    @Getter
    private Vector4f pressedColor = new Vector4f(0.3f, 0.3f, 0.3f, 0.5f);

    // Sprites for SPRITE_SWAP mode
    @Getter @Setter
    private Sprite hoveredSprite;

    @Getter @Setter
    private Sprite pressedSprite;

    // ========================================
    // Managed Visual
    // ========================================

    /**
     * The managed sibling UIVisual (UIPanel for COLOR_TINT, UIImage for SPRITE_SWAP).
     * Not serialized — resolved on start from existing siblings.
     */
    private transient UIVisual managedVisual;

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
    // Lifecycle
    // ========================================

    @Override
    protected void onStart() {
        super.onStart();
        ensureManagedVisual();
    }

    // ========================================
    // Managed Visual Lifecycle
    // ========================================

    /**
     * Finds or creates the correct sibling UIVisual based on TransitionMode.
     * Called from onStart() at runtime to adopt existing siblings or auto-create missing ones.
     * <p>
     * TransitionMode swapping at runtime is not supported — the editor (UIButtonInspector)
     * handles mode changes by swapping components via CompoundCommand.
     */
    public void ensureManagedVisual() {
        if (gameObject == null) return;

        switch (transitionMode) {
            case COLOR_TINT -> {
                UIPanel panel = gameObject.getComponent(UIPanel.class);
                if (panel != null) {
                    managedVisual = panel;
                    pushStateToVisual();
                    return;
                }
                // Wrong type present? Remove it (handles migration/corruption)
                UIImage wrongType = gameObject.getComponent(UIImage.class);
                if (wrongType != null) {
                    gameObject.removeComponent(wrongType);
                }
                panel = new UIPanel(new Vector4f(color));
                
                gameObject.addComponent(panel);
                managedVisual = panel;
                pushStateToVisual();
            }
            case SPRITE_SWAP -> {
                UIImage image = gameObject.getComponent(UIImage.class);
                if (image != null) {
                    managedVisual = image;
                    
                    pushStateToVisual();
                    return;
                }
                // Wrong type present? Remove it (handles migration/corruption)
                UIPanel wrongType = gameObject.getComponent(UIPanel.class);
                if (wrongType != null) {
                    gameObject.removeComponent(wrongType);
                }
                image = new UIImage(sprite);
                
                gameObject.addComponent(image);
                managedVisual = image;
                pushStateToVisual();
            }
        }
    }

    /**
     * Pushes the current button state (color, hover, press) to the managed visual.
     */
    private void pushStateToVisual() {
        if (managedVisual == null) return;

        switch (transitionMode) {
            case COLOR_TINT -> pushColorTintState();
            case SPRITE_SWAP -> pushSpriteSwapState();
        }
    }

    private void pushColorTintState() {
        Vector4f renderColor = computeCurrentColor();
        managedVisual.setColor(renderColor);
    }

    private void pushSpriteSwapState() {
        if (managedVisual instanceof UIImage image) {
            Sprite activeSprite;
            if (pressed && pressedSprite != null) {
                activeSprite = pressedSprite;
            } else if (hovered && hoveredSprite != null) {
                activeSprite = hoveredSprite;
            } else {
                activeSprite = sprite;
            }
            image.setSprite(activeSprite);
            image.setColor(new Vector4f(color));
        }
    }

    /**
     * Computes the current effective color based on hover/press state.
     */
    private Vector4f computeCurrentColor() {
        Vector4f renderColor = new Vector4f(color);
        if (useAutoHoverTint()) {
            if (pressed && pressedColor != null) {
                renderColor.set(pressedColor);
            } else if (pressed) {
                float tint = getEffectivePressedTint();
                renderColor.x *= (1f - tint);
                renderColor.y *= (1f - tint);
                renderColor.z *= (1f - tint);
            } else if (hovered && hoveredColor != null) {
                renderColor.set(hoveredColor);
            } else if (hovered) {
                float tint = getEffectiveHoverTint();
                renderColor.x *= (1f - tint);
                renderColor.y *= (1f - tint);
                renderColor.z *= (1f - tint);
            }
        }
        return renderColor;
    }

    // ========================================
    // Color Methods
    // ========================================

    public void setColor(float r, float g, float b, float a) {
        color.set(r, g, b, a);
        pushStateToVisual();
    }

    public void setColor(Vector4f color) {
        this.color.set(color);
        pushStateToVisual();
    }

    public void setAlpha(float alpha) {
        color.w = alpha;
        pushStateToVisual();
    }

    public void setHoveredColor(Vector4f hoveredColor) {
        this.hoveredColor = hoveredColor != null ? new Vector4f(hoveredColor) : null;
    }

    public void setHoveredColor(float r, float g, float b, float a) {
        this.hoveredColor = new Vector4f(r, g, b, a);
    }

    public void setPressedColor(Vector4f pressedColor) {
        this.pressedColor = pressedColor != null ? new Vector4f(pressedColor) : null;
    }

    public void setPressedColor(float r, float g, float b, float a) {
        this.pressedColor = new Vector4f(r, g, b, a);
    }

    // ========================================
    // Config
    // ========================================

    public void setConfig(GameConfig config) {
        this.config = config;
    }

    public float getEffectiveHoverTint() {
        if (hoverTint != null) {
            return hoverTint;
        }
        if (config != null) {
            return config.getUiButtonHoverTint();
        }
        return 0.1f;
    }

    public float getEffectivePressedTint() {
        if (pressedTint != null) {
            return pressedTint;
        }
        if (config != null) {
            return config.getUiButtonPressedTint();
        }
        return 0.2f;
    }

    public boolean useAutoHoverTint() {
        return onHover == null && onExit == null;
    }

    // ========================================
    // State Management (called by UIInputHandler)
    // ========================================

    public void setHoveredInternal(boolean hovered) {
        if (this.hovered == hovered) return;

        boolean wasHovered = this.hovered;
        this.hovered = hovered;

        if (hovered && !wasHovered) {
            if (onHover != null) {
                onHover.run();
            }
        } else if (!hovered && wasHovered) {
            if (onExit != null) {
                onExit.run();
            }
            pressed = false;
        }

        pushStateToVisual();
    }

    public void setPressedInternal(boolean pressed) {
        this.pressed = pressed;
        pushStateToVisual();
    }

    public void triggerClick() {
        if (onClick != null) {
            onClick.run();
        }
    }

    // ========================================
    // Hit Testing
    // ========================================

    public boolean containsPoint(float testX, float testY) {
        UITransform transform = getUITransform();
        if (transform == null) return false;

        Vector2f pivotWorld = transform.getWorldPivotPosition2D();
        Vector2f scale = transform.getComputedWorldScale2D();
        float w = transform.getEffectiveWidth() * scale.x;
        float h = transform.getEffectiveHeight() * scale.y;
        float rotation = transform.getComputedWorldRotation2D();
        Vector2f pivot = transform.getEffectivePivot();

        float posX = pivotWorld.x - pivot.x * w;
        float posY = pivotWorld.y - pivot.y * h;

        if (Math.abs(rotation) < 0.001f) {
            return testX >= posX && testX <= posX + w &&
                    testY >= posY && testY <= posY + h;
        }

        float pivotX = pivotWorld.x;
        float pivotY = pivotWorld.y;

        float relX = testX - pivotX;
        float relY = testY - pivotY;

        float radians = (float) Math.toRadians(-rotation);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        float localX = relX * cos - relY * sin + pivotX;
        float localY = relX * sin + relY * cos + pivotY;

        return localX >= posX && localX <= posX + w &&
                localY >= posY && localY <= posY + h;
    }

    @Override
    public String toString() {
        return String.format("UIButton[hovered=%s, image=%s]",
                hovered, sprite != null ? "yes" : "no");
    }
}
