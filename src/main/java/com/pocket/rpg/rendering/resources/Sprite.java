package com.pocket.rpg.rendering.resources;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a 2D sprite - a pure visual definition.
 * Contains texture data, UV coordinates, and base size in pixels.
 * Position, rotation, and scaling are handled by Transform component.
 * <p>
 * This class is designed to be shared across multiple GameObjects,
 * making it ideal for sprite caching in SpriteSheet.
 *
 * <h2>World Unit System</h2>
 * Sprites store their size in pixels but expose world-space dimensions
 * via {@link #getWorldWidth()} and {@link #getWorldHeight()}.
 * The conversion uses Pixels Per Unit (PPU) from {@link RenderingConfig}.
 * <p>
 * Individual sprites can override the global PPU using
 * {@link #setPixelsPerUnitOverride(Float)}.
 *
 * <h2>Pivot Point</h2>
 * The pivot point ({@link #pivotX}, {@link #pivotY}) defines the sprite's
 * origin for positioning and rotation. Values are normalized (0-1):
 * <ul>
 *   <li>(0, 0) = bottom-left</li>
 *   <li>(0.5, 0.5) = center (default)</li>
 *   <li>(0.5, 0) = bottom-center (good for characters standing on tiles)</li>
 *   <li>(1, 1) = top-right</li>
 * </ul>
 *
 * <h2>Asset Path Tracking</h2>
 * Sprite paths are tracked centrally by {@link com.pocket.rpg.resources.AssetManager}.
 * Use {@code Assets.getPathForResource(sprite)} to get the sprite's path.
 * For sprites from spritesheets, the path includes the index: "sheet.spritesheet#3".
 *
 * @see RenderingConfig#getPixelsPerUnit()
 */
@Getter
public class Sprite {

    private final Texture texture;

    @Setter
    private String name;

    /**
     * Base width in pixels (texture space).
     * For world-space width, use {@link #getWorldWidth()}.
     */
    @Setter
    private float width;

    /**
     * Base height in pixels (texture space).
     * For world-space height, use {@link #getWorldHeight()}.
     */
    @Setter
    private float height;

    // UV coordinates (texture coordinates, normalized 0-1)
    private float u0;  // Left U coordinate
    private float v0;  // Top V coordinate
    private float u1;  // Right U coordinate
    private float v1;  // Bottom V coordinate

    /**
     * Pivot X coordinate (0-1). 0 = left edge, 0.5 = center, 1 = right edge.
     * Used as default origin for SpriteRenderer.
     */
    @Setter
    private float pivotX = 0.5f;

    /**
     * Pivot Y coordinate (0-1). 0 = bottom edge, 0.5 = center, 1 = top edge.
     * Used as default origin for SpriteRenderer.
     */
    @Setter
    private float pivotY = 0.5f;

    /**
     * Optional per-sprite PPU override.
     * When null, uses the global PPU from RenderingConfig.
     * When set, this sprite uses the specified PPU regardless of global setting.
     */
    @Setter
    private Float pixelsPerUnitOverride = null;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Creates a sprite with the full texture.
     *
     * @param texture The texture to render
     */
    public Sprite(Texture texture) {
        this.texture = texture;
        this.name = "Sprite";
        this.width = texture.getWidth();
        this.height = texture.getHeight();
        setUVs(0, 0, 1, 1); // Full texture
    }

    public static Sprite copy(Sprite sprite) {
        var copy = new Sprite(sprite.texture, sprite.getWidth(), sprite.getHeight(), sprite.getName());
        copy.setPivot(sprite.getPivotX(), sprite.getPivotY());
        copy.setUVs(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
        copy.setPixelsPerUnitOverride(sprite.getPixelsPerUnitOverride());

        return copy;
    }

    /**
     * Creates a sprite with the full texture and a name.
     *
     * @param texture The texture to render
     * @param name    Name for debugging
     */
    public Sprite(Texture texture, String name) {
        this(texture);
        this.name = name;
    }

    /**
     * Creates a sprite with custom pixel size.
     *
     * @param texture The texture to render
     * @param width   Sprite base width in pixels
     * @param height  Sprite base height in pixels
     */
    public Sprite(Texture texture, float width, float height) {
        this.texture = texture;
        this.name = "Sprite";
        this.width = width;
        this.height = height;
        setUVs(0, 0, 1, 1); // Full texture
    }

    /**
     * Creates a sprite with custom pixel size and name.
     *
     * @param texture The texture to render
     * @param width   Sprite base width in pixels
     * @param height  Sprite base height in pixels
     * @param name    Name for debugging
     */
    public Sprite(Texture texture, float width, float height, String name) {
        this(texture, width, height);
        this.name = name;
    }

    /**
     * Creates a sprite from a sprite sheet region.
     *
     * @param texture     The sprite sheet texture
     * @param width       Sprite base width in pixels
     * @param height      Sprite base height in pixels
     * @param sheetX      X position in sprite sheet (pixels)
     * @param sheetY      Y position in sprite sheet (pixels)
     * @param sheetWidth  Width of region in sprite sheet (pixels)
     * @param sheetHeight Height of region in sprite sheet (pixels)
     */
    public Sprite(Texture texture, float width, float height,
                  int sheetX, int sheetY, int sheetWidth, int sheetHeight) {
        this.texture = texture;
        this.name = "Sprite";
        this.width = width;
        this.height = height;
        setUVsFromPixels(sheetX, sheetY, sheetWidth, sheetHeight);
    }

    /**
     * Creates a sprite from a sprite sheet region with a name.
     *
     * @param texture     The sprite sheet texture
     * @param width       Sprite base width in pixels
     * @param height      Sprite base height in pixels
     * @param sheetX      X position in sprite sheet (pixels)
     * @param sheetY      Y position in sprite sheet (pixels)
     * @param sheetWidth  Width of region in sprite sheet (pixels)
     * @param sheetHeight Height of region in sprite sheet (pixels)
     * @param name        Name for debugging
     */
    public Sprite(Texture texture, float width, float height,
                  int sheetX, int sheetY, int sheetWidth, int sheetHeight, String name) {
        this(texture, width, height, sheetX, sheetY, sheetWidth, sheetHeight);
        this.name = name;
    }

    // ========================================================================
    // PIVOT METHODS
    // ========================================================================

    /**
     * Sets the pivot point.
     *
     * @param pivotX X pivot (0-1, where 0.5 is center)
     * @param pivotY Y pivot (0-1, where 0.5 is center)
     */
    public void setPivot(float pivotX, float pivotY) {
        this.pivotX = pivotX;
        this.pivotY = pivotY;
    }

    /**
     * Sets pivot to center (default).
     */
    public void setPivotCenter() {
        setPivot(0.5f, 0.5f);
    }

    /**
     * Sets pivot to bottom-center.
     * Ideal for characters standing on tiles.
     */
    public void setPivotBottomCenter() {
        setPivot(0.5f, 0f);
    }

    /**
     * Sets pivot to bottom-left.
     */
    public void setPivotBottomLeft() {
        setPivot(0f, 0f);
    }

    /**
     * Sets pivot to top-left.
     */
    public void setPivotTopLeft() {
        setPivot(0f, 1f);
    }

    // ========================================================================
    // WORLD UNIT METHODS
    // ========================================================================

    /**
     * Gets the effective Pixels Per Unit for this sprite.
     * <p>
     * Returns the per-sprite override if set, otherwise returns
     * the global PPU from RenderingConfig.
     *
     * @return The PPU value to use for world-space calculations
     */
    public float getPixelsPerUnit() {
        if (pixelsPerUnitOverride != null) {
            return pixelsPerUnitOverride;
        }
        // Get from config
        RenderingConfig config = ConfigLoader.getConfig(ConfigLoader.ConfigType.RENDERING);
        return config.getPixelsPerUnit();
    }

    /**
     * Gets the sprite width in world units.
     * <p>
     * This is the size used for rendering and physics calculations.
     * Calculated as: {@code pixelWidth / pixelsPerUnit}
     *
     * <h3>Example</h3>
     * <pre>
     * // 16×16 pixel sprite with PPU=16
     * sprite.getWorldWidth();  // Returns 1.0
     *
     * // 32×32 pixel sprite with PPU=16
     * sprite.getWorldWidth();  // Returns 2.0
     * </pre>
     *
     * @return Width in world units
     * @see #getPixelsPerUnit()
     */
    public float getWorldWidth() {
        return width / getPixelsPerUnit();
    }

    /**
     * Gets the sprite height in world units.
     * <p>
     * This is the size used for rendering and physics calculations.
     * Calculated as: {@code pixelHeight / pixelsPerUnit}
     *
     * @return Height in world units
     * @see #getPixelsPerUnit()
     */
    public float getWorldHeight() {
        return height / getPixelsPerUnit();
    }

    // ========================================================================
    // UV METHODS
    // ========================================================================

    /**
     * Sets UV coordinates (normalized 0-1).
     *
     * @param u0 Left U coordinate
     * @param v0 Top V coordinate
     * @param u1 Right U coordinate
     * @param v1 Bottom V coordinate
     */
    public void setUVs(float u0, float v0, float u1, float v1) {
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
    }

    /**
     * Sets UV coordinates from pixel coordinates in the texture.
     *
     * @param pixelX      X position in texture (pixels)
     * @param pixelY      Y position in texture (pixels)
     * @param pixelWidth  Width in texture (pixels)
     * @param pixelHeight Height in texture (pixels)
     */
    public void setUVsFromPixels(int pixelX, int pixelY, int pixelWidth, int pixelHeight) {
        float texWidth = texture.getWidth();
        float texHeight = texture.getHeight();

        this.u0 = pixelX / texWidth;
        this.v0 = pixelY / texHeight;
        this.u1 = (pixelX + pixelWidth) / texWidth;
        this.v1 = (pixelY + pixelHeight) / texHeight;
    }

    /**
     * Sets the sprite's base size in pixels.
     *
     * @param width  New width in pixels
     * @param height New height in pixels
     */
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public String toString() {
        return String.format("Sprite[name=%s, pixels=%.0fx%.0f, world=%.2fx%.2f, pivot=(%.2f,%.2f), ppu=%.0f, uv=(%.2f,%.2f)-(%.2f,%.2f)]",
                name, width, height, getWorldWidth(), getWorldHeight(), pivotX, pivotY, getPixelsPerUnit(), u0, v0, u1, v1);
    }
}
