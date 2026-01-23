package com.pocket.rpg.rendering.pipeline;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.editor.rendering.TilemapLayerRenderable;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.batch.SpriteBatch;
import com.pocket.rpg.rendering.core.RenderCamera;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.rendering.culling.CullingSystem;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Single source of truth for how to submit each Renderable type to SpriteBatch.
 * <p>
 * This centralizes all rendering logic in one place. Adding a new renderable type
 * requires only adding a case here - no changes to other renderers.
 * <p>
 * Supports per-renderable tinting for editor features like layer dimming.
 * <p>
 * Owns the {@link CullingSystem} for tilemap chunk culling.
 * Call {@link #beginFrame(RenderCamera)} at the start of each frame to update
 * culling data before submitting renderables.
 */
public class RenderDispatcher {

    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);

    /**
     * Culling system for tilemap chunk visibility.
     */
    private final CullingSystem cullingSystem = new CullingSystem();

    /**
     * Current camera for this frame. Set by beginFrame().
     */
    private RenderCamera currentCamera;

    /**
     * Call at the start of each frame to update culling data.
     *
     * @param camera Camera for frustum culling
     */
    public void beginFrame(RenderCamera camera) {
        this.currentCamera = camera;
        cullingSystem.updateFrame(camera);
    }

    // ========================================================================
    // PUBLIC SUBMIT METHODS
    // ========================================================================

    /**
     * Submits a renderable with default white tint.
     *
     * @param renderable The renderable to submit (may be null)
     * @param batch      SpriteBatch to submit to
     * @param camera     Camera for frustum culling
     */
    public void submit(Renderable renderable, SpriteBatch batch, RenderCamera camera) {
        submit(renderable, batch, camera, WHITE);
    }

    /**
     * Submits a renderable with custom tint.
     * <p>
     * Use this for editor features like layer dimming where inactive layers
     * are rendered with reduced opacity.
     *
     * @param renderable The renderable to submit (may be null)
     * @param batch      SpriteBatch to submit to
     * @param camera     Camera for frustum culling (may be null to use frame camera)
     * @param tint       Tint color to apply
     */
    public void submit(Renderable renderable, SpriteBatch batch, RenderCamera camera, Vector4f tint) {
        if (renderable == null || !renderable.isRenderVisible()) {
            return;
        }

        // Use provided camera or fall back to current frame camera
        RenderCamera effectiveCamera = camera != null ? camera : currentCamera;
        Vector4f effectiveTint = tint != null ? tint : WHITE;

        // ===================
        // RUNTIME COMPONENTS
        // ===================
        if (renderable instanceof SpriteRenderer sr) {
            submitSpriteRenderer(sr, batch, effectiveTint);
        } else if (renderable instanceof TilemapRenderer tr) {
            submitTilemapChunks(tr, batch, effectiveTint);
        }
        // ===================
        // EDITOR TYPES
        // ===================
        else if (renderable instanceof EditorGameObject entity) {
            submitEditorGameObject(entity, batch, effectiveTint);
        } else if (renderable instanceof TilemapLayerRenderable tlr) {
            // TilemapLayerRenderable has its own tint - combine with passed tint
            Vector4f combinedTint = combineTints(tlr.tint(), effectiveTint);
            submitTilemapChunks(tlr.layer().getTilemap(), batch, combinedTint);
        }
        // ===================
        // UNKNOWN TYPE
        // ===================
        else {
            System.err.println("[RenderDispatcher] Unknown renderable type: " +
                    renderable.getClass().getSimpleName());
        }
    }

    // ========================================================================
    // RUNTIME COMPONENTS
    // ========================================================================

    private void submitSpriteRenderer(SpriteRenderer sr, SpriteBatch batch, Vector4f tint) {
        Sprite sprite = sr.getSprite();
        if (sprite == null) return;

        batch.submit(sr, tint);
    }

    // ========================================================================
    // EDITOR TYPES
    // ========================================================================

    private void submitEditorGameObject(EditorGameObject entity, SpriteBatch batch, Vector4f tint) {
        Sprite sprite = entity.getCurrentSprite();
        if (sprite == null) return;

        Vector3f pos = entity.getPosition();
        Vector2f size = entity.getCurrentSize();
        Vector3f scale = entity.getScale();
        Vector3f rotation = entity.getRotation();

        // Apply scale to size
        float width = size.x * scale.x;
        float height = size.y * scale.y;

        // Get origin and tint from SpriteRenderer if available
        float originX = 0.5f;
        float originY = 0.5f;
        Vector4f finalTint = tint;
        SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
        if (sr != null) {
            originX = sr.getEffectiveOriginX();
            originY = sr.getEffectiveOriginY();
            // Combine SpriteRenderer's tint with editor tint
            Vector4f spriteTint = sr.getTintColor();
            finalTint = combineTints(spriteTint, tint);
        }

        batch.submit(
                sprite,
                pos.x, pos.y,
                width, height,
                rotation.z,
                originX, originY,
                entity.getZIndex(),
                finalTint
        );
    }

    // ========================================================================
    // TILEMAP RENDERING
    // ========================================================================

    /**
     * Submits visible tilemap chunks using the CullingSystem.
     *
     * @param tilemap The tilemap renderer
     * @param batch   SpriteBatch to submit to
     * @param tint    Tint color to apply
     */
    private void submitTilemapChunks(TilemapRenderer tilemap, SpriteBatch batch, Vector4f tint) {
        if (tilemap == null) return;

        // Get visible chunks from culling system
        List<long[]> visibleChunks = cullingSystem.getVisibleChunks(tilemap);

        if (visibleChunks.isEmpty()) {
            return;
        }

        // Submit each visible chunk with tint
        for (long[] chunkCoord : visibleChunks) {
            int cx = (int) chunkCoord[0];
            int cy = (int) chunkCoord[1];
            batch.submit(tilemap, cx, cy, tint);
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Combines two tints by component-wise multiplication.
     * <p>
     * This is the standard way to combine tints in rendering:
     * <ul>
     *   <li>WHITE × ANY = ANY (identity)</li>
     *   <li>50% gray × 50% gray = 25% gray</li>
     *   <li>Red × Blue = Black (0,0,0)</li>
     * </ul>
     * <p>
     * Common use case: A tilemap layer has its own tint (e.g., sepia filter),
     * and the editor wants to dim it (0.5 alpha). The combined tint applies both.
     *
     * @param a First tint (may be null, treated as WHITE)
     * @param b Second tint (may be null, treated as WHITE)
     * @return Combined tint
     */
    public static Vector4f combineTints(Vector4f a, Vector4f b) {
        if (a == null && b == null) return new Vector4f(WHITE);
        if (a == null) return new Vector4f(b);
        if (b == null) return new Vector4f(a);

        // Check if either is WHITE to avoid allocation
        if (isWhite(a)) return new Vector4f(b);
        if (isWhite(b)) return new Vector4f(a);

        // Component-wise multiplication
        return new Vector4f(
                a.x * b.x,
                a.y * b.y,
                a.z * b.z,
                a.w * b.w
        );
    }

    /**
     * Checks if a tint is effectively white (no tinting).
     */
    private static boolean isWhite(Vector4f tint) {
        return tint.x >= 0.999f && tint.y >= 0.999f && tint.z >= 0.999f && tint.w >= 0.999f;
    }

}