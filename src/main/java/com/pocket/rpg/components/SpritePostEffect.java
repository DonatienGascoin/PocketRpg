package com.pocket.rpg.components;

import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.rendering.Renderer;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Component that applies post-processing effects to a single sprite.
 * The sprite is rendered to an offscreen framebuffer, effects are applied,
 * then the result is composited back to the scene.
 */
public class SpritePostEffect extends Component {

    @Getter
    private final List<PostEffect> effects = new ArrayList<>();

    // Framebuffers for ping-pong rendering
    private int fbo1, fbo2;
    private int texture1, texture2;
    private int quadVAO;

    // Size of the effect buffer (should match sprite bounds + padding for bloom)
    @Setter
    @Getter
    private int bufferWidth = 256;

    @Setter
    @Getter
    private int bufferHeight = 256;

    @Setter
    @Getter
    private float padding = 64; // Extra pixels around sprite for bloom bleed

    private boolean initialized = false;

    /**
     * Adds a post-processing effect to this sprite.
     */
    public void addEffect(PostEffect effect) {
        effects.add(effect);
    }

    /**
     * Removes a post-processing effect.
     */
    public void removeEffect(PostEffect effect) {
        effects.remove(effect);
    }

    /**
     * Clears all effects.
     */
    public void clearEffects() {
        effects.clear();
    }

    @Override
    public void start() {
        super.start();
        initFramebuffers();

        // Initialize all effects
        for (PostEffect effect : effects) {
            effect.init(null); // Effects don't need window for sprite-level processing
        }

        initialized = true;
    }

    /**
     * Initializes framebuffers for ping-pong rendering.
     */
    private void initFramebuffers() {
        // Create quad for rendering effects
        quadVAO = createQuad();

        // Create two framebuffers for ping-pong
        fbo1 = createFramebuffer();
        texture1 = createTexture(bufferWidth, bufferHeight);
        attachTextureToFramebuffer(fbo1, texture1);

        fbo2 = createFramebuffer();
        texture2 = createTexture(bufferWidth, bufferHeight);
        attachTextureToFramebuffer(fbo2, texture2);

        // Verify framebuffers
        glBindFramebuffer(GL_FRAMEBUFFER, fbo1);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer 1 is not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, fbo2);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer 2 is not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Renders the sprite with effects applied.
     * Call this from your custom rendering loop.
     */
    public void renderWithEffects(Renderer renderer, SpriteRenderer spriteRenderer) {
        if (!initialized || effects.isEmpty()) {
            // No effects, render normally
            renderer.drawSpriteRenderer(spriteRenderer);
            return;
        }

        // Step 1: Render sprite to first framebuffer
        glBindFramebuffer(GL_FRAMEBUFFER, fbo1);
        glClearColor(0, 0, 0, 0); // Transparent background
        glClear(GL_COLOR_BUFFER_BIT);

        // Calculate offset to center sprite in buffer
        Transform transform = gameObject.getTransform();
        Vector2f originalPos = new Vector2f(transform.getPosition().x, transform.getPosition().y);

        // Temporarily move sprite to center of buffer
        transform.getPosition().x = bufferWidth / 2.0f;
        transform.getPosition().y = bufferHeight / 2.0f;

        renderer.drawSpriteRenderer(spriteRenderer);

        // Restore original position
        transform.getPosition().x = originalPos.x;
        transform.getPosition().y = originalPos.y;

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Step 2: Apply effects using ping-pong rendering
        int currentInput = texture1;
        int currentOutput = fbo2;
        int currentOutputTex = texture2;

        for (PostEffect effect : effects) {
            int passCount = effect.getPassCount();

            for (int pass = 0; pass < passCount; pass++) {
                effect.applyPass(pass, currentInput, currentOutput, quadVAO, bufferWidth, bufferHeight);

                // Swap for next pass
                if (currentInput == texture1) {
                    currentInput = texture2;
                    currentOutput = fbo1;
                    currentOutputTex = texture1;
                } else {
                    currentInput = texture1;
                    currentOutput = fbo2;
                    currentOutputTex = texture2;
                }
            }
        }

        // Step 3: Render final result to screen at sprite's actual position
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Render the processed texture as a quad at the sprite's position
        renderTexturedQuad(currentInput, originalPos.x, originalPos.y,
                bufferWidth, bufferHeight, transform.getRotation().z);
    }

    /**
     * Renders a textured quad at the specified position.
     */
    private void renderTexturedQuad(int texture, float x, float y, float width, float height, float rotation) {
        // This is a simplified version - you'd want to use your renderer's shader
        // For now, this shows the concept

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);

        // Enable blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * Creates a framebuffer object.
     */
    private int createFramebuffer() {
        return glGenFramebuffers();
    }

    /**
     * Creates a texture for the framebuffer.
     */
    private int createTexture(int width, int height) {
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return textureId;
    }

    /**
     * Attaches a texture to a framebuffer.
     */
    private void attachTextureToFramebuffer(int fbo, int texture) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Creates a simple quad VAO.
     */
    private int createQuad() {
        float[] vertices = {
                // Positions    // UVs
                -1.0f,  1.0f,  0.0f, 1.0f,
                -1.0f, -1.0f,  0.0f, 0.0f,
                1.0f, -1.0f,  1.0f, 0.0f,

                -1.0f,  1.0f,  0.0f, 1.0f,
                1.0f, -1.0f,  1.0f, 0.0f,
                1.0f,  1.0f,  1.0f, 1.0f
        };

        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        glBindVertexArray(0);

        return vao;
    }

    @Override
    public void destroy() {
        super.destroy();

        // Clean up framebuffers and textures
        if (fbo1 != 0) glDeleteFramebuffers(fbo1);
        if (fbo2 != 0) glDeleteFramebuffers(fbo2);
        if (texture1 != 0) glDeleteTextures(texture1);
        if (texture2 != 0) glDeleteTextures(texture2);
        if (quadVAO != 0) glDeleteVertexArrays(quadVAO);

        // Destroy effects
        for (PostEffect effect : effects) {
            effect.destroy();
        }
    }
}