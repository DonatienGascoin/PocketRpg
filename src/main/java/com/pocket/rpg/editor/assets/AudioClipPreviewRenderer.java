package com.pocket.rpg.editor.assets;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.editor.EditorAudio;
import com.pocket.rpg.editor.core.MaterialIcons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * Preview renderer for AudioClip assets.
 * Shows play/stop button and audio info.
 */
public class AudioClipPreviewRenderer implements AssetPreviewRenderer<AudioClip> {

    @Override
    public void render(AudioClip clip, float maxSize) {
        if (clip == null) {
            ImGui.textDisabled("No audio clip");
            return;
        }

        // Audio icon
        ImGui.textColored(0.5f, 0.8f, 1.0f, 1.0f, MaterialIcons.AudioFile);

        ImGui.spacing();

        // Clip name
        ImGui.text(clip.getName());

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Play/Stop button
        boolean isPlaying = EditorAudio.isPreviewingClip(clip);
        float buttonWidth = 80;

        if (isPlaying) {
            // Stop button (red)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.4f, 0.4f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.2f, 0.2f, 1.0f);

            if (ImGui.button(MaterialIcons.Stop + " Stop", buttonWidth, 0)) {
                EditorAudio.stopPreview(clip);
            }

            ImGui.popStyleColor(3);
        } else {
            // Play button (green)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 0.4f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.5f, 0.2f, 1.0f);

            if (ImGui.button(MaterialIcons.PlayArrow + " Play", buttonWidth, 0)) {
                EditorAudio.playPreview(clip);
            }

            ImGui.popStyleColor(3);
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Clip info
        ImGui.textDisabled("Duration:");
        ImGui.sameLine();
        ImGui.text(String.format("%.2f s", clip.getDuration()));

        ImGui.textDisabled("Channels:");
        ImGui.sameLine();
        ImGui.text(clip.isMono() ? "Mono" : "Stereo");

        ImGui.textDisabled("Sample Rate:");
        ImGui.sameLine();
        ImGui.text(clip.getSampleRate() + " Hz");
    }

    @Override
    public Class<AudioClip> getAssetType() {
        return AudioClip.class;
    }
}
