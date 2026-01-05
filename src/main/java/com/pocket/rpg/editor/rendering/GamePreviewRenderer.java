package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.editor.camera.PreviewCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.LayerUtils;
import com.pocket.rpg.editor.scene.LayerUtils.LayerRenderInfo;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.rendering.SceneRenderingBackend;
import com.pocket.rpg.rendering.SpriteBatch;
import lombok.Getter;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33.glViewport;

/**
 * Renders EditorScene preview using game camera settings.
 * <p>
 * Used by GameViewPanel to show a static preview of what the game
 * will look like when stopped (similar to Unity's Game view).
 */
public class GamePreviewRenderer {

    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;

    private EditorFramebuffer framebuffer;
    private SceneRenderingBackend backend;
    private EntityRenderer entityRenderer;
    private PreviewCamera previewCamera;
    private ViewportConfig viewportConfig;

    @Getter private boolean initialized = false;
    @Getter private boolean dirty = true;

    public GamePreviewRenderer(GameConfig gameConfig, RenderingConfig renderingConfig) {
        this.gameConfig = gameConfig;
        this.renderingConfig = renderingConfig;
    }

    public void init() {
        if (initialized) return;

        int width = gameConfig.getGameWidth();
        int height = gameConfig.getGameHeight();

        framebuffer = new EditorFramebuffer(width, height);
        framebuffer.init();

        viewportConfig = new ViewportConfig(gameConfig);
        previewCamera = new PreviewCamera(viewportConfig);

        backend = new SceneRenderingBackend(renderingConfig);
        backend.init(width, height);

        entityRenderer = new EntityRenderer();

        initialized = true;
        System.out.println("GamePreviewRenderer initialized (" + width + "x" + height + ")");
    }

    public void render(EditorScene scene) {
        if (!initialized) {
            init();
        }

        if (scene == null) {
            renderEmpty();
            return;
        }

        // Apply scene camera settings
        SceneCameraSettings settings = scene.getCameraSettings();
        previewCamera.applySceneSettings(settings.getPosition(), settings.getOrthographicSize());

        framebuffer.bind();
        glViewport(0, 0, framebuffer.getWidth(), framebuffer.getHeight());

        Vector4f bgColor = renderingConfig.getClearColor();
        framebuffer.clear(bgColor.x, bgColor.y, bgColor.z, 1.0f);

        backend.begin(previewCamera);

        renderTilemapLayers(scene);

        SpriteBatch batch = backend.getSpriteBatch();
        entityRenderer.render(batch, scene);

        backend.end();

        framebuffer.unbind();
        dirty = false;
    }

    private void renderEmpty() {
        framebuffer.bind();
        framebuffer.clear(0.1f, 0.1f, 0.1f, 1.0f);
        framebuffer.unbind();
        dirty = false;
    }

    private void renderTilemapLayers(EditorScene scene) {
        for (LayerRenderInfo info : LayerUtils.getLayersForPreviewRendering(scene)) {
            backend.renderTilemap(info.layer().getTilemap());
        }
    }

    public int getOutputTexture() {
        return framebuffer != null ? framebuffer.getTextureId() : 0;
    }

    public int getWidth() {
        return gameConfig.getGameWidth();
    }

    public int getHeight() {
        return gameConfig.getGameHeight();
    }

    public void markDirty() {
        dirty = true;
    }

    public void destroy() {
        if (backend != null) {
            backend.destroy();
            backend = null;
        }

        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }

        entityRenderer = null;
        previewCamera = null;
        viewportConfig = null;
        initialized = false;

        System.out.println("GamePreviewRenderer destroyed");
    }
}
