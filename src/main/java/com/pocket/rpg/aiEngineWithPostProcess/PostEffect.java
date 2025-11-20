package com.pocket.rpg.aiEngineWithPostProcess;

public interface PostEffect {

    /**
     * Initializes OpenGL resources (e.g., compiles shaders).
     * This must be called after the OpenGL context is current.
     */
    void init();

    /**
     * Gets the number of passes this effect requires.
     * Default is 1 for single-pass effects.
     *
     * @return The number of rendering passes needed (minimum 1)
     */
    default int getPassCount() {
        return 1;
    }

    /**
     * Applies a specific pass of the effect.
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

    /**
     * Cleans up OpenGL resources (e.g., deletes shaders/programs).
     */
    void destroy();
}