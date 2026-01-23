package com.pocket.rpg.audio.music;

import java.util.function.BiConsumer;

/**
 * Manages smooth crossfade transitions between music tracks.
 */
public class CrossfadeController {

    private MusicTrack outgoingTrack;
    private MusicTrack incomingTrack;
    private float duration;
    private float elapsed;
    private boolean active = false;
    private BiConsumer<MusicTrack, MusicTrack> onComplete;

    /**
     * Start a crossfade transition.
     *
     * @param outgoing   Track to fade out
     * @param incoming   Track to fade in
     * @param duration   Crossfade duration in seconds
     * @param onComplete Callback when crossfade completes (receives old, new tracks)
     */
    public void start(MusicTrack outgoing, MusicTrack incoming, float duration,
                      BiConsumer<MusicTrack, MusicTrack> onComplete) {
        this.outgoingTrack = outgoing;
        this.incomingTrack = incoming;
        this.duration = duration;
        this.elapsed = 0f;
        this.active = true;
        this.onComplete = onComplete;

        // Start incoming at zero volume
        incoming.setVolume(0f);
    }

    /**
     * Update crossfade transition.
     */
    public void update(float deltaTime) {
        if (!active) {
            return;
        }

        elapsed += deltaTime;
        float progress = Math.min(elapsed / duration, 1f);

        // Linear crossfade
        float outVol = 1f - progress;
        float inVol = progress;

        if (outgoingTrack != null) {
            outgoingTrack.setVolume(outVol);
        }
        if (incomingTrack != null) {
            incomingTrack.setVolume(inVol);
        }

        if (progress >= 1f) {
            active = false;
            if (onComplete != null) {
                onComplete.accept(outgoingTrack, incomingTrack);
            }
            outgoingTrack = null;
            incomingTrack = null;
        }
    }

    /**
     * @return true if crossfade is in progress
     */
    public boolean isActive() {
        return active;
    }

    /**
     * @return the incoming track (for tracking)
     */
    public MusicTrack getIncomingTrack() {
        return incomingTrack;
    }

    /**
     * Cancel crossfade (keeps current state).
     */
    public void cancel() {
        active = false;
        outgoingTrack = null;
        incomingTrack = null;
        onComplete = null;
    }
}
