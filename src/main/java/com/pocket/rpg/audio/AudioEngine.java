package com.pocket.rpg.audio;

import com.pocket.rpg.audio.backend.AudioBackend;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.mixing.AudioMixer;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.audio.sources.PlaybackSettings;
import lombok.Getter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Core audio engine managing playback, sources, and 3D positioning.
 */
public class AudioEngine {

    @Getter
    private final AudioBackend backend;

    @Getter
    private final AudioMixer mixer;

    @Getter
    private final AudioConfig config;

    // Active audio handles
    private final List<ActiveSource> activeSources = new ArrayList<>();

    // Listener position for 3D audio
    private final Vector3f listenerPosition = new Vector3f();

    public AudioEngine(AudioBackend backend, AudioMixer mixer, AudioConfig config) {
        this.backend = backend;
        this.mixer = mixer;
        this.config = config;
    }

    /**
     * Play a one-shot sound with settings.
     */
    public AudioHandle play(AudioClip clip, PlaybackSettings settings) {
        if (clip == null) {
            return null;
        }

        // Check if we're at max sources
        cleanupFinishedSources();
        if (activeSources.size() >= config.getMaxSimultaneousSounds()) {
            // Try to steal a low-priority source
            if (!stealSource(settings.getPriority())) {
                return null; // No source available
            }
        }

        // Create source
        int sourceId = backend.createSource();
        backend.setSourceBuffer(sourceId, clip.getBufferId());

        // Apply settings
        float finalVolume = mixer.calculateFinalVolume(settings.getChannel(), settings.getVolume());
        backend.setSourceVolume(sourceId, finalVolume);
        backend.setSourcePitch(sourceId, settings.getPitch());
        backend.setSourceLooping(sourceId, settings.isLoop());

        // 3D positioning
        if (settings.is3D() && clip.supports3D()) {
            backend.setSourcePosition(sourceId, settings.getPosition());
            backend.setSourceAttenuation(sourceId,
                    settings.getMinDistance(),
                    settings.getMaxDistance(),
                    settings.getRolloffFactor());
        }

        // Play
        backend.playSource(sourceId);

        // Create handle and track
        AudioHandle handle = new AudioHandle(backend, sourceId, clip);
        activeSources.add(new ActiveSource(handle, settings));

        return handle;
    }

    /**
     * Remove a handle from active source tracking.
     * Use this when a handle is managed externally (e.g. by MusicPlayer).
     */
    public void removeFromTracking(AudioHandle handle) {
        activeSources.removeIf(source -> source.handle == handle);
    }

    /**
     * Stop all sounds on a channel.
     */
    public void stopChannel(AudioChannel channel) {
        for (ActiveSource source : activeSources) {
            if (source.settings.getChannel() == channel) {
                source.handle.stop();
            }
        }
    }

    /**
     * Pause all sounds.
     */
    public void pauseAll() {
        for (ActiveSource source : activeSources) {
            source.handle.pause();
        }
    }

    /**
     * Resume all sounds.
     */
    public void resumeAll() {
        for (ActiveSource source : activeSources) {
            source.handle.resume();
        }
    }

    /**
     * Stop all sounds.
     */
    public void stopAll() {
        for (ActiveSource source : activeSources) {
            source.handle.stop();
        }
        activeSources.clear();
    }

    /**
     * Update engine state.
     */
    public void update(float deltaTime) {
        cleanupFinishedSources();

        // Update volumes for active sources (in case mixer volumes changed)
        for (ActiveSource source : activeSources) {
            if (source.handle.isPlaying()) {
                float finalVolume = mixer.calculateFinalVolume(
                        source.settings.getChannel(),
                        source.settings.getVolume()
                );
                backend.setSourceVolume(source.handle.getSourceId(), finalVolume);
            }
        }
    }

    /**
     * Set the listener position for 3D audio.
     */
    public void setListenerPosition(Vector3f position) {
        listenerPosition.set(position);
        backend.setListenerPosition(position);
    }

    /**
     * Set the listener orientation for 3D audio.
     */
    public void setListenerOrientation(Vector3f forward, Vector3f up) {
        backend.setListenerOrientation(forward, up);
    }

    /**
     * Get the number of currently active sources.
     */
    public int getActiveSourceCount() {
        return activeSources.size();
    }

    /**
     * Fade a playing sound's volume.
     */
    public void fadeVolume(AudioHandle handle, float targetVolume, float duration) {
        fadeVolume(handle, targetVolume, duration, null);
    }

    /**
     * Fade a playing sound's volume with callback.
     */
    public void fadeVolume(AudioHandle handle, float targetVolume, float duration, Runnable onComplete) {
        // Find the active source
        for (ActiveSource source : activeSources) {
            if (source.handle == handle) {
                source.fadeTarget = targetVolume;
                source.fadeSpeed = Math.abs(targetVolume - source.settings.getVolume()) / duration;
                source.fading = true;
                source.onFadeComplete = onComplete;
                break;
            }
        }
    }

    private void cleanupFinishedSources() {
        Iterator<ActiveSource> it = activeSources.iterator();
        while (it.hasNext()) {
            ActiveSource source = it.next();
            if (!source.handle.isValid()) {
                backend.deleteSource(source.handle.getSourceId());
                source.handle.invalidate();
                it.remove();
            } else if (!source.handle.isPlaying() && !backend.isSourcePaused(source.handle.getSourceId())) {
                backend.deleteSource(source.handle.getSourceId());
                source.handle.invalidate();
                it.remove();
            }
        }
    }

    private boolean stealSource(int newPriority) {
        // Find lowest priority source
        ActiveSource lowestPriority = null;
        for (ActiveSource source : activeSources) {
            if (!source.settings.isLoop()) { // Don't steal looping sounds
                if (lowestPriority == null || source.settings.getPriority() > lowestPriority.settings.getPriority()) {
                    lowestPriority = source;
                }
            }
        }

        if (lowestPriority != null && lowestPriority.settings.getPriority() > newPriority) {
            // Steal this source
            lowestPriority.handle.stop();
            backend.deleteSource(lowestPriority.handle.getSourceId());
            activeSources.remove(lowestPriority);
            return true;
        }

        return false;
    }

    /**
     * Internal tracking for active audio sources.
     */
    private static class ActiveSource {
        final AudioHandle handle;
        final PlaybackSettings settings;

        // Fade state
        boolean fading = false;
        float fadeTarget;
        float fadeSpeed;
        Runnable onFadeComplete;

        ActiveSource(AudioHandle handle, PlaybackSettings settings) {
            this.handle = handle;
            this.settings = settings;
        }
    }
}
