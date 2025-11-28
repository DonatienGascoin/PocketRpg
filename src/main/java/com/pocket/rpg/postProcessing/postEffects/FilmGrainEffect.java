package com.pocket.rpg.postProcessing.postEffects;

import com.pocket.rpg.core.AbstractWindow;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.rendering.Shader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Film grain effect that adds subtle noise/texture to create a vintage film
 * or security camera aesthetic. The grain animates over time for realism.
 */
public class FilmGrainEffect implements PostEffect {
    private static final float DEFAULT_INTENSITY = 0.05f;

    private final float grainIntensity;

    private Shader grainShader;
    private float time = 0.0f;

    /**
     * Creates a film grain effect with default parameters.
     */
    public FilmGrainEffect() {
        this(DEFAULT_INTENSITY);
    }

    /**
     * Creates a film grain effect with specified intensity.
     *
     * @param grainIntensity Strength of the grain noise (0.0 - 0.2).
     *                       - 0.02 = very subtle
     *                       - 0.05 = noticeable (recommended)
     *                       - 0.1+ = heavy grain
     */
    public FilmGrainEffect(float grainIntensity) {
        this.grainIntensity = grainIntensity;
    }

    @Override
    public void init() {
        grainShader = new Shader("gameData/assets/shaders/filmGrain.glsl");
        grainShader.compileAndLink();

        grainShader.use();
        grainShader.uploadInt("screenTexture", 0);
        grainShader.uploadFloat("grainIntensity", this.grainIntensity);
        grainShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        grainShader.use();

        // Update time for animated grain
        time += 0.016f; // Approximate frame time
        grainShader.uploadFloat("time", time);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        grainShader.detach();
    }

    @Override
    public void destroy() {
        grainShader.delete();
    }
}
