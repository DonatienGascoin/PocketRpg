package com.pocket.rpg.rendering.postfx.effects;

import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.resources.Shader;
import org.joml.Vector2f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Edge detection effect that outlines objects using the Sobel operator.
 * Creates a cel-shaded or comic book art style and can improve visual clarity.
 */
public class EdgeDetectionEffect implements PostEffect {
    private static final float DEFAULT_THRESHOLD = 0.1f;
    private static final Vector3f DEFAULT_EDGE_COLOR = new Vector3f(0.0f, 0.0f, 0.0f); // Black

    private final float edgeThreshold;
    private final Vector3f edgeColor;

    private Shader edgeShader;

    /**
     * Creates an edge detection effect with default black outlines.
     */
    public EdgeDetectionEffect() {
        this(DEFAULT_THRESHOLD, DEFAULT_EDGE_COLOR);
    }

    /**
     * Creates an edge detection effect with specified parameters.
     *
     * @param edgeThreshold Sensitivity of edge detection (0.05 - 0.3).
     *                      - 0.05 = detects subtle edges
     *                      - 0.1 = moderate (recommended)
     *                      - 0.2+ = only strong edges
     * @param edgeColor     RGB color for detected edges (0.0 - 1.0 per channel).
     *                      - (0, 0, 0) = black outlines (classic)
     *                      - (1, 1, 1) = white outlines
     *                      - (1, 0.5, 0) = orange outlines
     */
    public EdgeDetectionEffect(float edgeThreshold, Vector3f edgeColor) {
        this.edgeThreshold = edgeThreshold;
        this.edgeColor = edgeColor;
    }

    /**
     * Convenience constructor for RGB values.
     */
    public EdgeDetectionEffect(float threshold, float r, float g, float b) {
        this(threshold, new Vector3f(r, g, b));
    }

    @Override
    public void init() {
        edgeShader = new Shader("gameData/assets/shaders/edgeDetection.glsl");
        edgeShader.compileAndLink();

        edgeShader.use();
        edgeShader.uploadInt("screenTexture", 0);
        edgeShader.uploadFloat("edgeThreshold", this.edgeThreshold);
        edgeShader.uploadVec3f("edgeColor", this.edgeColor);
        edgeShader.detach();
    }

    @Override
    public void applyPass(int passIndex, int inputTextureId, int outputFboId, int quadVAO, int inputWidth, int inputHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFboId);
        edgeShader.use();

        edgeShader.uploadVec2f("texelSize", new Vector2f(1.0f / inputWidth, 1.0f / inputHeight));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTextureId);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        edgeShader.detach();
    }

    @Override
    public void destroy() {
        edgeShader.delete();
    }
}
