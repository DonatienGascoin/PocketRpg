package com.pocket.rpg.rendering;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameCamera;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.rendering.culling.CullingSystem;
import com.pocket.rpg.rendering.renderers.BatchRenderer;
import com.pocket.rpg.rendering.renderers.Renderer;
import com.pocket.rpg.scenes.Scene;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Orchestrates the complete rendering pipeline:
 * 1. Camera updates
 * 2. Culling system updates
 * 3. Renderable rendering with culling (sprites, tilemaps, etc.)
 *
 * <h2>Render Order</h2>
 * Renderables are processed in zIndex order (ascending).
 * Lower zIndex renders first (background), higher renders on top.
 */
public class RenderPipeline {

    @Getter
    private final CullingSystem cullingSystem;
    @Getter
    private final Renderer renderer;
    private final ViewportConfig viewportConfig;

    private final Vector4f clearColor;

    public RenderPipeline(Renderer renderer, ViewportConfig viewportConfig, RenderingConfig config) {
        this.renderer = renderer;
        this.viewportConfig = viewportConfig;
        this.cullingSystem = new CullingSystem();
        this.clearColor = config.getClearColor();
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
            // 1. Get camera from scene
            GameCamera activeCamera = scene.getCamera();
            if (activeCamera == null) {
                System.err.println("ERROR: Scene has no camera: " + scene.getName());
                return;
            }

            // 2. Update culling system with camera bounds
            cullingSystem.updateFrame(activeCamera);

            // 3. Get rendering matrices from camera
            Matrix4f projectionMatrix = activeCamera.getProjectionMatrix();
            Matrix4f viewMatrix = activeCamera.getViewMatrix();

            // 4. Clear screen
            glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // 5. Begin rendering with camera matrices
            renderer.beginWithMatrices(projectionMatrix, viewMatrix, clearColor);

            // 6. Render all renderables (sorted by zIndex)
            List<Renderable> renderables = scene.getRenderers();
            for (Renderable renderable : renderables) {
                renderRenderable(renderable);
            }

            // 7. End rendering
            renderer.end();

        } catch (Exception e) {
            System.err.println("ERROR during rendering: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void renderRenderable(Renderable renderable) {
        if (!renderable.isRenderVisible()) {
            return;
        }

        if (renderable instanceof SpriteRenderer spriteRenderer) {
            renderSpriteRenderer(spriteRenderer);
        } else if (renderable instanceof TilemapRenderer tilemapRenderer) {
            renderTilemap(tilemapRenderer);
        } else {
            System.err.println("WARNING: Unknown Renderable type: " + renderable.getClass().getSimpleName());
        }
    }

    private void renderSpriteRenderer(SpriteRenderer spriteRenderer) {
        if (cullingSystem.shouldRender(spriteRenderer)) {
            renderer.drawSpriteRenderer(spriteRenderer);
        }
    }

    private void renderTilemap(TilemapRenderer tilemapRenderer) {
        List<long[]> visibleChunks = cullingSystem.getVisibleChunks(tilemapRenderer);

        if (visibleChunks.isEmpty()) {
            return;
        }

        if (renderer instanceof BatchRenderer batchRenderer) {
            batchRenderer.drawTilemap(tilemapRenderer, visibleChunks);
        } else {
            System.err.println("WARNING: Tilemap rendering requires BatchRenderer");
        }
    }
}