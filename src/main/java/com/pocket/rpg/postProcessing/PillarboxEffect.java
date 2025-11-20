package com.pocket.rpg.postProcessing;

import com.pocket.rpg.rendering.Shader;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.opengl.GL33.*;

/**
 * Pillarbox/letterbox effect that maintains aspect ratio when rendering to screen.
 * This should be the LAST effect in the post-processing chain.
 */
public class PillarboxEffect implements PostEffect {

    private final float targetAspectRatio;
    private final long windowHandle;
    private Shader shader;

    /**
     * Creates a pillarbox effect for the specified aspect ratio.
     *
     * @param targetWidth  Target rendering width
     * @param targetHeight Target rendering height
     * @param windowHandle GLFW window handle for querying window dimensions
     */
    public PillarboxEffect(int targetWidth, int targetHeight, long windowHandle) {
        this.targetAspectRatio = (float) targetWidth / targetHeight;
        this.windowHandle = windowHandle;
    }

    @Override
    public void init() {
        shader = new Shader("assets/shaders/pillarbox.glsl");
        shader.use();
        shader.uploadInt("screenTexture", 0);
        shader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO,
                          int inputWidth, int inputHeight) {
        // Get current window dimensions
        int[] winWidth = new int[1];
        int[] winHeight = new int[1];
        glfwGetFramebufferSize(windowHandle, winWidth, winHeight);

        // Bind to default framebuffer (screen)
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Clear entire window to black
        glViewport(0, 0, winWidth[0], winHeight[0]);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Calculate pillarboxed viewport dimensions
        int newWidth = winWidth[0];
        int newHeight = (int) (newWidth / targetAspectRatio);

        if (newHeight > winHeight[0]) {
            newHeight = winHeight[0];
            newWidth = (int) (newHeight * targetAspectRatio);
        }

        int viewportX = (winWidth[0] - newWidth) / 2;
        int viewportY = (winHeight[0] - newHeight) / 2;
        glViewport(viewportX, viewportY, newWidth, newHeight);

        // Draw texture with nearest neighbor for sharp pixels
        glDisable(GL_DEPTH_TEST);
        shader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        // Use nearest neighbor filtering for sharp rendering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Cleanup
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        shader.detach();
    }

    @Override
    public void destroy() {
        shader.delete();
    }
}