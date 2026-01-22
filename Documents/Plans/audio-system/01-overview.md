# 1. Audio System Overview

## Goals

The audio system provides a Unity-style API for audio playback while maintaining engine architectural principles:

- **Clean Architecture**: Platform abstraction via `AudioBackend` interface, mirroring `InputBackend`
- **Dependency Injection**: Constructor-injected dependencies for testability
- **Component-Based**: `AudioSource` and `AudioListener` components attached to GameObjects
- **Service Locator Facade**: Static `Audio` class for convenient global access
- **Event-Driven**: Integration with `GameEventBus` for transitions and scene events
- **Resource Management**: Integration with existing `AssetManager` for audio clip loading
- **Editor Support**: Preview and test audio directly from the inspector

## Technology Choice

**OpenAL via LWJGL** is the backend:
- Already using LWJGL for OpenGL/GLFW
- Cross-platform audio support
- 3D spatial audio built-in
- Streaming support for large files

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Game Code                               │
│  AudioSource component, Audio.playOneShot(), Audio.music()      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Audio (Static Facade)                        │
│  Delegates to AudioContext (like Input delegates to InputContext)│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AudioContext                               │
│  Interface defining all audio operations                        │
│  - DefaultAudioContext (game runtime)                           │
│  - EditorAudioContext (editor with preview support)             │
│  - MockAudioContext (for testing)                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AudioEngine                                │
│  Core audio management: mixing, pooling, 3D positioning         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AudioBackend                               │
│  Platform abstraction interface                                 │
│  - OpenALAudioBackend (LWJGL/OpenAL implementation)             │
│  - NullAudioBackend (silent/testing)                            │
└─────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
com.pocket.rpg.audio/
├── Audio.java                    # Static facade (like Input.java)
├── AudioContext.java             # Interface for DI
├── DefaultAudioContext.java      # Runtime implementation
├── AudioEngine.java              # Core audio management
├── AudioConfig.java              # Configuration (volumes, etc.)
│
├── clips/
│   ├── AudioClip.java            # Audio data container
│   ├── AudioClipLoader.java      # AssetManager loader
│   └── StreamingAudioClip.java   # For large files (music)
│
├── sources/
│   ├── AudioHandle.java          # Handle to playing sound
│   ├── AudioSourcePool.java      # Object pooling for sources
│   └── PlaybackState.java        # Enum: PLAYING, PAUSED, STOPPED
│
├── mixing/
│   ├── AudioMixer.java           # Volume/mixing management
│   ├── AudioChannel.java         # Enum: MASTER, MUSIC, SFX, etc.
│   └── AudioBus.java             # Per-channel settings
│
├── components/
│   ├── AudioSource.java          # Component for 3D audio
│   ├── AudioListener.java        # Component (on camera/player)
│   └── AmbientZone.java          # Trigger-based ambient audio
│
├── music/
│   ├── MusicPlayer.java          # Background music management
│   ├── MusicTrack.java           # Music metadata
│   └── CrossfadeController.java  # Smooth transitions
│
├── backend/
│   ├── AudioBackend.java         # Platform abstraction interface
│   ├── OpenALAudioBackend.java   # LWJGL implementation
│   └── NullAudioBackend.java     # Silent implementation
│
├── events/
│   ├── AudioEvent.java           # Base audio event
│   ├── SoundPlayedEvent.java     # Dispatched when sound plays
│   └── MusicChangedEvent.java    # Dispatched on music change
│
└── editor/                       # Editor-specific (see doc 6)
    ├── EditorAudio.java
    ├── EditorAudioContext.java
    └── ...
```

## Platform Factory Integration

```java
// PlatformFactory.java - add method
public interface PlatformFactory {
    // ... existing methods ...

    AudioBackend createAudioBackend();
}

// GLFWPlatformFactory.java
@Override
public AudioBackend createAudioBackend() {
    return new OpenALAudioBackend();
}

// HeadlessPlatformFactory.java (for tests)
@Override
public AudioBackend createAudioBackend() {
    return new NullAudioBackend();
}
```

## Dependencies

Already included with LWJGL:

```xml
<dependency>
    <groupId>org.lwjgl</groupId>
    <artifactId>lwjgl-openal</artifactId>
</dependency>

<dependency>
    <groupId>org.lwjgl</groupId>
    <artifactId>lwjgl-stb</artifactId>
</dependency>
```

Native libraries (bundled with LWJGL natives):
- Windows: OpenAL32.dll
- Linux: libopenal.so
- macOS: libopenal.dylib
