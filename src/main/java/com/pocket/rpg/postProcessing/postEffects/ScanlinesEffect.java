package com.pocket.rpg.postProcessing.postEffects;

import com.pocket.rpg.engine.Window;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.rendering.Shader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Scanlines effect that adds horizontal lines across the screen to simulate
 * a CRT monitor or retro display. Perfect for vintage aesthetics.
 */
public class ScanlinesEffect implements PostEffect {
    private static final float DEFAULT_INTENSITY = 0.3f;
    private static final float DEFAULT_COUNT = 300.0f;

    private final float scanlineIntensity;
    private final float scanlineCount;

    private Shader scanlinesShader;

    /**
     * Creates a scanlines effect with default parameters.
     */
    public ScanlinesEffect() {
        this(DEFAULT_INTENSITY, DEFAULT_COUNT);
    }

    /**
     * Creates a scanlines effect with specified parameters.
     *
     * @param scanlineIntensity Darkness of the scanlines (0.0 - 1.0).
     *                         - 0.0 = no effect
     *                         - 0.3 = subtle (recommended)
     *                         - 0.7 = pronounced
     * @param scanlineCount    Number of scanlines across the screen.
     *                         - 150 = thick lines
     *                         - 300 = normal (recommended)
     *                         - 600 = thin lines
     */
    public ScanlinesEffect(float scanlineIntensity, float scanlineCount) {
        this.scanlineIntensity = scanlineIntensity;
        this.scanlineCount = scanlineCount;
    }

    @Override
    public void init(Window window) {
        scanlinesShader = new Shader("assets/shaders/scanlines.glsl");
        scanlinesShader.compileAndLink();

        scanlinesShader.use();
        scanlinesShader.uploadInt("screenTexture", 0);
        scanlinesShader.uploadFloat("scanlineIntensity", this.scanlineIntensity);
        scanlinesShader.uploadFloat("scanlineCount", this.scanlineCount);
        scanlinesShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        scanlinesShader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        scanlinesShader.detach();
    }

    @Override
    public void destroy() {
        scanlinesShader.delete();
    }
}
