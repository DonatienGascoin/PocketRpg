package com.pocket.rpg.rendering;

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
    private final int textureId;
    private final int width;
    private final int height;
    private final int channels;

    /**
     * Loads a texture from the specified file path.
     *
     * @param filepath Path to the image file (relative to classpath or absolute)
     * @throws RuntimeException if the image fails to load
     */
    public Texture(String filepath) {
        this.filePath = filepath;
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

        // Free image data
        stbi_image_free(imageData);

        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);
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
     */
    public void destroy() {
        glDeleteTextures(textureId);
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
}