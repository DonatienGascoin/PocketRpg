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
 * 
 * FIXED: Now notifies scene about rendering phase
 */
public class RenderPipeline {

    private final CameraSystem cameraSystem;
    @Getter
    private final CullingSystem cullingSystem;
    @Getter
    private final Renderer renderer;
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
     * FIX: Now properly handles scene rendering phase
     *
     * @param scene The scene to render
     */
    public void render(Scene scene) {
        if (scene == null) {
            System.err.println("WARNING: Attempted to render null scene");
            return;
        }

        try {
            // 1. Update camera system
            cameraSystem.updateFrame();

            // 2. Update culling system
            Camera activeCamera = cameraSystem.getActiveCamera();
            cullingSystem.updateFrame(activeCamera);

            // 3. Get camera matrices and clear color
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

            // FIX: Notify scene that rendering is starting
            scene.beginRendering();

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

            // FIX: Notify scene that rendering is complete
            scene.endRendering();

            // 8. Report statistics if reporter is set
            if (statisticsReporter != null) {
                statisticsReporter.report(cullingSystem.getStatistics());
            }

        } catch (Exception e) {
            System.err.println("ERROR during rendering: " + e.getMessage());
            e.printStackTrace();
            
            // Ensure scene exits rendering state even on error
            try {
                scene.endRendering();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Checks if a sprite should be considered for rendering.
     * Basic visibility checks before culling.
     * FIX: Enhanced null safety
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
