package com.pocket.rpg.rendering;

import com.pocket.rpg.components.SpriteRenderer;
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
 * 3. Sprite rendering with culling
 * 4. Statistics reporting
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

            // 7. Render all sprite renderers with culling
            List<SpriteRenderer> spriteRenderers = scene.getSpriteRenderers();
            for (SpriteRenderer spriteRenderer : spriteRenderers) {
                if (shouldRenderSprite(spriteRenderer)) {
                    // Sprite is visible - render it
                    if (cullingSystem.shouldRender(spriteRenderer)) {
                        renderer.drawSpriteRenderer(spriteRenderer);
                    }
                }
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
     * Handles static batch dirty flag.
     * Uses polymorphism to avoid casting in main render method.
     */
    private void handleStaticBatchDirty() {
        // Polymorphic call - BatchRenderer will handle it, Renderer will ignore it
        if (renderer instanceof BatchRenderer batchRenderer) {
            batchRenderer.getBatch().markStaticBatchDirty();
        }
    }

    /**
     * Checks if a sprite should be considered for rendering.
     * Basic visibility checks before culling.
     */
    private boolean shouldRenderSprite(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null) {
            return false;
        }

        if (!spriteRenderer.isEnabled()) {
            return false;
        }

        if (spriteRenderer.getSprite() == null) {
            return false;
        }

        if (spriteRenderer.getGameObject() == null) {
            return false;
        }

        if (!spriteRenderer.getGameObject().isEnabled()) {
            return false;
        }

        return true;
    }
}