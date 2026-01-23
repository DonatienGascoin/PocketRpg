# 4. Audio Mixing

## Audio Channels

Hierarchical volume control with dedicated channels:

```java
public enum AudioChannel {
    MASTER,     // Affects all audio
    MUSIC,      // Background music
    SFX,        // Sound effects
    VOICE,      // Character dialogue
    AMBIENT,    // Environmental sounds
    UI          // Menu/interface sounds
}
```

## AudioMixer

Manages volume levels and channel settings:

```java
public class AudioMixer {
    private final Map<AudioChannel, AudioBus> buses = new EnumMap<>(AudioChannel.class);

    public AudioMixer(AudioConfig config) {
        // Initialize buses with config values
        buses.put(AudioChannel.MASTER, new AudioBus(config.getMasterVolume()));
        buses.put(AudioChannel.MUSIC, new AudioBus(config.getMusicVolume()));
        buses.put(AudioChannel.SFX, new AudioBus(config.getSfxVolume()));
        buses.put(AudioChannel.VOICE, new AudioBus(config.getVoiceVolume()));
        buses.put(AudioChannel.AMBIENT, new AudioBus(config.getAmbientVolume()));
        buses.put(AudioChannel.UI, new AudioBus(config.getUiVolume()));
    }

    // ========== Volume Control ==========

    public void setVolume(AudioChannel channel, float volume) {
        buses.get(channel).setVolume(clamp(volume, 0f, 1f));
    }

    public float getVolume(AudioChannel channel) {
        return buses.get(channel).getVolume();
    }

    // ========== Mute Control ==========

    public void mute(AudioChannel channel) {
        buses.get(channel).setMuted(true);
    }

    public void unmute(AudioChannel channel) {
        buses.get(channel).setMuted(false);
    }

    public void toggleMute(AudioChannel channel) {
        AudioBus bus = buses.get(channel);
        bus.setMuted(!bus.isMuted());
    }

    public boolean isMuted(AudioChannel channel) {
        return buses.get(channel).isMuted();
    }

    // ========== Final Volume Calculation ==========

    /**
     * Calculate the final volume for a sound.
     * Chain: master * channel * source * distance
     */
    public float calculateFinalVolume(AudioChannel channel, float sourceVolume) {
        AudioBus masterBus = buses.get(AudioChannel.MASTER);
        AudioBus channelBus = buses.get(channel);

        if (masterBus.isMuted() || channelBus.isMuted()) {
            return 0f;
        }

        return masterBus.getVolume() * channelBus.getVolume() * sourceVolume;
    }

    /**
     * Calculate with distance attenuation for 3D sounds.
     */
    public float calculateFinalVolume(AudioChannel channel, float sourceVolume,
                                       float distanceAttenuation) {
        return calculateFinalVolume(channel, sourceVolume) * distanceAttenuation;
    }

    // ========== Pause/Resume Channels ==========

    public void pauseChannel(AudioChannel channel) {
        buses.get(channel).setPaused(true);
    }

    public void resumeChannel(AudioChannel channel) {
        buses.get(channel).setPaused(false);
    }

    public boolean isChannelPaused(AudioChannel channel) {
        return buses.get(channel).isPaused();
    }
}
```

## AudioBus

Per-channel settings container:

```java
public class AudioBus {
    private float volume;
    private boolean muted = false;
    private boolean paused = false;

    // For volume transitions
    private float targetVolume;
    private float fadeSpeed;
    private boolean fading = false;

    public AudioBus(float initialVolume) {
        this.volume = initialVolume;
        this.targetVolume = initialVolume;
    }

    // ========== Volume ==========

    public float getVolume() { return volume; }

    public void setVolume(float volume) {
        this.volume = volume;
        this.targetVolume = volume;
        this.fading = false;
    }

    public void fadeToVolume(float target, float duration) {
        this.targetVolume = target;
        this.fadeSpeed = Math.abs(target - volume) / duration;
        this.fading = true;
    }

    public void update(float deltaTime) {
        if (fading) {
            if (volume < targetVolume) {
                volume = Math.min(volume + fadeSpeed * deltaTime, targetVolume);
            } else if (volume > targetVolume) {
                volume = Math.max(volume - fadeSpeed * deltaTime, targetVolume);
            }

            if (Math.abs(volume - targetVolume) < 0.001f) {
                volume = targetVolume;
                fading = false;
            }
        }
    }

    // ========== Mute/Pause ==========

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    /**
     * Effective volume considering mute state.
     */
    public float getEffectiveVolume() {
        return muted ? 0f : volume;
    }
}
```

## Volume Calculation Chain

```
finalVolume = masterVolume * channelVolume * sourceVolume * distanceAttenuation

Where:
- masterVolume: Global volume (0-1)
- channelVolume: Per-channel volume (0-1)
- sourceVolume: AudioSource.volume or playOneShot volume (0-1)
- distanceAttenuation: 3D falloff based on listener distance (0-1)
```

## AudioConfig

Stored in `gameData/config/audio.json`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioConfig {
    @Builder.Default
    private float masterVolume = 1.0f;

    @Builder.Default
    private float musicVolume = 0.8f;

    @Builder.Default
    private float sfxVolume = 1.0f;

    @Builder.Default
    private float voiceVolume = 1.0f;

    @Builder.Default
    private float ambientVolume = 0.7f;

    @Builder.Default
    private float uiVolume = 1.0f;

    @Builder.Default
    private int maxSimultaneousSounds = 32;

    @Builder.Default
    private float defaultRolloffFactor = 1.0f;

    @Builder.Default
    private boolean enableReverb = false;

    @Builder.Default
    private float musicCrossfadeDuration = 2.0f;
}
```

**JSON format:**
```json
{
  "masterVolume": 1.0,
  "musicVolume": 0.8,
  "sfxVolume": 1.0,
  "voiceVolume": 1.0,
  "ambientVolume": 0.7,
  "uiVolume": 1.0,
  "maxSimultaneousSounds": 32,
  "defaultRolloffFactor": 1.0,
  "musicCrossfadeDuration": 2.0,
  "enableReverb": false
}
```

## Audio Ducking (Advanced)

Automatically lower certain channels when others play:

```java
public class DuckingRule {
    private final AudioChannel trigger;     // When this plays...
    private final AudioChannel target;      // ...duck this channel
    private final float duckVolume;         // To this volume (0-1)
    private final float fadeTime;           // Over this duration

    public DuckingRule(AudioChannel trigger, AudioChannel target,
                       float duckVolume, float fadeTime) {
        this.trigger = trigger;
        this.target = target;
        this.duckVolume = duckVolume;
        this.fadeTime = fadeTime;
    }
}

// Usage: Duck music when voice plays
Audio.getMixer().addDuckingRule(new DuckingRule(
    AudioChannel.VOICE,   // When voice plays
    AudioChannel.MUSIC,   // Duck music
    0.3f,                 // To 30% volume
    0.2f                  // Over 0.2 seconds
));
```

## EngineConfiguration Update

Add audio config to existing configuration system:

```java
@Getter
public class EngineConfiguration {
    private final GameConfig game;
    private final InputConfig input;
    private final RenderingConfig rendering;
    private final AudioConfig audio;  // NEW

    public static EngineConfiguration load() {
        return new EngineConfiguration(
            loadConfig("gameData/config/game.json", GameConfig.class),
            loadConfig("gameData/config/input.json", InputConfig.class),
            loadConfig("gameData/config/rendering.json", RenderingConfig.class),
            loadConfig("gameData/config/audio.json", AudioConfig.class)
        );
    }
}
```
