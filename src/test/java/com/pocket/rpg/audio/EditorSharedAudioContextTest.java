package com.pocket.rpg.audio;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.mixing.AudioMixer;
import com.pocket.rpg.audio.music.MusicPlayer;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.audio.sources.PlaybackSettings;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorSharedAudioContextTest {

    private TrackingAudioContext delegate;
    private EditorSharedAudioContext shared;

    @BeforeEach
    void setUp() {
        delegate = new TrackingAudioContext();
        shared = new EditorSharedAudioContext(delegate);
    }

    // ========================================================================
    // LIFECYCLE NO-OPS
    // ========================================================================

    @Test
    void initialize_doesNotCallDelegate() {
        shared.initialize();

        assertFalse(delegate.initializeCalled);
    }

    @Test
    void destroy_doesNotCallDelegate() {
        shared.destroy();

        assertFalse(delegate.destroyCalled);
    }

    // ========================================================================
    // DELEGATION
    // ========================================================================

    @Test
    void update_delegatesToWrappedContext() {
        shared.update(0.016f);

        assertTrue(delegate.updateCalled);
        assertEquals(0.016f, delegate.lastDeltaTime, 0.0001f);
    }

    @Test
    void playOneShot_delegatesToWrappedContext() {
        shared.playOneShot(null, 1.0f, AudioChannel.SFX);

        assertTrue(delegate.playShotCalled);
    }

    @Test
    void playOneShotAt_delegatesToWrappedContext() {
        shared.playOneShotAt(null, new Vector3f(1, 2, 3), 0.5f, AudioChannel.SFX);

        assertTrue(delegate.playShotAtCalled);
    }

    @Test
    void playOneShotWithSettings_delegatesToWrappedContext() {
        shared.playOneShot(null, (PlaybackSettings) null);

        assertTrue(delegate.playShotSettingsCalled);
    }

    @Test
    void pauseAll_delegatesToWrappedContext() {
        shared.pauseAll();

        assertTrue(delegate.pauseAllCalled);
    }

    @Test
    void resumeAll_delegatesToWrappedContext() {
        shared.resumeAll();

        assertTrue(delegate.resumeAllCalled);
    }

    @Test
    void stopAll_delegatesToWrappedContext() {
        shared.stopAll();

        assertTrue(delegate.stopAllCalled);
    }

    @Test
    void stopChannel_delegatesToWrappedContext() {
        shared.stopChannel(AudioChannel.MUSIC);

        assertTrue(delegate.stopChannelCalled);
        assertEquals(AudioChannel.MUSIC, delegate.lastStoppedChannel);
    }

    @Test
    void getMusicPlayer_delegatesToWrappedContext() {
        MusicPlayer result = shared.getMusicPlayer();

        assertTrue(delegate.getMusicPlayerCalled);
        assertNull(result); // our stub returns null
    }

    @Test
    void getMixer_delegatesToWrappedContext() {
        AudioMixer result = shared.getMixer();

        assertTrue(delegate.getMixerCalled);
        assertNull(result);
    }

    @Test
    void getEngine_delegatesToWrappedContext() {
        AudioEngine result = shared.getEngine();

        assertTrue(delegate.getEngineCalled);
        assertNull(result);
    }

    // ========================================================================
    // TEST DOUBLE
    // ========================================================================

    private static class TrackingAudioContext implements AudioContext {
        boolean initializeCalled = false;
        boolean destroyCalled = false;
        boolean updateCalled = false;
        float lastDeltaTime;
        boolean playShotCalled = false;
        boolean playShotAtCalled = false;
        boolean playShotSettingsCalled = false;
        boolean pauseAllCalled = false;
        boolean resumeAllCalled = false;
        boolean stopAllCalled = false;
        boolean stopChannelCalled = false;
        AudioChannel lastStoppedChannel;
        boolean getMusicPlayerCalled = false;
        boolean getMixerCalled = false;
        boolean getEngineCalled = false;

        @Override
        public void initialize() { initializeCalled = true; }

        @Override
        public void destroy() { destroyCalled = true; }

        @Override
        public void update(float deltaTime) {
            updateCalled = true;
            lastDeltaTime = deltaTime;
        }

        @Override
        public AudioHandle playOneShot(AudioClip clip, float volume, AudioChannel channel) {
            playShotCalled = true;
            return null;
        }

        @Override
        public AudioHandle playOneShotAt(AudioClip clip, Vector3f position, float volume, AudioChannel channel) {
            playShotAtCalled = true;
            return null;
        }

        @Override
        public AudioHandle playOneShot(AudioClip clip, PlaybackSettings settings) {
            playShotSettingsCalled = true;
            return null;
        }

        @Override
        public void pauseAll() { pauseAllCalled = true; }

        @Override
        public void resumeAll() { resumeAllCalled = true; }

        @Override
        public void stopAll() { stopAllCalled = true; }

        @Override
        public void stopChannel(AudioChannel channel) {
            stopChannelCalled = true;
            lastStoppedChannel = channel;
        }

        @Override
        public MusicPlayer getMusicPlayer() {
            getMusicPlayerCalled = true;
            return null;
        }

        @Override
        public AudioMixer getMixer() {
            getMixerCalled = true;
            return null;
        }

        @Override
        public AudioEngine getEngine() {
            getEngineCalled = true;
            return null;
        }
    }
}
