package com.pocket.rpg.audio.mixing;

import com.pocket.rpg.audio.AudioConfig;
import lombok.Getter;

import java.util.EnumMap;
import java.util.Map;

/**
 * Audio mixer managing volume levels and channel settings.
 * Provides hierarchical volume control: Master -> Channel -> Source.
 */
public class AudioMixer {

    private final Map<AudioChannel, AudioBus> buses = new EnumMap<>(AudioChannel.class);

    @Getter
    private final AudioConfig config;

    public AudioMixer(AudioConfig config) {
        this.config = config;

        // Initialize buses from config
        buses.put(AudioChannel.MASTER, new AudioBus(config.getMasterVolume()));
        buses.put(AudioChannel.MUSIC, new AudioBus(config.getMusicVolume()));
        buses.put(AudioChannel.SFX, new AudioBus(config.getSfxVolume()));
        buses.put(AudioChannel.VOICE, new AudioBus(config.getVoiceVolume()));
        buses.put(AudioChannel.AMBIENT, new AudioBus(config.getAmbientVolume()));
        buses.put(AudioChannel.UI, new AudioBus(config.getUiVolume()));
    }

    // ========================================================================
    // VOLUME CONTROL
    // ========================================================================

    /**
     * Set volume for a channel.
     */
    public void setVolume(AudioChannel channel, float volume) {
        AudioBus bus = buses.get(channel);
        if (bus != null) {
            bus.setVolume(volume);
            updateConfig(channel, volume);
        }
    }

    /**
     * Get volume for a channel.
     */
    public float getVolume(AudioChannel channel) {
        AudioBus bus = buses.get(channel);
        return bus != null ? bus.getVolume() : 0f;
    }

    /**
     * Fade a channel to a target volume.
     */
    public void fadeVolume(AudioChannel channel, float target, float duration) {
        AudioBus bus = buses.get(channel);
        if (bus != null) {
            bus.fadeToVolume(target, duration);
        }
    }

    // ========================================================================
    // MUTE CONTROL
    // ========================================================================

    /**
     * Mute a channel.
     */
    public void mute(AudioChannel channel) {
        AudioBus bus = buses.get(channel);
        if (bus != null) {
            bus.setMuted(true);
        }
    }

    /**
     * Unmute a channel.
     */
    public void unmute(AudioChannel channel) {
        AudioBus bus = buses.get(channel);
        if (bus != null) {
            bus.setMuted(false);
        }
    }

    /**
     * Toggle mute state for a channel.
     */
    public void toggleMute(AudioChannel channel) {
        AudioBus bus = buses.get(channel);
        if (bus != null) {
            bus.setMuted(!bus.isMuted());
        }
    }

    /**
     * Check if a channel is muted.
     */
    public boolean isMuted(AudioChannel channel) {
        AudioBus bus = buses.get(channel);
        return bus != null && bus.isMuted();
    }

    // ========================================================================
    // PAUSE CONTROL
    // ========================================================================

    /**
     * Pause a channel.
     */
    public void pauseChannel(AudioChannel channel) {
        AudioBus bus = buses.get(channel);
        if (bus != null) {
            bus.setPaused(true);
        }
    }

    /**
     * Resume a channel.
     */
    public void resumeChannel(AudioChannel channel) {
        AudioBus bus = buses.get(channel);
        if (bus != null) {
            bus.setPaused(false);
        }
    }

    /**
     * Check if a channel is paused.
     */
    public boolean isChannelPaused(AudioChannel channel) {
        AudioBus bus = buses.get(channel);
        return bus != null && bus.isPaused();
    }

    // ========================================================================
    // VOLUME CALCULATION
    // ========================================================================

    /**
     * Calculate the final volume for a sound.
     * Chain: master * channel * source
     *
     * @param channel      Audio channel
     * @param sourceVolume Source volume (0-1)
     * @return Final volume
     */
    public float calculateFinalVolume(AudioChannel channel, float sourceVolume) {
        AudioBus masterBus = buses.get(AudioChannel.MASTER);
        AudioBus channelBus = buses.get(channel);

        if (masterBus == null || channelBus == null) {
            return 0f;
        }

        if (masterBus.isMuted() || channelBus.isMuted()) {
            return 0f;
        }

        return masterBus.getVolume() * channelBus.getVolume() * sourceVolume;
    }

    /**
     * Calculate final volume with distance attenuation for 3D sounds.
     */
    public float calculateFinalVolume(AudioChannel channel, float sourceVolume, float distanceAttenuation) {
        return calculateFinalVolume(channel, sourceVolume) * distanceAttenuation;
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    /**
     * Update all buses (for fade transitions).
     */
    public void update(float deltaTime) {
        for (AudioBus bus : buses.values()) {
            bus.update(deltaTime);
        }
    }

    // ========================================================================
    // CONFIG SYNC
    // ========================================================================

    private void updateConfig(AudioChannel channel, float volume) {
        switch (channel) {
            case MASTER -> config.setMasterVolume(volume);
            case MUSIC -> config.setMusicVolume(volume);
            case SFX -> config.setSfxVolume(volume);
            case VOICE -> config.setVoiceVolume(volume);
            case AMBIENT -> config.setAmbientVolume(volume);
            case UI -> config.setUiVolume(volume);
        }
    }

    /**
     * Save current mixer settings to config file.
     */
    public void saveConfig() {
        config.save();
    }
}
