package com.pocket.rpg.editor.ui;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.PlayModeController;
import com.pocket.rpg.editor.PlayModeController.PlayState;
import com.pocket.rpg.editor.camera.PreviewCamera;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.rendering.EditorUIBridge;
import com.pocket.rpg.editor.rendering.PreviewCameraAdapter;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.rendering.targets.FramebufferTarget;
import com.pocket.rpg.rendering.pipeline.RenderParams;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import com.pocket.rpg.rendering.core.Renderable;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import java.util.ArrayList;
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

    // Cached aspect ratio
    private float aspectRatio;

    // UI canvas bridge (cached wrappers)
    private final EditorUIBridge uiBridge = new EditorUIBridge();

    // Track scene for dirty detection
    private EditorScene lastScene;
    private boolean previewDirty = true;
    private float lastOrthoSize = -1;

    // Last rendered viewport info (for UI overlay)
    private float lastViewportX;
    private float lastViewportY;
    private float lastDisplayWidth;
    private float lastDisplayHeight;

    private boolean initialized = false;

    // Post-processing for preview (refreshed before each render)
    private PostProcessor previewPostProcessor;
    private String lastEffectsHash = "";

    // Transition debug UI state
    private List<String> cachedScenes = new ArrayList<>();
    private final ImInt selectedSceneIndex = new ImInt(0);
    private final ImInt selectedTransitionIndex = new ImInt(0);
    private long lastSceneScanTime = 0;
    private static final long SCENE_SCAN_INTERVAL_MS = 2000;

    // Post-processing toggle state (shared between preview and play mode)
    private boolean postFxEnabled = true;

    // Editor audio mute state (runtime only, doesn't affect AudioConfig)
    private boolean editorAudioMuted = false;

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

        // Create pipeline (post-processor is refreshed before each render)
        previewPipeline = new RenderPipeline(viewportConfig, renderingConfig);
        previewPipeline.init();

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
            // Stopped - show static preview (UI is rendered by RenderPipeline)
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
            if (ImGui.button(MaterialIcons.PlayArrow + " Play")) {
                playController.play();
            }

            ImGui.sameLine();
            ImGui.textDisabled("Scene: " + getSceneName());

            // Audio mute and post-fx toggle on far right
            renderRightToolbarButtons(95);
        } else {
            if (state == PlayState.PLAYING) {
                if (ImGui.button(MaterialIcons.Pause + " Pause")) {
                    playController.pause();
                }
            } else {
                if (ImGui.button(MaterialIcons.PlayArrow + " Resume")) {
                    playController.resume();
                }
            }

            ImGui.sameLine();

            if (ImGui.button(MaterialIcons.Stop + " Stop")) {
                playController.stop();
            }

            ImGui.sameLine();

            String stateText = state == PlayState.PLAYING ? "Playing" : "Paused";
            ImGui.textColored(0.4f, 0.8f, 0.4f, 1.0f, stateText);

            // Transition debug button, audio mute, and post-fx toggle on far right
            ImGui.sameLine(ImGui.getWindowWidth() - ImGui.getStyle().getWindowPaddingX() - 200);
            ImGui.text("|");
            ImGui.sameLine();
            renderTransitionButton();
            ImGui.sameLine();
            renderRightToolbarButtonsInline();
        }
    }

    /**
     * Renders audio mute and post-fx buttons on the right side of toolbar (preview mode).
     */
    private void renderRightToolbarButtons(float rightOffset) {
        ImGui.sameLine(ImGui.getWindowWidth() - ImGui.getStyle().getWindowPaddingX() - rightOffset);
        ImGui.text("|");
        ImGui.sameLine();
        renderRightToolbarButtonsInline();
    }

    /**
     * Renders audio mute and post-fx buttons inline (no positioning).
     */
    private void renderRightToolbarButtonsInline() {
        renderAudioMuteButton();
        ImGui.sameLine();
        renderPostFxToggleButton();
    }

    /**
     * Renders the audio mute toggle button.
     * This is editor-only mute that doesn't affect the saved AudioConfig.
     */
    private void renderAudioMuteButton() {
        // CRITICAL: Capture state BEFORE button for push/pop matching (see common-pitfalls.md)
        boolean wasMuted = editorAudioMuted;

        // Style muted button with red color
        if (wasMuted) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.3f, 0.3f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.4f, 0.4f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.2f, 0.2f, 1f);
        }

        String icon = wasMuted ? MaterialIcons.VolumeOff : MaterialIcons.VolumeUp;
        if (ImGui.button(icon + "##audioMute")) {
            editorAudioMuted = !editorAudioMuted;
            applyEditorAudioMute();
        }

        // Pop uses same captured state as push
        if (wasMuted) {
            ImGui.popStyleColor(3);
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(wasMuted ? "Unmute Audio" : "Mute Audio");
        }
    }

    /**
     * Applies the editor mute state to the audio system.
     * Uses master channel mute which is runtime-only (doesn't persist to AudioConfig).
     */
    private void applyEditorAudioMute() {
        if (editorAudioMuted) {
            Audio.muteChannel(AudioChannel.MASTER);
        } else {
            Audio.unmuteChannel(AudioChannel.MASTER);
        }
    }

    /**
     * Renders the post-fx toggle button itself.
     */
    private void renderPostFxToggleButton() {
        // Color the button based on state
        if (postFxEnabled) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1.0f);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.4f, 0.4f, 0.4f, 1.0f);
        }

        String icon = postFxEnabled ? MaterialIcons.AutoFixHigh : MaterialIcons.AutoFixOff;
        if (ImGui.button(icon)) {
            postFxEnabled = !postFxEnabled;
            applyPostFxState();
            markPreviewDirty();
        }

        ImGui.popStyleColor();

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(postFxEnabled ? "Post-Processing: ON\nClick to disable" : "Post-Processing: OFF\nClick to enable");
        }
    }

    /**
     * Applies the post-fx enabled state to both preview and play mode post processors.
     */
    private void applyPostFxState() {
        // Apply to preview post processor
        if (previewPostProcessor != null) {
            previewPostProcessor.setEnabled(postFxEnabled);
        }

        // Apply to play mode post processor
        PostProcessor playPostProcessor = playController.getPostProcessor();
        if (playPostProcessor != null) {
            playPostProcessor.setEnabled(postFxEnabled);
        }
    }

    /**
     * Renders the transition debug button and its popup.
     */
    private void renderTransitionButton() {
        var transitionManager = playController.getTransitionManager();
        boolean isTransitioning = transitionManager != null && transitionManager.isTransitioning();

        // Show progress inline if transitioning
        if (isTransitioning) {
            float progress = transitionManager.getProgress();
            ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.6f, 0.2f, 1.0f);
            ImGui.button(MaterialIcons.SwapHoriz + " " + String.format("%.0f%%", progress * 100));
            ImGui.popStyleColor();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Transitioning to: " + transitionManager.getTargetScene());
            }
        } else {
            if (ImGui.button(MaterialIcons.BugReport + " Debug")) {
                ImGui.openPopup("TransitionPopup");
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Transition Debug");
            }
        }

        // Popup content
        if (ImGui.beginPopup("TransitionPopup")) {
            renderTransitionPopupContent();
            ImGui.endPopup();
        }
    }

    /**
     * Renders the content of the transition popup.
     */
    private void renderTransitionPopupContent() {
        // Refresh scene list periodically
        long now = System.currentTimeMillis();
        if (now - lastSceneScanTime > SCENE_SCAN_INTERVAL_MS) {
            cachedScenes = playController.getAvailableScenes();
            lastSceneScanTime = now;
        }

        // Scene and transition type on same line
        ImGui.text("Scene:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(120);
        if (!cachedScenes.isEmpty()) {
            String[] sceneArray = cachedScenes.toArray(new String[0]);
            if (selectedSceneIndex.get() >= sceneArray.length) {
                selectedSceneIndex.set(0);
            }
            ImGui.combo("##TargetScene", selectedSceneIndex, sceneArray);
        } else {
            ImGui.textDisabled("No scenes");
        }

        ImGui.sameLine();
        ImGui.text("Type:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(100);
        TransitionConfig.TransitionType[] types = TransitionConfig.TransitionType.values();
        String[] typeNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].name().replace("_", " ");
        }
        ImGui.combo("##TransitionType", selectedTransitionIndex, typeNames);

        ImGui.sameLine();

        // Trigger button
        var transitionManager = playController.getTransitionManager();
        boolean canTrigger = transitionManager != null &&
                !transitionManager.isTransitioning() &&
                !cachedScenes.isEmpty();

        if (!canTrigger) {
            ImGui.beginDisabled();
        }

        if (ImGui.button(MaterialIcons.PlayArrow + " Go")) {
            String targetScene = cachedScenes.get(selectedSceneIndex.get());
            TransitionConfig.TransitionType type = types[selectedTransitionIndex.get()];

            // Use fade durations from game config
            TransitionConfig defaultConfig = gameConfig.getDefaultTransitionConfig();
            TransitionConfig config = TransitionConfig.builder()
                    .type(type)
                    .fadeOutDuration(defaultConfig.getFadeOutDuration())
                    .fadeInDuration(defaultConfig.getFadeInDuration())
                    .fadeColor(defaultConfig.getFadeColor())
                    .build();

            transitionManager.startTransition(targetScene, config);
            ImGui.closeCurrentPopup();
        }

        if (!canTrigger) {
            ImGui.endDisabled();
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

        // Check if rendering config ortho size changed
        float currentOrthoSize = renderingConfig.getDefaultOrthographicSize(gameConfig.getGameHeight());
        if (currentOrthoSize != lastOrthoSize) {
            needsRender = true;
            lastOrthoSize = currentOrthoSize;
        }

        // Check if post-processing effects changed (count or properties)
        String currentEffectsHash = computeEffectsHash();
        if (!currentEffectsHash.equals(lastEffectsHash)) {
            needsRender = true;
            lastEffectsHash = currentEffectsHash;
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
     * Computes a hash of the current post-processing effects configuration.
     * Used to detect when effects have changed (added, removed, or properties modified).
     */
    private String computeEffectsHash() {
        List<PostEffect> effects = gameConfig.getPostProcessingEffects();
        if (effects == null || effects.isEmpty()) {
            return "empty";
        }

        StringBuilder sb = new StringBuilder();
        for (PostEffect effect : effects) {
            sb.append(effect.getClass().getSimpleName()).append(":");
            // Use reflection to get field values for change detection
            for (var field : effect.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (java.lang.reflect.Modifier.isTransient(field.getModifiers())) continue;
                if (field.getType().getSimpleName().equals("Shader")) continue;
                try {
                    field.setAccessible(true);
                    sb.append(field.getName()).append("=").append(field.get(effect)).append(",");
                } catch (Exception ignored) {}
            }
            sb.append(";");
        }
        return sb.toString();
    }

    /**
     * Initializes or reinitializes the preview PostProcessor.
     * Called before each preview render to ensure effects are up-to-date.
     */
    private void refreshPreviewPostProcessor() {
        // Clean up old post processor
        if (previewPostProcessor != null) {
            previewPostProcessor.destroy();
            previewPostProcessor = null;
            previewPipeline.setPostProcessor(null);
        }

        // Create new one if effects are configured
        List<PostEffect> effects = gameConfig.getPostProcessingEffects();
        if (effects != null && !effects.isEmpty()) {
            previewPostProcessor = new PostProcessor(gameConfig);
            previewPostProcessor.init(context.getWindow());
            previewPostProcessor.setEnabled(postFxEnabled); // Respect toggle state
            previewPipeline.setPostProcessor(previewPostProcessor);
        }
    }

    /**
     * Renders scene preview to framebuffer using RenderPipeline.
     * Includes UI rendering using the actual game fonts.
     */
    private void renderPreviewToFramebuffer(EditorScene scene) {
        if (previewPipeline == null || previewFramebuffer == null) return;

        // Always refresh post-processor to pick up effect changes (add/remove/property edits)
        refreshPreviewPostProcessor();

        // Apply scene camera settings (position from scene, ortho size from rendering config)
        if (scene != null) {
            SceneCameraSettings settings = scene.getCameraSettings();
            float orthoSize = renderingConfig.getDefaultOrthographicSize(gameConfig.getGameHeight());
            previewCamera.applySceneSettings(settings.getPosition(), orthoSize);
        }

        // Get renderables (tilemaps + entities)
        List<Renderable> renderables = scene != null ? scene.getRenderables() : List.of();

        // Get UI canvases via cached bridge (O(1) when hierarchy unchanged)
        List<UICanvas> uiCanvases = uiBridge.getUICanvases(scene);

        // Create render target
        FramebufferTarget target = new FramebufferTarget(previewFramebuffer);

        // Enable post-fx if we have a post processor with effects
        boolean hasPostFx = previewPostProcessor != null && !previewPostProcessor.getEffects().isEmpty();

        // Build params - scene + UI + post-fx
        RenderParams params = RenderParams.builder()
                .renderables(renderables)
                .camera(cameraAdapter)
                .clearColor(renderingConfig.getClearColor())
                .renderScene(true)
                .renderUI(!uiCanvases.isEmpty())
                .uiCanvases(uiCanvases)
                .renderPostFx(hasPostFx)
                .renderOverlay(false)
                .build();

        // Execute pipeline
        previewPipeline.execute(target, params);
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
        float orthoSize = renderingConfig.getDefaultOrthographicSize(gameConfig.getGameHeight());

        ImVec2 windowPos = ImGui.getWindowPos();
        ImVec2 contentMin = ImGui.getWindowContentRegionMin();

        float overlayX = windowPos.x + contentMin.x + 5;
        float overlayY = windowPos.y + contentMin.y + 25;

        var drawList = ImGui.getWindowDrawList();

        String info = String.format("Camera: (%.1f, %.1f) | Size: %.1f",
                settings.getPosition().x, settings.getPosition().y,
                orthoSize);

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
        if (previewPostProcessor != null) {
            previewPostProcessor.destroy();
            previewPostProcessor = null;
        }

        if (previewPipeline != null) {
            previewPipeline.destroy();
            previewPipeline = null;
        }

        if (previewFramebuffer != null) {
            previewFramebuffer.destroy();
            previewFramebuffer = null;
        }

        uiBridge.clear();
        viewportConfig = null;
        previewCamera = null;
        cameraAdapter = null;
        initialized = false;
    }
}
