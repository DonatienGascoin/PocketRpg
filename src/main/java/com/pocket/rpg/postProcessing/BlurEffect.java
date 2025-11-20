package com.pocket.rpg.postProcessing;

import com.pocket.rpg.engine.Window;
import com.pocket.rpg.rendering.Shader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Two-pass Gaussian blur effect implementation.
 * Applies horizontal and vertical blur passes for optimal performance.
 */
public class BlurEffect implements PostEffect {
    private static final float DEFAULT_BLUR_STRENGTH = 2.0f;
    private static final int PASS_COUNT = 2;

    // Sampling offsets for the blur kernel
    private static final float NEAR_SAMPLE_OFFSET = 1.5f;  // Distance for near samples
    private static final float FAR_SAMPLE_OFFSET = 3.0f;   // Distance for far samples
    private static final float SAMPLE_COUNT = 5.0f;        // Total samples (1 center + 4 offset)

    private final float blurStrength;

    private Shader blurShader;

    /**
     * Creates a blur effect with the specified strength.
     *
     * @param blurStrength Multiplier for blur sampling distance (higher = more blur)
     */
    public BlurEffect(float blurStrength) {
        this.blurStrength = blurStrength;
    }

    /**
     * Creates a blur effect with default strength.
     */
    public BlurEffect() {
        this(DEFAULT_BLUR_STRENGTH);
    }

    @Override
    public void init(Window window) {
        blurShader = new Shader("assets/shaders/blurShader.glsl");
        blurShader.compileAndLink();
        blurShader.use();
        blurShader.uploadInt("screenTexture", 0);
        blurShader.uploadFloat("blurStrength", blurStrength);
        blurShader.uploadFloat("nearSampleOffset", NEAR_SAMPLE_OFFSET);
        blurShader.uploadFloat("farSampleOffset", FAR_SAMPLE_OFFSET);
        blurShader.uploadFloat("sampleCount", SAMPLE_COUNT);
        blurShader.detach();
    }

    @Override
    public int getPassCount() {
        return PASS_COUNT;
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO,
                          int inputWidth, int inputHeight) {
        // Pass 0: Horizontal blur
        // Pass 1: Vertical blur
        float dirX = (passIndex == 0) ? 1.0f : 0.0f;
        float dirY = (passIndex == 1) ? 1.0f : 0.0f;

        applyPassInternal(inputTextureId, outputFboId, quadVAO, inputWidth, inputHeight, dirX, dirY);
    }

    /**
     * Internal method to apply a blur pass in the specified direction.
     *
     * @param dirX Horizontal direction (1.0 for horizontal pass, 0.0 for vertical)
     * @param dirY Vertical direction (0.0 for horizontal pass, 1.0 for vertical)
     */
    private void applyPassInternal(int inputTextureId, int outputFboId, int quadVAO,
                                   int inputWidth, int inputHeight, float dirX, float dirY) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        blurShader.use();

        blurShader.uploadVec2f("direction", dirX, dirY);
        blurShader.uploadVec2f("texelSize", 1.0f / inputWidth, 1.0f / inputHeight);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Cleanup
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        blurShader.detach();
    }

    @Override
    public void destroy() {
        blurShader.delete();
    }
}