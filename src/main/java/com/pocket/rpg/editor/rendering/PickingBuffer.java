package com.pocket.rpg.editor.rendering;

import lombok.Getter;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Offscreen framebuffer for GPU color picking.
 * <p>
 * Entities are rendered with their ID encoded as an RGB color.
 * Reading a single pixel back tells us which entity is under the cursor.
 * <p>
 * Uses RGB8 format (no alpha needed), GL_NEAREST filtering (no interpolation),
 * and no depth/stencil (2D painter's algorithm via z-sorting).
 */
public class PickingBuffer {

    @Getter
    private int width;

    @Getter
    private int height;

    private int fboId;
    private int textureId;

    @Getter
    private boolean initialized = false;

    public PickingBuffer(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public void init() {
        if (initialized) return;

        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        // RGB8 color attachment — entity IDs only use RGB channels
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, width, height, 0,
                GL_RGB, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        // GL_NEAREST prevents interpolation of entity ID values at edges
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureId, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[PickingBuffer] Framebuffer is not complete!");
            destroy();
            return;
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        initialized = true;
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) return;
        if (newWidth == width && newHeight == height) return;

        this.width = newWidth;
        this.height = newHeight;

        if (!initialized) {
            init();
            return;
        }

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, width, height, 0,
                GL_RGB, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind() {
        if (!initialized) init();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, width, height);
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Clears to black (RGB 0,0,0 = entity ID 0 = "no entity").
     */
    public void clear() {
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    /**
     * Reads the entity ID at the given pixel coordinates.
     *
     * @param pixelX X coordinate (0 = left)
     * @param pixelY Y coordinate (0 = bottom, OpenGL convention)
     * @return Entity ID (0 = no entity / background)
     */
    public int readEntityId(int pixelX, int pixelY) {
        if (!initialized) return 0;

        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(3);
            glReadPixels(pixelX, pixelY, 1, 1, GL_RGB, GL_UNSIGNED_BYTE, pixel);
            int r = pixel.get(0) & 0xFF;
            int g = pixel.get(1) & 0xFF;
            int b = pixel.get(2) & 0xFF;
            return r | (g << 8) | (b << 16);
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    /**
     * Encodes an entity ID (1-indexed) as a color for the picking shader.
     * Uses (n + 0.5) / 256.0 centering to avoid float round-trip errors.
     *
     * @param entityId 1-based entity ID (max 16,777,215)
     * @return Color with encoded ID in RGB, alpha = 1.0
     */
    public static Vector4f encodeEntityId(int entityId) {
        float r = ((entityId & 0xFF) + 0.5f) / 256.0f;
        float g = (((entityId >> 8) & 0xFF) + 0.5f) / 256.0f;
        float b = (((entityId >> 16) & 0xFF) + 0.5f) / 256.0f;
        return new Vector4f(r, g, b, 1.0f);
    }

    public void destroy() {
        if (!initialized) return;

        glDeleteFramebuffers(fboId);
        glDeleteTextures(textureId);

        fboId = 0;
        textureId = 0;
        initialized = false;
    }
}
