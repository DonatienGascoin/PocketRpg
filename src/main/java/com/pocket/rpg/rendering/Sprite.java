package com.pocket.rpg.rendering;


/**
 * Represents a 2D sprite with position, scale, rotation, texture, and UV coordinates.
 * Supports both full textures and sprite sheet regions.
 */
public class Sprite {

    private final Texture texture;
    private float x;
    private float y;
    private float width;
    private float height;
    private float rotation; // In degrees
    private float originX;  // Rotation origin X (0-1, relative to sprite size)
    private float originY;  // Rotation origin Y (0-1, relative to sprite size)

    // UV coordinates (texture coordinates)
    private float u0;  // Left U coordinate (0-1)
    private float v0;  // Top V coordinate (0-1)
    private float u1;  // Right U coordinate (0-1)
    private float v1;  // Bottom V coordinate (0-1)

    /**
     * Creates a sprite with the full texture.
     *
     * @param texture The texture to render
     */
    public Sprite(Texture texture) {
        this.texture = texture;
        this.x = 0;
        this.y = 0;
        this.width = texture.getWidth();
        this.height = texture.getHeight();
        this.rotation = 0;
        this.originX = 0.5f;
        this.originY = 0.5f;
        setUVs(0, 0, 1, 1); // Full texture
    }

    /**
     * Creates a sprite with the full texture at a specific position.
     *
     * @param texture The texture to render
     * @param x       X position
     * @param y       Y position
     */
    public Sprite(Texture texture, float x, float y) {
        this(texture);
        this.x = x;
        this.y = y;
    }

    /**
     * Creates a sprite with custom size.
     *
     * @param texture The texture to render
     * @param x       X position
     * @param y       Y position
     * @param width   Sprite width
     * @param height  Sprite height
     */
    public Sprite(Texture texture, float x, float y, float width, float height) {
        this(texture, x, y);
        this.width = width;
        this.height = height;
    }

    /**
     * Creates a sprite from a sprite sheet region.
     *
     * @param texture    The sprite sheet texture
     * @param x          X position on screen
     * @param y          Y position on screen
     * @param width      Sprite width on screen
     * @param height     Sprite height on screen
     * @param sheetX     X position in sprite sheet (pixels)
     * @param sheetY     Y position in sprite sheet (pixels)
     * @param sheetWidth Width of region in sprite sheet (pixels)
     * @param sheetHeight Height of region in sprite sheet (pixels)
     */
    public Sprite(Texture texture, float x, float y, float width, float height,
                  int sheetX, int sheetY, int sheetWidth, int sheetHeight) {
        this(texture, x, y, width, height);
        setUVsFromPixels(sheetX, sheetY, sheetWidth, sheetHeight);
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

    // Getters and Setters

    public Texture getTexture() {
        return texture;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public float getOriginX() {
        return originX;
    }

    public void setOriginX(float originX) {
        this.originX = originX;
    }

    public float getOriginY() {
        return originY;
    }

    public void setOriginY(float originY) {
        this.originY = originY;
    }

    public void setOrigin(float originX, float originY) {
        this.originX = originX;
        this.originY = originY;
    }

    public float getU0() {
        return u0;
    }

    public float getV0() {
        return v0;
    }

    public float getU1() {
        return u1;
    }

    public float getV1() {
        return v1;
    }
}