package com.pocket.rpg.postProcessing;

import com.pocket.rpg.rendering.Shader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Post-processing effect that applies both vignetting and desaturation to create
 * a cinematic or retro appearance.
 */
public class ColorVignetteEffect implements PostEffect {
    private static final float DEFAULT_VIGNETTE_INTENSITY = 1.5f;
    private static final float DEFAULT_DESATURATION_AMOUNT = 0.5f;

    private final float vignetteIntensity;
    private final float desaturationAmount;

    private Shader vignetteShader;

    /**
     * Creates a color vignette effect with default parameters.
     */
    public ColorVignetteEffect() {
        this(DEFAULT_VIGNETTE_INTENSITY, DEFAULT_DESATURATION_AMOUNT);
    }

    /**
     * Creates a color vignette effect with specified parameters.
     *
     * @param vignetteIntensity  Multiplier for how far the vignette reaches into the center.
     *                           Higher values create a more dramatic darkening effect (e.g., 1.5).
     * @param desaturationAmount Amount of desaturation to apply, from 0.0 (no desaturation)
     *                           to 1.0 (full grayscale). Typical value is 0.5 for 50% desaturation.
     */
    public ColorVignetteEffect(float vignetteIntensity, float desaturationAmount) {
        this.vignetteIntensity = vignetteIntensity;
        this.desaturationAmount = desaturationAmount;
    }

    @Override
    public void init() {
        vignetteShader = new Shader("assets/shaders/colorVignette.glsl");
        vignetteShader.compileAndLink();

        vignetteShader.use();
        vignetteShader.uploadInt("screenTexture", 0);
        vignetteShader.uploadFloat("vignetteIntensity", this.vignetteIntensity);
        vignetteShader.uploadFloat("desaturationAmount", this.desaturationAmount);
        vignetteShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        vignetteShader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Clean up state
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        vignetteShader.detach();
    }

    @Override
    public void destroy() {
        vignetteShader.delete();
    }

}