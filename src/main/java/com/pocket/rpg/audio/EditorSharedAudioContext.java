package com.pocket.rpg.audio;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.mixing.AudioMixer;
import com.pocket.rpg.audio.music.MusicPlayer;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.audio.sources.PlaybackSettings;
import org.joml.Vector3f;

/**
 * Wraps the editor's audio context for use during play mode.
 * <p>
 * Lifecycle methods ({@link #initialize()}, {@link #destroy()}) are no-ops because
 * the editor owns the real audio backend. All other methods delegate to the
 * underlying context so play mode audio works normally.
 *
 * @see Audio
 */
public class EditorSharedAudioContext implements AudioContext {

    private final AudioContext delegate;

    public EditorSharedAudioContext(AudioContext delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initialize() {
        // no-op — editor owns the backend
    }

    @Override
    public void destroy() {
        // no-op — editor owns the backend
    }

    @Override
    public void update(float deltaTime) {
        delegate.update(deltaTime);
    }

    @Override
    public AudioHandle playOneShot(AudioClip clip, float volume, AudioChannel channel) {
        return delegate.playOneShot(clip, volume, channel);
    }

    @Override
    public AudioHandle playOneShotAt(AudioClip clip, Vector3f position, float volume, AudioChannel channel) {
        return delegate.playOneShotAt(clip, position, volume, channel);
    }

    @Override
    public AudioHandle playOneShot(AudioClip clip, PlaybackSettings settings) {
        return delegate.playOneShot(clip, settings);
    }

    @Override
    public void pauseAll() {
        delegate.pauseAll();
    }

    @Override
    public void resumeAll() {
        delegate.resumeAll();
    }

    @Override
    public void stopAll() {
        delegate.stopAll();
    }

    @Override
    public void stopChannel(AudioChannel channel) {
        delegate.stopChannel(channel);
    }

    @Override
    public MusicPlayer getMusicPlayer() {
        return delegate.getMusicPlayer();
    }

    @Override
    public AudioMixer getMixer() {
        return delegate.getMixer();
    }

    @Override
    public AudioEngine getEngine() {
        return delegate.getEngine();
    }
}
