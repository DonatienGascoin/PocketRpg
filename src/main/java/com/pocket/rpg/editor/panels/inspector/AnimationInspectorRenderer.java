package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.OpenAnimationEditorEvent;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * Inspector renderer for Animation assets.
 * <p>
 * Features:
 * <ul>
 *   <li>Animated preview with play/stop controls</li>
 *   <li>Frame count, duration, FPS info</li>
 *   <li>Loop toggle (editable)</li>
 *   <li>"Open Animation Editor" button</li>
 * </ul>
 */
public class AnimationInspectorRenderer implements AssetInspectorRenderer<Animation> {

    // Playback state
    private boolean isPlaying = false;
    private float playbackTime = 0;
    private int currentFrameIndex = 0;

    // Change tracking
    private boolean hasChanges = false;
    private boolean originalLooping = true;

    // Cached path for tracking asset changes
    private String cachedPath;

    @Override
    public boolean render(Animation animation, String assetPath, float maxPreviewSize) {
        // Reset state if asset changed
        if (!assetPath.equals(cachedPath)) {
            cachedPath = assetPath;
            isPlaying = false;
            playbackTime = 0;
            currentFrameIndex = 0;
            hasChanges = false;
            originalLooping = animation.isLooping();
        }

        // Update playback
        if (isPlaying && animation.getFrameCount() > 0) {
            updatePlayback(animation);
        }

        // Preview section
        ImGui.text("Preview");
        ImGui.separator();
        renderAnimatedPreview(animation, maxPreviewSize);

        // Playback controls
        renderPlaybackControls(animation);

        ImGui.separator();

        // Properties section
        ImGui.text("Properties");
        ImGui.separator();
        renderProperties(animation);

        ImGui.separator();

        // Open Animation Editor button
        renderOpenEditorButton();

        return hasChanges;
    }

    /**
     * Updates playback timing and current frame.
     */
    private void updatePlayback(Animation animation) {
        float deltaTime = ImGui.getIO().getDeltaTime();
        playbackTime += deltaTime;

        float totalDuration = animation.getTotalDuration();
        if (totalDuration <= 0) {
            return;
        }

        // Handle looping
        if (playbackTime >= totalDuration) {
            if (animation.isLooping()) {
                playbackTime = playbackTime % totalDuration;
            } else {
                playbackTime = totalDuration;
                isPlaying = false;
            }
        }

        // Find current frame
        float accumulatedTime = 0;
        for (int i = 0; i < animation.getFrameCount(); i++) {
            accumulatedTime += animation.getFrame(i).duration();
            if (playbackTime < accumulatedTime) {
                currentFrameIndex = i;
                break;
            }
        }
    }

    /**
     * Renders the animated preview.
     */
    private void renderAnimatedPreview(Animation animation, float maxSize) {
        if (animation.getFrameCount() == 0) {
            ImGui.textDisabled("No frames");
            return;
        }

        try {
            Sprite sprite = animation.getFrameSprite(currentFrameIndex);
            if (sprite != null && sprite.getTexture() != null) {
                // Calculate display size maintaining aspect ratio
                float aspectRatio = (float) sprite.getWidth() / sprite.getHeight();
                float displayWidth, displayHeight;

                if (aspectRatio >= 1.0f) {
                    displayWidth = Math.min(maxSize, sprite.getWidth());
                    displayHeight = displayWidth / aspectRatio;
                } else {
                    displayHeight = Math.min(maxSize, sprite.getHeight());
                    displayWidth = displayHeight * aspectRatio;
                }

                // Center the preview
                float availWidth = ImGui.getContentRegionAvailX();
                if (displayWidth < availWidth) {
                    ImGui.setCursorPosX(ImGui.getCursorPosX() + (availWidth - displayWidth) / 2);
                }

                // Draw sprite using ImGui.image with UV coordinates
                // Note: V is flipped for OpenGL
                ImGui.image(
                    sprite.getTexture().getTextureId(),
                    displayWidth, displayHeight,
                    sprite.getU0(), sprite.getV1(),  // UV0 (flipped V)
                    sprite.getU1(), sprite.getV0()   // UV1 (flipped V)
                );

                // Frame indicator
                ImGui.textDisabled("Frame: " + (currentFrameIndex + 1) + "/" + animation.getFrameCount());
            }
        } catch (Exception e) {
            ImGui.textDisabled("Could not render frame");
        }
    }

    /**
     * Renders play/stop/restart controls.
     */
    private void renderPlaybackControls(Animation animation) {
        float buttonWidth = 60;

        // Center the controls
        float totalWidth = buttonWidth * 3 + ImGui.getStyle().getItemSpacingX() * 2;
        float availWidth = ImGui.getContentRegionAvailX();
        if (totalWidth < availWidth) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + (availWidth - totalWidth) / 2);
        }

        // Play/Pause button
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

        // Stop button
        if (ImGui.button(MaterialIcons.Stop + " Stop", buttonWidth, 0)) {
            isPlaying = false;
            playbackTime = 0;
            currentFrameIndex = 0;
        }

        ImGui.sameLine();

        // Restart button
        if (ImGui.button(MaterialIcons.Replay + " Restart", buttonWidth, 0)) {
            playbackTime = 0;
            currentFrameIndex = 0;
            isPlaying = true;
        }
    }

    /**
     * Renders animation properties.
     */
    private void renderProperties(Animation animation) {
        // Frame count (read-only)
        ImGui.textDisabled("Frames:");
        ImGui.sameLine();
        ImGui.text(String.valueOf(animation.getFrameCount()));

        // Duration (read-only)
        float duration = animation.getTotalDuration();
        ImGui.textDisabled("Duration:");
        ImGui.sameLine();
        ImGui.text(String.format("%.2fs", duration));

        // FPS (read-only)
        if (duration > 0 && animation.getFrameCount() > 0) {
            float fps = animation.getFrameCount() / duration;
            ImGui.textDisabled("FPS:");
            ImGui.sameLine();
            ImGui.text(String.format("%.1f", fps));
        }

        // Loop toggle (editable)
        ImGui.spacing();
        boolean looping = animation.isLooping();
        if (ImGui.checkbox("Loop", looping)) {
            animation.setLooping(!looping);
            hasChanges = animation.isLooping() != originalLooping;
        }
    }

    /**
     * Renders the "Open Animation Editor" button.
     */
    private void renderOpenEditorButton() {
        float buttonWidth = ImGui.getContentRegionAvailX();

        if (ImGui.button(MaterialIcons.Movie + " Open Animation Editor...", buttonWidth, 0)) {
            EditorEventBus.get().publish(new OpenAnimationEditorEvent(cachedPath));
        }
    }

    @Override
    public boolean hasEditableProperties() {
        // Loop is editable, but saving requires animation file modification
        // For now, return false to avoid confusion
        return false;
    }

    @Override
    public void save(Animation animation, String assetPath) {
        // Animation saving would require re-serializing the .anim.json file
        // This is more complex and might conflict with the Animation Editor
        // For now, changes to loop are runtime-only
        originalLooping = animation.isLooping();
        hasChanges = false;
    }

    @Override
    public void onDeselect() {
        isPlaying = false;
        playbackTime = 0;
        currentFrameIndex = 0;
        cachedPath = null;
        hasChanges = false;
    }

    @Override
    public Class<Animation> getAssetType() {
        return Animation.class;
    }
}
