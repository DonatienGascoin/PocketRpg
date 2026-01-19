package com.pocket.rpg.editor.panels.animation;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

import java.util.function.IntConsumer;

/**
 * Renders the animation preview with playback controls.
 * Shows the current frame sprite with zoom options and handles playback timing.
 */
public class AnimationPreviewRenderer {

    private static final int CHECKER_SIZE = 8;

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

    // Cached max sprite dimensions (for stable preview sizing)
    private float maxSpriteWidth = 0f;
    private float maxSpriteHeight = 0f;

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
        isPlaying = !isPlaying;
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
                ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.7f, 1.0f);
            }
            if (ImGui.smallButton(mode.label)) {
                zoomMode = mode;
            }
            if (isSelected) {
                ImGui.popStyleColor();
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

        // Draw checker background
        drawCheckerBackground(drawList, previewX, previewY, previewWidth, previewHeight);

        // Draw current frame sprite
        if (animation != null && animation.getFrameCount() > 0) {
            int frameIndex = Math.min(currentPreviewFrame, animation.getFrameCount() - 1);
            Sprite sprite = getFrameSpriteSafe(frameIndex);
            if (sprite != null && sprite.getTexture() != null) {
                drawSpritePreview(drawList, sprite, previewX, previewY, previewWidth, previewHeight);
            }
        } else {
            // No animation - show placeholder text
            String text = animation == null ? "Select an animation" : "No frames";
            ImVec2 textSize = new ImVec2();
            ImGui.calcTextSize(textSize, text);
            float textX = previewX + (previewWidth - textSize.x) / 2;
            float textY = previewY + (previewHeight - textSize.y) / 2;
            drawList.addText(textX, textY, ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1f), text);
        }

        // Invisible button for interaction area
        ImGui.invisibleButton("PreviewArea", previewWidth, previewHeight);

        // Update playback
        updatePlayback(ImGui.getIO().getDeltaTime());
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
     * Draws checker background pattern.
     */
    private void drawCheckerBackground(ImDrawList drawList, float x, float y, float width, float height) {
        int colorA = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1f);
        int colorB = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 1f);

        int cols = (int) Math.ceil(width / CHECKER_SIZE);
        int rows = (int) Math.ceil(height / CHECKER_SIZE);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float rectX = x + col * CHECKER_SIZE;
                float rectY = y + row * CHECKER_SIZE;
                float rectW = Math.min(CHECKER_SIZE, x + width - rectX);
                float rectH = Math.min(CHECKER_SIZE, y + height - rectY);

                int color = ((row + col) % 2 == 0) ? colorA : colorB;
                drawList.addRectFilled(rectX, rectY, rectX + rectW, rectY + rectH, color);
            }
        }
    }

    /**
     * Draws the sprite in the preview area.
     */
    private void drawSpritePreview(ImDrawList drawList, Sprite sprite, float areaX, float areaY, float areaWidth, float areaHeight) {
        float spriteW = sprite.getWidth();
        float spriteH = sprite.getHeight();

        // Calculate fit scale based on max sprite dimensions (for stable preview)
        float refWidth = maxSpriteWidth > 0 ? maxSpriteWidth : spriteW;
        float refHeight = maxSpriteHeight > 0 ? maxSpriteHeight : spriteH;

        float fitScale = Math.min(areaWidth / refWidth, areaHeight / refHeight);

        // Apply zoom mode multiplier
        float scale = fitScale * zoomMode.multiplier;

        // Calculate display size for current sprite
        float displayWidth = spriteW * scale;
        float displayHeight = spriteH * scale;

        // Center in preview area
        float offsetX = (areaWidth - displayWidth) / 2;
        float offsetY = (areaHeight - displayHeight) / 2;

        float left = areaX + offsetX;
        float top = areaY + offsetY;
        float right = left + displayWidth;
        float bottom = top + displayHeight;

        // Draw sprite with UV coordinates (flip V for OpenGL)
        int textureId = sprite.getTexture().getTextureId();
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        drawList.addImage(textureId, left, top, right, bottom, u0, v1, u1, v0);
    }

    /**
     * Recalculates max sprite dimensions for stable preview sizing.
     */
    public void recalculateMaxSpriteDimensions() {
        maxSpriteWidth = 0f;
        maxSpriteHeight = 0f;

        if (animation == null) return;

        for (int i = 0; i < animation.getFrameCount(); i++) {
            Sprite sprite = getFrameSpriteSafe(i);
            if (sprite != null) {
                maxSpriteWidth = Math.max(maxSpriteWidth, sprite.getWidth());
                maxSpriteHeight = Math.max(maxSpriteHeight, sprite.getHeight());
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
