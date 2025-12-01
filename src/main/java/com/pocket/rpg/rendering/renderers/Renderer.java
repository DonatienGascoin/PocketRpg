package com.pocket.rpg.rendering.renderers;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.Shader;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * A 2D sprite renderer focused purely on rendering.
 * FIXED: Now uses fixed game resolution for pixel-perfect rendering.
 */
public class Renderer {

    protected RenderingConfig config;
    // FIX: Game resolution (fixed, never changes)
//    private int gameWidth = 640;
//    private int gameHeight = 480;

    private Shader shader;
    private int quadVAO;
    private int quadVBO;

    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;
    private Matrix4f modelMatrix;

    private FloatBuffer vertexBuffer;

    /**
     * FIX: Initialize with game resolution (fixed internal resolution).
     */
    public void init(int gameWidth, int gameHeight) {
        if (gameWidth <= 0 || gameHeight <= 0) {
            throw new IllegalArgumentException("Game resolution must be positive: " +
                    gameWidth + "x" + gameHeight);
        }

//        this.gameWidth = gameWidth;
//        this.gameHeight = gameHeight;

        System.out.println("Renderer initialized with game resolution: " + gameWidth + "x" + gameHeight);

        shader = new Shader("gameData/assets/shaders/sprite.glsl");
        shader.compileAndLink();

        vertexBuffer = MemoryUtil.memAllocFloat(24);

        createQuadMesh();

        projectionMatrix = new Matrix4f();
        viewMatrix = new Matrix4f();
        modelMatrix = new Matrix4f();

        setProjection(gameWidth, gameHeight);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * FIX: Sets projection using game resolution.
     * This should match the camera's projection.
     */
    public void setProjection(int width, int height) {
        if (width <= 0 || height <= 0) {
            System.err.println("WARNING: Invalid projection dimensions: " + width + "x" + height);
            return;
        }

//        this.gameWidth = width;
//        this.gameHeight = height;
        projectionMatrix.identity().ortho(0, width, height, 0, -1, 1);
    }

    /**
     * Begins rendering with explicit matrices.
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
     * Draws a sprite renderer.
     */
    public void drawSpriteRenderer(SpriteRenderer spriteRenderer) {
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

        updateQuadUVs(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());

        sprite.getTexture().bind(0);

        buildModelMatrix(sprite, transform, spriteRenderer);

        shader.uploadMat4f("model", modelMatrix);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        Texture.unbind(0);
    }

    private void buildModelMatrix(Sprite sprite, Transform transform, SpriteRenderer spriteRenderer) {
        Vector3f pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        Vector3f scale = transform.getScale();

        float finalWidth = sprite.getWidth() * scale.x;
        float finalHeight = sprite.getHeight() * scale.y;

        float originX = finalWidth * spriteRenderer.getOriginX();
        float originY = finalHeight * spriteRenderer.getOriginY();

        modelMatrix.identity();

        modelMatrix.translate(pos.x, pos.y, pos.z);

        if (rot.z != 0) {
            modelMatrix.translate(originX, originY, 0);
            modelMatrix.rotateZ((float) Math.toRadians(rot.z));
            modelMatrix.translate(-originX, -originY, 0);
        }

        modelMatrix.scale(finalWidth, finalHeight, 1);
    }

    public void end() {
        shader.detach();
    }

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

        updateQuadUVs(0, 0, 1, 1);
    }

    private void updateQuadUVs(float u0, float v0, float u1, float v1) {
        if (vertexBuffer == null) {
            System.err.println("ERROR: vertexBuffer is null");
            return;
        }

        vertexBuffer.clear();

        vertexBuffer.put(0.0f).put(0.0f).put(u0).put(v1);
        vertexBuffer.put(0.0f).put(1.0f).put(u0).put(v0);
        vertexBuffer.put(1.0f).put(1.0f).put(u1).put(v0);

        vertexBuffer.put(0.0f).put(0.0f).put(u0).put(v1);
        vertexBuffer.put(1.0f).put(1.0f).put(u1).put(v0);
        vertexBuffer.put(1.0f).put(0.0f).put(u1).put(v1);

        vertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
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