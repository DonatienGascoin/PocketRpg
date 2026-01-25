package com.pocket.rpg.rendering.postfx.effects;

import com.pocket.rpg.rendering.postfx.EffectDescription;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.resources.Shader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Pixelation effect that reduces apparent resolution for a retro pixel art look.
 * Can be scaled dynamically for interesting transition effects.
 */
@EffectDescription("Reduces resolution for a retro pixel art aesthetic.")
public class PixelationEffect implements PostEffect {
    private static final float DEFAULT_PIXEL_SIZE = 0.005f;

    private final float pixelSize;

    private transient Shader pixelationShader;

    /**
     * Creates a pixelation effect with default parameters.
     */
    public PixelationEffect() {
        this(DEFAULT_PIXEL_SIZE);
    }

    /**
     * Creates a pixelation effect with specified pixel size.
     *
     * @param pixelSize Size of pixels in UV space (0.001 - 0.05).
     *                  - 0.001 = very small pixels (minimal effect)
     *                  - 0.005 = moderate pixelation (recommended)
     *                  - 0.01 = large pixels (chunky retro look)
     *                  - 0.02+ = extreme pixelation
     */
    public PixelationEffect(float pixelSize) {
        this.pixelSize = pixelSize;
    }

    @Override
    public void init() {
        pixelationShader = new Shader("gameData/assets/shaders/pixelation.glsl");
        pixelationShader.compileAndLink();

        pixelationShader.use();
        pixelationShader.uploadInt("screenTexture", 0);
        pixelationShader.uploadFloat("pixelSize", this.pixelSize);
        pixelationShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        pixelationShader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        pixelationShader.detach();
    }

    @Override
    public void destroy() {
        pixelationShader.delete();
    }
}
