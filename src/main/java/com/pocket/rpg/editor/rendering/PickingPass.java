package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.batch.BatchRenderer;
import com.pocket.rpg.rendering.batch.SpriteBatch;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

/**
 * Reusable GPU color picking pass.
 * <p>
 * Manages a {@link PickingBuffer} and a {@link BatchRenderer} with the picking shader.
 * Callers provide entity submission logic via {@link PickingSubmitter}; this class handles
 * the GL state setup/teardown, buffer management, coordinate conversion, and ID readback.
 * <p>
 * Used by both the scene view (sprite entities) and UI designer (UI elements).
 */
public class PickingPass {

    private PickingBuffer pickingBuffer;
    private BatchRenderer pickingBatchRenderer;
    private Map<Integer, EditorGameObject> entityIdMap;
    private boolean initialized = false;

    /** 1x1 white sprite for rendering solid quads (UIPanel, UIButton without sprite). */
    @Getter
    private Sprite whiteSprite;
    private int whiteTextureId;

    private final RenderingConfig renderingConfig;

    public PickingPass(RenderingConfig renderingConfig) {
        this.renderingConfig = renderingConfig;
    }

    public void init(int width, int height) {
        if (initialized) return;

        pickingBuffer = new PickingBuffer(width, height);
        pickingBuffer.init();
        pickingBatchRenderer = new BatchRenderer(renderingConfig, "gameData/assets/shaders/picking.glsl");
        pickingBatchRenderer.init(width, height);

        // Create 1x1 white texture for solid quad picking
        whiteTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, whiteTextureId);
        ByteBuffer whitePixel = BufferUtils.createByteBuffer(4);
        whitePixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, whitePixel);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);
        whiteSprite = new Sprite(Texture.wrap(whiteTextureId, 1, 1));

        initialized = pickingBuffer.isInitialized();
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Resizes the picking buffer to match a new viewport size.
     */
    public void resize(int width, int height) {
        if (pickingBuffer != null) {
            pickingBuffer.resize(width, height);
        }
    }

    /**
     * Executes a picking render pass.
     *
     * @param projection The projection matrix
     * @param view       The view matrix (identity for screen-space UI)
     * @param submitter  Callback that submits entities to the batch
     */
    public void execute(Matrix4f projection, Matrix4f view, PickingSubmitter submitter) {
        if (!initialized) return;

        entityIdMap = new HashMap<>();

        glDisable(GL_BLEND);

        pickingBuffer.bind();
        pickingBuffer.clear();

        pickingBatchRenderer.beginWithMatrices(projection, view, null);

        SpriteBatch batch = pickingBatchRenderer.getBatch();
        submitter.submit(batch, entityIdMap);

        pickingBatchRenderer.end();
        pickingBuffer.unbind();

        // Restore GL state
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Reads the entity at the given pixel coordinates.
     *
     * @param pixelX X coordinate (0 = left)
     * @param pixelY Y coordinate (0 = bottom, OpenGL convention)
     * @return The entity at that pixel, or null
     */
    public EditorGameObject readEntityAt(int pixelX, int pixelY) {
        if (!initialized || entityIdMap == null) return null;

        pixelX = Math.clamp(pixelX, 0, pickingBuffer.getWidth() - 1);
        pixelY = Math.clamp(pixelY, 0, pickingBuffer.getHeight() - 1);

        int entityId = pickingBuffer.readEntityId(pixelX, pixelY);
        if (entityId == 0) return null;

        return entityIdMap.getOrDefault(entityId, null);
    }

    public int getWidth() {
        return pickingBuffer != null ? pickingBuffer.getWidth() : 0;
    }

    public int getHeight() {
        return pickingBuffer != null ? pickingBuffer.getHeight() : 0;
    }

    public void destroy() {
        if (pickingBuffer != null) {
            pickingBuffer.destroy();
            pickingBuffer = null;
        }
        if (whiteTextureId != 0) {
            glDeleteTextures(whiteTextureId);
            whiteTextureId = 0;
        }
        whiteSprite = null;
        pickingBatchRenderer = null;
        entityIdMap = null;
        initialized = false;
    }

    /**
     * Callback interface for submitting entities to the picking batch.
     * Implementations should iterate their entities and call
     * {@link SpriteBatch#submit} with colors from {@link PickingBuffer#encodeEntityId}.
     */
    @FunctionalInterface
    public interface PickingSubmitter {
        /**
         * @param batch      The sprite batch to submit to
         * @param entityIdMap Map to populate with (id -> entity) entries.
         *                    Use {@link PickingPass#nextId(Map, EditorGameObject)} for convenience.
         */
        void submit(SpriteBatch batch, Map<Integer, EditorGameObject> entityIdMap);
    }

    /**
     * Registers an entity in the ID map and returns its encoded color.
     * Convenience method for use inside {@link PickingSubmitter#submit}.
     */
    public static Vector4f registerEntity(Map<Integer, EditorGameObject> entityIdMap, EditorGameObject entity) {
        int id = entityIdMap.size() + 1;
        entityIdMap.put(id, entity);
        return PickingBuffer.encodeEntityId(id);
    }
}
