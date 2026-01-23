# 3. Audio Components

## AudioSource

Component for playing sounds attached to GameObjects with full control:

```java
public class AudioSource extends Component {
    // ========== Serialized Fields ==========

    // Clip settings
    private AudioClip clip;
    private float volume = 1.0f;
    private float pitch = 1.0f;
    private boolean loop = false;
    private boolean playOnStart = false;

    // 3D settings
    private boolean spatialize = true;       // Enable 3D positioning
    private float minDistance = 1.0f;        // Full volume within this range
    private float maxDistance = 500.0f;      // Silent beyond this range
    private float rolloffFactor = 1.0f;      // Attenuation curve steepness

    // Mixing
    private AudioChannel channel = AudioChannel.SFX;

    // ========== Runtime State ==========

    @HideInInspector
    private AudioHandle currentHandle;

    // ========== Lifecycle ==========

    @Override
    protected void onEnable() {
        if (playOnStart && clip != null) {
            play();
        }
    }

    @Override
    protected void onDisable() {
        if (currentHandle != null && currentHandle.isPlaying()) {
            pause();
        }
    }

    @Override
    public void update(float deltaTime) {
        if (spatialize && currentHandle != null && currentHandle.isPlaying()) {
            // Update 3D position from transform
            currentHandle.setPosition(getTransform().getPosition());
        }
    }

    @Override
    protected void onDestroy() {
        stop();
    }

    // ========== Playback Control ==========

    public void play() {
        if (clip == null) return;

        stop(); // Stop any existing playback

        PlaybackSettings settings = new PlaybackSettings()
            .volume(volume)
            .pitch(pitch)
            .channel(channel)
            .loop(loop)
            .minDistance(minDistance)
            .maxDistance(maxDistance)
            .rolloff(rolloffFactor);

        if (spatialize && clip.supports3D()) {
            settings.position(getTransform().getPosition());
        }

        currentHandle = Audio.playOneShot(clip, settings);
    }

    public void pause() {
        if (currentHandle != null) {
            currentHandle.pause();
        }
    }

    public void resume() {
        if (currentHandle != null) {
            currentHandle.resume();
        }
    }

    public void stop() {
        if (currentHandle != null) {
            currentHandle.stop();
            currentHandle = null;
        }
    }

    public void setTime(float seconds) {
        if (currentHandle != null) {
            currentHandle.setTime(seconds);
        }
    }

    // ========== State Queries ==========

    public boolean isPlaying() {
        return currentHandle != null && currentHandle.isPlaying();
    }

    public float getTime() {
        return currentHandle != null ? currentHandle.getPlaybackTime() : 0f;
    }

    // ========== Getters/Setters ==========

    public AudioClip getClip() { return clip; }
    public void setClip(AudioClip clip) { this.clip = clip; }

    public float getVolume() { return volume; }
    public void setVolume(float volume) {
        this.volume = volume;
        if (currentHandle != null) {
            currentHandle.setVolume(volume);
        }
    }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) {
        this.pitch = pitch;
        if (currentHandle != null) {
            currentHandle.setPitch(pitch);
        }
    }

    // ... other getters/setters
}
```

**Usage:**
```java
GameObject enemy = new GameObject("Enemy");
AudioSource audio = enemy.addComponent(new AudioSource());
audio.setClip(growlClip);
audio.setLoop(true);
audio.setSpatialize(true);
audio.play();
```

## AudioListener

Defines the "ears" of the game - usually attached to camera or player:

```java
public class AudioListener extends Component {
    private static AudioListener activeListener;

    // ========== Lifecycle ==========

    @Override
    protected void onEnable() {
        // Only one listener active at a time
        if (activeListener != null && activeListener != this) {
            activeListener.setEnabled(false);
        }
        activeListener = this;
    }

    @Override
    protected void onDisable() {
        if (activeListener == this) {
            activeListener = null;
        }
    }

    @Override
    public void lateUpdate(float deltaTime) {
        // Update listener position/orientation from transform
        Vector3f pos = getTransform().getPosition();
        Vector3f forward = getTransform().getForward();
        Vector3f up = getTransform().getUp();

        Audio.getEngine().setListenerPosition(pos);
        Audio.getEngine().setListenerOrientation(forward, up);
    }

    // ========== Static Access ==========

    public static AudioListener getActive() {
        return activeListener;
    }

    public static Vector3f getListenerPosition() {
        return activeListener != null
            ? activeListener.getTransform().getPosition()
            : new Vector3f(0, 0, 0);
    }
}
```

**Typical setup:**
```java
// On camera
camera.addComponent(new AudioListener());

// Or on player for first-person games
player.addComponent(new AudioListener());
```

## AmbientZone

Trigger-based ambient audio that plays when the listener enters a zone:

```java
public class AmbientZone extends Component {
    // Zone settings
    private AudioClip ambientClip;
    private float volume = 1.0f;
    private float fadeInTime = 1.0f;
    private float fadeOutTime = 1.0f;

    // Zone bounds (uses collider or manual radius)
    @ComponentRef(target = ComponentRef.Target.SELF)
    private Collider zoneCollider;
    private float radius = 10.0f;  // Fallback if no collider

    // Runtime
    @HideInInspector
    private AudioHandle ambientHandle;
    @HideInInspector
    private boolean listenerInside = false;

    @Override
    public void update(float deltaTime) {
        boolean wasInside = listenerInside;
        listenerInside = isListenerInZone();

        if (listenerInside && !wasInside) {
            // Entered zone
            startAmbient();
        } else if (!listenerInside && wasInside) {
            // Exited zone
            stopAmbient();
        }
    }

    private boolean isListenerInZone() {
        Vector3f listenerPos = AudioListener.getListenerPosition();
        Vector3f zonePos = getTransform().getPosition();

        if (zoneCollider != null) {
            return zoneCollider.containsPoint(listenerPos);
        }

        // Fallback: sphere check
        return listenerPos.distance(zonePos) <= radius;
    }

    private void startAmbient() {
        if (ambientClip == null) return;

        ambientHandle = Audio.playOneShot(ambientClip, new PlaybackSettings()
            .volume(0f)  // Start silent
            .channel(AudioChannel.AMBIENT)
            .loop(true));

        // Fade in
        Audio.getEngine().fadeVolume(ambientHandle, volume, fadeInTime);
    }

    private void stopAmbient() {
        if (ambientHandle != null) {
            // Fade out then stop
            Audio.getEngine().fadeVolume(ambientHandle, 0f, fadeOutTime, () -> {
                ambientHandle.stop();
                ambientHandle = null;
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (ambientHandle != null) {
            ambientHandle.stop();
        }
    }
}
```

**Usage:**
```java
// Create ambient zone for forest area
GameObject forestZone = new GameObject("ForestAmbience");
forestZone.addComponent(new BoxCollider(50, 50));  // Zone bounds

AmbientZone ambient = forestZone.addComponent(new AmbientZone());
ambient.setAmbientClip(forestSoundsClip);
ambient.setVolume(0.6f);
ambient.setFadeInTime(2.0f);
```

## Component Lifecycle Integration

### Scene Load/Unload

```java
// AudioSource automatically handles scene lifecycle:
// - onEnable: Plays if playOnStart is true
// - onDisable: Pauses playback
// - onDestroy: Stops and cleans up

// For scene transitions, the TransitionManager can duck audio:
public class AudioTransitionHandler implements GameEventListener {
    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof SceneUnloadEvent) {
            // Stop non-persistent sounds
            Audio.stopChannel(AudioChannel.SFX);
            Audio.stopChannel(AudioChannel.AMBIENT);
            // Music continues (handled separately)
        }
    }
}
```

### Pause Menu

```java
// When game is paused
Audio.pauseChannel(AudioChannel.SFX);
Audio.pauseChannel(AudioChannel.AMBIENT);
// Keep music but duck volume
float prevMusicVol = Audio.getChannelVolume(AudioChannel.MUSIC);
Audio.setChannelVolume(AudioChannel.MUSIC, 0.3f);

// When resumed
Audio.resumeChannel(AudioChannel.SFX);
Audio.resumeChannel(AudioChannel.AMBIENT);
Audio.setChannelVolume(AudioChannel.MUSIC, prevMusicVol);
```
