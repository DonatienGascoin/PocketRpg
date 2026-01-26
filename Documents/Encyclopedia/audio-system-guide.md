# Audio System Guide

> **Summary:** Complete guide to playing sounds, music, and ambient audio in your game using components and the Audio API.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Audio Components](#audio-components)
4. [Music System](#music-system)
5. [Audio Channels](#audio-channels)
6. [Workflows](#workflows)
7. [Editor Tools](#editor-tools)
8. [Tips & Best Practices](#tips--best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Code Integration](#code-integration)
11. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Play a sound effect | Add `AudioSource` component, assign clip, check "Play On Start" or call `play()` |
| Configure scene music | Configuration Panel > Music tab, add scene mapping |
| Area-based music change | Add `MusicZone` component, set radius and music clip |
| Event-based music (combat) | Add `MusicTrigger` component, call `trigger()` / `restore()` from code |
| Enable 3D spatial audio | Add `AudioListener` to camera, enable "Spatialize" on AudioSource |
| Ambient zone audio | Add `AmbientZone` component, set radius and clip |
| Adjust channel volume | Configuration Panel > Audio tab, or `Audio.setChannelVolume(channel, volume)` |
| Mute audio in editor | Click volume icon in Game panel toolbar |

---

## Overview

The audio system provides:

- **Component-based audio** - Attach sounds to GameObjects with AudioSource
- **3D spatial audio** - Sounds attenuate based on distance from AudioListener
- **Channel mixing** - Six channels (Master, Music, SFX, Voice, Ambient, UI) with independent volume control
- **Music system** - Dedicated player with crossfade support for seamless transitions
- **Ambient zones** - Trigger-based audio that fades in/out when entering areas

Audio uses OpenAL backend for hardware-accelerated 3D audio. All audio files should be `.ogg` or `.wav` format placed in `gameData/audio/`.

---

## Audio Components

### AudioSource

Plays sounds attached to a GameObject. Use for sound effects, character voices, environmental sounds.

| Property | Description |
|----------|-------------|
| Clip | The audio file to play |
| Volume | Playback volume (0-1) |
| Pitch | Speed/pitch multiplier (1.0 = normal) |
| Loop | Repeat when finished |
| Play On Start | Auto-play when GameObject enables |
| Spatialize | Enable 3D positioning (requires AudioListener) |
| Channel | Mixing channel (SFX, Voice, etc.) |
| Min Distance | Distance for full volume |
| Max Distance | Distance where sound is inaudible |
| Rolloff Factor | Attenuation curve steepness |
| Priority | Voice stealing priority (0=highest, 255=lowest) |

**Inspector location:** Add Component > Audio > AudioSource

### AudioListener

Defines the "ears" for 3D audio. Attach to the camera or player character.

- Only **one AudioListener** can be active at a time
- Position updates automatically from Transform
- Rotation affects stereo panning

**Inspector location:** Add Component > Audio > AudioListener

### AmbientZone

Trigger-based ambient audio that plays when the listener enters a radius.

| Property | Description |
|----------|-------------|
| Ambient Clip | Looping audio to play |
| Volume | Target volume when inside |
| Fade In Time | Seconds to fade in when entering |
| Fade Out Time | Seconds to fade out when leaving |
| Radius | Zone radius in world units |

**Gizmo:** Blue circle shows the trigger radius in Scene view.

**Inspector location:** Add Component > Audio > AmbientZone

### MusicZone

Area-based music override. When the AudioListener enters this zone, the background music crossfades to the zone's music. When exiting, it returns to the previous music.

| Property | Description |
|----------|-------------|
| Zone Music | Music clip to play inside this zone |
| Radius | Zone radius in world units |
| Priority | Higher priority zones override lower ones |

**Gizmo:** Cyan circle shows the zone radius (different from AmbientZone purple).

**Inspector location:** Add Component > Audio > MusicZone

### MusicTrigger

Event-based music override for combat, cutscenes, etc. Highest priority - overrides both MusicZone and scene music.

| Property | Description |
|----------|-------------|
| Trigger Music | Music to play when triggered |
| Restore On End | Whether to restore previous music on `restore()` |

**Usage:** Reference from other components and call `trigger()` / `restore()`.

**Inspector location:** Add Component > Audio > MusicTrigger

---

## Music System

The MusicManager provides automatic scene-based music with zone and event overrides.

### Priority System

Music sources are prioritized (highest to lowest):

1. **MusicTrigger** - Event-based (combat, cutscenes)
2. **MusicZone** - Area-based (boss rooms, danger zones)
3. **Scene Mapping** - From MusicConfig (Configuration Panel > Music)
4. **Default Music** - Fallback from MusicConfig

### Configuration (No Code Required)

1. Open **Configuration Panel > Music tab**
2. Set **Default Music** (optional fallback)
3. Click **Add Scene Mapping**
4. Select a scene using the picker
5. Add one or more music tracks (multiple = random selection)
6. Save

Music automatically changes when scenes load.

### MusicZone vs AmbientZone

| | MusicZone | AmbientZone |
|---|-----------|-------------|
| **Channel** | MUSIC | AMBIENT |
| **Behavior** | Replaces current music | Layers on top of music |
| **Stacking** | Only one active | Multiple can play |
| **Use for** | Boss themes, area music | Environmental sounds |

Both can coexist - AmbientZone adds atmosphere while MusicZone changes the track.

---

## Audio Channels

Six mixing channels allow independent volume control:

| Channel | Use For | Default Volume |
|---------|---------|----------------|
| MASTER | Affects all audio | 100% |
| MUSIC | Background music | 80% |
| SFX | Sound effects | 100% |
| VOICE | Character dialogue | 100% |
| AMBIENT | Environmental audio | 70% |
| UI | Menu/button sounds | 100% |

Configure in: **Configuration Panel > Audio tab**

Volumes are saved to `gameData/config/audio.json`.

---

## Workflows

### Playing Sound Effects

**Method 1: Component (no code)**
1. Select the GameObject
2. Add Component > Audio > AudioSource
3. Drag audio clip to the Clip field
4. Check "Play On Start" for automatic playback
5. Or trigger via script: `audioSource.play()`

**Method 2: One-shot (code)**
```java
// Fire-and-forget sound
Audio.playOneShot(explosionClip);

// With volume
Audio.playOneShot(footstepClip, 0.5f);

// At 3D position
Audio.playOneShot(gunfireClip, enemyPosition);
```

### Playing Background Music

```java
// Simple play
Audio.music().play(overworldMusic);

// With fade in (2 seconds)
Audio.music().play(overworldMusic, 2.0f);

// Stop with fade out
Audio.music().stop(1.5f);
```

### Crossfading Music Between Scenes

Call during your scene transition:

```java
// Crossfade to new track over 2 seconds
Audio.music().crossfadeTo(dungeonMusic, 2.0f);

// Uses default crossfade duration from AudioConfig
Audio.music().crossfadeTo(dungeonMusic);
```

The crossfade duration can be configured in **Configuration Panel > Audio > Music > Crossfade Duration**.

### Setting Up 3D Audio

1. Add `AudioListener` to your camera or player
2. On your AudioSource, enable **Spatialize**
3. Configure **Min Distance** (full volume radius)
4. Configure **Max Distance** (inaudible radius)
5. Adjust **Rolloff Factor** for attenuation curve

### Creating Ambient Zones

1. Create empty GameObject at zone center
2. Add Component > Audio > AmbientZone
3. Assign looping ambient clip
4. Set radius to match your area
5. Configure fade times for smooth transitions

Multiple zones can overlap - audio blends naturally.

### Adjusting Volume at Runtime

```java
// Set channel volume (0.0 - 1.0)
Audio.setChannelVolume(AudioChannel.MUSIC, 0.5f);
Audio.setChannelVolume(AudioChannel.SFX, 0.8f);

// Get current volume
float musicVol = Audio.getChannelVolume(AudioChannel.MUSIC);

// Mute/unmute (runtime only, not saved)
Audio.muteChannel(AudioChannel.MUSIC);
Audio.unmuteChannel(AudioChannel.MUSIC);
```

---

## Editor Tools

### Audio Browser Panel

**Window > Audio Browser**

- Browse all audio files in `gameData/audio/`
- Preview clips with play/stop buttons
- Drag clips to Inspector fields
- See duration and format info

### Audio Configuration Tab

**Window > Configuration > Audio tab**

| Section | Settings |
|---------|----------|
| Channel Volumes | Sliders for all 6 channels with mute buttons |
| Music | Crossfade duration |
| Engine | Max simultaneous sounds, rolloff factor, reverb toggle |

### Music Configuration Tab

**Window > Configuration > Music tab**

Configure scene-to-music mappings without code:

| Element | Description |
|---------|-------------|
| Default Music | Fallback when no scene mapping exists |
| Scene Mappings | List of scene-to-track assignments |
| Scene Picker | Select scene using asset picker (no string typing) |
| Track List | Multiple tracks = random selection on scene load |

Saved to `gameData/config/music.json`.

### Game Panel Audio Mute

The Game panel toolbar has a volume icon button:
- Click to mute/unmute all game audio
- Red when muted
- Editor-only (doesn't affect saved config)
- Visible in both preview and play modes

---

## Tips & Best Practices

- **Use appropriate channels** - Route sounds to correct channels so players can adjust categories independently
- **Set priorities** - Give important sounds (dialogue, UI) lower priority numbers to prevent voice stealing
- **Preload clips** - Load audio during scene init to avoid hitches: `AssetLoader.loadAudio("path")`
- **Use one-shots for SFX** - Don't create AudioSource components for every bullet/footstep
- **Crossfade music early** - Start crossfade before scene transition completes for seamless audio
- **Test with muted channels** - Verify game is playable with music/SFX muted
- **Keep ambient zones simple** - One looping clip per zone; use multiple zones for layered ambience

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| No sound plays | Check AudioListener exists and is enabled |
| Sound too quiet | Verify channel volumes in Audio config, check AudioSource volume |
| 3D audio not working | Ensure Spatialize is enabled and AudioListener is in scene |
| Music doesn't change on scene load | Verify MusicManager.initialize() called, check Music tab mappings |
| MusicZone not working | Check radius gizmo, verify AudioListener position updates |
| MusicTrigger stuck | Call `restore()` after event ends, check `restoreOnEnd` is true |
| Ambient zone not triggering | Check radius gizmo covers intended area, verify AudioListener moves |
| Audio clips not loading | Verify files are in `gameData/audio/` with .ogg or .wav extension |
| Crackling/popping | Reduce max simultaneous sounds in Audio config |

---

## Code Integration

### AudioSource Component Access

```java
public class Enemy extends Component {
    private AudioSource audioSource;

    @Override
    protected void onEnable() {
        audioSource = getComponent(AudioSource.class);
    }

    public void onDeath() {
        // Play death sound
        audioSource.playOneShot(deathClip);
    }

    public void onHit() {
        // Play with volume variation
        audioSource.playOneShot(hitClip, 0.8f + random.nextFloat() * 0.4f);
    }
}
```

### MusicTrigger Usage (Combat Music)

```java
public class CombatManager extends Component {

    // Assign in Inspector - no hardcoded clips!
    @Getter @Setter
    private MusicTrigger combatMusic;

    public void onCombatStart() {
        if (combatMusic != null) {
            combatMusic.trigger();
        }
    }

    public void onCombatEnd() {
        if (combatMusic != null) {
            combatMusic.restore();  // Returns to zone/scene music
        }
    }
}
```

### Initialize MusicManager (Game Startup)

```java
// In your game initialization
public void initializeAudio(SceneManager sceneManager, AssetContext assets) {
    // MusicManager auto-handles scene music from config
    MusicManager.initialize(sceneManager, assets);
}
```

Scene music is now configured via Configuration Panel > Music tab - no code needed for basic scene-to-music mapping.

### Volume Settings UI

```java
public class OptionsMenu {
    public void setMusicVolume(float value) {
        Audio.setChannelVolume(AudioChannel.MUSIC, value);
        // Volume auto-syncs to AudioConfig
    }

    public void setSfxVolume(float value) {
        Audio.setChannelVolume(AudioChannel.SFX, value);
    }

    public void saveSettings() {
        // Persist to disk
        Audio.getMixer().saveConfig();
    }
}
```

### PlaybackSettings for Fine Control

```java
// Full control over playback
AudioHandle handle = Audio.playOneShot(clip, new PlaybackSettings()
    .volume(0.8f)
    .pitch(1.2f)
    .channel(AudioChannel.SFX)
    .loop(false)
    .priority(50)
    .position(worldPosition)
    .minDistance(5f)
    .maxDistance(100f)
    .rolloff(1.5f));

// Control the playing sound
handle.pause();
handle.resume();
handle.setVolume(0.5f);
handle.stop();
```

---

## Related

- [Components Guide](components-guide.md) - General component documentation
- [Asset Loader Guide](asset-loader-guide.md) - Loading audio clips
- [Save System Guide](save-system-guide.md) - Persisting audio settings
