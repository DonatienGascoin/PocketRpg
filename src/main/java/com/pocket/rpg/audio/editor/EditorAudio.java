package com.pocket.rpg.audio.editor;

import com.pocket.rpg.audio.backend.AudioBackend;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.components.audio.AudioSource;
import com.pocket.rpg.audio.sources.AudioHandle;

/**
 * Static facade for editor-specific audio operations.
 * Parallel to Audio for editor preview functionality.
 */
public final class EditorAudio {

    private static EditorAudioContext context;

    private EditorAudio() {
    }

    /**
     * Initialize editor audio with a backend.
     */
    public static void initialize(AudioBackend backend) {
        if (context != null) {
            context.destroy();
        }
        context = new EditorAudioContext(backend);
    }

    /**
     * Destroy editor audio.
     */
    public static void destroy() {
        if (context != null) {
            context.destroy();
            context = null;
        }
    }

    /**
     * Check if editor audio is initialized.
     */
    public static boolean isInitialized() {
        return context != null;
    }

    // ========================================================================
    // PREVIEW PLAYBACK
    // ========================================================================

    /**
     * Play a clip for preview.
     */
    public static AudioHandle playPreview(AudioClip clip) {
        return context != null ? context.playPreview(clip, 1.0f) : null;
    }

    /**
     * Play a clip for preview with volume.
     */
    public static AudioHandle playPreview(AudioClip clip, float volume) {
        return context != null ? context.playPreview(clip, volume) : null;
    }

    /**
     * Stop previewing a clip.
     */
    public static void stopPreview(AudioClip clip) {
        if (context != null) {
            context.stopPreview(clip);
        }
    }

    /**
     * Check if a clip is being previewed.
     */
    public static boolean isPreviewingClip(AudioClip clip) {
        return context != null && context.isPreviewingClip(clip);
    }

    /**
     * Get the preview handle for a clip.
     */
    public static AudioHandle getPreviewHandle(AudioClip clip) {
        return context != null ? context.getPreviewHandle(clip) : null;
    }

    // ========================================================================
    // AUDIOSOURCE PREVIEW
    // ========================================================================

    /**
     * Preview an AudioSource with its configured settings.
     */
    public static void previewSource(AudioSource source) {
        if (context != null) {
            context.previewSource(source);
        }
    }

    /**
     * Stop previewing an AudioSource.
     */
    public static void stopSourcePreview(AudioSource source) {
        if (context != null) {
            context.stopSourcePreview(source);
        }
    }

    /**
     * Check if an AudioSource is being previewed.
     */
    public static boolean isPreviewingSource(AudioSource source) {
        return context != null && context.isPreviewingSource(source);
    }

    /**
     * Get the preview time for an AudioSource.
     */
    public static float getSourcePreviewTime(AudioSource source) {
        return context != null ? context.getSourcePreviewTime(source) : 0f;
    }

    // ========================================================================
    // GLOBAL CONTROL
    // ========================================================================

    /**
     * Stop all previews.
     */
    public static void stopAllPreviews() {
        if (context != null) {
            context.stopAllPreviews();
        }
    }

    /**
     * Set preview volume.
     */
    public static void setPreviewVolume(float volume) {
        if (context != null) {
            context.setPreviewVolume(volume);
        }
    }

    /**
     * Get preview volume.
     */
    public static float getPreviewVolume() {
        return context != null ? context.getPreviewVolume() : 0f;
    }

    // ========================================================================
    // LIFECYCLE HOOKS
    // ========================================================================

    /**
     * Called when entering Play Mode - stops all previews.
     */
    public static void onEnterPlayMode() {
        stopAllPreviews();
    }

    /**
     * Called when exiting Play Mode.
     */
    public static void onExitPlayMode() {
        // Nothing special needed
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    /**
     * Update editor audio (called each frame).
     */
    public static void update(float deltaTime) {
        if (context != null) {
            context.update(deltaTime);
        }
    }
}
