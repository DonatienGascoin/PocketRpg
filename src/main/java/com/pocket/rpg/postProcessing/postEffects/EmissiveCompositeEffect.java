package com.pocket.rpg.postProcessing.postEffects;

import com.pocket.rpg.engine.Window;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.rendering.Shader;
import org.joml.Vector2f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Post-processing effect that composites emissive (glowing) sprites with bloom
 * onto the main scene.
 *
 * STRATEGY 2: Emissive Masking
 * This effect should be used with EmissiveRenderer to create efficient
 * per-sprite bloom for many glowing objects.
 *
 * Usage:
 * 1. Mark sprites as emissive using SpriteRenderer.setEmissive(true)
 * 2. Render normal scene
 * 3. Render emissive sprites to separate buffer (EmissiveRenderer handles this)
 * 4. Apply this effect to composite the blurred emissive layer
 */
public class EmissiveCompositeEffect implements PostEffect {

    private static final float DEFAULT_BLOOM_INTENSITY = 1.5f;

    private final float bloomIntensity;
    private int emissiveTextureId = -1;

    private Shader compositeShader;

    /**
     * Creates an emissive composite effect with default bloom intensity.
     */
    public EmissiveCompositeEffect() {
        this(DEFAULT_BLOOM_INTENSITY);
    }

    /**
     * Creates an emissive composite effect with specified bloom intensity.
     *
     * @param bloomIntensity Strength of the emissive glow (0.5 - 3.0)
     *                       1.0 = normal, 2.0 = strong glow
     */
    public EmissiveCompositeEffect(float bloomIntensity) {
        this.bloomIntensity = bloomIntensity;
    }

    /**
     * Sets the emissive texture to composite.
     * This should be called before applying the effect, typically from
     * the post-processor or scene renderer.
     *
     * @param textureId The GL texture ID of the emissive buffer
     */
    public void setEmissiveTexture(int textureId) {
        this.emissiveTextureId = textureId;
    }

    @Override
    public void init(Window window) {
        compositeShader = new Shader("assets/shaders/emissiveComposite.glsl");
        compositeShader.compileAndLink();

        compositeShader.use();
        compositeShader.uploadInt("mainTexture", 0);
        compositeShader.uploadInt("emissiveTexture", 1);
        compositeShader.uploadFloat("bloomIntensity", bloomIntensity);
        compositeShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId,
                          int quadVAO, int inputWidth, int inputHeight) {

        // If no emissive texture is set, just pass through
        if (emissiveTextureId < 0) {
            // Simple passthrough
            glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);

            // Just copy input to output without compositing
            Shader passthroughShader = new Shader("assets/shaders/passThrough.glsl");
            passthroughShader.compileAndLink();
            passthroughShader.use();
            passthroughShader.uploadInt("screenTexture", 0);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, inputTextureId);

            glBindVertexArray(quadVAO);
            glDrawArrays(GL_TRIANGLES, 0, 6);

            glBindTexture(GL_TEXTURE_2D, 0);
            glBindVertexArray(0);
            passthroughShader.detach();
            passthroughShader.delete();
            return;
        }

        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        compositeShader.use();

        // Upload texel size for blur
        compositeShader.uploadVec2f("texelSize",
                new Vector2f(1.0f / inputWidth, 1.0f / inputHeight));

        // Bind main scene texture to unit 0
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        // Bind emissive texture to unit 1
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, emissiveTextureId);

        // Render fullscreen quad
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Cleanup
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        compositeShader.detach();
    }

    @Override
    public void destroy() {
        if (compositeShader != null) {
            compositeShader.delete();
        }
    }
}