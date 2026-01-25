package com.pocket.rpg.rendering.postfx.effects;

import com.pocket.rpg.rendering.postfx.EffectDescription;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.resources.Shader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Post-processing effect that desaturates (removes color from) the screen.
 * This can create retro, noir, or dramatic visual styles.
 */
@EffectDescription("Removes color saturation for grayscale effects.")
public class DesaturationEffect implements PostEffect {
    private static final float DEFAULT_DESATURATION_AMOUNT = 0.5f;

    private final float desaturationAmount;

    private transient Shader desaturationShader;

    /**
     * Creates a desaturation effect with default parameters.
     * Default applies 50% desaturation for a subtle vintage look.
     */
    public DesaturationEffect() {
        this(DEFAULT_DESATURATION_AMOUNT);
    }

    /**
     * Creates a desaturation effect with specified amount.
     *
     * @param desaturationAmount Amount of desaturation to apply, from 0.0 to 1.0.
     *                           - 0.0 = no desaturation (full color, no effect)
     *                           - 0.5 = 50% desaturation (vintage look)
     *                           - 1.0 = full grayscale (black and white)
     */
    public DesaturationEffect(float desaturationAmount) {
        this.desaturationAmount = desaturationAmount;
    }

    @Override
    public void init() {
        desaturationShader = new Shader("gameData/assets/shaders/desaturation.glsl");
        desaturationShader.compileAndLink();

        desaturationShader.use();
        desaturationShader.uploadInt("screenTexture", 0);
        desaturationShader.uploadFloat("desaturationAmount", this.desaturationAmount);
        desaturationShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        desaturationShader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Clean up state
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        desaturationShader.detach();
    }

    @Override
    public void destroy() {
        desaturationShader.delete();
    }
}