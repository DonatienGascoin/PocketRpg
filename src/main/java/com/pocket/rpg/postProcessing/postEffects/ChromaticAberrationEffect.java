package com.pocket.rpg.postProcessing.postEffects;

import com.pocket.rpg.core.AbstractWindow;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.rendering.Shader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Chromatic aberration effect that splits RGB channels to simulate lens distortion.
 * Creates a cinematic or retro camera feel, especially at screen edges.
 * Great for impact moments or corrupted/damaged visuals.
 */
public class ChromaticAberrationEffect implements PostEffect {
    private static final float DEFAULT_STRENGTH = 0.005f;

    private final float aberrationStrength;

    private Shader aberrationShader;

    /**
     * Creates a chromatic aberration effect with default parameters.
     */
    public ChromaticAberrationEffect() {
        this(DEFAULT_STRENGTH);
    }

    /**
     * Creates a chromatic aberration effect with specified strength.
     *
     * @param aberrationStrength Strength of the color channel separation (0.0 - 0.02).
     *                          - 0.002 = subtle effect
     *                          - 0.005 = noticeable (recommended)
     *                          - 0.01+ = extreme distortion
     */
    public ChromaticAberrationEffect(float aberrationStrength) {
        this.aberrationStrength = aberrationStrength;
    }

    @Override
    public void init() {
        aberrationShader = new Shader("gameData/assets/shaders/chromaticAberration.glsl");
        aberrationShader.compileAndLink();

        aberrationShader.use();
        aberrationShader.uploadInt("screenTexture", 0);
        aberrationShader.uploadFloat("aberrationStrength", this.aberrationStrength);
        aberrationShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        aberrationShader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        aberrationShader.detach();
    }

    @Override
    public void destroy() {
        aberrationShader.delete();
    }
}
