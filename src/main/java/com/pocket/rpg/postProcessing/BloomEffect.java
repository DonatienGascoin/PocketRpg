package com.pocket.rpg.postProcessing;

import com.pocket.rpg.engine.Window;
import com.pocket.rpg.rendering.Shader;
import org.joml.Vector2f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Bloom effect that makes bright areas glow by extracting bright pixels
 * and blurring them, then adding them back to the original image.
 * This creates a dreamy, ethereal look perfect for magic effects and glowing objects.
 */
public class BloomEffect implements PostEffect {
    private static final float DEFAULT_THRESHOLD = 0.8f;
    private static final float DEFAULT_INTENSITY = 1.5f;

    private final float bloomThreshold;
    private final float bloomIntensity;

    private Shader bloomShader;

    /**
     * Creates a bloom effect with default parameters.
     */
    public BloomEffect() {
        this(DEFAULT_THRESHOLD, DEFAULT_INTENSITY);
    }

    /**
     * Creates a bloom effect with specified parameters.
     *
     * @param bloomThreshold Brightness threshold for bloom (0.0 - 1.0).
     *                       Only pixels brighter than this will glow.
     *                       Lower values = more bloom. Typical: 0.8
     * @param bloomIntensity Strength of the bloom glow (0.0 - 3.0).
     *                       Higher values = stronger glow. Typical: 1.5
     */
    public BloomEffect(float bloomThreshold, float bloomIntensity) {
        this.bloomThreshold = bloomThreshold;
        this.bloomIntensity = bloomIntensity;
    }

    @Override
    public void init(Window window) {
        bloomShader = new Shader("assets/shaders/bloom.glsl");
        bloomShader.compileAndLink();

        bloomShader.use();
        bloomShader.uploadInt("screenTexture", 0);
        bloomShader.uploadFloat("bloomThreshold", this.bloomThreshold);
        bloomShader.uploadFloat("bloomIntensity", this.bloomIntensity);
        bloomShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        bloomShader.use();

        // Set horizontal/vertical pass
        bloomShader.uploadBoolean("horizontal", passIndex == 1);
        bloomShader.uploadVec2f("texelSize", new Vector2f(1.0f / inputWidth, 1.0f / inputHeight));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        bloomShader.detach();
    }

    @Override
    public void destroy() {
        bloomShader.delete();
    }

    /**
     * Bloom requires 2 passes: vertical blur then horizontal blur.
     */
    @Override
    public int getPassCount() {
        return 2;
    }
}
