package com.pocket.rpg.rendering.postfx;

import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.rendering.resources.Shader;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.opengl.GL33.*;

/**
 * Handles aspect ratio preservation by rendering with pillarboxing/letterboxing.
 * This is NOT a PostEffect - it's a presentation layer that handles the final
 * blit from the internal resolution to the screen with proper viewport management.
 */
public class PillarBox {

    private final float targetAspectRatio;
    private Shader shader;
    private long windowHandle;

    /**
     * Creates a pillarbox manager for the specified aspect ratio.
     *
     * @param targetAspectRatio Target aspect ratio (e.g., 16:9 = 1.777, 4:3 = 1.333)
     */
    public PillarBox(float targetAspectRatio) {
        this.targetAspectRatio = targetAspectRatio;
    }

    /**
     * Initializes the pillarbox shader and resources.
     *
     * @param window The game window
     */
    public void init(AbstractWindow window) {
        this.windowHandle = window.getWindowHandle();
        shader = new Shader("gameData/assets/shaders/pillarbox.glsl");
        shader.compileAndLink();
        shader.use();
        shader.uploadInt("screenTexture", 0);
        shader.detach();
    }

    /**
     * Renders the input texture to the screen with pillarboxing/letterboxing
     * to maintain the target aspect ratio.
     *
     * @param inputTextureId The texture to render
     * @param quadVAO        The fullscreen quad VAO
     */
    public void renderToScreen(int inputTextureId, int quadVAO) {
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

        // Calculate pillarboxed/letterboxed viewport dimensions
        ViewportInfo viewport = calculateViewport(winWidth[0], winHeight[0]);

        // Set viewport to maintain aspect ratio
        glViewport(viewport.x, viewport.y, viewport.width, viewport.height);

        // Disable depth testing for 2D rendering
        glDisable(GL_DEPTH_TEST);

        // Render the texture
        shader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        // Use nearest neighbor filtering for sharp pixel-perfect rendering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Cleanup
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        shader.detach();
    }

    /**
     * Calculates the viewport dimensions to maintain aspect ratio with
     * pillarboxing (vertical bars) or letterboxing (horizontal bars).
     *
     * @param windowWidth  Current window width
     * @param windowHeight Current window height
     * @return ViewportInfo containing position and dimensions
     */
    private ViewportInfo calculateViewport(int windowWidth, int windowHeight) {
        int newWidth = windowWidth;
        int newHeight = (int) (newWidth / targetAspectRatio);

        // If height exceeds window, fit by height instead
        if (newHeight > windowHeight) {
            newHeight = windowHeight;
            newWidth = (int) (newHeight * targetAspectRatio);
        }

        // Center the viewport
        int viewportX = (windowWidth - newWidth) / 2;
        int viewportY = (windowHeight - newHeight) / 2;

        return new ViewportInfo(viewportX, viewportY, newWidth, newHeight);
    }

    /**
     * Cleans up OpenGL resources.
     */
    public void destroy() {
        if (shader != null) {
            shader.delete();
        }
    }

    /**
     * Simple data class for viewport information.
     */
    private static class ViewportInfo {
        final int x, y, width, height;

        ViewportInfo(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}


