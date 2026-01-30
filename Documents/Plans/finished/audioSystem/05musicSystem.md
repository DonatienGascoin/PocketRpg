# 5. Music System

## MusicPlayer

Dedicated system for background music with crossfading:

```java
public class MusicPlayer {
    private final AudioEngine engine;
    private final AudioMixer mixer;

    private MusicTrack currentTrack;
    private MusicTrack nextTrack;
    private float crossfadeDuration = 2.0f;
    private CrossfadeController crossfadeController;

    private float volume = 1.0f;  // Relative to channel volume
    private boolean paused = false;

    public MusicPlayer(AudioEngine engine, AudioMixer mixer) {
        this.engine = engine;
        this.mixer = mixer;
        this.crossfadeController = new CrossfadeController();
    }

    // ========== Basic Playback ==========

    public void play(AudioClip music) {
        play(music, 0f);
    }

    public void play(AudioClip music, float fadeIn) {
        stop(0f);

        currentTrack = new MusicTrack(music);
        currentTrack.play(engine);

        if (fadeIn > 0) {
            currentTrack.setVolume(0f);
            currentTrack.fadeTo(calculateVolume(), fadeIn);
        } else {
            currentTrack.setVolume(calculateVolume());
        }
    }

    public void stop() {
        stop(0f);
    }

    public void stop(float fadeOut) {
        if (currentTrack == null) return;

        if (fadeOut > 0) {
            MusicTrack trackToStop = currentTrack;
            currentTrack.fadeTo(0f, fadeOut, () -> {
                trackToStop.stop();
            });
            currentTrack = null;
        } else {
            currentTrack.stop();
            currentTrack = null;
        }
    }

    public void pause() {
        if (currentTrack != null) {
            currentTrack.pause();
            paused = true;
        }
    }

    public void resume() {
        if (currentTrack != null && paused) {
            currentTrack.resume();
            paused = false;
        }
    }

    // ========== Crossfade ==========

    public void crossfadeTo(AudioClip newMusic, float duration) {
        if (currentTrack == null) {
            play(newMusic, duration);
            return;
        }

        crossfadeController.start(
            currentTrack,
            new MusicTrack(newMusic),
            duration,
            (oldTrack, newTrack) -> {
                oldTrack.stop();
                currentTrack = newTrack;
            }
        );

        nextTrack = crossfadeController.getIncomingTrack();
        nextTrack.play(engine);
    }

    // ========== Volume ==========

    public void setVolume(float volume) {
        this.volume = clamp(volume, 0f, 1f);
        if (currentTrack != null) {
            currentTrack.setVolume(calculateVolume());
        }
    }

    public float getVolume() {
        return volume;
    }

    private float calculateVolume() {
        return mixer.calculateFinalVolume(AudioChannel.MUSIC, volume);
    }

    // ========== State ==========

    public boolean isPlaying() {
        return currentTrack != null && currentTrack.isPlaying();
    }

    public AudioClip getCurrentClip() {
        return currentTrack != null ? currentTrack.getClip() : null;
    }

    public float getPlaybackPosition() {
        return currentTrack != null ? currentTrack.getPosition() : 0f;
    }

    public void setPlaybackPosition(float seconds) {
        if (currentTrack != null) {
            currentTrack.setPosition(seconds);
        }
    }

    // ========== Update ==========

    public void update(float deltaTime) {
        if (crossfadeController.isActive()) {
            crossfadeController.update(deltaTime);
        }

        if (currentTrack != null) {
            currentTrack.update(deltaTime);
        }
    }
}
```

## MusicTrack

Wrapper for music playback with fade control:

```java
public class MusicTrack {
    private final AudioClip clip;
    private AudioHandle handle;

    private float volume = 1.0f;
    private float targetVolume = 1.0f;
    private float fadeSpeed = 0f;
    private Runnable onFadeComplete;

    public MusicTrack(AudioClip clip) {
        this.clip = clip;
    }

    public void play(AudioEngine engine) {
        handle = engine.playStreaming(clip, new PlaybackSettings()
            .channel(AudioChannel.MUSIC)
            .loop(true)
            .volume(volume));
    }

    public void stop() {
        if (handle != null) {
            handle.stop();
            handle = null;
        }
    }

    public void pause() {
        if (handle != null) {
            handle.pause();
        }
    }

    public void resume() {
        if (handle != null) {
            handle.resume();
        }
    }

    public void setVolume(float vol) {
        this.volume = vol;
        this.targetVolume = vol;
        this.fadeSpeed = 0f;
        if (handle != null) {
            handle.setVolume(vol);
        }
    }

    public void fadeTo(float target, float duration) {
        fadeTo(target, duration, null);
    }

    public void fadeTo(float target, float duration, Runnable onComplete) {
        this.targetVolume = target;
        this.fadeSpeed = Math.abs(target - volume) / duration;
        this.onFadeComplete = onComplete;
    }

    public void update(float deltaTime) {
        if (fadeSpeed > 0) {
            if (volume < targetVolume) {
                volume = Math.min(volume + fadeSpeed * deltaTime, targetVolume);
            } else {
                volume = Math.max(volume - fadeSpeed * deltaTime, targetVolume);
            }

            if (handle != null) {
                handle.setVolume(volume);
            }

            if (Math.abs(volume - targetVolume) < 0.001f) {
                volume = targetVolume;
                fadeSpeed = 0f;
                if (onFadeComplete != null) {
                    onFadeComplete.run();
                    onFadeComplete = null;
                }
            }
        }
    }

    public boolean isPlaying() {
        return handle != null && handle.isPlaying();
    }

    public AudioClip getClip() {
        return clip;
    }

    public float getPosition() {
        return handle != null ? handle.getPlaybackTime() : 0f;
    }

    public void setPosition(float seconds) {
        if (handle != null) {
            handle.setTime(seconds);
        }
    }
}
```

## CrossfadeController

Manages smooth transitions between tracks:

```java
public class CrossfadeController {
    private MusicTrack outgoingTrack;
    private MusicTrack incomingTrack;
    private float duration;
    private float elapsed;
    private boolean active = false;
    private BiConsumer<MusicTrack, MusicTrack> onComplete;

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

    public void update(float deltaTime) {
        if (!active) return;

        elapsed += deltaTime;
        float progress = Math.min(elapsed / duration, 1f);

        // Linear crossfade (can be replaced with curves)
        float outVol = 1f - progress;
        float inVol = progress;

        outgoingTrack.setVolume(outVol);
        incomingTrack.setVolume(inVol);

        if (progress >= 1f) {
            active = false;
            if (onComplete != null) {
                onComplete.accept(outgoingTrack, incomingTrack);
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    public MusicTrack getIncomingTrack() {
        return incomingTrack;
    }
}
```

## Streaming Audio

For large music files, use streaming instead of loading entire file:

```java
public class StreamingAudioClip extends AudioClip {
    private final String path;
    private final int[] bufferIds;  // Ring buffer of OpenAL buffers
    private final int bufferCount = 3;

    // Decoder state
    private OggDecoder decoder;
    private boolean endOfStream = false;

    public StreamingAudioClip(String path) {
        this.path = path;
        this.bufferIds = new int[bufferCount];
        // Create OpenAL buffers
        for (int i = 0; i < bufferCount; i++) {
            bufferIds[i] = AL10.alGenBuffers();
        }
    }

    public void startStreaming() {
        decoder = new OggDecoder(path);
        endOfStream = false;
        // Fill initial buffers
        for (int bufferId : bufferIds) {
            fillBuffer(bufferId);
        }
    }

    public void updateStreaming(int sourceId) {
        // Check for processed buffers
        int processed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);

        while (processed-- > 0 && !endOfStream) {
            int bufferId = AL10.alSourceUnqueueBuffers(sourceId);
            fillBuffer(bufferId);
            AL10.alSourceQueueBuffers(sourceId, bufferId);
        }
    }

    private void fillBuffer(int bufferId) {
        ByteBuffer data = decoder.decode(BUFFER_SIZE);
        if (data == null || data.remaining() == 0) {
            endOfStream = true;
            return;
        }
        AL10.alBufferData(bufferId, getFormat(), data, decoder.getSampleRate());
    }

    @Override
    public void dispose() {
        for (int bufferId : bufferIds) {
            AL10.alDeleteBuffers(bufferId);
        }
        if (decoder != null) {
            decoder.close();
        }
    }
}
```

## Usage Examples

```java
// In scene
@Override
public void onLoad() {
    AudioClip forestTheme = Assets.load("audio/music/forest.ogg", AudioClip.class);
    Audio.music().play(forestTheme, 2.0f); // 2 second fade in
}

// Scene transition with crossfade
public void enterDungeon() {
    AudioClip dungeonTheme = Assets.load("audio/music/dungeon.ogg", AudioClip.class);
    Audio.music().crossfadeTo(dungeonTheme, 1.5f);
}

// Boss fight music
public void startBossFight() {
    AudioClip bossTheme = Assets.load("audio/music/boss.ogg", AudioClip.class);
    Audio.music().crossfadeTo(bossTheme, 0.5f); // Quick transition
}

// Victory
public void onBossDefeated() {
    Audio.music().stop(0.5f); // Fade out
    // Play victory jingle (one-shot, not looping)
    Audio.playOneShot(victoryJingle, new PlaybackSettings()
        .channel(AudioChannel.MUSIC)
        .volume(1.0f));
}
```

## Integration with SceneManager

```java
// Automatic music handling on scene load
public class SceneMusicHandler implements GameEventListener {
    private final Map<String, AudioClip> sceneMusic = new HashMap<>();

    public void registerSceneMusic(String sceneName, AudioClip music) {
        sceneMusic.put(sceneName, music);
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof SceneLoadedEvent loaded) {
            AudioClip music = sceneMusic.get(loaded.getSceneName());
            if (music != null) {
                Audio.music().crossfadeTo(music, 1.5f);
            }
        }
    }
}
```
