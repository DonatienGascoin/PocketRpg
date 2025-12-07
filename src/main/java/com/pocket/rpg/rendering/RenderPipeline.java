package com.pocket.rpg.rendering;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.Camera;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.rendering.culling.CullingSystem;
import com.pocket.rpg.rendering.renderers.BatchRenderer;
import com.pocket.rpg.rendering.renderers.Renderer;
import com.pocket.rpg.rendering.stats.StatisticsReporter;
import com.pocket.rpg.scenes.Scene;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

import static org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.glClear;
import static org.lwjgl.opengl.GL33.glClearColor;

/**
 * Orchestrates the complete rendering pipeline:
 * 1. Camera updates
 * 2. Culling system updates
 * 3. Renderable rendering with culling (sprites, tilemaps, etc.)
 * 4. Statistics reporting
 *
 * <h2>Render Order</h2>
 * Renderables are processed in zIndex order (ascending).
 * Lower zIndex renders first (background), higher renders on top.
 *
 * <h2>Type Dispatch</h2>
 * The pipeline uses instanceof checks to dispatch to type-specific rendering:
 * <ul>
 *   <li>{@link SpriteRenderer} → {@link Renderer#drawSpriteRenderer(SpriteRenderer)}</li>
 *   <li>Tilemap → (Phase 4: drawTilemap)</li>
 * </ul>
 */
public class RenderPipeline {

    @Getter
    private final CullingSystem cullingSystem;
    @Getter
    private final Renderer renderer;
    private final ViewportConfig viewportConfig;
    @Setter
    private StatisticsReporter statisticsReporter;

    private final Vector4f clearColor;

    /**
     * Creates a render pipeline with the specified components.
     */
    public RenderPipeline(Renderer renderer, ViewportConfig viewportConfig, RenderingConfig config) {
        this.renderer = renderer;
        this.viewportConfig = viewportConfig;
        this.cullingSystem = new CullingSystem();
        this.clearColor = config.getClearColor();
        if (config.isEnableStatistics()) {
            this.statisticsReporter = config.getReporter();
        }
    }

    /**
     * Renders a scene with full pipeline (camera, culling, rendering).
     *
     * @param scene The scene to render
     */
    public void render(Scene scene) {
        if (scene == null) {
            System.err.println("WARNING: Attempted to render null scene");
            return;
        }

        try {
            // 1. Get camera from scene (each scene owns its camera)
            Camera activeCamera = scene.getCamera();
            if (activeCamera == null) {
                System.err.println("ERROR: Scene has no camera: " + scene.getName());
                return;
            }

            // 2. Update culling system with camera bounds
            cullingSystem.updateFrame(activeCamera);

            // 3. Check if static batch needs rebuilding (flag-based, no casting)
            if (scene.isStaticBatchDirty()) {
                handleStaticBatchDirty();
                scene.clearStaticBatchDirty();
            }

            // 4. Get rendering matrices FROM CAMERA (not CameraSystem)
            Matrix4f projectionMatrix = activeCamera.getProjectionMatrix();
            Matrix4f viewMatrix = activeCamera.getViewMatrix();

            // 5. Clear screen
            glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // 6. Begin rendering with camera matrices
            renderer.beginWithMatrices(
                    projectionMatrix,
                    viewMatrix,
                    clearColor
            );

            // 7. Render all renderables (sorted by zIndex)
            List<Renderable> renderables = scene.getRenderers();
            for (Renderable renderable : renderables) {
                renderRenderable(renderable);
            }

            // 8. End rendering
            renderer.end();

            // 9. Report statistics if reporter is set
            if (statisticsReporter != null) {
                statisticsReporter.report(cullingSystem.getStatistics());
            }
        } catch (Exception e) {
            System.err.println("ERROR during rendering: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Dispatches rendering to type-specific handlers.
     * Uses instanceof for simplicity (acceptable for 2-3 renderable types).
     *
     * <p>For many types, consider the Visitor pattern instead:
     * each Renderable would implement accept(RenderableVisitor),
     * and this class would implement RenderableVisitor with
     * visit(SpriteRenderer), visit(Tilemap), etc.</p>
     *
     * @param renderable The renderable to dispatch
     */
    private void renderRenderable(Renderable renderable) {
        // Quick visibility check (enabled, gameObject exists, has data)
        if (!renderable.isRenderVisible()) {
            return;
        }

        // Type-specific dispatch
        if (renderable instanceof SpriteRenderer spriteRenderer) {
            renderSpriteRenderer(spriteRenderer);
        } else if (renderable instanceof TilemapRenderer tilemapRenderer) {
            renderTilemap(tilemapRenderer);
        } else {
            // Unknown renderable type - log warning in debug builds
            System.err.println("WARNING: Unknown Renderable type: " + renderable.getClass().getSimpleName());
        }
    }

    /**
     * Renders a SpriteRenderer with culling.
     *
     * @param spriteRenderer The sprite renderer to render
     */
    private void renderSpriteRenderer(SpriteRenderer spriteRenderer) {
        // Frustum culling
        if (cullingSystem.shouldRender(spriteRenderer)) {
            renderer.drawSpriteRenderer(spriteRenderer);
        }
    }

    /**
     * Renders a Tilemap with chunk-level culling.
     *
     * @param tilemapRenderer The tilemap to render
     */
    private void renderTilemap(TilemapRenderer tilemapRenderer) {
        // Get visible chunks from culling system
        List<long[]> visibleChunks = cullingSystem.getVisibleChunks(tilemapRenderer);

        if (visibleChunks.isEmpty()) {
            return;
        }

        // Dispatch to BatchRenderer if available
        if (renderer instanceof BatchRenderer batchRenderer) {
            batchRenderer.drawTilemap(tilemapRenderer, visibleChunks);
        } else {
            // Fallback: render tiles individually via base Renderer
            // This is less efficient but ensures compatibility
            renderTilemapFallback(tilemapRenderer, visibleChunks);
        }
    }

    /**
     * Fallback tilemap rendering for non-batched renderers.
     * Renders each tile as an individual sprite.
     */
    private void renderTilemapFallback(TilemapRenderer tilemapRenderer, List<long[]> visibleChunks) {
        // Not implemented - BatchRenderer is the expected path
        // If needed, could create temporary SpriteRenderers or
        // add drawTile() method to base Renderer
        System.err.println("WARNING: Tilemap rendering requires BatchRenderer");
    }

    /**
     * Handles static batch dirty flag.
     * Uses polymorphism to avoid casting in main render method.
     */
    private void handleStaticBatchDirty() {
        // Polymorphic call - BatchRenderer will handle it, Renderer will ignore it
        if (renderer instanceof BatchRenderer batchRenderer) {
            batchRenderer.getBatch().markStaticBatchDirty();
        }
    }
}