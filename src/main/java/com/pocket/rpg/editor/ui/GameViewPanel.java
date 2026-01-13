package com.pocket.rpg.editor.ui;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.PlayModeController;
import com.pocket.rpg.editor.PlayModeController.PlayState;
import com.pocket.rpg.editor.camera.PreviewCamera;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.panels.UIPreviewRenderer;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.rendering.PreviewCameraAdapter;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.rendering.targets.FramebufferTarget;
import com.pocket.rpg.rendering.pipeline.RenderParams;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.core.Renderable;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;
import org.joml.Vector4f;

import java.util.List;

/**
 * ImGui panel that displays the game during Play Mode,
 * and a static preview when stopped.
 * <p>
 * <b>RENDERING ARCHITECTURE NOTE:</b>
 * This class uses {@link RenderPipeline} (UnifiedRenderer) for the static preview
 * because it shows what the game will look like at runtime:
 * <ul>
 *   <li>Uses game camera settings (position, orthographic size)</li>
 *   <li>Renders scene as the game would see it</li>
 *   <li>No editor-specific features (no layer dimming, no selection)</li>
 * </ul>
 * <p>
 * During Play Mode, delegates to {@link PlayModeController#getOutputTexture()}
 * which also uses RenderPipeline with full post-processing.
 * <p>
 * This is different from the Scene Viewport which uses {@code EditorSceneRenderer}
 * for editor-specific layer dimming and selection highlighting.
 * <p>
 * Replaces: {@code GamePreviewRenderer} (to be deleted in Phase 6)
 *
 * @see RenderPipeline
 * @see PlayModeController
 * @see com.pocket.rpg.editor.rendering.EditorSceneRenderer
 */
public class GameViewPanel {

    private final EditorContext context;
    private final PlayModeController playController;
    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;

    // Unified rendering pipeline for preview
    private RenderPipeline previewPipeline;
    private EditorFramebuffer previewFramebuffer;
    private ViewportConfig viewportConfig;
    private PreviewCamera previewCamera;
    private PreviewCameraAdapter cameraAdapter;

    // UI renderer for overlay
    private UIPreviewRenderer uiRenderer;

    // Cached aspect ratio
    private float aspectRatio;

    // Track scene for dirty detection
    private EditorScene lastScene;
    private boolean previewDirty = true;

    // Last rendered viewport info (for UI overlay)
    private float lastViewportX;
    private float lastViewportY;
    private float lastDisplayWidth;
    private float lastDisplayHeight;

    private boolean initialized = false;

    // Clear color for preview
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.1f, 0.1f, 0.15f, 1.0f);

    public GameViewPanel(EditorContext context, PlayModeController playController,
                         GameConfig gameConfig, RenderingConfig renderingConfig) {
        this.context = context;
        this.playController = playController;
        this.gameConfig = gameConfig;
        this.renderingConfig = renderingConfig;
        this.aspectRatio = (float) gameConfig.getGameWidth() / gameConfig.getGameHeight();
    }

    /**
     * Convenience constructor for backwards compatibility.
     */
    public GameViewPanel(EditorContext context, PlayModeController playController, GameConfig gameConfig) {
        this(context, playController, gameConfig, context.getRenderingConfig());
    }

    /**
     * Initializes the preview renderer. Call after OpenGL context is ready.
     */
    public void init() {
        if (initialized) return;

        int width = gameConfig.getGameWidth();
        int height = gameConfig.getGameHeight();

        // Create framebuffer for preview
        previewFramebuffer = new EditorFramebuffer(width, height);
        previewFramebuffer.init();

        // Create viewport and camera
        viewportConfig = new ViewportConfig(width, height, width, height);
        previewCamera = new PreviewCamera(viewportConfig);
        cameraAdapter = new PreviewCameraAdapter(previewCamera);

        // Create pipeline (no post-processing for preview)
        previewPipeline = new RenderPipeline(viewportConfig, renderingConfig);
        previewPipeline.init();

        // UI renderer for overlay
        uiRenderer = new UIPreviewRenderer(gameConfig);

        initialized = true;
        System.out.println("GameViewPanel initialized (" + width + "x" + height + ")");
    }

    /**
     * Renders the Game View panel.
     */
    public void render() {
        if (playController == null) {
            return;
        }

        int windowFlags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

        ImGui.begin("Game", windowFlags);

        renderToolbar();
        ImGui.separator();

        PlayState state = playController.getState();

        if (state != PlayState.STOPPED) {
            // Playing or paused - show live game
            renderGameView();
        } else {
            // Stopped - show static preview
            renderPreview();
        }

        // Render UI overlay on top (both when playing and stopped)
        renderUIOverlay();

        ImGui.end();
    }

    /**
     * Renders the Play/Pause/Stop toolbar.
     */
    private void renderToolbar() {
        PlayState state = playController.getState();

        if (state == PlayState.STOPPED) {
            if (ImGui.button(FontAwesomeIcons.Play + " Play")) {
                playController.play();
            }

            ImGui.sameLine();
            ImGui.textDisabled("Scene: " + getSceneName());
        } else {
            if (state == PlayState.PLAYING) {
                if (ImGui.button(FontAwesomeIcons.Pause + " Pause")) {
                    playController.pause();
                }
            } else {
                if (ImGui.button(FontAwesomeIcons.Play + " Resume")) {
                    playController.resume();
                }
            }

            ImGui.sameLine();

            if (ImGui.button(FontAwesomeIcons.Stop + " Stop")) {
                playController.stop();
            }

            ImGui.sameLine();

            String stateText = state == PlayState.PLAYING ? "Playing" : "Paused";
            ImGui.textColored(0.4f, 0.8f, 0.4f, 1.0f, stateText);
        }
    }

    /**
     * Renders the live game view with pillarboxing.
     */
    private void renderGameView() {
        int textureId = playController.getOutputTexture();
        renderTextureWithAspectRatio(textureId);
    }

    /**
     * Renders the static preview when stopped.
     */
    private void renderPreview() {
        if (!initialized) {
            init();
        }

        EditorScene scene = context.getCurrentScene();

        // Check if we need to re-render
        boolean needsRender = previewDirty;

        if (scene != lastScene) {
            needsRender = true;
            lastScene = scene;
        }

        if (scene != null && scene.isDirty()) {
            needsRender = true;
        }

        // Render preview if needed
        if (needsRender) {
            renderPreviewToFramebuffer(scene);
            previewDirty = false;
        }

        // Display the preview texture
        int textureId = previewFramebuffer != null ? previewFramebuffer.getTextureId() : 0;
        renderTextureWithAspectRatio(textureId);

        // Show camera info overlay
        if (scene != null) {
            renderCameraInfoOverlay(scene);
        }
    }

    /**
     * Renders scene preview to framebuffer using RenderPipeline.
     */
    private void renderPreviewToFramebuffer(EditorScene scene) {
        if (previewPipeline == null || previewFramebuffer == null) return;

        // Apply scene camera settings
        if (scene != null) {
            SceneCameraSettings settings = scene.getCameraSettings();

            // DEBUG
            System.out.println("[DEBUG Preview] SceneCameraSettings orthoSize: " +
                    settings.getOrthographicSize());
            previewCamera.applySceneSettings(settings.getPosition(), settings.getOrthographicSize());
            // DEBUG
            System.out.println("[DEBUG Preview] After apply, previewCamera orthoSize: " +
                    previewCamera.getOrthographicSize());
        }

        // Get renderables (tilemaps + entities)
        List<Renderable> renderables = scene != null ? scene.getRenderables() : List.of();

        // Create render target
        FramebufferTarget target = new FramebufferTarget(previewFramebuffer);

        // Build params - scene only
        RenderParams params = RenderParams.builder()
                .renderables(renderables)
                .camera(cameraAdapter)
                .clearColor(CLEAR_COLOR)
                .renderScene(true)
                .renderUI(false)
                .renderPostFx(false)
                .renderOverlay(false)
                .build();

        // Execute pipeline
        previewPipeline.execute(target, params);
        System.out.println("[Preview] orthoSize=" + previewCamera.getOrthographicSize() +
                ", zoom=" + previewCamera.getZoom());
    }

    /**
     * Renders UI elements on top of the game view.
     */
    private void renderUIOverlay() {
        if (uiRenderer == null) return;

        EditorScene scene = context.getCurrentScene();
        if (scene == null) return;

        if (lastDisplayWidth <= 0 || lastDisplayHeight <= 0) return;

        var drawList = ImGui.getWindowDrawList();

        float gameWidth = gameConfig.getGameWidth();
        float scale = lastDisplayWidth / gameWidth;

        uiRenderer.render(drawList, scene, lastViewportX, lastViewportY, scale, 0, 0);
    }

    /**
     * Renders a texture maintaining aspect ratio with pillarboxing/letterboxing.
     */
    private void renderTextureWithAspectRatio(int textureId) {
        ImVec2 available = new ImVec2();
        ImGui.getContentRegionAvail(available);

        if (available.x <= 0 || available.y <= 0) {
            return;
        }

        float panelRatio = available.x / available.y;
        float displayWidth, displayHeight;

        if (panelRatio > aspectRatio) {
            displayHeight = available.y;
            displayWidth = displayHeight * aspectRatio;
        } else {
            displayWidth = available.x;
            displayHeight = displayWidth / aspectRatio;
        }

        float offsetX = (available.x - displayWidth) / 2f;
        float offsetY = (available.y - displayHeight) / 2f;

        ImVec2 cursorPos = new ImVec2();
        ImGui.getCursorPos(cursorPos);
        ImGui.setCursorPos(cursorPos.x + offsetX, cursorPos.y + offsetY);

        // Store viewport info for UI overlay
        ImVec2 windowPos = ImGui.getWindowPos();
        lastViewportX = windowPos.x + cursorPos.x + offsetX;
        lastViewportY = windowPos.y + cursorPos.y + offsetY;
        lastDisplayWidth = displayWidth;
        lastDisplayHeight = displayHeight;

        // Render texture (flip UV vertically for OpenGL)
        ImGui.image(textureId, displayWidth, displayHeight, 0, 1, 1, 0);
    }

    /**
     * Renders camera info overlay in the preview.
     */
    private void renderCameraInfoOverlay(EditorScene scene) {
        var settings = scene.getCameraSettings();

        ImVec2 windowPos = ImGui.getWindowPos();
        ImVec2 contentMin = ImGui.getWindowContentRegionMin();

        float overlayX = windowPos.x + contentMin.x + 5;
        float overlayY = windowPos.y + contentMin.y + 25;

        var drawList = ImGui.getWindowDrawList();

        String info = String.format("Camera: (%.1f, %.1f) | Size: %.1f",
                settings.getPosition().x, settings.getPosition().y,
                settings.getOrthographicSize());

        ImVec2 textSize = new ImVec2();
        ImGui.calcTextSize(textSize, info);

        drawList.addRectFilled(
                overlayX - 2, overlayY - 2,
                overlayX + textSize.x + 4, overlayY + textSize.y + 2,
                ImGui.colorConvertFloat4ToU32(0.0f, 0.0f, 0.0f, 0.6f)
        );

        drawList.addText(overlayX, overlayY,
                ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 1.0f),
                info);
    }

    private String getSceneName() {
        if (context.getCurrentScene() != null) {
            return context.getCurrentScene().getName();
        }
        return "No Scene";
    }

    public void updateAspectRatio() {
        aspectRatio = (float) gameConfig.getGameWidth() / gameConfig.getGameHeight();
    }

    public void markPreviewDirty() {
        previewDirty = true;
    }

    public void destroy() {
        if (previewPipeline != null) {
            previewPipeline.destroy();
            previewPipeline = null;
        }

        if (previewFramebuffer != null) {
            previewFramebuffer.destroy();
            previewFramebuffer = null;
        }

        viewportConfig = null;
        previewCamera = null;
        cameraAdapter = null;
        uiRenderer = null;
        initialized = false;
    }
}
