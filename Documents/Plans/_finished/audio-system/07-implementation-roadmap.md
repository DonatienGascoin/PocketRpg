# 7. Implementation Roadmap

## Phase Overview

| Phase | Focus | Key Deliverables |
|-------|-------|------------------|
| 1 | Foundation | Basic playback, WAV support, `Audio.playOneShot()` |
| 2 | Mixing | Volume channels, mute, settings persistence |
| 3 | Music | Background music, crossfading, OGG support |
| 4 | 3D Audio | Spatial audio, AudioSource/AudioListener components |
| 5 | Pooling | Performance, voice stealing, distance culling |
| 6 | Events | Scene transitions, pause menu integration |
| 7 | Editor | Inspector preview, Audio Browser panel |
| 8 | Polish | Advanced features, documentation |

---

## Phase 1: Foundation

**Goal:** Basic audio playback working

### Tasks

1. Create package structure (`com.pocket.rpg.audio/`)
2. Implement `AudioBackend` interface
3. Implement `OpenALAudioBackend`:
   - Initialize/destroy OpenAL context
   - Create/delete buffers
   - Create/delete sources
   - Basic playback (play, stop, pause)
4. Implement `AudioClip` class
5. Implement `AudioClipLoader` for WAV files
6. Register loader with `AssetManager`
7. Create `Audio` static facade (minimal)
8. Create `AudioContext` interface + `DefaultAudioContext`
9. Integrate into `PlatformFactory`
10. Basic `AudioConfig`

### Deliverables

- Can load WAV files via `Assets.load("audio/sfx/explosion.wav", AudioClip.class)`
- Can play one-shot sounds with `Audio.playOneShot(clip)`
- Volume control works

### Acceptance Tests

```java
@Test
void testLoadWavFile() {
    AudioClip clip = Assets.load("test.wav", AudioClip.class);
    assertNotNull(clip);
    assertTrue(clip.getDuration() > 0);
}

@Test
void testPlayOneShot() {
    Audio.initialize(mockContext);
    AudioClip clip = createTestClip();
    AudioHandle handle = Audio.playOneShot(clip);
    assertTrue(handle.isValid());
}
```

---

## Phase 2: Audio Mixing

**Goal:** Volume channels and mixing

### Tasks

1. Implement `AudioChannel` enum
2. Implement `AudioBus` (per-channel settings)
3. Implement `AudioMixer`:
   - Volume per channel
   - Mute/unmute
   - Volume calculation chain
4. Extend `AudioConfig` with channel volumes
5. Add channel parameter to `playOneShot`
6. Persist audio settings to `gameData/config/audio.json`
7. Load settings on startup

### Deliverables

- Master/Music/SFX/Voice/Ambient/UI volume controls
- Muting channels
- Settings persist across sessions

### Acceptance Tests

```java
@Test
void testChannelVolume() {
    AudioMixer mixer = new AudioMixer(config);
    mixer.setVolume(AudioChannel.SFX, 0.5f);
    mixer.setVolume(AudioChannel.MASTER, 0.8f);

    float finalVolume = mixer.calculateFinalVolume(AudioChannel.SFX, 1.0f);
    assertEquals(0.4f, finalVolume, 0.001f); // 0.5 * 0.8
}

@Test
void testMuteChannel() {
    AudioMixer mixer = new AudioMixer(config);
    mixer.mute(AudioChannel.MUSIC);

    float finalVolume = mixer.calculateFinalVolume(AudioChannel.MUSIC, 1.0f);
    assertEquals(0f, finalVolume);
}
```

---

## Phase 3: Music System

**Goal:** Background music with crossfading

### Tasks

1. Implement `StreamingAudioClip` for large files
2. Add OGG Vorbis support via stb_vorbis (LWJGL)
3. Implement `MusicTrack` wrapper
4. Implement `MusicPlayer`:
   - Play/stop/pause music
   - Fade in/out
5. Implement `CrossfadeController`
6. Add `Audio.music()` accessor

### Deliverables

- Play background music
- Smooth crossfades between tracks
- OGG file support for compressed audio

### Acceptance Tests

```java
@Test
void testMusicFadeIn() {
    MusicPlayer player = new MusicPlayer(engine, mixer);
    player.play(track1, 2.0f); // 2 second fade

    // Initial volume should be 0
    assertEquals(0f, player.getCurrentVolume(), 0.01f);

    // After fade completes
    simulateTime(2.5f);
    assertTrue(player.getCurrentVolume() > 0.9f);
}

@Test
void testMusicCrossfade() {
    MusicPlayer player = new MusicPlayer(engine, mixer);
    player.play(track1);
    assertTrue(player.isPlaying());

    player.crossfadeTo(track2, 1.0f);

    // After crossfade
    simulateTime(1.5f);
    assertEquals(track2, player.getCurrentClip());
}
```

---

## Phase 4: 3D Audio & Components

**Goal:** Spatial audio with components

### Tasks

1. Implement `AudioListener` component
2. Implement `AudioSource` component:
   - 3D positioning
   - Distance attenuation
   - Min/max distance
   - Rolloff factor
3. Listener position/orientation updates in OpenAL
4. 3D version of `playOneShot` with position
5. Update loop for source position tracking
6. Register components with `ComponentRegistry`

### Deliverables

- Sounds positioned in 3D space
- Distance-based volume falloff
- AudioListener on camera/player
- AudioSource on GameObjects

### Acceptance Tests

```java
@Test
void testDistanceAttenuation() {
    AudioEngine engine = createEngine();
    engine.setListenerPosition(new Vector3f(0, 0, 0));

    // Sound at distance 10, minDist=1, maxDist=100
    float attenuation = engine.calculateAttenuation(
        new Vector3f(10, 0, 0), 1.0f, 100.0f, 1.0f
    );

    assertTrue(attenuation < 1.0f);
    assertTrue(attenuation > 0.0f);
}

@Test
void testAudioSourceFollowsTransform() {
    GameObject go = new GameObject("Enemy");
    AudioSource source = go.addComponent(new AudioSource());
    source.setClip(testClip);
    source.setSpatialize(true);
    source.play();

    go.getTransform().setPosition(100, 50, 0);
    source.update(0.016f);

    verify(mockEngine).setSourcePosition(any(), eq(new Vector3f(100, 50, 0)));
}
```

---

## Phase 5: Audio Pooling & Performance

**Goal:** Efficient resource management

### Tasks

1. Implement `AudioSourcePool`:
   - Pre-allocated source pool
   - Automatic recycling
   - Priority-based voice stealing
2. Limit simultaneous sounds (`maxSimultaneousSounds`)
3. Implement priority system
4. Distance culling (don't play inaudible sounds)
5. Performance profiling

### Deliverables

- Configurable max simultaneous sounds
- Voice stealing when pool exhausted
- No audio popping/artifacts
- Efficient memory usage

### Acceptance Tests

```java
@Test
void testPoolRecycling() {
    AudioSourcePool pool = new AudioSourcePool(4);

    AudioHandle h1 = pool.acquire();
    AudioHandle h2 = pool.acquire();
    pool.release(h1);
    AudioHandle h3 = pool.acquire();

    // h3 should reuse h1's source
    assertEquals(2, pool.getActiveCount());
}

@Test
void testMaxSimultaneousSounds() {
    AudioEngine engine = createEngine(maxSounds: 8);

    for (int i = 0; i < 10; i++) {
        engine.playOneShot(testClip);
    }

    assertTrue(engine.getActiveSourceCount() <= 8);
}

@Test
void testVoiceStealing() {
    AudioEngine engine = createEngine(maxSounds: 2);

    AudioHandle h1 = engine.playOneShot(lowPriorityClip);
    AudioHandle h2 = engine.playOneShot(lowPriorityClip);
    AudioHandle h3 = engine.playOneShot(highPriorityClip);

    // h3 should have stolen h1's voice
    assertFalse(h1.isPlaying());
    assertTrue(h3.isPlaying());
}
```

---

## Phase 6: Event Integration

**Goal:** Connect audio to game events

### Tasks

1. Create `AudioEvent` hierarchy
2. Implement `AudioEventHandler`
3. Integrate with `GameEventBus`:
   - Scene transition events
   - Pause/resume events
4. Implement transition audio behaviors:
   - Duck during transitions
   - Stop channels on scene change
5. Pause menu integration

### Deliverables

- Audio responds to scene transitions
- Proper pause/resume behavior
- Event-driven audio control

### Acceptance Tests

```java
@Test
void testAudioDucksDuringTransition() {
    GameEventBus bus = new GameEventBus();
    AudioTransitionHandler handler = new AudioTransitionHandler(audioEngine);
    bus.register(handler);

    bus.dispatch(new TransitionStartedEvent());

    verify(audioEngine).setGlobalVolumeDuck(eq(0.5f), anyFloat());
}

@Test
void testSfxStopsOnSceneUnload() {
    GameEventBus bus = new GameEventBus();
    AudioTransitionHandler handler = new AudioTransitionHandler(audioEngine);
    bus.register(handler);

    bus.dispatch(new SceneUnloadEvent("TestScene"));

    verify(audioEngine).stopChannel(AudioChannel.SFX);
    verify(audioEngine).stopChannel(AudioChannel.AMBIENT);
    verify(audioEngine, never()).stopChannel(AudioChannel.MUSIC);
}
```

---

## Phase 7: Editor Integration

**Goal:** Test audio from inspector

### Tasks

1. Implement `EditorAudio` static facade
2. Implement `EditorAudioContext`
3. Create `AudioClipFieldEditor` with play button
4. Create `AudioSourceInspector` extension
5. Implement `AudioClipPreviewRenderer`
6. Create `AudioBrowserPanel`
7. Integrate with `PlayModeController`
8. Add editor audio preferences

### Deliverables

- Play/stop button on AudioClip fields in inspector
- Preview section for AudioSource components
- Audio Browser panel for testing all clips
- Preview stops when entering Play Mode

### Acceptance Tests

```java
@Test
void testEditorAudioPreview() {
    EditorAudio.initialize(mockBackend);
    AudioClip clip = createTestClip();

    AudioHandle handle = EditorAudio.playPreview(clip);

    assertTrue(handle.isPlaying());
    assertTrue(EditorAudio.isPreviewingClip(clip));
}

@Test
void testPreviewStopsOnPlayMode() {
    EditorAudio.initialize(mockBackend);
    AudioHandle handle = EditorAudio.playPreview(testClip);

    EditorAudio.onEnterPlayMode();

    assertFalse(handle.isPlaying());
}
```

---

## Phase 8: Polish & Advanced Features

**Goal:** Production-ready audio system

### Tasks

1. Implement `PlaybackSettings` builder pattern
2. Add `SoundBank` for variation/randomization
3. MP3 support (optional)
4. Audio ducking system
5. `AmbientZone` component
6. Comprehensive test coverage
7. Performance optimization pass
8. Documentation

### Deliverables

- Full-featured audio system
- Sound variation/randomization
- Ambient zones
- Complete test coverage
- API documentation

---

## Dependency Graph

```
Phase 1 (Foundation)
    │
    ├──> Phase 2 (Mixing)
    │        │
    │        └──> Phase 3 (Music)
    │
    └──> Phase 4 (3D Audio)
             │
             └──> Phase 5 (Pooling)
                      │
                      └──> Phase 6 (Events)

Phase 1 + 2 ──> Phase 7 (Editor) ──> Phase 8 (Polish)
```

**Critical Path:** 1 → 2 → 7 (for editor audio testing)

**Parallel Work:**
- Phase 3 (Music) can run parallel to Phase 4-5
- Phase 7 (Editor) can start after Phase 2, doesn't need 3D/pooling

---

## Risk Areas

| Risk | Mitigation |
|------|------------|
| OpenAL threading issues | Keep all OpenAL calls on main thread initially |
| Audio popping on stop | Implement small fade-out (10-50ms) on stop |
| Memory with large files | Use streaming for files > 1MB |
| Editor/game audio conflict | Separate contexts with clear lifecycle |
