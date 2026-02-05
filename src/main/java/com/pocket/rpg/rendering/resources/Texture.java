package com.pocket.rpg.rendering.resources;

import lombok.Getter;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load;

/**
 * Represents an OpenGL texture loaded from an image file.
 * Supports common image formats (PNG, JPG, BMP, etc.) via STB Image.
 */
public class Texture {
    @Getter
    private final String filePath;
    private int textureId;
    private int width;
    private int height;
    private int channels;
    private final boolean ownsTexture;

    /**
     * Loads a texture from the specified file path.
     *
     * @param filepath Path to the image file (relative to classpath or absolute)
     * @throws RuntimeException if the image fails to load
     */
    public Texture(String filepath) {
        this.filePath = filepath;
        this.ownsTexture = true;

        // Flip image vertically (OpenGL expects bottom-left origin)
        STBImage.stbi_set_flip_vertically_on_load(true);

        int[] widthArr = new int[1];
        int[] heightArr = new int[1];
        int[] channelsArr = new int[1];

        // Load image data
        ByteBuffer imageData = stbi_load(filepath, widthArr, heightArr, channelsArr, 0);
        if (imageData == null) {
            throw new RuntimeException("Failed to load texture: " + filepath +
                    "\nReason: " + stbi_failure_reason());
        }

        this.width = widthArr[0];
        this.height = heightArr[0];
        this.channels = channelsArr[0];

        // Determine format based on channels
        int format;
        int internalFormat;
        switch (channels) {
            case 1:
                format = GL_RED;
                internalFormat = GL_RED;
                break;
            case 3:
                format = GL_RGB;
                internalFormat = GL_RGB;
                break;
            case 4:
                format = GL_RGBA;
                internalFormat = GL_RGBA;
                break;
            default:
                stbi_image_free(imageData);
                throw new RuntimeException("Unsupported number of channels: " + channels);
        }

        // Create and bind texture
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Set texture parameters for pixel-perfect rendering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Upload texture data
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                format, GL_UNSIGNED_BYTE, imageData);

        // For single-channel textures, replicate R into G and B so they
        // display as grayscale instead of red
        if (channels == 1) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_G, GL_RED);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_B, GL_RED);
        }

        // Free image data
        stbi_image_free(imageData);

        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * Private constructor for wrapping existing textures.
     */
    private Texture(int textureId, int width, int height, int channels, String filePath, boolean ownsTexture) {
        this.textureId = textureId;
        this.width = width;
        this.height = height;
        this.channels = channels;
        this.filePath = filePath;
        this.ownsTexture = ownsTexture;
    }

    /**
     * Wraps an existing OpenGL texture ID.
     * The wrapped texture will NOT be deleted when destroy() is called.
     * Use this for textures managed elsewhere (e.g., font atlases).
     *
     * @param textureId Existing OpenGL texture ID
     * @param width Texture width in pixels
     * @param height Texture height in pixels
     * @return Texture wrapper (does not own the underlying GL texture)
     */
    public static Texture wrap(int textureId, int width, int height) {
        return new Texture(textureId, width, height, 4, "[wrapped]", false);
    }

    /**
     * Binds this texture to the specified texture unit.
     *
     * @param unit Texture unit (0-31, corresponding to GL_TEXTURE0-GL_TEXTURE31)
     */
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /**
     * Binds this texture to texture unit 0.
     */
    public void bind() {
        bind(0);
    }

    /**
     * Unbinds any texture from the specified unit.
     *
     * @param unit Texture unit to unbind
     */
    public static void unbind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * Unbinds any texture from unit 0.
     */
    public static void unbind() {
        unbind(0);
    }

    /**
     * Frees the OpenGL texture resource.
     * Only deletes the texture if this instance owns it (not a wrapped texture).
     */
    public void destroy() {
        if (ownsTexture) {
            glDeleteTextures(textureId);
        }
    }

    /**
     * Reloads this texture from disk, replacing GPU data while keeping the same instance.
     * Creates new GL texture before destroying old to avoid render gaps.
     * <p>
     * All existing references to this Texture remain valid after reload.
     *
     * @param path Path to image file
     * @throws RuntimeException if loading fails (this texture unchanged on failure)
     * @throws IllegalStateException if this is a wrapped texture (not owned)
     */
    public void reloadFromDisk(String path) {
        if (!ownsTexture) {
            throw new IllegalStateException("Cannot reload wrapped texture (not owned): " + filePath);
        }

        // 1. Load new image data FIRST (fail-fast, don't destroy old yet)
        STBImage.stbi_set_flip_vertically_on_load(true);

        int[] widthArr = new int[1];
        int[] heightArr = new int[1];
        int[] channelsArr = new int[1];

        ByteBuffer imageData = stbi_load(path, widthArr, heightArr, channelsArr, 0);
        if (imageData == null) {
            throw new RuntimeException("Failed to reload texture: " + path +
                    "\nReason: " + stbi_failure_reason());
        }

        int newWidth = widthArr[0];
        int newHeight = heightArr[0];
        int newChannels = channelsArr[0];

        // Determine format based on channels
        int format;
        int internalFormat;
        switch (newChannels) {
            case 1:
                format = GL_RED;
                internalFormat = GL_RED;
                break;
            case 3:
                format = GL_RGB;
                internalFormat = GL_RGB;
                break;
            case 4:
                format = GL_RGBA;
                internalFormat = GL_RGBA;
                break;
            default:
                stbi_image_free(imageData);
                throw new RuntimeException("Unsupported number of channels: " + newChannels);
        }

        // 2. Create new GL texture
        int newTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, newTextureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, newWidth, newHeight, 0,
                format, GL_UNSIGNED_BYTE, imageData);

        if (newChannels == 1) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_G, GL_RED);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_B, GL_RED);
        }

        glBindTexture(GL_TEXTURE_2D, 0);

        // 3. Free CPU-side image data
        stbi_image_free(imageData);

        // 4. Only NOW destroy old texture (no gap - new one ready)
        glDeleteTextures(this.textureId);

        // 5. Update internal state
        this.textureId = newTextureId;
        this.width = newWidth;
        this.height = newHeight;
        this.channels = newChannels;
    }

    // Getters

    public int getTextureId() {
        return textureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getChannels() {
        return channels;
    }

    /**
     * Returns true if this texture owns its OpenGL resource.
     * Wrapped textures do not own their resources.
     */
    public boolean ownsTexture() {
        return ownsTexture;
    }
}