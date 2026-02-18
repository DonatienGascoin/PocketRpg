package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.AudioConfig;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.mixing.AudioMixer;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * Configuration tab for audio settings.
 * Uses live editing model - edits apply directly to the live AudioMixer.
 */
public class AudioConfigTab implements ConfigTab {

    private final Runnable markDirty;

    public AudioConfigTab(Runnable markDirty) {
        this.markDirty = markDirty;
    }

    @Override
    public String getTabName() {
        return "Audio";
    }

    @Override
    public void save() {
        AudioMixer mixer = Audio.getMixer();
        if (mixer != null) {
            mixer.saveConfig();
        }
    }

    @Override
    public void revert() {
        // Reload config from disk and apply to mixer
        AudioConfig config = AudioConfig.load();
        applyConfigToMixer(config);
    }

    @Override
    public void resetToDefaults() {
        // Create default config and apply
        AudioConfig defaults = AudioConfig.builder().build();
        applyConfigToMixer(defaults);
    }

    @Override
    public void renderContent() {
        AudioMixer mixer = Audio.getMixer();
        if (mixer == null) {
            EditorColors.textColored(EditorColors.WARNING, "Audio system not initialized");
            return;
        }

        ImGui.pushID("AudioTab");

        // Channel Volumes section
        if (ImGui.collapsingHeader("Channel Volumes", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            drawChannelVolume("Master", AudioChannel.MASTER);
            drawChannelVolume("Music", AudioChannel.MUSIC);
            drawChannelVolume("SFX", AudioChannel.SFX);
            drawChannelVolume("Voice", AudioChannel.VOICE);
            drawChannelVolume("Ambient", AudioChannel.AMBIENT);
            drawChannelVolume("UI", AudioChannel.UI);

            ImGui.unindent();
        }

        // Music section
        if (ImGui.collapsingHeader("Music", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            AudioConfig config = mixer.getConfig();
            FieldEditors.drawFloat("Crossfade Duration", "musicCrossfade",
                    () -> (double) config.getMusicCrossfadeDuration(),
                    v -> { config.setMusicCrossfadeDuration((float) v); markDirty.run(); },
                    0.1f, 0.0f, 10.0f, "%.1f sec");
            tooltip("Duration for crossfading between music tracks");

            ImGui.unindent();
        }

        // Engine section
        if (ImGui.collapsingHeader("Engine", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            AudioConfig config = mixer.getConfig();

            FieldEditors.drawInt("Max Simultaneous Sounds", "maxSounds",
                    config::getMaxSimultaneousSounds,
                    v -> { config.setMaxSimultaneousSounds(Math.max(1, v)); markDirty.run(); });
            tooltip("Maximum number of sounds that can play at once");

            FieldEditors.drawFloat("Default Rolloff Factor", "rolloff",
                    () -> (double) config.getDefaultRolloffFactor(),
                    v -> { config.setDefaultRolloffFactor((float) v); markDirty.run(); },
                    0.1f, 0.0f, 10.0f, "%.1f");
            tooltip("How quickly 3D sounds attenuate with distance (higher = faster falloff)");

            FieldEditors.drawBoolean("Enable Reverb", "enableReverb",
                    config::isEnableReverb,
                    v -> { config.setEnableReverb(v); markDirty.run(); });
            tooltip("Enable environmental reverb effects");

            ImGui.unindent();
        }

        ImGui.popID();
    }

    private void drawChannelVolume(String label, AudioChannel channel) {
        ImGui.pushID(channel.name());

        // Label on the left (fixed width for alignment)
        float labelWidth = 80;
        ImGui.text(label);
        ImGui.sameLine(labelWidth);

        // Get current volume (0-1) and convert to display range (0-100)
        float volume = Audio.getChannelVolume(channel);
        float[] volumeArr = {volume * 100f};

        // Calculate widths - reserve space for mute button
        float buttonWidth = 30;
        float spacing = ImGui.getStyle().getItemSpacingX();
        float sliderWidth = ImGui.getContentRegionAvailX() - buttonWidth - spacing;

        // Volume slider with percentage display (0-100 range, converted back to 0-1 for API)
        ImGui.setNextItemWidth(sliderWidth);
        if (ImGui.sliderFloat("##volume", volumeArr, 0f, 100f, "%.0f%%")) {
            Audio.setChannelVolume(channel, volumeArr[0] / 100f);
            markDirty.run();
        }

        // Mute button - CRITICAL: capture state BEFORE button for push/pop matching
        ImGui.sameLine();
        boolean wasMuted = Audio.isChannelMuted(channel);
        String icon = wasMuted ? MaterialIcons.VolumeOff : MaterialIcons.VolumeUp;

        // Style muted button with red color
        if (wasMuted) {
            EditorColors.pushDangerButton();
        }
        if (ImGui.button(icon + "##mute", buttonWidth, 0)) {
            if (wasMuted) {
                Audio.unmuteChannel(channel);
            } else {
                Audio.muteChannel(channel);
            }
            // Note: mute state is runtime-only (not persisted to AudioConfig)
            // so we don't call markDirty here
        }
        // Pop uses same captured state as push
        if (wasMuted) {
            EditorColors.popButtonColors();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(wasMuted ? "Unmute " + label : "Mute " + label);
        }

        ImGui.popID();
    }

    private void applyConfigToMixer(AudioConfig config) {
        AudioMixer mixer = Audio.getMixer();
        if (mixer == null) return;

        // Apply volume settings
        mixer.setVolume(AudioChannel.MASTER, config.getMasterVolume());
        mixer.setVolume(AudioChannel.MUSIC, config.getMusicVolume());
        mixer.setVolume(AudioChannel.SFX, config.getSfxVolume());
        mixer.setVolume(AudioChannel.VOICE, config.getVoiceVolume());
        mixer.setVolume(AudioChannel.AMBIENT, config.getAmbientVolume());
        mixer.setVolume(AudioChannel.UI, config.getUiVolume());

        // Apply non-volume fields to the mixer's config
        AudioConfig mixerConfig = mixer.getConfig();
        mixerConfig.setMaxSimultaneousSounds(config.getMaxSimultaneousSounds());
        mixerConfig.setMusicCrossfadeDuration(config.getMusicCrossfadeDuration());
        mixerConfig.setDefaultRolloffFactor(config.getDefaultRolloffFactor());
        mixerConfig.setEnableReverb(config.isEnableReverb());
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }
}
