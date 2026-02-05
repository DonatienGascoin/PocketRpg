package com.pocket.rpg.audio.editor;

import com.pocket.rpg.audio.backend.AudioBackend;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.components.audio.AudioSource;
import com.pocket.rpg.audio.sources.AudioHandle;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Editor-specific audio context for preview functionality.
 */
public class EditorAudioContext {

    private final AudioBackend backend;
    private final Map<AudioClip, AudioHandle> clipPreviews = new HashMap<>();
    private final Map<AudioSource, AudioHandle> sourcePreviews = new IdentityHashMap<>();

    @Getter
    @Setter
    private float previewVolume = 0.8f;

    public EditorAudioContext(AudioBackend backend) {
        this.backend = backend;
        backend.initialize();
    }

    // ========================================================================
    // CLIP PREVIEW
    // ========================================================================

    /**
     * Play a clip for preview.
     */
    public AudioHandle playPreview(AudioClip clip, float volume) {
        if (clip == null) {
            return null;
        }

        // Stop existing preview of this clip
        stopPreview(clip);

        // Create source and play
        int sourceId = backend.createSource();
        backend.setSourceBuffer(sourceId, clip.getBufferId());
        backend.setSourceVolume(sourceId, volume * previewVolume);
        backend.setSourceLooping(sourceId, false);
        backend.playSource(sourceId);

        AudioHandle handle = new AudioHandle(backend, sourceId, clip);
        clipPreviews.put(clip, handle);

        return handle;
    }

    /**
     * Stop previewing a clip.
     */
    public void stopPreview(AudioClip clip) {
        AudioHandle handle = clipPreviews.remove(clip);
        if (handle != null && handle.isPlaying()) {
            handle.stop();
            backend.deleteSource(handle.getSourceId());
        }
    }

    /**
     * Check if a clip is being previewed.
     */
    public boolean isPreviewingClip(AudioClip clip) {
        AudioHandle handle = clipPreviews.get(clip);
        return handle != null && handle.isPlaying();
    }

    /**
     * Get the preview handle for a clip.
     */
    public AudioHandle getPreviewHandle(AudioClip clip) {
        return clipPreviews.get(clip);
    }

    // ========================================================================
    // AUDIOSOURCE PREVIEW
    // ========================================================================

    /**
     * Preview an AudioSource with its configured settings.
     */
    public void previewSource(AudioSource source) {
        stopSourcePreview(source);

        AudioClip clip = source.getClip();
        if (clip == null) {
            return;
        }

        int sourceId = backend.createSource();
        backend.setSourceBuffer(sourceId, clip.getBufferId());
        backend.setSourceVolume(sourceId, source.getVolume() * previewVolume);
        backend.setSourcePitch(sourceId, source.getPitch());
        backend.setSourceLooping(sourceId, source.isLoop());
        backend.playSource(sourceId);

        AudioHandle handle = new AudioHandle(backend, sourceId, clip);
        sourcePreviews.put(source, handle);
    }

    /**
     * Stop previewing an AudioSource.
     */
    public void stopSourcePreview(AudioSource source) {
        AudioHandle handle = sourcePreviews.remove(source);
        if (handle != null && handle.isPlaying()) {
            handle.stop();
            backend.deleteSource(handle.getSourceId());
        }
    }

    /**
     * Check if an AudioSource is being previewed.
     */
    public boolean isPreviewingSource(AudioSource source) {
        AudioHandle handle = sourcePreviews.get(source);
        return handle != null && handle.isPlaying();
    }

    /**
     * Get the preview time for an AudioSource.
     */
    public float getSourcePreviewTime(AudioSource source) {
        AudioHandle handle = sourcePreviews.get(source);
        return handle != null ? handle.getPlaybackTime() : 0f;
    }

    // ========================================================================
    // GLOBAL
    // ========================================================================

    /**
     * Stop all previews.
     */
    public void stopAllPreviews() {
        for (AudioHandle handle : clipPreviews.values()) {
            if (handle.isPlaying()) {
                handle.stop();
                backend.deleteSource(handle.getSourceId());
            }
        }
        clipPreviews.clear();

        for (AudioHandle handle : sourcePreviews.values()) {
            if (handle.isPlaying()) {
                handle.stop();
                backend.deleteSource(handle.getSourceId());
            }
        }
        sourcePreviews.clear();
    }

    /**
     * Update (cleanup finished previews).
     */
    public void update(float deltaTime) {
        // Cleanup finished clip previews
        clipPreviews.entrySet().removeIf(e -> {
            AudioHandle handle = e.getValue();
            if (!handle.isValid() || !handle.isPlaying()) {
                backend.deleteSource(handle.getSourceId());
                return true;
            }
            return false;
        });

        // Cleanup finished source previews
        sourcePreviews.entrySet().removeIf(e -> {
            AudioHandle handle = e.getValue();
            if (!handle.isValid() || !handle.isPlaying()) {
                backend.deleteSource(handle.getSourceId());
                return true;
            }
            return false;
        });
    }

    /**
     * Destroy and release resources.
     */
    public void destroy() {
        stopAllPreviews();
        backend.destroy();
    }
}
