package com.pocket.rpg.rendering;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * A 2D sprite renderer focused purely on rendering.
 * Camera and culling logic removed - handled by RenderPipeline.
 */
public class Renderer {

    private int viewportWidth = 800;
    private int viewportHeight = 600;

    private Shader shader;
    private int quadVAO;
    private int quadVBO;

    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;
    private Matrix4f modelMatrix;

    private FloatBuffer vertexBuffer;

    public void init(int viewportWidth, int viewportHeight) {
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;

        shader = new Shader("assets/shaders/sprite.glsl");
        shader.compileAndLink();

        vertexBuffer = MemoryUtil.memAllocFloat(24);

        createQuadMesh();

        projectionMatrix = new Matrix4f();
        viewMatrix = new Matrix4f();
        modelMatrix = new Matrix4f();

        setProjection(viewportWidth, viewportHeight);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public void setProjection(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
        projectionMatrix.identity().ortho(0, width, height, 0, -1, 1);
    }

    /**
     * Begins rendering with explicit matrices.
     * Called by RenderPipeline.
     */
    public void beginWithMatrices(Matrix4f projection, Matrix4f view, Vector4f clearColor) {
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

    public void drawSpriteRenderer(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return;
        }

        Sprite sprite = spriteRenderer.getSprite();
        Transform transform = spriteRenderer.getGameObject().getTransform();

        if (transform == null) {
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