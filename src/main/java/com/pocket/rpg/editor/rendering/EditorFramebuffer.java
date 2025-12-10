package com.pocket.rpg.editor.rendering;

import lombok.Getter;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL framebuffer for rendering the scene in the editor.
 * 
 * The scene is rendered to this framebuffer, then displayed
 * as an ImGui image in the viewport panel.
 */
public class EditorFramebuffer {

    @Getter
    private int width;
    
    @Getter
    private int height;
    
    @Getter
    private int fboId;
    
    @Getter
    private int textureId;
    
    private int rboId; // Depth/stencil renderbuffer
    
    @Getter
    private boolean initialized = false;

    /**
     * Creates a framebuffer with initial dimensions.
     */
    public EditorFramebuffer(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    /**
     * Initializes OpenGL resources.
     */
    public void init() {
        if (initialized) return;
        
        // Create framebuffer
        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        
        // Create color texture
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, 
            GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureId, 0);
        
        // Create depth/stencil renderbuffer
        rboId = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rboId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, 
            GL_RENDERBUFFER, rboId);
        
        // Check completeness
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
     * Resizes the framebuffer.
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) return;
        if (newWidth == width && newHeight == height) return;
        
        this.width = newWidth;
        this.height = newHeight;
        
        if (!initialized) {
            init();
            return;
        }
        
        // Resize texture
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, 0);
        
        // Resize renderbuffer
        glBindRenderbuffer(GL_RENDERBUFFER, rboId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
    }

    /**
     * Binds the framebuffer for rendering.
     */
    public void bind() {
        if (!initialized) {
            init();
        }
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, width, height);
    }

    /**
     * Unbinds the framebuffer (returns to default).
     */
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Clears the framebuffer with the specified color.
     */
    public void clear(float r, float g, float b, float a) {
        glClearColor(r, g, b, a);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Destroys OpenGL resources.
     */
    public void destroy() {
        if (!initialized) return;
        
        glDeleteFramebuffers(fboId);
        glDeleteTextures(textureId);
        glDeleteRenderbuffers(rboId);
        
        fboId = 0;
        textureId = 0;
        rboId = 0;
        initialized = false;
        
        System.out.println("EditorFramebuffer destroyed");
    }
}
