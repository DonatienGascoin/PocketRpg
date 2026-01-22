# 2. Core API

## Audio Static Facade

Following the `Input`, `Time`, `PostProcessing` pattern:

```java
public final class Audio {
    private static AudioContext context;

    private Audio() {} // Prevent instantiation

    public static void initialize(AudioContext context) {
        Audio.context = context;
    }

    public static void destroy() {
        if (context != null) {
            context.destroy();
            context = null;
        }
    }

    // ========== One-Shot Sounds ==========

    public static AudioHandle playOneShot(AudioClip clip) {
        return context.playOneShot(clip, 1.0f, AudioChannel.SFX);
    }

    public static AudioHandle playOneShot(AudioClip clip, float volume) {
        return context.playOneShot(clip, volume, AudioChannel.SFX);
    }

    public static AudioHandle playOneShot(AudioClip clip, Vector3f position) {
        return context.playOneShotAt(clip, position, 1.0f, AudioChannel.SFX);
    }

    public static AudioHandle playOneShot(AudioClip clip, PlaybackSettings settings) {
        return context.playOneShot(clip, settings);
    }

    // ========== Music ==========

    public static MusicPlayer music() {
        return context.getMusicPlayer();
    }

    // ========== Mixing ==========

    public static void setMasterVolume(float volume) {
        context.getMixer().setVolume(AudioChannel.MASTER, volume);
    }

    public static float getMasterVolume() {
        return context.getMixer().getVolume(AudioChannel.MASTER);
    }

    public static void setChannelVolume(AudioChannel channel, float volume) {
        context.getMixer().setVolume(channel, volume);
    }

    public static float getChannelVolume(AudioChannel channel) {
        return context.getMixer().getVolume(channel);
    }

    // ========== Global Control ==========

    public static void pauseAll() {
        context.pauseAll();
    }

    public static void resumeAll() {
        context.resumeAll();
    }

    public static void stopAll() {
        context.stopAll();
    }

    // ========== Engine Access (for components) ==========

    public static AudioEngine getEngine() {
        return context.getEngine();
    }
}
```

## AudioContext Interface

```java
public interface AudioContext {
    // Initialization
    void initialize(AudioConfig config);
    void destroy();

    // Update (called each frame)
    void update(float deltaTime);

    // One-shot playback
    AudioHandle playOneShot(AudioClip clip, float volume, AudioChannel channel);
    AudioHandle playOneShotAt(AudioClip clip, Vector3f position, float volume, AudioChannel channel);
    AudioHandle playOneShot(AudioClip clip, PlaybackSettings settings);

    // Source management
    AudioHandle play(AudioSource source);
    void stop(AudioHandle handle);
    void pause(AudioHandle handle);
    void resume(AudioHandle handle);

    // Global control
    void pauseAll();
    void resumeAll();
    void stopAll();
    void stopChannel(AudioChannel channel);

    // Subsystems
    MusicPlayer getMusicPlayer();
    AudioMixer getMixer();
    AudioEngine getEngine();
}
```

## AudioClip

Audio data container:

```java
public class AudioClip {
    private final String name;
    private final String path;
    private final int bufferId;        // OpenAL buffer ID
    private final float duration;      // Length in seconds
    private final int sampleRate;
    private final int channels;        // 1 = mono, 2 = stereo

    // Getters
    public String getName() { return name; }
    public String getPath() { return path; }
    public float getDuration() { return duration; }
    public int getSampleRate() { return sampleRate; }
    public int getChannels() { return channels; }
    public boolean isMono() { return channels == 1; }
    public boolean isStereo() { return channels == 2; }

    // 3D audio requires mono clips
    public boolean supports3D() { return isMono(); }
}
```

**Supported Formats:**
- WAV (uncompressed, fast loading)
- OGG Vorbis (compressed, smaller files)
- MP3 (via extension, for music)

## AudioHandle

Returned when playing sounds, allows control of in-flight audio:

```java
public class AudioHandle {
    private final int sourceId;
    private final long playStartTime;
    private final AudioClip clip;

    public boolean isPlaying();
    public boolean isValid();

    public void stop();
    public void pause();
    public void resume();

    public void setVolume(float volume);
    public void setPitch(float pitch);
    public void setPosition(Vector3f position);

    public float getPlaybackTime();
    public AudioClip getClip();
}
```

## PlaybackSettings

Builder pattern for complex playback configuration:

```java
public class PlaybackSettings {
    private float volume = 1.0f;
    private float pitch = 1.0f;
    private AudioChannel channel = AudioChannel.SFX;
    private Vector3f position = null;  // null = 2D
    private boolean loop = false;
    private float delay = 0f;
    private float minDistance = 1.0f;
    private float maxDistance = 500.0f;
    private float rolloffFactor = 1.0f;

    public PlaybackSettings volume(float v) { this.volume = v; return this; }
    public PlaybackSettings pitch(float p) { this.pitch = p; return this; }
    public PlaybackSettings channel(AudioChannel c) { this.channel = c; return this; }
    public PlaybackSettings position(Vector3f pos) { this.position = pos; return this; }
    public PlaybackSettings loop(boolean l) { this.loop = l; return this; }
    public PlaybackSettings delay(float d) { this.delay = d; return this; }
    public PlaybackSettings minDistance(float d) { this.minDistance = d; return this; }
    public PlaybackSettings maxDistance(float d) { this.maxDistance = d; return this; }
    public PlaybackSettings rolloff(float r) { this.rolloffFactor = r; return this; }

    // Convenience factory methods
    public static PlaybackSettings at(Vector3f position) {
        return new PlaybackSettings().position(position);
    }

    public static PlaybackSettings looping() {
        return new PlaybackSettings().loop(true);
    }
}
```

## Usage Examples

```java
// Simple one-shot
Audio.playOneShot(explosionClip);

// With volume
Audio.playOneShot(footstepClip, 0.5f);

// At world position (3D)
Audio.playOneShot(gunfireClip, enemyPosition);

// Full control
Audio.playOneShot(clip, new PlaybackSettings()
    .volume(0.8f)
    .pitch(1.2f)
    .channel(AudioChannel.SFX)
    .position(worldPos));

// Control in-flight audio
AudioHandle handle = Audio.playOneShot(alarmClip, PlaybackSettings.looping());
// ... later ...
handle.stop();
```
