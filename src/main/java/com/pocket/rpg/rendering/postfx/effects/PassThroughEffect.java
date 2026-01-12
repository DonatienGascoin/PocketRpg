package com.pocket.rpg.rendering.postfx.effects;

import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.resources.Shader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Simple pass-through effect that renders the input texture without modification.
 * Useful as a final blit step or for debugging the post-processing pipeline.
 * <p>
 * Note: This was previously named PillarboxEffect, but pillarboxing is actually
 * handled by the PostProcessor's viewport management, not by this effect.
 */
public class PassThroughEffect implements PostEffect {

    private Shader passThroughShader;

    @Override
    public void init() {
        passThroughShader = new Shader("gameData/assets/shaders/passThrough.glsl");
        passThroughShader.compileAndLink();
        passThroughShader.use();
        passThroughShader.uploadInt("screenTexture", 0);
        passThroughShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO,
                          int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);

        passThroughShader.use();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Cleanup
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        passThroughShader.detach();
    }

    @Override
    public void destroy() {
        passThroughShader.delete();
    }
}