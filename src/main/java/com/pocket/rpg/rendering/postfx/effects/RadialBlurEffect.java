package com.pocket.rpg.rendering.postfx.effects;

import com.pocket.rpg.rendering.postfx.EffectDescription;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.resources.Shader;
import org.joml.Vector2f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Radial blur effect that blurs outward from a center point.
 * Perfect for speed effects, explosions, or dramatic focus moments.
 * Creates a sense of motion radiating from the center.
 */
@EffectDescription("Blurs radially from a center point for zoom effects.")
public class RadialBlurEffect implements PostEffect {
    private static final Vector2f DEFAULT_CENTER = new Vector2f(0.5f, 0.5f);
    private static final float DEFAULT_STRENGTH = 0.03f;
    private static final int DEFAULT_SAMPLES = 10;

    private final Vector2f blurCenter;
    private final float blurStrength;
    private final int samples;

    private transient Shader radialBlurShader;

    /**
     * Creates a radial blur effect centered on the screen.
     */
    public RadialBlurEffect() {
        this(DEFAULT_CENTER, DEFAULT_STRENGTH, DEFAULT_SAMPLES);
    }

    /**
     * Creates a radial blur effect with specified parameters.
     *
     * @param blurCenter   Center point of the blur in UV space (0.0 - 1.0).
     *                     (0.5, 0.5) = screen center
     *                     (0, 0) = bottom-left corner
     *                     (1, 1) = top-right corner
     * @param blurStrength Intensity of the radial blur (0.01 - 0.1).
     *                     - 0.01 = subtle
     *                     - 0.03 = moderate (recommended)
     *                     - 0.07+ = extreme
     * @param samples      Number of blur samples (6 - 16).
     *                     More samples = smoother blur but slower.
     *                     10 is recommended.
     */
    public RadialBlurEffect(Vector2f blurCenter, float blurStrength, int samples) {
        this.blurCenter = blurCenter;
        this.blurStrength = blurStrength;
        this.samples = samples;
    }

    /**
     * Convenience constructor for center coordinates.
     */
    public RadialBlurEffect(float centerX, float centerY, float strength, int samples) {
        this(new Vector2f(centerX, centerY), strength, samples);
    }

    @Override
    public void init() {
        radialBlurShader = new Shader("gameData/assets/shaders/radialBlur.glsl");
        radialBlurShader.compileAndLink();

        radialBlurShader.use();
        radialBlurShader.uploadInt("screenTexture", 0);
        radialBlurShader.uploadVec2f("blurCenter", this.blurCenter);
        radialBlurShader.uploadFloat("blurStrength", this.blurStrength);
        radialBlurShader.uploadInt("samples", this.samples);
        radialBlurShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        radialBlurShader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        radialBlurShader.detach();
    }

    @Override
    public void destroy() {
        radialBlurShader.delete();
    }
}
