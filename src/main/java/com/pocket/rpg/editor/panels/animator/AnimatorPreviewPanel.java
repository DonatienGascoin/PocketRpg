package com.pocket.rpg.editor.panels.animator;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.animation.animator.AnimatorController;
import com.pocket.rpg.animation.animator.AnimatorParameter;
import com.pocket.rpg.animation.animator.ParameterType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.rendering.SpritePreviewRenderer;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import lombok.Getter;

/**
 * Preview panel for animator editor.
 * Shows animation preview using the render pipeline (WYSIWYG with pivot support)
 * and allows parameter value editing for testing.
 */
public class AnimatorPreviewPanel {

    private static final float PREVIEW_MIN_HEIGHT = 150f;

    @Getter
    private final AnimatorPreviewState previewState = new AnimatorPreviewState();

    private final SpritePreviewRenderer spriteRenderer = new SpritePreviewRenderer();

    // Cached sprite dimensions (in world units for stable scaling)
    private float maxSpriteWorldWidth = 0f;
    private float maxSpriteWorldHeight = 0f;

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

    /**
     * Stops playback and resets state.
     */
    public void stopAndReset() {
        previewState.setPlaying(false);
        previewState.reset();
    }

    /**
     * Cleans up GPU resources.
     */
    public void destroy() {
        spriteRenderer.destroy();
    }

    private void renderPlaybackControls() {
        boolean isPlaying = previewState.isPlaying();

        // Play/Pause button - green when playing
        if (isPlaying) {
            EditorColors.pushSuccessButton();
            if (ImGui.button(MaterialIcons.Pause + "##pause", 30, 0)) {
                previewState.setPlaying(false);
            }
            EditorColors.popButtonColors();
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

        // Stop button - red tint when playing
        if (isPlaying) {
            EditorColors.pushDangerButton();
        }
        if (ImGui.button(MaterialIcons.Stop + "##stop", 30, 0)) {
            stopAndReset();
        }
        if (isPlaying) {
            EditorColors.popButtonColors();
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

        // Get current frame sprite
        Sprite sprite = null;
        Animation anim = previewState.getCurrentAnimation();
        if (anim != null && anim.getFrameCount() > 0) {
            int frameIndex = Math.min(previewState.getCurrentFrameIndex(), anim.getFrameCount() - 1);
            sprite = getFrameSpriteSafe(anim, frameIndex);
        }

        // Render through pipeline (handles checker bg, pivot, and "No animation" text)
        spriteRenderer.renderSprite(drawList, sprite,
                previewX, previewY, previewWidth, previewHeight,
                maxSpriteWorldWidth, maxSpriteWorldHeight);

        // Reserve space
        ImGui.dummy(previewWidth, previewHeight);
    }

    private void renderStateInfo() {
        // Current state
        String stateName = previewState.getCurrentStateName();
        ImGui.text("State: ");
        ImGui.sameLine();
        if (stateName != null) {
            EditorColors.textColored(EditorColors.SUCCESS, stateName);
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
        maxSpriteWorldWidth = 0f;
        maxSpriteWorldHeight = 0f;

        Animation anim = previewState.getCurrentAnimation();
        if (anim == null) return;

        for (int i = 0; i < anim.getFrameCount(); i++) {
            Sprite sprite = getFrameSpriteSafe(anim, i);
            if (sprite != null) {
                maxSpriteWorldWidth = Math.max(maxSpriteWorldWidth, sprite.getWorldWidth());
                maxSpriteWorldHeight = Math.max(maxSpriteWorldHeight, sprite.getWorldHeight());
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
