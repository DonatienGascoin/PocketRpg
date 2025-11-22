package com.pocket.rpg.rendering;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a 2D sprite - a pure visual definition.
 * Contains only texture data, UV coordinates, and base size.
 * Position, rotation, and scaling are handled by Transform component.
 *
 * This class is designed to be shared across multiple GameObjects,
 * making it ideal for sprite caching in SpriteSheet.
 */
@Getter
public class Sprite {

    private final Texture texture;
    @Setter
    private String name;

    @Setter
    private float width;   // Base width in pixels
    @Setter
    private float height;  // Base height in pixels

    // UV coordinates (texture coordinates, normalized 0-1)
    private float u0;  // Left U coordinate
    private float v0;  // Top V coordinate
    private float u1;  // Right U coordinate
    private float v1;  // Bottom V coordinate

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
     * Creates a sprite with custom size.
     *
     * @param texture The texture to render
     * @param width   Sprite base width
     * @param height  Sprite base height
     */
    public Sprite(Texture texture, float width, float height) {
        this.texture = texture;
        this.name = "Sprite";
        this.width = width;
        this.height = height;
        setUVs(0, 0, 1, 1); // Full texture
    }

    /**
     * Creates a sprite with custom size and name.
     *
     * @param texture The texture to render
     * @param width   Sprite base width
     * @param height  Sprite base height
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
     * @param width       Sprite base width
     * @param height      Sprite base height
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
     * @param width       Sprite base width
     * @param height      Sprite base height
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
     * Sets the sprite's base size.
     *
     * @param width  New width
     * @param height New height
     */
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public String toString() {
        return String.format("Sprite[name=%s, size=%.0fx%.0f, uv=(%.2f,%.2f)-(%.2f,%.2f)]",
                name, width, height, u0, v0, u1, v1);
    }
}