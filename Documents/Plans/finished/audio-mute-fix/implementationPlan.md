# Fix: Audio Mute Toggle Has No Effect During Play Mode

## Context

The audio mute button in the Game View toolbar works before entering play mode but has no effect once play mode is active. The root cause is that **music track handle volumes are never updated when the mixer state changes** (e.g., mute/unmute). SFX sources are properly updated because they remain tracked by `AudioEngine`, but music handles are intentionally removed from engine tracking and managed by `MusicPlayer` — which never reapplies the mixer's current volume/mute state.

## Current Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Audio (Static Facade)                        │
│  Audio.muteChannel(MASTER) → context.getMixer().mute(MASTER)        │
│  Audio.update(dt)          → context.update(dt)                     │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
           ┌───────────────────┴────────────────────┐
           │                                        │
  ┌────────▼──────────┐               ┌─────────────▼──────────────┐
  │ DefaultAudioContext│               │ EditorSharedAudioContext    │
  │ (editor creates)  │◄──────────────│ (play mode wraps editor's) │
  │                    │   delegates   │ initialize/destroy = no-op │
  │ - AudioMixer ──────┼──────────────►│ getMixer() → delegate      │
  │ - AudioEngine      │               │ getEngine() → delegate     │
  │ - MusicPlayer      │               └────────────────────────────┘
  └────────┬───────────┘
           │ owns
     ┌─────┴──────────────────────────────────────────┐
     │                                                │
┌────▼──────────┐                           ┌─────────▼────────┐
│  AudioEngine  │                           │   MusicPlayer    │
│               │                           │                  │
│ activeSources │ ← SFX tracked here        │ currentTrack ────┼──► MusicTrack
│               │   volume synced each      │ nextTrack ───────┼──► MusicTrack
│ update():     │   frame via               │ volume (player)  │
│  for each src:│   calculateFinalVolume()  │                  │
│   setVolume(  │                           │ update():        │
│    mixer.calc │   ✅ WORKS                │  crossfade.upd() │
│    FinalVol())│                           │  track.update()  │
│               │                           │  ❌ NO MIXER     │
│               │                           │     REAPPLY      │
└───────────────┘                           └──────────────────┘
                                                     │
                                               ┌─────▼──────────┐
                                               │  MusicTrack    │
                                               │                │
                                               │ volume ────────┼─ PROBLEM: baked
                                               │  (= mixer *    │  mixer value,
                                               │   playerVol)   │  never refreshed
                                               │                │
                                               │ handle ────────┼─ OpenAL source
                                               │  (removed from │  (volume set once
                                               │   engine       │   at play time)
                                               │   tracking)    │
                                               └────────────────┘
```

### The Bug in Detail

1. **Before play mode**: User clicks mute → `Audio.muteChannel(MASTER)` → mutes the MASTER bus in the shared mixer → works because no audio is actively playing

2. **Play mode starts**: `PlayModeController` wraps the editor's audio context in `EditorSharedAudioContext`. Both editor and play mode share the **same** `AudioMixer`, `AudioEngine`, and `MusicPlayer` instances.

3. **During play mode, user clicks mute**:
   - `Audio.muteChannel(MASTER)` correctly mutes the MASTER bus on the shared mixer
   - **SFX**: `AudioEngine.update()` (line 141-150) recalculates volume for all tracked sources using `mixer.calculateFinalVolume()` → returns 0 when muted → **SFX gets muted correctly** ✅
   - **Music**: `MusicTrack` handles are removed from `AudioEngine` tracking (MusicTrack.java:39). The `MusicPlayer.update()` only progresses fade animations — it never reapplies the mixer volume to the handles. The track's `volume` field has the mixer calculation "baked in" from when `play()` was called, and is never refreshed → **Music keeps playing** ❌

### Key Code Paths

| Step | File | Line | What happens |
|------|------|------|-------------|
| Music starts | `MusicPlayer.play()` | 60-68 | Sets `track.volume = calculateVolume()` (bakes mixer into volume) |
| Track plays | `MusicTrack.play()` | 33-40 | Creates OpenAL source, **removes from engine tracking** |
| Mixer state changes | `AudioMixer.mute()` | 72-76 | Sets bus.muted = true |
| Engine update | `AudioEngine.update()` | 141-150 | Syncs volume for tracked sources (music NOT tracked) |
| Music update | `MusicPlayer.update()` | 223-235 | Only updates fades, **never reapplies mixer** |

## Solution

Separate the `MusicTrack.volume` field into a **fade volume** (0-1, for fade/crossfade animations) and apply the mixer-computed gain externally each frame. This ensures that mute/unmute and volume changes on the mixer take effect immediately for music.

### Fixed Architecture

```
┌───────────────┐                           ┌──────────────────┐
│  AudioEngine  │                           │   MusicPlayer    │
│               │                           │                  │
│ activeSources │ ← SFX tracked here        │ currentTrack ────┼──► MusicTrack
│               │   volume synced each      │ nextTrack ───────┼──► MusicTrack
│ update():     │   frame                   │ volume (player)  │
│  for each src:│   ✅ WORKS                │                  │
│   mixer.calc  │                           │ update():        │
│   FinalVol()  │                           │  crossfade.upd() │
│               │                           │  track.update()  │
│               │                           │  ✅ APPLY MIXER  │
│               │                           │  track.apply     │
│               │                           │  EffectiveVol(   │
│               │                           │   calculateVol())│
└───────────────┘                           └──────────────────┘
                                                     │
                                               ┌─────▼──────────┐
                                               │  MusicTrack    │
                                               │                │
                                               │ volume ────────┼─ FIXED: pure fade
                                               │  (0-1 fade     │  volume only
                                               │   only)        │  (0-1 range)
                                               │                │
                                               │ applyEffective │
                                               │ Volume(gain):  │
                                               │  handle.setVol │
                                               │  (volume*gain) │  ← mixer applied
                                               │                │     each frame
                                               │ handle ────────┼─ OpenAL source
                                               └────────────────┘

Volume computation:
  effectiveVolume = trackFadeVolume × mixer.calculateFinalVolume(MUSIC, playerVolume)
                  = trackFadeVolume × masterBusVol × musicBusVol × playerVolume

  When MASTER muted: effectiveVolume = trackFadeVolume × 0 = 0  ✅
```

## Changes

### 1. MusicTrack.java
**Path**: `src/main/java/com/pocket/rpg/audio/music/MusicTrack.java`

- **`setVolume(float vol)`**: Remove `handle.setVolume(vol)` call. The track volume becomes a pure "fade volume" — the actual handle volume is applied externally by `applyEffectiveVolume()`.

- **`update(float deltaTime)`**: Remove `handle.setVolume(volume)` call during fade animation. Same reason — handle volume is applied by `MusicPlayer`.

- **Add `applyEffectiveVolume(float mixerGain)`**: New method that sets the handle volume to `volume * mixerGain`. Called each frame by `MusicPlayer` after fade/crossfade updates.

### 2. MusicPlayer.java
**Path**: `src/main/java/com/pocket/rpg/audio/music/MusicPlayer.java`

- **`play(AudioClip, float fadeIn)`**: Change track volume from `calculateVolume()` to `1.0f` (or `0.0f` for fade-in). The mixer gain is no longer baked into the track volume. Apply effective volume immediately after play.

- **`setVolume(float volume)`**: Apply effective volume to current track immediately (was already doing this, just uses the new method).

- **`update(float deltaTime)`**: After updating all tracks and crossfade, apply the mixer-computed gain to all active tracks. This is the key fix — ensures mute/volume changes are reflected every frame.

### 3. No changes needed
- **CrossfadeController.java** — already works with 0-1 fade volumes via `setVolume()`, no mixer awareness needed
- **AudioEngine.java** — SFX volume sync already works correctly
- **Audio.java** / **AudioMixer.java** / **EditorSharedAudioContext.java** — no changes needed
- **GameViewPanel.java** — the mute button logic is correct

## Verification

1. **Run the editor**: `mvn exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication"`
2. **Test mute before play mode**: Click the audio mute button in the Game View toolbar. Button should turn red.
3. **Enter play mode**: Click Play. Music/SFX should be muted from the start.
4. **Unmute during play mode**: Click the mute button again. Audio should resume.
5. **Mute during play mode**: Click mute while playing. All audio (music + SFX) should go silent immediately.
6. **Test crossfade with mute**: Use the transition debug button to switch scenes during play mode while muted. Audio should stay silent through the transition.
7. **Stop play mode**: Verify the mute state is preserved after stopping.
