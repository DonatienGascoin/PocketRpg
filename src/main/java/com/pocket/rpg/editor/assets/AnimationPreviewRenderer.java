package com.pocket.rpg.editor.assets;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImGui;

/**
 * Preview and inspector renderer for Animation assets.
 * <p>
 * {@link #renderPreview} shows the first frame as a static thumbnail.
 * {@link #renderInspector} shows animated playback with controls and properties.
 */
public class AnimationPreviewRenderer implements AssetPreviewRenderer<Animation> {

    // Playback state
    private boolean isPlaying = false;
    private float playbackTime = 0;
    private int currentFrameIndex = 0;

    // Cached path for tracking asset changes
    private String cachedPath;

    @Override
    public void renderPreview(Animation animation, float maxSize) {
        if (animation == null || animation.getFrameCount() == 0) {
            ImGui.textDisabled("No frames");
            return;
        }

        try {
            Sprite sprite = animation.getFrameSprite(0);
            if (sprite != null && sprite.getTexture() != null) {
                float aspectRatio = (float) sprite.getWidth() / sprite.getHeight();
                float displayWidth, displayHeight;

                if (aspectRatio >= 1.0f) {
                    displayWidth = Math.min(maxSize, sprite.getWidth());
                    displayHeight = displayWidth / aspectRatio;
                } else {
                    displayHeight = Math.min(maxSize, sprite.getHeight());
                    displayWidth = displayHeight * aspectRatio;
                }

                ImGui.image(
                    sprite.getTexture().getTextureId(),
                    displayWidth, displayHeight,
                    sprite.getU0(), sprite.getV1(),
                    sprite.getU1(), sprite.getV0()
                );
            }
        } catch (Exception e) {
            ImGui.textDisabled("Could not render preview");
        }
    }

    @Override
    public void renderInspector(Animation animation, String assetPath, float maxSize) {
        // Reset state if asset changed
        if (!assetPath.equals(cachedPath)) {
            cachedPath = assetPath;
            isPlaying = false;
            playbackTime = 0;
            currentFrameIndex = 0;
        }

        // Update playback
        if (isPlaying && animation.getFrameCount() > 0) {
            updatePlayback(animation);
        }

        // Preview section
        ImGui.text("Preview");
        ImGui.separator();
        renderAnimatedPreview(animation, maxSize);

        // Playback controls
        renderPlaybackControls(animation);

        ImGui.separator();

        // Properties section (read-only)
        ImGui.text("Properties");
        ImGui.separator();
        renderProperties(animation);
    }

    private void updatePlayback(Animation animation) {
        float deltaTime = ImGui.getIO().getDeltaTime();
        playbackTime += deltaTime;

        float totalDuration = animation.getTotalDuration();
        if (totalDuration <= 0) {
            return;
        }

        if (playbackTime >= totalDuration) {
            if (animation.isLooping()) {
                playbackTime = playbackTime % totalDuration;
            } else {
                playbackTime = totalDuration;
                isPlaying = false;
            }
        }

        float accumulatedTime = 0;
        for (int i = 0; i < animation.getFrameCount(); i++) {
            accumulatedTime += animation.getFrame(i).duration();
            if (playbackTime < accumulatedTime) {
                currentFrameIndex = i;
                break;
            }
        }
    }

    private void renderAnimatedPreview(Animation animation, float maxSize) {
        if (animation.getFrameCount() == 0) {
            ImGui.textDisabled("No frames");
            return;
        }

        try {
            Sprite sprite = animation.getFrameSprite(currentFrameIndex);
            if (sprite != null && sprite.getTexture() != null) {
                float aspectRatio = (float) sprite.getWidth() / sprite.getHeight();
                float displayWidth, displayHeight;

                if (aspectRatio >= 1.0f) {
                    displayWidth = Math.min(maxSize, sprite.getWidth());
                    displayHeight = displayWidth / aspectRatio;
                } else {
                    displayHeight = Math.min(maxSize, sprite.getHeight());
                    displayWidth = displayHeight * aspectRatio;
                }

                float availWidth = ImGui.getContentRegionAvailX();
                if (displayWidth < availWidth) {
                    ImGui.setCursorPosX(ImGui.getCursorPosX() + (availWidth - displayWidth) / 2);
                }

                ImGui.image(
                    sprite.getTexture().getTextureId(),
                    displayWidth, displayHeight,
                    sprite.getU0(), sprite.getV1(),
                    sprite.getU1(), sprite.getV0()
                );

                ImGui.textDisabled("Frame: " + (currentFrameIndex + 1) + "/" + animation.getFrameCount());
            }
        } catch (Exception e) {
            ImGui.textDisabled("Could not render frame");
        }
    }

    private void renderPlaybackControls(Animation animation) {
        float buttonWidth = 60;

        float totalWidth = buttonWidth * 3 + ImGui.getStyle().getItemSpacingX() * 2;
        float availWidth = ImGui.getContentRegionAvailX();
        if (totalWidth < availWidth) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + (availWidth - totalWidth) / 2);
        }

        if (isPlaying) {
            if (ImGui.button(MaterialIcons.Pause + " Pause", buttonWidth, 0)) {
                isPlaying = false;
            }
        } else {
            if (ImGui.button(MaterialIcons.PlayArrow + " Play", buttonWidth, 0)) {
                isPlaying = true;
                if (playbackTime >= animation.getTotalDuration() && !animation.isLooping()) {
                    playbackTime = 0;
                    currentFrameIndex = 0;
                }
            }
        }

        ImGui.sameLine();

        if (ImGui.button(MaterialIcons.Stop + " Stop", buttonWidth, 0)) {
            isPlaying = false;
            playbackTime = 0;
            currentFrameIndex = 0;
        }

        ImGui.sameLine();

        if (ImGui.button(MaterialIcons.Replay + " Restart", buttonWidth, 0)) {
            playbackTime = 0;
            currentFrameIndex = 0;
            isPlaying = true;
        }
    }

    private void renderProperties(Animation animation) {
        ImGui.textDisabled("Frames:");
        ImGui.sameLine();
        ImGui.text(String.valueOf(animation.getFrameCount()));

        float duration = animation.getTotalDuration();
        ImGui.textDisabled("Duration:");
        ImGui.sameLine();
        ImGui.text(String.format("%.2fs", duration));

        if (duration > 0 && animation.getFrameCount() > 0) {
            float fps = animation.getFrameCount() / duration;
            ImGui.textDisabled("FPS:");
            ImGui.sameLine();
            ImGui.text(String.format("%.1f", fps));
        }

        ImGui.textDisabled("Loop:");
        ImGui.sameLine();
        ImGui.text(animation.isLooping() ? "Yes" : "No");
    }

    @Override
    public Class<Animation> getAssetType() {
        return Animation.class;
    }
}
