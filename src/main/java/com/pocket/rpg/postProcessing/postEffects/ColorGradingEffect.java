package com.pocket.rpg.postProcessing.postEffects;

import com.pocket.rpg.engine.Window;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.rendering.Shader;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Color grading/tint effect that shifts the entire screen toward a specific color.
 * Perfect for environmental storytelling (red for danger, blue for cold, etc.)
 * or indicating player status effects.
 */
public class ColorGradingEffect implements PostEffect {
    private static final Vector3f DEFAULT_TINT = new Vector3f(1.0f, 0.8f, 0.8f); // Warm tint
    private static final float DEFAULT_STRENGTH = 0.3f;

    private final Vector3f tintColor;
    private final float tintStrength;

    private Shader gradingShader;

    /**
     * Creates a color grading effect with default warm tint.
     */
    public ColorGradingEffect() {
        this(DEFAULT_TINT, DEFAULT_STRENGTH);
    }

    /**
     * Creates a color grading effect with specified color and strength.
     *
     * @param tintColor    RGB color to tint toward (0.0 - 1.0 per channel).
     *                     Examples:
     *                     - (1.0, 0.3, 0.3) = red tint for danger/damage
     *                     - (0.3, 0.5, 1.0) = blue tint for cold/ice
     *                     - (0.5, 1.0, 0.3) = green tint for poison
     *                     - (1.0, 0.8, 0.5) = warm/sepia tone
     * @param tintStrength How much to apply the tint (0.0 - 1.0).
     *                     - 0.0 = no tint
     *                     - 0.3 = subtle (recommended)
     *                     - 0.7 = strong
     */
    public ColorGradingEffect(Vector3f tintColor, float tintStrength) {
        this.tintColor = tintColor;
        this.tintStrength = tintStrength;
    }

    /**
     * Convenience constructor for RGB values.
     */
    public ColorGradingEffect(float r, float g, float b, float strength) {
        this(new Vector3f(r, g, b), strength);
    }

    @Override
    public void init(Window window) {
        gradingShader = new Shader("assets/shaders/colorGrading.glsl");
        gradingShader.compileAndLink();

        gradingShader.use();
        gradingShader.uploadInt("screenTexture", 0);
        gradingShader.uploadVec3f("tintColor", this.tintColor);
        gradingShader.uploadFloat("tintStrength", this.tintStrength);
        gradingShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        gradingShader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        gradingShader.detach();
    }

    @Override
    public void destroy() {
        gradingShader.delete();
    }
}
