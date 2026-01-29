package com.pocket.rpg.audio.backend;

import org.joml.Vector3f;

/**
 * Silent/no-op audio backend for testing or headless environments.
 */
public class NullAudioBackend implements AudioBackend {

    private int nextBufferId = 1;
    private int nextSourceId = 1;

    @Override
    public void initialize() {
        System.out.println("NullAudioBackend initialized (silent mode)");
    }

    @Override
    public void destroy() {
        System.out.println("NullAudioBackend destroyed");
    }

    @Override
    public int createBuffer(short[] data, int format, int sampleRate) {
        return nextBufferId++;
    }

    @Override
    public void deleteBuffer(int bufferId) {
        // No-op
    }

    @Override
    public int createSource() {
        return nextSourceId++;
    }

    @Override
    public void deleteSource(int sourceId) {
        // No-op
    }

    @Override
    public void setSourceBuffer(int sourceId, int bufferId) {
        // No-op
    }

    @Override
    public void playSource(int sourceId) {
        // No-op
    }

    @Override
    public void pauseSource(int sourceId) {
        // No-op
    }

    @Override
    public void stopSource(int sourceId) {
        // No-op
    }

    @Override
    public boolean isSourcePlaying(int sourceId) {
        return false;
    }

    @Override
    public boolean isSourcePaused(int sourceId) {
        return false;
    }

    @Override
    public void setSourceVolume(int sourceId, float volume) {
        // No-op
    }

    @Override
    public void setSourcePitch(int sourceId, float pitch) {
        // No-op
    }

    @Override
    public void setSourceLooping(int sourceId, boolean loop) {
        // No-op
    }

    @Override
    public void setSourcePosition(int sourceId, Vector3f position) {
        // No-op
    }

    @Override
    public void setSourceAttenuation(int sourceId, float minDistance, float maxDistance, float rolloffFactor) {
        // No-op
    }

    @Override
    public float getSourcePlaybackTime(int sourceId) {
        return 0f;
    }

    @Override
    public void setSourcePlaybackTime(int sourceId, float seconds) {
        // No-op
    }

    @Override
    public void setListenerPosition(Vector3f position) {
        // No-op
    }

    @Override
    public void setListenerOrientation(Vector3f forward, Vector3f up) {
        // No-op
    }

    @Override
    public int getFormatMono16() {
        return 0x1101; // AL_FORMAT_MONO16
    }

    @Override
    public int getFormatStereo16() {
        return 0x1103; // AL_FORMAT_STEREO16
    }
}
