package com.pocket.rpg.rendering;


import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * A simple 2D sprite renderer with support for batching, transformations, and textures.
 * Uses an orthographic projection for 2D rendering.
 */
public class Renderer {

    private int quadVAO;
    private int quadVBO;

    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;
    private Matrix4f modelMatrix;
    // For dynamic UV updates
    private FloatBuffer vertexBuffer;

    private Shader shader;

    /**
     * Initializes the renderer with the specified viewport dimensions.
     *
     * @param viewportWidth  Viewport width
     * @param viewportHeight Viewport height
     */
    public void init(int viewportWidth, int viewportHeight) {
        // Create shader program
        shader = new Shader("assets/shaders/default.glsl");

        // Create quad mesh
        vertexBuffer = MemoryUtil.memAllocFloat(24); // 6 vertices * 4 floats (pos + uv)
        createQuadMesh();

        // Initialize matrices
        projectionMatrix = new Matrix4f();
        viewMatrix = new Matrix4f();
        modelMatrix = new Matrix4f();

        // Set up orthographic projection (origin at top-left, Y-down)
        setProjection(viewportWidth, viewportHeight);

        // Enable blending for transparent sprites
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Sets up the orthographic projection matrix.
     *
     * @param width  Viewport width
     * @param height Viewport height
     */
    public void setProjection(int width, int height) {
        // Orthographic projection: (0, 0) at top-left, (width, height) at bottom-right
        projectionMatrix.identity().ortho(0, width, height, 0, -1, 1);
    }

    /**
     * Begins a rendering batch.
     * Call this before rendering sprites.
     */
    public void begin() {
        shader.use();

        // Upload matrices

        float[] projData = new float[16];
        float[] viewData = new float[16];

        projectionMatrix.get(projData);
        viewMatrix.get(viewData);

        shader.uploadMat4f("projection", projectionMatrix);
        shader.uploadMat4f("view", viewMatrix);

        // Set texture sampler
        shader.uploadInt("textureSampler", 0);
    }

    /**
     * Renders a sprite.
     *
     * @param sprite The sprite to render
     */
    public void drawSprite(Sprite sprite) {
        // Update quad UVs for this sprite
        updateQuadUVs(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());

        // Bind texture
        sprite.getTexture().bind(0);

        // Build model matrix (TRS: Translate, Rotate, Scale)
        modelMatrix.identity();

        // Translate to position
        modelMatrix.translate(sprite.getX(), sprite.getY(), 0);

        // Rotate around origin
        if (sprite.getRotation() != 0) {
            float originX = sprite.getWidth() * sprite.getOriginX();
            float originY = sprite.getHeight() * sprite.getOriginY();

            modelMatrix.translate(originX, originY, 0);
            modelMatrix.rotateZ((float) Math.toRadians(sprite.getRotation()));
            modelMatrix.translate(-originX, -originY, 0);
        }

        // Scale to sprite size
        modelMatrix.scale(sprite.getWidth(), sprite.getHeight(), 1);

        // Upload model matrix
        float[] modelData = new float[16];
        modelMatrix.get(modelData);
//        shader.uploadFloatArray("model", modelData);
        shader.uploadMat4f("model", modelMatrix);

        // Draw quad
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        // Unbind texture
        Texture.unbind(0);
    }

    /**
     * Ends the rendering batch.
     * Call this after rendering all sprites.
     */
    public void end() {
        shader.detach();
    }

    /**
     * Creates a unit quad mesh (0,0 to 1,1) with texture coordinates.
     */
    private void createQuadMesh() {
        float[] vertices = {
                // Position   // TexCoords
                0.0f, 0.0f, 0.0f, 0.0f,  // Top-left
                0.0f, 1.0f, 0.0f, 1.0f,  // Bottom-left
                1.0f, 1.0f, 1.0f, 1.0f,  // Bottom-right

                0.0f, 0.0f, 0.0f, 0.0f,  // Top-left
                1.0f, 1.0f, 1.0f, 1.0f,  // Bottom-right
                1.0f, 0.0f, 1.0f, 0.0f   // Top-right
        };

        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();

        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        // Position attribute
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        // Texture coordinate attribute
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        glBindVertexArray(0);
        // Initialize with default UVs (0,0 to 1,1)
        updateQuadUVs(0, 0, 1, 1);
    }

    /**
     * Updates the quad's UV coordinates for sprite sheet support.
     *
     * @param u0 Left U coordinate
     * @param v0 Top V coordinate
     * @param u1 Right U coordinate
     * @param v1 Bottom V coordinate
     */
    private void updateQuadUVs(float u0, float v0, float u1, float v1) {
        // Clear and refill buffer
        vertexBuffer.clear();

        // Triangle 1
        vertexBuffer.put(0.0f).put(0.0f).put(u0).put(v0);  // Top-left
        vertexBuffer.put(0.0f).put(1.0f).put(u0).put(v1);  // Bottom-left
        vertexBuffer.put(1.0f).put(1.0f).put(u1).put(v1);  // Bottom-right

        // Triangle 2
        vertexBuffer.put(0.0f).put(0.0f).put(u0).put(v0);  // Top-left
        vertexBuffer.put(1.0f).put(1.0f).put(u1).put(v1);  // Bottom-right
        vertexBuffer.put(1.0f).put(0.0f).put(u1).put(v0);  // Top-right

        vertexBuffer.flip();

        // Upload to GPU
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Sets the view matrix for camera transforms.
     *
     * @param viewMatrix The view matrix
     */
    public void setViewMatrix(Matrix4f viewMatrix) {
        this.viewMatrix.set(viewMatrix);
    }

    /**
     * Resets the view matrix to identity.
     */
    public void resetView() {
        viewMatrix.identity();
    }

    /**
     * Cleans up OpenGL resources.
     */
    public void destroy() {
        shader.delete();
        glDeleteVertexArrays(quadVAO);
        glDeleteBuffers(quadVBO);
        MemoryUtil.memFree(vertexBuffer);
    }
}