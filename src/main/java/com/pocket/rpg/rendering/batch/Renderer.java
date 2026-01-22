package com.pocket.rpg.rendering.batch;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.resources.Shader;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * A 2D sprite renderer focused purely on rendering.
 * Uses world units for all position and size calculations.
 * <p>
 * Note: For batched rendering (production use), use {@link BatchRenderer} instead.
 * This class is primarily for simple/debug rendering.
 */
public class Renderer {

    protected static final Vector4f DEFAULT_TINT_COLOR = new Vector4f(1f, 1f, 1f, 1f);
    protected RenderingConfig config;

    private Shader shader;
    private int quadVAO;
    private int quadVBO;

    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;
    private Matrix4f modelMatrix;

    private FloatBuffer vertexBuffer;

    /**
     * Initialize with game resolution.
     * Note: The projection is typically overridden by Camera matrices via beginWithMatrices().
     */
    public void init(int gameWidth, int gameHeight) {
        if (gameWidth <= 0 || gameHeight <= 0) {
            throw new IllegalArgumentException("Game resolution must be positive: " +
                    gameWidth + "x" + gameHeight);
        }

        System.out.println("Renderer initialized with game resolution: " + gameWidth + "x" + gameHeight);

        shader = new Shader("gameData/assets/shaders/sprite.glsl");
        shader.compileAndLink();

        vertexBuffer = MemoryUtil.memAllocFloat(24);

        createQuadMesh();

        projectionMatrix = new Matrix4f();
        viewMatrix = new Matrix4f();
        modelMatrix = new Matrix4f();

        // Default projection (will be overridden by Camera)
        setProjection(gameWidth, gameHeight);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Sets a default pixel-based projection.
     * Note: This is typically overridden by Camera matrices.
     */
    public void setProjection(int width, int height) {
        if (width <= 0 || height <= 0) {
            System.err.println("WARNING: Invalid projection dimensions: " + width + "x" + height);
            return;
        }
        // Default fallback projection (centered, Y-up, in pixels)
        float halfW = width / 2f;
        float halfH = height / 2f;
        projectionMatrix.identity().ortho(-halfW, halfW, -halfH, halfH, -1, 1);
    }

    /**
     * Begins rendering with explicit matrices from Camera.
     * Called by RenderPipeline.
     */
    public void beginWithMatrices(Matrix4f projection, Matrix4f view, Vector4f clearColor) {
        if (projection == null || view == null) {
            System.err.println("ERROR: Cannot begin rendering with null matrices");
            return;
        }

        this.projectionMatrix = new Matrix4f(projection);
        this.viewMatrix = new Matrix4f(view);

        shader.use();
        shader.uploadMat4f("projection", projectionMatrix);
        shader.uploadMat4f("view", viewMatrix);
        shader.uploadInt("textureSampler", 0);
    }

    public void begin() {
        shader.use();
        shader.uploadMat4f("projection", projectionMatrix);
        shader.uploadMat4f("view", viewMatrix);
        shader.uploadInt("textureSampler", 0);
    }

    /**
     * Draws a sprite renderer using world units.
     */
    public void drawSpriteRenderer(SpriteRenderer spriteRenderer) {
        drawSpriteRenderer(spriteRenderer, DEFAULT_TINT_COLOR);
    }

    /**
     * Draws a sprite renderer using world units.
     */
    public void drawSpriteRenderer(SpriteRenderer spriteRenderer, Vector4f tintColor) {
        if (spriteRenderer == null) {
            System.err.println("WARNING: Attempted to render null SpriteRenderer");
            return;
        }

        if (spriteRenderer.getSprite() == null) {
            System.err.println("WARNING: SpriteRenderer has null sprite");
            return;
        }

        if (spriteRenderer.getGameObject() == null) {
            System.err.println("WARNING: SpriteRenderer has null GameObject");
            return;
        }

        Sprite sprite = spriteRenderer.getSprite();
        Transform transform = spriteRenderer.getGameObject().getTransform();

        if (transform == null) {
            System.err.println("WARNING: GameObject has null Transform");
            return;
        }

        if (sprite.getTexture() == null) {
            System.err.println("WARNING: Sprite has null Texture");
            return;
        }

        updateQuadUVs(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), tintColor);

        sprite.getTexture().bind(0);

        buildModelMatrix(sprite, transform, spriteRenderer);

        shader.uploadMat4f("model", modelMatrix);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        Texture.unbind(0);
    }

    /**
     * Builds the model matrix using world units.
     */
    private void buildModelMatrix(Sprite sprite, Transform transform, SpriteRenderer spriteRenderer) {
        Vector3f pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        Vector3f scale = transform.getScale();

        // Use WORLD UNITS for dimensions
        float finalWidth = sprite.getWorldWidth() * scale.x;
        float finalHeight = sprite.getWorldHeight() * scale.y;

        float originX = finalWidth * spriteRenderer.getEffectiveOriginX();
        float originY = finalHeight * spriteRenderer.getEffectiveOriginY();

        modelMatrix.identity();

        // Translate to position
        modelMatrix.translate(pos.x, pos.y, pos.z);

        // Apply rotation around origin point
        if (rot.z != 0) {
            modelMatrix.translate(originX, originY, 0);
            modelMatrix.rotateZ((float) Math.toRadians(rot.z));
            modelMatrix.translate(-originX, -originY, 0);
        }

        // Scale to final world size
        modelMatrix.scale(finalWidth, finalHeight, 1);
    }

    public void end() {
        shader.detach();
    }

    /**
     * Creates the unit quad mesh.
     * Vertices are in [0,1] range, scaled by model matrix.
     * Y-up coordinate system: (0,0) bottom-left, (1,1) top-right.
     */
    private void createQuadMesh() {
        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();

        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);

        glBufferData(GL_ARRAY_BUFFER, 24 * Float.BYTES, GL_DYNAMIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        glBindVertexArray(0);

        updateQuadUVs(0, 0, 1, 1, DEFAULT_TINT_COLOR);
    }

    /**
     * Updates quad UVs and vertex positions.
     * UV mapping accounts for stbi_set_flip_vertically_on_load(true).
     */
    private void updateQuadUVs(float u0, float v0, float u1, float v1, Vector4f tintColor) {
        if (vertexBuffer == null) {
            System.err.println("ERROR: vertexBuffer is null");
            return;
        }
        vertexBuffer.clear();

        // Match original working UV mapping
        // Triangle 1
        putVertex(0f, 0f, u0, v1, tintColor.x, tintColor.y, tintColor.z, tintColor.w);
        putVertex(0.0f, 1.0f, u0, v0, tintColor.x, tintColor.y, tintColor.z, tintColor.w);
        putVertex(1.0f, 1.0f, u1, v0, tintColor.x, tintColor.y, tintColor.z, tintColor.w);

        // Triangle 2
        putVertex(0.0f, 0.0f, u0, v1, tintColor.x, tintColor.y, tintColor.z, tintColor.w);
        putVertex(1.0f, 1.0f, u1, v0, tintColor.x, tintColor.y, tintColor.z, tintColor.w);
        putVertex(1.0f, 0.0f, u1, v1, tintColor.x, tintColor.y, tintColor.z, tintColor.w);

        vertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void putVertex(float x, float y, float u, float v, float r, float g, float b, float a) {
        vertexBuffer
                .put(x).put(y)
                .put(u).put(v)
                .put(r).put(g)
                .put(b).put(a);
    }

    /**
     * Cleanup resources.
     */
    public void destroy() {
        if (shader != null) {
            shader.delete();
            shader = null;
        }

        if (quadVAO != 0) {
            glDeleteVertexArrays(quadVAO);
            quadVAO = 0;
        }

        if (quadVBO != 0) {
            glDeleteBuffers(quadVBO);
            quadVBO = 0;
        }

        if (vertexBuffer != null) {
            MemoryUtil.memFree(vertexBuffer);
            vertexBuffer = null;
        }

        System.out.println("Renderer destroyed and resources freed");
    }
}