package com.pocket.rpg.audio.sources;

import com.pocket.rpg.audio.backend.AudioBackend;
import com.pocket.rpg.audio.clips.AudioClip;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pool of pre-allocated audio sources for efficient playback.
 * Manages voice stealing when pool is exhausted.
 */
public class AudioSourcePool {

    private final AudioBackend backend;
    private final int maxSources;

    private final List<PooledSource> sources = new ArrayList<>();

    @Getter
    private int activeCount = 0;

    public AudioSourcePool(AudioBackend backend, int maxSources) {
        this.backend = backend;
        this.maxSources = maxSources;
    }

    /**
     * Acquire a source from the pool.
     *
     * @param clip     Clip to play
     * @param priority Priority (0 = highest, 255 = lowest)
     * @return AudioHandle, or null if no source available
     */
    public AudioHandle acquire(AudioClip clip, int priority) {
        // First, try to find a free source
        for (PooledSource source : sources) {
            if (!source.inUse) {
                return activateSource(source, clip, priority);
            }
        }

        // If under limit, create new source
        if (sources.size() < maxSources) {
            int sourceId = backend.createSource();
            PooledSource source = new PooledSource(sourceId);
            sources.add(source);
            return activateSource(source, clip, priority);
        }

        // Try voice stealing - find lowest priority source
        PooledSource victim = sources.stream()
                .filter(s -> s.inUse && !s.isLooping)
                .max(Comparator.comparingInt(s -> s.priority))
                .orElse(null);

        if (victim != null && victim.priority > priority) {
            // Steal this source
            victim.handle.stop();
            victim.inUse = false;
            activeCount--;
            return activateSource(victim, clip, priority);
        }

        // No source available
        return null;
    }

    /**
     * Release a source back to the pool.
     */
    public void release(AudioHandle handle) {
        for (PooledSource source : sources) {
            if (source.handle == handle) {
                source.inUse = false;
                source.handle = null;
                activeCount--;
                break;
            }
        }
    }

    /**
     * Clean up finished sources.
     */
    public void update() {
        for (PooledSource source : sources) {
            if (source.inUse && source.handle != null && !source.handle.isPlaying()) {
                source.inUse = false;
                source.handle.invalidate();
                source.handle = null;
                activeCount--;
            }
        }
    }

    /**
     * Stop all sources and release resources.
     */
    public void destroy() {
        for (PooledSource source : sources) {
            if (source.handle != null) {
                source.handle.stop();
            }
            backend.deleteSource(source.sourceId);
        }
        sources.clear();
        activeCount = 0;
    }

    /**
     * Stop all playing sources.
     */
    public void stopAll() {
        for (PooledSource source : sources) {
            if (source.inUse && source.handle != null) {
                source.handle.stop();
                source.inUse = false;
                source.handle = null;
            }
        }
        activeCount = 0;
    }

    private AudioHandle activateSource(PooledSource source, AudioClip clip, int priority) {
        source.inUse = true;
        source.priority = priority;
        source.isLooping = false;

        backend.setSourceBuffer(source.sourceId, clip.getBufferId());

        AudioHandle handle = new AudioHandle(backend, source.sourceId, clip);
        source.handle = handle;
        activeCount++;

        return handle;
    }

    /**
     * Internal tracking for pooled sources.
     */
    private static class PooledSource {
        final int sourceId;
        boolean inUse = false;
        int priority = 128;
        boolean isLooping = false;
        AudioHandle handle;

        PooledSource(int sourceId) {
            this.sourceId = sourceId;
        }
    }
}
