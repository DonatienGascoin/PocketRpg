package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * A 2D sprite renderer that renders SpriteRenderer components.
 * Handles transformations, textures, and camera operations.
 * Uses orthographic projection for 2D rendering.
 */
public class Renderer {

    private Shader shader;
    private int quadVAO;
    private int quadVBO;

    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;
    private Matrix4f modelMatrix;

    // For dynamic UV updates
    private FloatBuffer vertexBuffer;

    // Default clear color when no camera is provided
    private static final Vector4f DEFAULT_CLEAR_COLOR = new Vector4f(0.1f, 0.1f, 0.15f, 1.0f);

    /**
     * Initializes the renderer with the specified viewport dimensions.
     *
     * @param viewportWidth  Viewport width
     * @param viewportHeight Viewport height
     */
    public void init(int viewportWidth, int viewportHeight) {
        // Create shader program
        shader = new Shader("assets/shaders/sprite.glsl");
        shader.compileAndLink();

        // Allocate vertex buffer for UV updates
        vertexBuffer = MemoryUtil.memAllocFloat(24); // 6 vertices * 4 floats (pos + uv)

        // Create quad mesh
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
     * Begins a rendering batch with camera support.
     * Handles OpenGL clear operations and applies camera matrices.
     *
     * @param camera The active camera (can be null for default behavior)
     */
    public void beginWithCamera(Camera camera) {
        // Apply camera clear color (or default)
        Vector4f clearColor = camera != null ? camera.getClearColor() : DEFAULT_CLEAR_COLOR;
        glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Apply camera view matrix
        if (camera != null) {
            setViewMatrix(camera.getViewMatrix());
        } else {
            resetView();
        }

        // Continue with normal begin
        begin();
    }

    /**
     * Begins a rendering batch.
     * Call this before rendering sprites.
     */
    public void begin() {
        shader.use();

        // Upload matrices
        shader.uploadMat4f("projection", projectionMatrix);
        shader.uploadMat4f("view", viewMatrix);

        // Set texture sampler
        shader.uploadInt("textureSampler", 0);
    }

    /**
     * Renders a SpriteRenderer component.
     * Reads Transform from the GameObject and Sprite from the component.
     *
     * @param spriteRenderer The SpriteRenderer component to render
     */
    public void drawSpriteRenderer(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return;
        }

        Sprite sprite = spriteRenderer.getSprite();
        Transform transform = spriteRenderer.getGameObject().getTransform();

        if (transform == null) {
            return;
        }

        // Update quad UVs for this sprite
        updateQuadUVs(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());

        // Bind texture
        sprite.getTexture().bind(0);

        // Build model matrix from Transform and Sprite
        buildModelMatrix(sprite, transform, spriteRenderer);

        // Upload model matrix
        shader.uploadMat4f("model", modelMatrix);

        // Draw quad
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        // Unbind texture
        Texture.unbind(0);
    }

    /**
     * Builds the model matrix from Transform, Sprite, and SpriteRenderer.
     * Applies: Translation -> Rotation (around origin) -> Scale
     *
     * @param sprite         The sprite (for size)
     * @param transform      The transform (for position, rotation, scale)
     * @param spriteRenderer The sprite renderer (for origin/pivot)
     */
    private void buildModelMatrix(Sprite sprite, Transform transform, SpriteRenderer spriteRenderer) {
        Vector3f pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        Vector3f scale = transform.getScale();

        // Calculate final size (sprite base size * transform scale)
        float finalWidth = sprite.getWidth() * scale.x;
        float finalHeight = sprite.getHeight() * scale.y;

        // Calculate origin offset in pixels
        float originX = finalWidth * spriteRenderer.getOriginX();
        float originY = finalHeight * spriteRenderer.getOriginY();

        // Build model matrix (TRS: Translate, Rotate, Scale)
        modelMatrix.identity();

        // 1. Translate to position
        modelMatrix.translate(pos.x, pos.y, pos.z);

        // 2. Rotate around origin (if rotation is not zero)
        if (rot.z != 0) {
            modelMatrix.translate(originX, originY, 0);
            modelMatrix.rotateZ((float) Math.toRadians(rot.z));
            modelMatrix.translate(-originX, -originY, 0);
        }

        // 3. Scale to final size
        modelMatrix.scale(finalWidth, finalHeight, 1);
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
     * Uses GL_DYNAMIC_DRAW since UVs are updated frequently.
     */
    private void createQuadMesh() {
        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();

        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);

        // IMPORTANT: Use GL_DYNAMIC_DRAW for frequently updated data
        glBufferData(GL_ARRAY_BUFFER, 24 * Float.BYTES, GL_DYNAMIC_DRAW);

        // Position attribute (location 0)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        // Texture coordinate attribute (location 1)
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
        vertexBuffer.put(0.0f).put(0.0f).put(u0).put(v1);  // Top-left (flipped V)
        vertexBuffer.put(0.0f).put(1.0f).put(u0).put(v0);  // Bottom-left (flipped V)
        vertexBuffer.put(1.0f).put(1.0f).put(u1).put(v0);  // Bottom-right (flipped V)

        // Triangle 2
        vertexBuffer.put(0.0f).put(0.0f).put(u0).put(v1);  // Top-left (flipped V)
        vertexBuffer.put(1.0f).put(1.0f).put(u1).put(v0);  // Bottom-right (flipped V)
        vertexBuffer.put(1.0f).put(0.0f).put(u1).put(v1);  // Top-right (flipped V)

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
        if (shader != null) {
            shader.delete();
        }
        if (quadVAO != 0) {
            glDeleteVertexArrays(quadVAO);
        }
        if (quadVBO != 0) {
            glDeleteBuffers(quadVBO);
        }
        if (vertexBuffer != null) {
            MemoryUtil.memFree(vertexBuffer);
        }
    }
}