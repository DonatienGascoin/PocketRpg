package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.scenes.Scene;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

import java.util.List;

import static org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL33.glClear;
import static org.lwjgl.opengl.GL33.glClearColor;

/**
 * Orchestrates the complete rendering pipeline:
 * 1. Camera system updates
 * 2. Culling system updates
 * 3. Sprite rendering with culling
 * 4. Statistics reporting
 */
public class RenderPipeline {

    private final CameraSystem cameraSystem;
    /**
     * -- GETTER --
     *  Gets the culling system.
     */
    @Getter
    private final CullingSystem cullingSystem;
    /**
     * -- GETTER --
     *  Gets the renderer.
     */
    @Getter
    private final Renderer renderer;
    /**
     * -- SETTER --
     * Sets the statistics reporter.
     * Set to null to disable statistics reporting.
     */
    @Setter
    private StatisticsReporter statisticsReporter;

    /**
     * Creates a render pipeline with the specified components.
     */
    public RenderPipeline(Renderer renderer, CameraSystem cameraSystem) {
        this.renderer = renderer;
        this.cullingSystem = new CullingSystem();
        this.cameraSystem = cameraSystem;
    }

    /**
     * Renders a scene with full pipeline (camera, culling, rendering).
     *
     * @param scene The scene to render
     */
    public void render(Scene scene) {
        if (scene == null) return;

        // 1. Update camera system
        cameraSystem.updateFrame();

        // 2. Update culling system
        cullingSystem.updateFrame(cameraSystem.getActiveCamera());

        // 3. Get active camera and matrices
        Camera activeCamera = cameraSystem.getActiveCamera();
        Vector4f clearColor = cameraSystem.getClearColor();

        // 4. Clear screen
        glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // 5. Begin rendering with camera matrices
        renderer.beginWithMatrices(
                cameraSystem.getProjectionMatrix(),
                cameraSystem.getViewMatrix(),
                clearColor
        );

        // 6. Render all sprite renderers with culling
        List<SpriteRenderer> spriteRenderers = scene.getSpriteRenderers();
        for (SpriteRenderer spriteRenderer : spriteRenderers) {
            if (shouldRenderSprite(spriteRenderer)) {
                // Sprite is visible - render it
                if (cullingSystem.shouldRender(spriteRenderer)) {
                    renderer.drawSpriteRenderer(spriteRenderer);
                }
            }
        }

        // 7. End rendering
        renderer.end();

        // 8. Report statistics if reporter is set
        if (statisticsReporter != null) {
            statisticsReporter.report(cullingSystem.getStatistics());
        }
    }

    /**
     * Checks if a sprite should be considered for rendering.
     * Basic visibility checks before culling.
     */
    private boolean shouldRenderSprite(SpriteRenderer spriteRenderer) {
        return spriteRenderer != null &&
                spriteRenderer.isEnabled() &&
                spriteRenderer.getSprite() != null &&
                spriteRenderer.getGameObject() != null &&
                spriteRenderer.getGameObject().isEnabled();
    }
}