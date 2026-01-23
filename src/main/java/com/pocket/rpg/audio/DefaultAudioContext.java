package com.pocket.rpg.audio;

import com.pocket.rpg.audio.backend.AudioBackend;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.mixing.AudioMixer;
import com.pocket.rpg.audio.music.MusicPlayer;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.audio.sources.PlaybackSettings;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * Default implementation of AudioContext for game runtime.
 */
public class DefaultAudioContext implements AudioContext {

    private final AudioBackend backend;

    @Getter
    private final AudioConfig config;

    @Getter
    private AudioMixer mixer;

    @Getter
    private AudioEngine engine;

    @Getter
    private MusicPlayer musicPlayer;

    private boolean initialized = false;

    public DefaultAudioContext(AudioBackend backend, AudioConfig config) {
        this.backend = backend;
        this.config = config;
    }

    @Override
    public void initialize() {
        if (initialized) {
            return;
        }

        // Initialize backend
        backend.initialize();

        // Create mixer
        mixer = new AudioMixer(config);

        // Create engine
        engine = new AudioEngine(backend, mixer, config);

        // Create music player
        musicPlayer = new MusicPlayer(engine, mixer);

        initialized = true;
        System.out.println("Audio context initialized");
    }

    @Override
    public void destroy() {
        if (!initialized) {
            return;
        }

        // Stop all sounds
        stopAll();

        // Stop music
        if (musicPlayer != null) {
            musicPlayer.stop();
        }

        // Destroy backend
        backend.destroy();

        initialized = false;
        System.out.println("Audio context destroyed");
    }

    @Override
    public void update(float deltaTime) {
        if (!initialized) {
            return;
        }

        // Update mixer (for fades)
        mixer.update(deltaTime);

        // Update engine
        engine.update(deltaTime);

        // Update music player
        musicPlayer.update(deltaTime);
    }

    @Override
    public AudioHandle playOneShot(AudioClip clip, float volume, AudioChannel channel) {
        return playOneShot(clip, new PlaybackSettings()
                .volume(volume)
                .channel(channel));
    }

    @Override
    public AudioHandle playOneShotAt(AudioClip clip, Vector3f position, float volume, AudioChannel channel) {
        return playOneShot(clip, new PlaybackSettings()
                .volume(volume)
                .channel(channel)
                .position(position));
    }

    @Override
    public AudioHandle playOneShot(AudioClip clip, PlaybackSettings settings) {
        if (!initialized || clip == null) {
            return null;
        }
        return engine.play(clip, settings);
    }

    @Override
    public void pauseAll() {
        if (initialized) {
            engine.pauseAll();
        }
    }

    @Override
    public void resumeAll() {
        if (initialized) {
            engine.resumeAll();
        }
    }

    @Override
    public void stopAll() {
        if (initialized) {
            engine.stopAll();
        }
    }

    @Override
    public void stopChannel(AudioChannel channel) {
        if (initialized) {
            engine.stopChannel(channel);
        }
    }
}
