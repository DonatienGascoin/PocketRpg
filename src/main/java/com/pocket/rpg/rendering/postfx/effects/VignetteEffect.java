package com.pocket.rpg.rendering.postfx.effects;

import com.pocket.rpg.rendering.postfx.EffectDescription;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.resources.Shader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Post-processing effect that applies a vignette (darkening at the edges) to the screen.
 * This creates a cinematic focus effect by drawing attention to the center of the screen.
 */
@EffectDescription("Darkens screen edges for cinematic focus, drawing attention to the center.")
public class VignetteEffect implements PostEffect {
    private static final float DEFAULT_VIGNETTE_INTENSITY = 1.0f;
    private static final float DEFAULT_VIGNETTE_STRENGTH = 0.75f;

    private final float vignetteIntensity;
    private final float vignetteStrength;

    private transient Shader vignetteShader;

    /**
     * Creates a vignette effect with default parameters.
     * Default creates a subtle vignette at the corners that darkens by 75%.
     */
    public VignetteEffect() {
        this(DEFAULT_VIGNETTE_INTENSITY, DEFAULT_VIGNETTE_STRENGTH);
    }

    /**
     * Creates a vignette effect with specified parameters.
     *
     * @param vignetteIntensity How far the vignette reaches into the center (lower = only corners).
     *                          Typical range: 0.5 - 2.0
     *                          - 0.5 = very tight, only extreme corners
     *                          - 1.0 = moderate, natural looking (recommended)
     *                          - 2.0 = spreads far into the center
     * @param vignetteStrength  How dark the vignette becomes (0.0 = no darkening, 1.0 = full black).
     *                          Typical range: 0.3 - 1.0
     *                          - 0.3 = subtle darkening
     *                          - 0.75 = noticeable but not extreme (recommended)
     *                          - 1.0 = full black at edges
     */
    public VignetteEffect(float vignetteIntensity, float vignetteStrength) {
        this.vignetteIntensity = vignetteIntensity;
        this.vignetteStrength = vignetteStrength;
    }

    @Override
    public void init() {
        vignetteShader = new Shader("gameData/assets/shaders/vignette.glsl");
        vignetteShader.compileAndLink();

        vignetteShader.use();
        vignetteShader.uploadInt("screenTexture", 0);
        vignetteShader.uploadFloat("vignetteIntensity", this.vignetteIntensity);
        vignetteShader.uploadFloat("vignetteStrength", this.vignetteStrength);
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
        if (vignetteShader != null) {
            vignetteShader.delete();
        }
    }
}