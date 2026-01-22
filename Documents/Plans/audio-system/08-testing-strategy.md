# 8. Testing Strategy

## Overview

The audio system requires multiple testing approaches:
- **Unit tests**: Individual classes in isolation
- **Integration tests**: AudioContext + Engine + Backend working together
- **Component tests**: AudioSource/Listener with mock GameObjects
- **Editor tests**: Inspector preview functionality
- **Performance tests**: Stress tests with many simultaneous sounds

## Mock Audio Backend

For testing without actual audio hardware:

```java
public class MockAudioBackend implements AudioBackend {
    private final List<PlayedSound> playedSounds = new ArrayList<>();
    private final Map<Integer, MockSource> sources = new HashMap<>();
    private int nextSourceId = 1;
    private int nextBufferId = 1;

    // ========== Buffer Operations ==========

    @Override
    public int createBuffer(AudioData data) {
        return nextBufferId++;
    }

    @Override
    public void deleteBuffer(int bufferId) {
        // No-op for mock
    }

    // ========== Source Operations ==========

    @Override
    public int createSource() {
        int id = nextSourceId++;
        sources.put(id, new MockSource(id));
        return id;
    }

    @Override
    public void deleteSource(int sourceId) {
        sources.remove(sourceId);
    }

    @Override
    public void playSource(int sourceId) {
        MockSource source = sources.get(sourceId);
        if (source != null) {
            source.state = PlaybackState.PLAYING;
            source.playStartTime = System.nanoTime();
            playedSounds.add(new PlayedSound(sourceId, source.bufferId, source.volume));
        }
    }

    @Override
    public void stopSource(int sourceId) {
        MockSource source = sources.get(sourceId);
        if (source != null) {
            source.state = PlaybackState.STOPPED;
        }
    }

    @Override
    public void pauseSource(int sourceId) {
        MockSource source = sources.get(sourceId);
        if (source != null) {
            source.state = PlaybackState.PAUSED;
        }
    }

    @Override
    public void setSourceBuffer(int sourceId, int bufferId) {
        MockSource source = sources.get(sourceId);
        if (source != null) {
            source.bufferId = bufferId;
        }
    }

    @Override
    public void setSourceVolume(int sourceId, float volume) {
        MockSource source = sources.get(sourceId);
        if (source != null) {
            source.volume = volume;
        }
    }

    @Override
    public void setSourcePitch(int sourceId, float pitch) {
        MockSource source = sources.get(sourceId);
        if (source != null) {
            source.pitch = pitch;
        }
    }

    @Override
    public void setSourcePosition(int sourceId, Vector3f position) {
        MockSource source = sources.get(sourceId);
        if (source != null) {
            source.position = new Vector3f(position);
        }
    }

    @Override
    public void setSourceLooping(int sourceId, boolean loop) {
        MockSource source = sources.get(sourceId);
        if (source != null) {
            source.looping = loop;
        }
    }

    @Override
    public PlaybackState getSourceState(int sourceId) {
        MockSource source = sources.get(sourceId);
        return source != null ? source.state : PlaybackState.STOPPED;
    }

    // ========== Listener ==========

    private Vector3f listenerPosition = new Vector3f();
    private Vector3f listenerForward = new Vector3f(0, 0, -1);
    private Vector3f listenerUp = new Vector3f(0, 1, 0);

    @Override
    public void setListenerPosition(Vector3f position) {
        this.listenerPosition = new Vector3f(position);
    }

    @Override
    public void setListenerOrientation(Vector3f forward, Vector3f up) {
        this.listenerForward = new Vector3f(forward);
        this.listenerUp = new Vector3f(up);
    }

    // ========== Test Helpers ==========

    public int getPlayCount() {
        return playedSounds.size();
    }

    public List<PlayedSound> getPlayedSounds() {
        return Collections.unmodifiableList(playedSounds);
    }

    public boolean wasPlayed(int bufferId) {
        return playedSounds.stream().anyMatch(s -> s.bufferId == bufferId);
    }

    public MockSource getSource(int sourceId) {
        return sources.get(sourceId);
    }

    public int getActiveSourceCount() {
        return (int) sources.values().stream()
            .filter(s -> s.state == PlaybackState.PLAYING)
            .count();
    }

    public void reset() {
        playedSounds.clear();
        sources.clear();
        nextSourceId = 1;
        nextBufferId = 1;
    }

    // ========== Inner Classes ==========

    public static class MockSource {
        public final int id;
        public int bufferId;
        public float volume = 1.0f;
        public float pitch = 1.0f;
        public Vector3f position = new Vector3f();
        public boolean looping = false;
        public PlaybackState state = PlaybackState.STOPPED;
        public long playStartTime;

        public MockSource(int id) {
            this.id = id;
        }
    }

    public record PlayedSound(int sourceId, int bufferId, float volume) {}
}
```

## NullAudioBackend

Silent backend for headless testing:

```java
public class NullAudioBackend implements AudioBackend {
    @Override
    public int createBuffer(AudioData data) { return 1; }

    @Override
    public void deleteBuffer(int bufferId) {}

    @Override
    public int createSource() { return 1; }

    @Override
    public void deleteSource(int sourceId) {}

    @Override
    public void playSource(int sourceId) {}

    @Override
    public void stopSource(int sourceId) {}

    @Override
    public void pauseSource(int sourceId) {}

    @Override
    public void setSourceBuffer(int sourceId, int bufferId) {}

    @Override
    public void setSourceVolume(int sourceId, float volume) {}

    @Override
    public void setSourcePitch(int sourceId, float pitch) {}

    @Override
    public void setSourcePosition(int sourceId, Vector3f position) {}

    @Override
    public void setSourceLooping(int sourceId, boolean loop) {}

    @Override
    public PlaybackState getSourceState(int sourceId) {
        return PlaybackState.STOPPED;
    }

    @Override
    public void setListenerPosition(Vector3f position) {}

    @Override
    public void setListenerOrientation(Vector3f forward, Vector3f up) {}
}
```

## Test Utilities

```java
public class AudioTestUtils {

    /**
     * Create a test AudioClip without loading from file.
     */
    public static AudioClip createTestClip(String name, float duration) {
        return new AudioClip(name, "test/" + name, 1, duration, 44100, 1);
    }

    /**
     * Create a mock AudioContext with MockAudioBackend.
     */
    public static MockAudioContext createMockContext() {
        return new MockAudioContext(new MockAudioBackend());
    }

    /**
     * Simulate time passing for fade/crossfade tests.
     */
    public static void simulateTime(AudioContext context, float seconds, float stepSize) {
        float elapsed = 0;
        while (elapsed < seconds) {
            context.update(stepSize);
            elapsed += stepSize;
        }
    }
}
```

## Unit Test Examples

### AudioMixer Tests

```java
class AudioMixerTest {

    private AudioMixer mixer;

    @BeforeEach
    void setUp() {
        mixer = new AudioMixer(AudioConfig.builder().build());
    }

    @Test
    void testDefaultVolumes() {
        assertEquals(1.0f, mixer.getVolume(AudioChannel.MASTER));
        assertEquals(0.8f, mixer.getVolume(AudioChannel.MUSIC));
        assertEquals(1.0f, mixer.getVolume(AudioChannel.SFX));
    }

    @Test
    void testSetVolume() {
        mixer.setVolume(AudioChannel.SFX, 0.5f);
        assertEquals(0.5f, mixer.getVolume(AudioChannel.SFX));
    }

    @Test
    void testVolumeClamp() {
        mixer.setVolume(AudioChannel.SFX, -0.5f);
        assertEquals(0f, mixer.getVolume(AudioChannel.SFX));

        mixer.setVolume(AudioChannel.SFX, 1.5f);
        assertEquals(1f, mixer.getVolume(AudioChannel.SFX));
    }

    @Test
    void testFinalVolumeCalculation() {
        mixer.setVolume(AudioChannel.MASTER, 0.8f);
        mixer.setVolume(AudioChannel.SFX, 0.5f);

        float finalVolume = mixer.calculateFinalVolume(AudioChannel.SFX, 1.0f);

        assertEquals(0.4f, finalVolume, 0.001f);
    }

    @Test
    void testMuteAffectsFinalVolume() {
        mixer.mute(AudioChannel.SFX);

        float finalVolume = mixer.calculateFinalVolume(AudioChannel.SFX, 1.0f);

        assertEquals(0f, finalVolume);
    }

    @Test
    void testMasterMuteAffectsAllChannels() {
        mixer.mute(AudioChannel.MASTER);

        assertEquals(0f, mixer.calculateFinalVolume(AudioChannel.SFX, 1.0f));
        assertEquals(0f, mixer.calculateFinalVolume(AudioChannel.MUSIC, 1.0f));
        assertEquals(0f, mixer.calculateFinalVolume(AudioChannel.VOICE, 1.0f));
    }
}
```

### AudioHandle Tests

```java
class AudioHandleTest {

    private MockAudioBackend backend;
    private AudioEngine engine;

    @BeforeEach
    void setUp() {
        backend = new MockAudioBackend();
        engine = new AudioEngine(backend, AudioConfig.builder().build());
    }

    @Test
    void testHandleIsValidAfterPlay() {
        AudioClip clip = AudioTestUtils.createTestClip("test", 1.0f);
        AudioHandle handle = engine.playOneShot(clip, 1.0f);

        assertTrue(handle.isValid());
        assertTrue(handle.isPlaying());
    }

    @Test
    void testHandleStopsPlayback() {
        AudioClip clip = AudioTestUtils.createTestClip("test", 1.0f);
        AudioHandle handle = engine.playOneShot(clip, 1.0f);

        handle.stop();

        assertFalse(handle.isPlaying());
    }

    @Test
    void testHandleVolumeChange() {
        AudioClip clip = AudioTestUtils.createTestClip("test", 1.0f);
        AudioHandle handle = engine.playOneShot(clip, 1.0f);

        handle.setVolume(0.5f);

        MockAudioBackend.MockSource source = backend.getSource(handle.getSourceId());
        assertEquals(0.5f, source.volume);
    }
}
```

### MusicPlayer Tests

```java
class MusicPlayerTest {

    private MockAudioBackend backend;
    private AudioMixer mixer;
    private MusicPlayer player;

    @BeforeEach
    void setUp() {
        backend = new MockAudioBackend();
        mixer = new AudioMixer(AudioConfig.builder().build());
        AudioEngine engine = new AudioEngine(backend, AudioConfig.builder().build());
        player = new MusicPlayer(engine, mixer);
    }

    @Test
    void testPlayMusic() {
        AudioClip track = AudioTestUtils.createTestClip("music", 120f);

        player.play(track);

        assertTrue(player.isPlaying());
        assertEquals(track, player.getCurrentClip());
    }

    @Test
    void testStopMusic() {
        AudioClip track = AudioTestUtils.createTestClip("music", 120f);
        player.play(track);

        player.stop();

        assertFalse(player.isPlaying());
        assertNull(player.getCurrentClip());
    }

    @Test
    void testCrossfade() {
        AudioClip track1 = AudioTestUtils.createTestClip("track1", 120f);
        AudioClip track2 = AudioTestUtils.createTestClip("track2", 120f);

        player.play(track1);
        player.crossfadeTo(track2, 1.0f);

        // Simulate crossfade completion
        for (int i = 0; i < 100; i++) {
            player.update(0.02f);
        }

        assertEquals(track2, player.getCurrentClip());
    }
}
```

## Component Tests

```java
class AudioSourceComponentTest {

    private MockAudioContext mockContext;

    @BeforeEach
    void setUp() {
        mockContext = AudioTestUtils.createMockContext();
        Audio.initialize(mockContext);
    }

    @AfterEach
    void tearDown() {
        Audio.destroy();
    }

    @Test
    void testPlayOnStart() {
        GameObject go = new GameObject("test");
        AudioSource source = go.addComponent(new AudioSource());
        source.setClip(AudioTestUtils.createTestClip("test", 1f));
        source.setPlayOnStart(true);

        source.onEnable();

        assertTrue(source.isPlaying());
    }

    @Test
    void testSpatialPositionUpdate() {
        GameObject go = new GameObject("test");
        go.getTransform().setPosition(0, 0, 0);

        AudioSource source = go.addComponent(new AudioSource());
        source.setClip(AudioTestUtils.createTestClip("test", 1f));
        source.setSpatialize(true);
        source.play();

        // Move object
        go.getTransform().setPosition(10, 5, 0);
        source.update(0.016f);

        // Verify position was updated
        MockAudioBackend backend = mockContext.getBackend();
        // Check that setSourcePosition was called with new position
    }

    @Test
    void testStopOnDestroy() {
        GameObject go = new GameObject("test");
        AudioSource source = go.addComponent(new AudioSource());
        source.setClip(AudioTestUtils.createTestClip("test", 1f));
        source.play();

        assertTrue(source.isPlaying());

        source.onDestroy();

        assertFalse(source.isPlaying());
    }
}
```

## Editor Tests

```java
class EditorAudioTest {

    private MockAudioBackend backend;

    @BeforeEach
    void setUp() {
        backend = new MockAudioBackend();
        EditorAudio.initialize(backend);
    }

    @AfterEach
    void tearDown() {
        EditorAudio.destroy();
    }

    @Test
    void testPlayPreview() {
        AudioClip clip = AudioTestUtils.createTestClip("test", 1f);

        AudioHandle handle = EditorAudio.playPreview(clip);

        assertTrue(handle.isPlaying());
        assertTrue(EditorAudio.isPreviewingClip(clip));
    }

    @Test
    void testStopPreview() {
        AudioClip clip = AudioTestUtils.createTestClip("test", 1f);
        EditorAudio.playPreview(clip);

        EditorAudio.stopPreview(clip);

        assertFalse(EditorAudio.isPreviewingClip(clip));
    }

    @Test
    void testStopAllOnPlayMode() {
        AudioClip clip1 = AudioTestUtils.createTestClip("test1", 1f);
        AudioClip clip2 = AudioTestUtils.createTestClip("test2", 1f);
        EditorAudio.playPreview(clip1);
        EditorAudio.playPreview(clip2);

        EditorAudio.onEnterPlayMode();

        assertFalse(EditorAudio.isPreviewingClip(clip1));
        assertFalse(EditorAudio.isPreviewingClip(clip2));
    }

    @Test
    void testPreviewVolume() {
        EditorAudio.setPreviewVolume(0.5f);
        AudioClip clip = AudioTestUtils.createTestClip("test", 1f);

        EditorAudio.playPreview(clip, 1.0f);

        // Final volume should be 0.5 (1.0 * 0.5)
        assertEquals(1, backend.getPlayCount());
        assertEquals(0.5f, backend.getPlayedSounds().get(0).volume(), 0.01f);
    }
}
```

## Performance Tests

```java
class AudioPerformanceTest {

    @Test
    void testManySimultaneousSounds() {
        MockAudioBackend backend = new MockAudioBackend();
        AudioEngine engine = new AudioEngine(backend,
            AudioConfig.builder().maxSimultaneousSounds(32).build());

        AudioClip clip = AudioTestUtils.createTestClip("test", 0.5f);

        long startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            engine.playOneShot(clip, 1.0f);
        }
        long elapsed = System.nanoTime() - startTime;

        // Should complete in under 10ms
        assertTrue(elapsed < 10_000_000);

        // Should respect max sounds limit
        assertTrue(backend.getActiveSourceCount() <= 32);
    }

    @Test
    void testPoolRecyclingPerformance() {
        MockAudioBackend backend = new MockAudioBackend();
        AudioEngine engine = new AudioEngine(backend,
            AudioConfig.builder().maxSimultaneousSounds(8).build());

        AudioClip shortClip = AudioTestUtils.createTestClip("short", 0.1f);

        // Play many short sounds
        for (int i = 0; i < 1000; i++) {
            engine.playOneShot(shortClip, 1.0f);
            engine.update(0.05f); // Simulate sounds finishing
        }

        // Pool should have recycled sources efficiently
        // Total sources created should be <= maxSimultaneousSounds
    }
}
```

## Test Categories

Run specific test categories:

```bash
# Unit tests only
mvn test -Dgroups="unit"

# Integration tests
mvn test -Dgroups="integration"

# Performance tests
mvn test -Dgroups="performance"

# All audio tests
mvn test -Dtest="*Audio*"
```

JUnit 5 tags:

```java
@Tag("unit")
class AudioMixerTest { ... }

@Tag("integration")
class AudioContextIntegrationTest { ... }

@Tag("performance")
class AudioPerformanceTest { ... }
```
