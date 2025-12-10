package com.pocket.rpg.editor.rendering;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL framebuffer for rendering the scene to a texture.
 * 
 * The scene is rendered to this framebuffer, then the resulting texture
 * is displayed in the ImGui viewport panel.
 * 
 * Supports dynamic resizing when the viewport panel changes size.
 */
public class EditorFramebuffer {
    
    private int fboId;
    private int textureId;
    private int depthRboId;
    
    private int width;
    private int height;
    
    private boolean initialized = false;

    /**
     * Creates a framebuffer with the specified dimensions.
     * 
     * @param width Initial width in pixels
     * @param height Initial height in pixels
     */
    public EditorFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Initializes the OpenGL framebuffer resources.
     * Must be called on the OpenGL thread after context creation.
     */
    public void init() {
        if (initialized) {
            return;
        }

        // Create framebuffer object
        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        // Create color texture attachment
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureId, 0);

        // Create depth/stencil renderbuffer attachment
        depthRboId = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depthRboId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthRboId);

        // Check framebuffer completeness
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer is not complete!");
        }

        // Unbind
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        initialized = true;
        System.out.println("EditorFramebuffer initialized: " + width + "x" + height);
    }

    /**
     * Resizes the framebuffer to new dimensions.
     * Recreates texture and renderbuffer attachments.
     * 
     * @param newWidth New width in pixels
     * @param newHeight New height in pixels
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }
        
        if (newWidth == this.width && newHeight == this.height) {
            return;
        }

        this.width = newWidth;
        this.height = newHeight;

        if (!initialized) {
            return;
        }

        // Resize color texture
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Resize depth/stencil renderbuffer
        glBindRenderbuffer(GL_RENDERBUFFER, depthRboId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
    }

    /**
     * Binds this framebuffer for rendering.
     * After calling this, all draw calls render to the framebuffer texture.
     */
    public void bind() {
        if (!initialized) {
            init();
        }
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, width, height);
    }

    /**
     * Unbinds this framebuffer, restoring the default framebuffer.
     */
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Clears the framebuffer with the specified color.
     * Call this after binding and before rendering.
     * 
     * @param r Red component (0-1)
     * @param g Green component (0-1)
     * @param b Blue component (0-1)
     * @param a Alpha component (0-1)
     */
    public void clear(float r, float g, float b, float a) {
        glClearColor(r, g, b, a);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Returns the texture ID for rendering in ImGui.
     * Use this with ImGui.image() to display the framebuffer contents.
     */
    public int getTextureId() {
        return textureId;
    }

    /**
     * Returns the framebuffer width.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the framebuffer height.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the aspect ratio (width / height).
     */
    public float getAspectRatio() {
        return (float) width / height;
    }

    /**
     * Checks if the framebuffer is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Destroys the framebuffer and releases OpenGL resources.
     */
    public void destroy() {
        if (!initialized) {
            return;
        }

        glDeleteFramebuffers(fboId);
        glDeleteTextures(textureId);
        glDeleteRenderbuffers(depthRboId);

        fboId = 0;
        textureId = 0;
        depthRboId = 0;
        initialized = false;

        System.out.println("EditorFramebuffer destroyed");
    }
}
