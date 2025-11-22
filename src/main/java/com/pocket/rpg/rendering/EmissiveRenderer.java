package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Extended renderer that supports emissive (glowing) sprites.
 * <p>
 * STRATEGY 2: Emissive Masking
 * Best for: Many glowing sprites (particles, magic effects, UI highlights)
 * Performance: ~1-2ms total for unlimited emissive sprites
 * <p>
 * This renderer maintains a separate FBO for emissive sprites that can later
 * be processed with bloom and composited onto the main scene.
 */
public class EmissiveRenderer extends Renderer {

    // Emissive rendering resources
    private int emissiveFBO;
    private int emissiveTexture;
    private int emissiveRBO;

    // Track emissive sprites separately
    private final List<SpriteRenderer> emissiveSprites = new ArrayList<>();

    private int viewportWidth;
    private int viewportHeight;

    private boolean emissiveInitialized = false;

    @Override
    public void init(int viewportWidth, int viewportHeight) {
        super.init(viewportWidth, viewportHeight);
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        initEmissiveBuffer();
    }

    /**
     * Initializes the emissive framebuffer for rendering glowing sprites.
     */
    private void initEmissiveBuffer() {
        // Create FBO
        emissiveFBO = glGenFramebuffers();

        // Create texture attachment
        emissiveTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, emissiveTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, viewportWidth, viewportHeight, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Create depth/stencil renderbuffer
        emissiveRBO = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, emissiveRBO);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, viewportWidth, viewportHeight);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        // Attach to FBO
        glBindFramebuffer(GL_FRAMEBUFFER, emissiveFBO);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, emissiveTexture, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, emissiveRBO);

        // Check FBO completeness
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Emissive framebuffer is not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        emissiveInitialized = true;
    }

    /**
     * Registers a sprite as emissive (will be rendered to emissive buffer).
     */
    public void registerEmissiveSprite(SpriteRenderer sprite) {
        if (!emissiveSprites.contains(sprite)) {
            emissiveSprites.add(sprite);
        }
    }

    /**
     * Unregisters an emissive sprite.
     */
    public void unregisterEmissiveSprite(SpriteRenderer sprite) {
        emissiveSprites.remove(sprite);
    }

    /**
     * Begins the emissive rendering pass.
     * Call this after rendering normal sprites but before post-processing.
     */
    public void beginEmissivePass() {
        if (!emissiveInitialized) return;

        glBindFramebuffer(GL_FRAMEBUFFER, emissiveFBO);
        glViewport(0, 0, viewportWidth, viewportHeight);
        glClearColor(0, 0, 0, 0); // Transparent black
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Ends the emissive rendering pass.
     */
    public void endEmissivePass() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Renders all registered emissive sprites to the emissive buffer.
     * Call this between beginEmissivePass() and endEmissivePass().
     */
    public void renderEmissiveSprites() {
        if (!emissiveInitialized || emissiveSprites.isEmpty()) return;

        begin();

        for (SpriteRenderer sprite : emissiveSprites) {
            if (sprite.isEnabled() &&
                    sprite.getSprite() != null &&
                    sprite.getGameObject() != null &&
                    sprite.getGameObject().isEnabled()) {
                drawSpriteRenderer(sprite);
            }
        }

        end();
    }

    /**
     * Renders all registered emissive sprites with camera support.
     */
    public void renderEmissiveSpritesWithCamera(Camera camera) {
        if (!emissiveInitialized || emissiveSprites.isEmpty()) return;

        // Don't clear here - that's done in beginEmissivePass
        if (camera != null) {
            setViewMatrix(camera.getViewMatrix());
        } else {
            resetView();
        }

        begin();

        for (SpriteRenderer sprite : emissiveSprites) {
            if (sprite.isEnabled() &&
                    sprite.getSprite() != null &&
                    sprite.getGameObject() != null &&
                    sprite.getGameObject().isEnabled()) {
                drawSpriteRenderer(sprite);
            }
        }

        end();
    }

    /**
     * Gets the emissive texture containing all glowing sprites.
     * This can be processed with bloom effects.
     */
    public int getEmissiveTexture() {
        return emissiveTexture;
    }

    /**
     * Gets the emissive FBO ID.
     */
    public int getEmissiveFBO() {
        return emissiveFBO;
    }

    /**
     * Clears all registered emissive sprites.
     */
    public void clearEmissiveSprites() {
        emissiveSprites.clear();
    }

    /**
     * Gets the number of registered emissive sprites.
     */
    public int getEmissiveSpriteCount() {
        return emissiveSprites.size();
    }

    @Override
    public void destroy() {
        super.destroy();

        if (emissiveFBO != 0) glDeleteFramebuffers(emissiveFBO);
        if (emissiveTexture != 0) glDeleteTextures(emissiveTexture);
        if (emissiveRBO != 0) glDeleteRenderbuffers(emissiveRBO);

        emissiveSprites.clear();
    }
}