package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.editor.EditorAudio;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

/**
 * Content implementation for previewing audio clips (.wav, .ogg, .mp3) in the unified AssetEditorPanel.
 * <p>
 * Read-only viewer with play/stop controls, playback progress bar, and clip metadata.
 * No save or undo needed — audio clips are binary assets that aren't edited in the editor.
 */
@EditorContentFor(com.pocket.rpg.audio.clips.AudioClip.class)
public class AudioClipEditorContent implements AssetEditorContent {

    private AudioClip clip;
    private AssetEditorShell shell;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        this.clip = (AudioClip) asset;
        this.shell = shell;
    }

    @Override
    public void onAssetUnloaded() {
        if (clip != null) {
            EditorAudio.stopPreview(clip);
        }
        clip = null;
    }

    @Override
    public Class<?> getAssetClass() {
        return AudioClip.class;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public void render() {
        if (clip == null) return;

        float contentWidth = ImGui.getContentRegionAvailX();
        float centerX = contentWidth / 2;

        ImGui.spacing();
        ImGui.spacing();

        // Audio icon (large, centered)
        String icon = MaterialIcons.AudioFile;
        ImVec2 iconSize = new ImVec2();
        ImGui.calcTextSize(iconSize, icon);
        ImGui.setCursorPosX(centerX - iconSize.x / 2);
        EditorColors.textColored(EditorColors.INFO, icon);

        ImGui.spacing();

        // Clip name (centered)
        String name = clip.getName();
        ImVec2 nameSize = new ImVec2();
        ImGui.calcTextSize(nameSize, name);
        ImGui.setCursorPosX(centerX - nameSize.x / 2);
        ImGui.text(name);

        ImGui.spacing();
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        ImGui.spacing();

        // Play/Stop button (centered, large)
        boolean isPlaying = EditorAudio.isPreviewingClip(clip);
        float buttonWidth = 120;
        ImGui.setCursorPosX(centerX - buttonWidth / 2);

        if (isPlaying) {
            EditorColors.pushDangerButton();
            if (ImGui.button(MaterialIcons.Stop + " Stop", buttonWidth, 32)) {
                EditorAudio.stopPreview(clip);
            }
            EditorColors.popButtonColors();
        } else {
            EditorColors.pushSuccessButton();
            if (ImGui.button(MaterialIcons.PlayArrow + " Play", buttonWidth, 32)) {
                EditorAudio.playPreview(clip);
            }
            EditorColors.popButtonColors();
        }

        ImGui.spacing();

        // Progress bar
        float progress = 0f;
        float playbackTime = 0f;
        if (isPlaying) {
            AudioHandle handle = EditorAudio.getPreviewHandle(clip);
            if (handle != null) {
                playbackTime = handle.getPlaybackTime();
                if (clip.getDuration() > 0) {
                    progress = playbackTime / clip.getDuration();
                }
            }
        }

        float progressBarWidth = Math.min(300, contentWidth - 40);
        float barX = centerX - progressBarWidth / 2;
        ImGui.setCursorPosX(barX);

        // Draw progress bar with centered overlay text
        String overlay = String.format("%.1f / %.1f s", playbackTime, clip.getDuration());
        ImVec2 cursorScreen = ImGui.getCursorScreenPos();
        float barHeight = 20;
        ImGui.progressBar(progress, progressBarWidth, barHeight, "");

        // Draw centered text on top of the bar
        ImVec2 textSize = new ImVec2();
        ImGui.calcTextSize(textSize, overlay);
        float textX = cursorScreen.x + (progressBarWidth - textSize.x) / 2;
        float textY = cursorScreen.y + (barHeight - textSize.y) / 2;
        ImGui.getWindowDrawList().addText(textX, textY, ImGui.getColorU32(ImGuiCol.Text), overlay);

        ImGui.spacing();
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Clip metadata (centered table)
        float tableWidth = 250;
        ImGui.setCursorPosX(centerX - tableWidth / 2);
        if (ImGui.beginChild("##clipInfo", tableWidth, 0, false)) {
            ImGui.textDisabled("Duration:");
            ImGui.sameLine(100);
            ImGui.text(String.format("%.2f s", clip.getDuration()));

            ImGui.textDisabled("Channels:");
            ImGui.sameLine(100);
            ImGui.text(clip.isMono() ? "Mono" : "Stereo");

            ImGui.textDisabled("Sample Rate:");
            ImGui.sameLine(100);
            ImGui.text(clip.getSampleRate() + " Hz");

            ImGui.textDisabled("3D Audio:");
            ImGui.sameLine(100);
            ImGui.text(clip.supports3D() ? "Supported" : "Not supported (stereo)");
        }
        ImGui.endChild();
    }
}
