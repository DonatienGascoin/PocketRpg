package com.pocket.rpg.editor.assets;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.editor.EditorAudio;
import com.pocket.rpg.editor.core.EditorColors;
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
        EditorColors.textColored(EditorColors.INFO, MaterialIcons.AudioFile);

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
            EditorColors.pushDangerButton();

            if (ImGui.button(MaterialIcons.Stop + " Stop", buttonWidth, 0)) {
                EditorAudio.stopPreview(clip);
            }

            EditorColors.popButtonColors();
        } else {
            // Play button (green)
            EditorColors.pushSuccessButton();

            if (ImGui.button(MaterialIcons.PlayArrow + " Play", buttonWidth, 0)) {
                EditorAudio.playPreview(clip);
            }

            EditorColors.popButtonColors();
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
