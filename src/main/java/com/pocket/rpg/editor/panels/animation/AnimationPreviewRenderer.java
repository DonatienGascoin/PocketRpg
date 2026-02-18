package com.pocket.rpg.editor.panels.animation;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.editor.rendering.SpritePreviewRenderer;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.editor.core.EditorColors;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.function.IntConsumer;

/**
 * Renders the animation preview with playback controls.
 * Uses the render pipeline for WYSIWYG sprite display with pivot support.
 */
public class AnimationPreviewRenderer {

    /**
     * Zoom mode for preview display.
     */
    public enum ZoomMode {
        ZOOM_0_5X("0.5x", 0.5f),
        FIT("Fit", 1.0f);

        public final String label;
        public final float multiplier;

        ZoomMode(String label, float multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }
    }

    // Animation reference
    private Animation animation;

    // Preview state
    private boolean isPlaying = false;
    private int currentPreviewFrame = 0;
    private float previewTimer = 0f;
    private float previewSpeed = 1.0f;
    private ZoomMode zoomMode = ZoomMode.FIT;

    // Cached max sprite dimensions (world units, for stable preview sizing)
    private float maxSpriteWorldWidth = 0f;
    private float maxSpriteWorldHeight = 0f;

    // Pipeline-based sprite renderer
    private final SpritePreviewRenderer spriteRenderer = new SpritePreviewRenderer();

    // Callback when preview frame changes (for syncing with timeline)
    private IntConsumer onPreviewFrameChanged;

    public AnimationPreviewRenderer() {
    }

    /**
     * Sets the animation to preview.
     */
    public void setAnimation(Animation animation) {
        this.animation = animation;
        this.currentPreviewFrame = 0;
        this.previewTimer = 0f;
        this.isPlaying = false;
        recalculateMaxSpriteDimensions();
    }

    /**
     * Sets callback for when preview frame changes.
     */
    public void setOnPreviewFrameChanged(IntConsumer callback) {
        this.onPreviewFrameChanged = callback;
    }

    // Getters
    public boolean isPlaying() { return isPlaying; }
    public int getCurrentPreviewFrame() { return currentPreviewFrame; }
    public float getPreviewTimer() { return previewTimer; }
    public float getPreviewSpeed() { return previewSpeed; }
    public ZoomMode getZoomMode() { return zoomMode; }

    // Setters
    public void setPlaying(boolean playing) { this.isPlaying = playing; }
    public void setPreviewSpeed(float speed) { this.previewSpeed = speed; }
    public void setZoomMode(ZoomMode mode) { this.zoomMode = mode; }

    /**
     * Sets the current preview frame directly (e.g., when user clicks timeline).
     */
    public void setCurrentPreviewFrame(int frame) {
        if (animation != null && frame >= 0 && frame < animation.getFrameCount()) {
            this.currentPreviewFrame = frame;
            this.previewTimer = 0f;
        }
    }

    /**
     * Starts playback.
     */
    public void play() {
        // Reset to start if a non-looping animation has finished
        if (animation != null && !animation.isLooping()
                && currentPreviewFrame >= animation.getFrameCount() - 1) {
            currentPreviewFrame = 0;
            previewTimer = 0f;
        }
        isPlaying = true;
    }

    /**
     * Stops playback and resets to first frame.
     */
    public void stop() {
        isPlaying = false;
        currentPreviewFrame = 0;
        previewTimer = 0f;
    }

    /**
     * Toggles playback state.
     */
    public void togglePlayback() {
        if (!isPlaying) {
            play();
        } else {
            isPlaying = false;
        }
    }

    /**
     * Resets preview timer (for when frame is manually changed).
     */
    public void resetPreviewTimer() {
        previewTimer = 0f;
    }

    /**
     * Renders the preview panel.
     */
    public void render() {
        // Zoom controls
        ImGui.text("Preview");
        ImGui.sameLine();

        for (ZoomMode mode : ZoomMode.values()) {
            boolean isSelected = zoomMode == mode;
            if (isSelected) {
                EditorColors.pushInfoButton();
            }
            if (ImGui.smallButton(mode.label)) {
                zoomMode = mode;
            }
            if (isSelected) {
                EditorColors.popButtonColors();
            }
            ImGui.sameLine();
        }
        ImGui.newLine();

        ImGui.separator();

        // Preview area
        ImVec2 availSize = ImGui.getContentRegionAvail();
        float previewWidth = availSize.x;
        float previewHeight = availSize.y;

        if (previewWidth <= 0 || previewHeight <= 0) return;

        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        float previewX = cursorPos.x;
        float previewY = cursorPos.y;

        ImDrawList drawList = ImGui.getWindowDrawList();

        // Get current frame sprite
        Sprite sprite = null;
        if (animation != null && animation.getFrameCount() > 0) {
            int frameIndex = Math.min(currentPreviewFrame, animation.getFrameCount() - 1);
            sprite = getFrameSpriteSafe(frameIndex);
        }

        // Scale max ref dimensions by zoom
        float refW = maxSpriteWorldWidth > 0 ? maxSpriteWorldWidth / zoomMode.multiplier : 0;
        float refH = maxSpriteWorldHeight > 0 ? maxSpriteWorldHeight / zoomMode.multiplier : 0;

        // Render through pipeline (handles checker bg, pivot, and "No animation" text)
        spriteRenderer.renderSprite(drawList, sprite,
                previewX, previewY, previewWidth, previewHeight,
                refW, refH);

        // Invisible button for interaction area
        ImGui.invisibleButton("PreviewArea", previewWidth, previewHeight);

        // Update playback
        updatePlayback(ImGui.getIO().getDeltaTime());
    }

    /**
     * Cleans up GPU resources.
     */
    public void destroy() {
        spriteRenderer.destroy();
    }

    /**
     * Updates playback timing.
     */
    private void updatePlayback(float deltaTime) {
        if (!isPlaying || animation == null || animation.getFrameCount() == 0) {
            return;
        }

        previewTimer += deltaTime * previewSpeed;

        AnimationFrame currentFrame = animation.getFrame(currentPreviewFrame);
        while (previewTimer >= currentFrame.duration()) {
            previewTimer -= currentFrame.duration();
            currentPreviewFrame++;

            if (currentPreviewFrame >= animation.getFrameCount()) {
                if (animation.isLooping()) {
                    currentPreviewFrame = 0;
                } else {
                    currentPreviewFrame = animation.getFrameCount() - 1;
                    isPlaying = false;
                    return;
                }
            }

            currentFrame = animation.getFrame(currentPreviewFrame);

            // Notify listener of frame change
            if (onPreviewFrameChanged != null) {
                onPreviewFrameChanged.accept(currentPreviewFrame);
            }
        }
    }

    /**
     * Recalculates max sprite dimensions for stable preview sizing.
     */
    public void recalculateMaxSpriteDimensions() {
        maxSpriteWorldWidth = 0f;
        maxSpriteWorldHeight = 0f;

        if (animation == null) return;

        for (int i = 0; i < animation.getFrameCount(); i++) {
            Sprite sprite = getFrameSpriteSafe(i);
            if (sprite != null) {
                maxSpriteWorldWidth = Math.max(maxSpriteWorldWidth, sprite.getWorldWidth());
                maxSpriteWorldHeight = Math.max(maxSpriteWorldHeight, sprite.getWorldHeight());
            }
        }
    }

    /**
     * Safely gets a frame's sprite, returning null if loading fails or path is empty.
     */
    private Sprite getFrameSpriteSafe(int frameIndex) {
        if (animation == null || frameIndex < 0 || frameIndex >= animation.getFrameCount()) {
            return null;
        }
        AnimationFrame frame = animation.getFrame(frameIndex);
        if (!frame.hasSprite()) {
            return null;
        }
        try {
            return animation.getFrameSprite(frameIndex);
        } catch (Exception e) {
            return null;
        }
    }
}
