package com.pocket.rpg.editor.panels.animator;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.animation.animator.AnimatorController;
import com.pocket.rpg.animation.animator.AnimatorParameter;
import com.pocket.rpg.animation.animator.ParameterType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import lombok.Getter;

/**
 * Preview panel for animator editor.
 * Shows animation preview and allows parameter value editing for testing.
 */
public class AnimatorPreviewPanel {

    private static final int CHECKER_SIZE = 8;
    private static final float PREVIEW_MIN_HEIGHT = 150f;

    @Getter
    private final AnimatorPreviewState previewState = new AnimatorPreviewState();

    // Cached sprite dimensions
    private float maxSpriteWidth = 32f;
    private float maxSpriteHeight = 32f;

    public AnimatorPreviewPanel() {
    }

    /**
     * Sets the controller for preview.
     */
    public void setController(AnimatorController controller) {
        previewState.setController(controller);
        recalculateMaxSpriteDimensions();
    }

    /**
     * Updates preview state.
     */
    public void update(float deltaTime) {
        previewState.update(deltaTime);
    }

    /**
     * Renders the preview panel.
     */
    public void render(AnimatorController controller) {
        if (controller == null) {
            ImGui.textDisabled("No controller");
            return;
        }

        // Playback controls
        renderPlaybackControls();

        ImGui.separator();

        // Animation preview
        renderAnimationPreview();

        ImGui.separator();

        // State info
        renderStateInfo();
    }

    private void renderPlaybackControls() {
        boolean isPlaying = previewState.isPlaying();

        // Play/Pause button
        if (isPlaying) {
            if (ImGui.button(MaterialIcons.Pause + "##pause", 30, 0)) {
                previewState.setPlaying(false);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Pause");
            }
        } else {
            if (ImGui.button(MaterialIcons.PlayArrow + "##play", 30, 0)) {
                previewState.setPlaying(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Play");
            }
        }

        ImGui.sameLine();

        // Stop button
        if (ImGui.button(MaterialIcons.Stop + "##stop", 30, 0)) {
            previewState.setPlaying(false);
            previewState.reset();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Stop & Reset");
        }
    }

    private void renderAnimationPreview() {
        ImVec2 availSize = ImGui.getContentRegionAvail();
        float previewWidth = availSize.x;
        float previewHeight = Math.min(availSize.y * 0.5f, PREVIEW_MIN_HEIGHT);

        if (previewWidth <= 0 || previewHeight <= 0) return;

        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        float previewX = cursorPos.x;
        float previewY = cursorPos.y;

        ImDrawList drawList = ImGui.getWindowDrawList();

        // Draw checker background
        drawCheckerBackground(drawList, previewX, previewY, previewWidth, previewHeight);

        // Draw current frame sprite
        Animation anim = previewState.getCurrentAnimation();
        if (anim != null && anim.getFrameCount() > 0) {
            int frameIndex = Math.min(previewState.getCurrentFrameIndex(), anim.getFrameCount() - 1);
            Sprite sprite = getFrameSpriteSafe(anim, frameIndex);
            if (sprite != null && sprite.getTexture() != null) {
                drawSpritePreview(drawList, sprite, previewX, previewY, previewWidth, previewHeight);
            }
        } else {
            // No animation
            String text = "No animation";
            ImVec2 textSize = new ImVec2();
            ImGui.calcTextSize(textSize, text);
            float textX = previewX + (previewWidth - textSize.x) / 2;
            float textY = previewY + (previewHeight - textSize.y) / 2;
            drawList.addText(textX, textY, ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1f), text);
        }

        // Reserve space
        ImGui.dummy(previewWidth, previewHeight);
    }

    private void renderStateInfo() {
        // Current state
        String stateName = previewState.getCurrentStateName();
        ImGui.text("State: ");
        ImGui.sameLine();
        if (stateName != null) {
            ImGui.textColored(0.4f, 0.8f, 0.4f, 1f, stateName);
        } else {
            ImGui.textDisabled("none");
        }

        // Frame info
        Animation anim = previewState.getCurrentAnimation();
        if (anim != null && anim.getFrameCount() > 0) {
            int frame = previewState.getCurrentFrameIndex();
            int total = anim.getFrameCount();
            ImGui.text("Frame: " + (frame + 1) + "/" + total);
        }

        // Pending transition indicator
        if (previewState.isWaitingForCompletion() && previewState.getPendingTransition() != null) {
            ImGui.textColored(1f, 0.6f, 0.2f, 1f,
                MaterialIcons.ArrowForward + " " + previewState.getPendingTransition().getTo());
        }
    }

    /**
     * Renders parameter value editors.
     * Call this from the parameters list section.
     */
    public void renderParameterValue(AnimatorParameter param) {
        String name = param.getName();
        ParameterType type = param.getType();

        ImGui.pushID("pval_" + name);

        switch (type) {
            case BOOL -> {
                boolean value = previewState.getBool(name);
                if (ImGui.checkbox("##val", value)) {
                    previewState.setBool(name, !value);
                }
            }
            case TRIGGER -> {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.3f, 0.1f, 1f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.6f, 0.4f, 0.2f, 1f);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.5f, 0.3f, 1f);
                if (ImGui.smallButton(MaterialIcons.Bolt)) {
                    previewState.setTrigger(name);
                }
                ImGui.popStyleColor(3);
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Fire trigger");
                }
            }
            case DIRECTION -> {
                Direction current = previewState.getDirection(name);
                ImGui.setNextItemWidth(80);
                if (ImGui.beginCombo("##dir", current.name())) {
                    for (Direction dir : Direction.values()) {
                        boolean isSelected = dir == current;
                        if (ImGui.selectable(dir.name(), isSelected)) {
                            previewState.setDirection(name, dir);
                        }
                        if (isSelected) {
                            ImGui.setItemDefaultFocus();
                        }
                    }
                    ImGui.endCombo();
                }
            }
        }

        ImGui.popID();
    }

    private void drawCheckerBackground(ImDrawList drawList, float x, float y, float width, float height) {
        int colorA = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f);
        int colorB = ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f);

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

    private void drawSpritePreview(ImDrawList drawList, Sprite sprite, float areaX, float areaY, float areaWidth, float areaHeight) {
        float spriteW = sprite.getWidth();
        float spriteH = sprite.getHeight();

        // Calculate fit scale
        float refWidth = maxSpriteWidth > 0 ? maxSpriteWidth : spriteW;
        float refHeight = maxSpriteHeight > 0 ? maxSpriteHeight : spriteH;

        float scale = Math.min(areaWidth / refWidth, areaHeight / refHeight);

        float displayWidth = spriteW * scale;
        float displayHeight = spriteH * scale;

        // Center in preview area
        float offsetX = (areaWidth - displayWidth) / 2;
        float offsetY = (areaHeight - displayHeight) / 2;

        float left = areaX + offsetX;
        float top = areaY + offsetY;
        float right = left + displayWidth;
        float bottom = top + displayHeight;

        // Draw sprite (flip V for OpenGL)
        int textureId = sprite.getTexture().getTextureId();
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        drawList.addImage(textureId, left, top, right, bottom, u0, v1, u1, v0);
    }

    private Sprite getFrameSpriteSafe(Animation anim, int frameIndex) {
        if (anim == null || frameIndex < 0 || frameIndex >= anim.getFrameCount()) {
            return null;
        }
        AnimationFrame frame = anim.getFrame(frameIndex);
        if (!frame.hasSprite()) {
            return null;
        }
        try {
            return anim.getFrameSprite(frameIndex);
        } catch (Exception e) {
            return null;
        }
    }

    private void recalculateMaxSpriteDimensions() {
        maxSpriteWidth = 32f;
        maxSpriteHeight = 32f;

        Animation anim = previewState.getCurrentAnimation();
        if (anim == null) return;

        for (int i = 0; i < anim.getFrameCount(); i++) {
            Sprite sprite = getFrameSpriteSafe(anim, i);
            if (sprite != null) {
                maxSpriteWidth = Math.max(maxSpriteWidth, sprite.getWidth());
                maxSpriteHeight = Math.max(maxSpriteHeight, sprite.getHeight());
            }
        }
    }

    /**
     * Gets the current state name for graph highlighting.
     */
    public String getActiveStateName() {
        return previewState.isPlaying() ? previewState.getCurrentStateName() : null;
    }

    /**
     * Gets the pending transition index for graph highlighting.
     */
    public String getPendingTransitionTarget() {
        if (previewState.isPlaying() && previewState.isWaitingForCompletion() && previewState.getPendingTransition() != null) {
            return previewState.getPendingTransition().getTo();
        }
        return null;
    }
}
