package com.pocket.rpg.rendering.postfx.effects;

import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.resources.Shader;
import org.joml.Vector2f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Screen displacement effect that distorts the screen with waves and directional shake.
 * More sophisticated than basic camera shake - creates ripple and distortion effects.
 * Perfect for impacts, explosions, or environmental disturbances.
 */
public class DisplacementEffect implements PostEffect {
    private static final float DEFAULT_STRENGTH = 0.005f;
    private static final Vector2f DEFAULT_SHAKE_DIR = new Vector2f(0.0f, 0.0f);

    private final float displacementStrength;
    private final Vector2f shakeDirection;

    private Shader displacementShader;
    private float time = 0.0f;

    /**
     * Creates a displacement effect with wave distortion only.
     */
    public DisplacementEffect() {
        this(DEFAULT_STRENGTH, DEFAULT_SHAKE_DIR);
    }

    /**
     * Creates a displacement effect with specified strength.
     *
     * @param displacementStrength Overall strength of distortion (0.001 - 0.02).
     *                             - 0.001 = very subtle waves
     *                             - 0.005 = noticeable (recommended)
     *                             - 0.01+ = strong distortion
     */
    public DisplacementEffect(float displacementStrength) {
        this(displacementStrength, DEFAULT_SHAKE_DIR);
    }

    /**
     * Creates a displacement effect with waves and directional shake.
     *
     * @param displacementStrength Strength of wave distortion (0.001 - 0.02).
     * @param shakeDirection       Direction and strength of shake (x, y).
     *                             Set to (0, 0) for waves only.
     *                             Examples:
     *                             - (0.002, 0) = horizontal shake
     *                             - (0, 0.002) = vertical shake
     *                             - (0.001, 0.001) = diagonal shake
     */
    public DisplacementEffect(float displacementStrength, Vector2f shakeDirection) {
        this.displacementStrength = displacementStrength;
        this.shakeDirection = shakeDirection;
    }

    /**
     * Convenience constructor for shake direction components.
     */
    public DisplacementEffect(float strength, float shakeX, float shakeY) {
        this(strength, new Vector2f(shakeX, shakeY));
    }

    @Override
    public void init() {
        displacementShader = new Shader("gameData/assets/shaders/displacement.glsl");
        displacementShader.compileAndLink();

        displacementShader.use();
        displacementShader.uploadInt("screenTexture", 0);
        displacementShader.uploadFloat("displacementStrength", this.displacementStrength);
        displacementShader.uploadVec2f("shakeDirection", this.shakeDirection);
        displacementShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        displacementShader.use();

        // Update time for animated waves
        time += 0.016f; // Approximate frame time
        displacementShader.uploadFloat("time", time);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        displacementShader.detach();
    }

    @Override
    public void destroy() {
        displacementShader.delete();
    }
}
