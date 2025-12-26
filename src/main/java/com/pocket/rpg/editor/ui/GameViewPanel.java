package com.pocket.rpg.editor.ui;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.PlayModeController;
import com.pocket.rpg.editor.PlayModeController.PlayState;
import com.pocket.rpg.editor.rendering.GamePreviewRenderer;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;

/**
 * ImGui panel that displays the game during Play Mode,
 * and a static preview when stopped.
 * <p>
 * Features:
 * - Play/Pause/Stop toolbar
 * - Pillarboxed game display maintaining aspect ratio
 * - Static preview when stopped (using game camera settings)
 * - FPS display during play
 */
public class GameViewPanel {

    private final EditorContext context;
    private final PlayModeController playController;
    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;

    // Preview renderer for stopped state
    private GamePreviewRenderer previewRenderer;

    // Cached aspect ratio
    private float aspectRatio;

    // Track scene for dirty detection
    private EditorScene lastScene;
    private boolean lastSceneDirty;

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
        previewRenderer = new GamePreviewRenderer(gameConfig, renderingConfig);
        previewRenderer.init();
    }

    /**
     * Renders the Game View panel.
     */
    public void render() {
        // Don't render if controller is not set
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

        ImGui.end();
    }

    /**
     * Renders the Play/Pause/Stop toolbar.
     */
    private void renderToolbar() {
        PlayState state = playController.getState();

        if (state == PlayState.STOPPED) {
            // Play button
            if (ImGui.button("▶ Play")) {
                playController.play();
            }

            ImGui.sameLine();
            ImGui.textDisabled("Scene: " + getSceneName());
        } else {
            // Pause/Resume button
            if (state == PlayState.PLAYING) {
                if (ImGui.button("⏸ Pause")) {
                    playController.pause();
                }
            } else {
                if (ImGui.button("▶ Resume")) {
                    playController.resume();
                }
            }

            ImGui.sameLine();

            // Stop button
            if (ImGui.button("⏹ Stop")) {
                playController.stop();
            }

            ImGui.sameLine();

            // State indicator
            String stateText = state == PlayState.PLAYING ? "Playing" : "Paused";
            ImGui.textColored(0.4f, 0.8f, 0.4f, 1.0f, stateText);

            ImGui.sameLine();
            ImGui.text("| FPS: " + (int) ImGui.getIO().getFramerate());
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
        // Initialize preview renderer if needed
        if (previewRenderer == null || !previewRenderer.isInitialized()) {
            init();
        }

        EditorScene scene = context.getCurrentScene();

        // Check if we need to re-render
        boolean needsRender = previewRenderer.isDirty();

        // Scene changed?
        if (scene != lastScene) {
            needsRender = true;
            lastScene = scene;
            lastSceneDirty = scene != null && scene.isDirty();
        }

        // Scene became dirty?
        if (scene != null && scene.isDirty() != lastSceneDirty) {
            needsRender = true;
            lastSceneDirty = scene.isDirty();
        }

        // Render preview if needed
        if (needsRender) {
            previewRenderer.render(scene);
        }

        // Display the preview texture
        int textureId = previewRenderer.getOutputTexture();
        renderTextureWithAspectRatio(textureId);

        // Show camera info overlay
        if (scene != null) {
            renderCameraInfoOverlay(scene);
        }
    }

    /**
     * Renders a texture maintaining aspect ratio with pillarboxing/letterboxing.
     */
    private void renderTextureWithAspectRatio(int textureId) {
        // Get available content region
        ImVec2 available = new ImVec2();
        ImGui.getContentRegionAvail(available);

        if (available.x <= 0 || available.y <= 0) {
            return;
        }

        // Calculate display size maintaining aspect ratio
        float panelRatio = available.x / available.y;
        float displayWidth, displayHeight;

        if (panelRatio > aspectRatio) {
            // Panel is wider - pillarbox (black bars on sides)
            displayHeight = available.y;
            displayWidth = displayHeight * aspectRatio;
        } else {
            // Panel is taller - letterbox (black bars top/bottom)
            displayWidth = available.x;
            displayHeight = displayWidth / aspectRatio;
        }

        // Center the image
        float offsetX = (available.x - displayWidth) / 2f;
        float offsetY = (available.y - displayHeight) / 2f;

        ImVec2 cursorPos = new ImVec2();
        ImGui.getCursorPos(cursorPos);
        ImGui.setCursorPos(cursorPos.x + offsetX, cursorPos.y + offsetY);

        // Render texture (flip UV vertically for OpenGL)
        ImGui.image(textureId, displayWidth, displayHeight, 0, 1, 1, 0);

        // Handle mouse input on game view
        if (ImGui.isItemHovered()) {
            // Future: route mouse to game or show coordinates
        }
    }

    /**
     * Renders camera info overlay in the preview.
     */
    private void renderCameraInfoOverlay(EditorScene scene) {
        var settings = scene.getCameraSettings();

        ImVec2 windowPos = ImGui.getWindowPos();
        ImVec2 contentMin = ImGui.getWindowContentRegionMin();

        float overlayX = windowPos.x + contentMin.x + 5;
        float overlayY = windowPos.y + contentMin.y + 25; // Below separator

        var drawList = ImGui.getWindowDrawList();

        // Background
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

    /**
     * Gets the current scene name for display.
     */
    private String getSceneName() {
        if (context.getCurrentScene() != null) {
            return context.getCurrentScene().getName();
        }
        return "No Scene";
    }

    /**
     * Updates aspect ratio if GameConfig changes.
     */
    public void updateAspectRatio() {
        aspectRatio = (float) gameConfig.getGameWidth() / gameConfig.getGameHeight();
    }

    /**
     * Marks the preview as needing re-render.
     * Call when the scene changes externally.
     */
    public void markPreviewDirty() {
        if (previewRenderer != null) {
            previewRenderer.markDirty();
        }
    }

    /**
     * Destroys rendering resources.
     */
    public void destroy() {
        if (previewRenderer != null) {
            previewRenderer.destroy();
            previewRenderer = null;
        }
    }
}