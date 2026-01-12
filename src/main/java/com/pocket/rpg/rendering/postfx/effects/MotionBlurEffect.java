package com.pocket.rpg.rendering.postfx.effects;

import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.resources.Shader;
import org.joml.Vector2f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Motion blur effect that blurs in a specific direction to simulate movement.
 * Great for dash abilities, speed effects, or disorientation.
 */
public class MotionBlurEffect implements PostEffect {
    private static final Vector2f DEFAULT_DIRECTION = new Vector2f(1.0f, 0.0f);
    private static final float DEFAULT_STRENGTH = 0.02f;
    private static final int DEFAULT_SAMPLES = 8;

    private final Vector2f blurDirection;
    private final float blurStrength;
    private final int samples;

    private Shader motionBlurShader;

    /**
     * Creates a motion blur effect with default horizontal blur.
     */
    public MotionBlurEffect() {
        this(DEFAULT_DIRECTION, DEFAULT_STRENGTH, DEFAULT_SAMPLES);
    }

    /**
     * Creates a motion blur effect with specified parameters.
     *
     * @param blurDirection Direction of blur in screen space (normalized).
     *                      Examples:
     *                      - (1, 0) = horizontal right
     *                      - (0, 1) = vertical up
     *                      - (0.707, 0.707) = diagonal
     * @param blurStrength  Length of blur streak (0.01 - 0.1).
     *                      - 0.01 = subtle
     *                      - 0.02 = moderate (recommended)
     *                      - 0.05+ = extreme
     * @param samples       Number of blur samples (4 - 16).
     *                      More samples = smoother blur but slower.
     *                      8 is a good balance.
     */
    public MotionBlurEffect(Vector2f blurDirection, float blurStrength, int samples) {
        this.blurDirection = blurDirection.normalize(new Vector2f());
        this.blurStrength = blurStrength;
        this.samples = samples;
    }

    /**
     * Convenience constructor for direction components.
     */
    public MotionBlurEffect(float dirX, float dirY, float strength, int samples) {
        this(new Vector2f(dirX, dirY), strength, samples);
    }

    @Override
    public void init() {
        motionBlurShader = new Shader("gameData/assets/shaders/motionBlur.glsl");
        motionBlurShader.compileAndLink();

        motionBlurShader.use();
        motionBlurShader.uploadInt("screenTexture", 0);
        motionBlurShader.uploadVec2f("blurDirection", this.blurDirection);
        motionBlurShader.uploadFloat("blurStrength", this.blurStrength);
        motionBlurShader.uploadInt("samples", this.samples);
        motionBlurShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        motionBlurShader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        motionBlurShader.detach();
    }

    @Override
    public void destroy() {
        motionBlurShader.delete();
    }
}
