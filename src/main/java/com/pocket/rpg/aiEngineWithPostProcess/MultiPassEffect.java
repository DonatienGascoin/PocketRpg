package com.pocket.rpg.aiEngineWithPostProcess;

/**
 * Interface for post-processing effects that require multiple rendering passes.
 * Effects implementing this interface will have each pass applied automatically
 * by the PostProcessor.
 */
public interface MultiPassEffect extends PostEffect {

    /**
     * Gets the number of passes this effect requires.
     *
     * @return The number of rendering passes needed (minimum 1)
     */
    int getPassCount();

    /**
     * Applies a specific pass of the effect.
     * This method is called once for each pass, with passIndex ranging from 0 to getPassCount()-1.
     *
     * @param passIndex      The index of the current pass (0-based)
     * @param inputTextureId The ID of the texture containing the previous pass's result
     * @param outputFboId    The ID of the FBO to render the current pass's result into
     * @param quadVAO        The VAO of the full-screen quad
     * @param inputWidth     The fixed internal game width
     * @param inputHeight    The fixed internal game height
     */
    void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO,
                   int inputWidth, int inputHeight);
}