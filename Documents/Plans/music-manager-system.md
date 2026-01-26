# Music Manager System Plan

## Overview

Centralized music management system with editor configuration. Maps scenes to music tracks without hardcoded strings, using proper asset serialization.

## Files to Create

### 1. `src/main/java/com/pocket/rpg/audio/music/MusicConfig.java`
Configuration data for scene-to-music mappings. Serializes to `gameData/config/music.json`.

### 2. `src/main/java/com/pocket/rpg/audio/music/MusicManager.java`
Runtime manager implementing `SceneLifecycleListener`. Handles automatic music transitions on scene load.

### 3. `src/main/java/com/pocket/rpg/editor/panels/config/MusicConfigTab.java`
Editor tab in ConfigurationPanel for editing scene-music mappings with asset pickers.

### 4. `src/main/java/com/pocket/rpg/components/MusicZone.java`
Component for area-based music overrides (radius trigger).

### 5. `src/main/java/com/pocket/rpg/components/MusicTrigger.java`
Component for event-based music overrides (code triggered).

## Files to Modify

### 6. `src/main/java/com/pocket/rpg/editor/panels/ConfigurationPanel.java`
Add MusicConfigTab to tabs list.

### 7. `Documents/Encyclopedia/audio-system-guide.md`
Document MusicManager, MusicZone, MusicTrigger.

## Design Details

### MusicConfig Structure
```java
@Data
public class MusicConfig {
    private String defaultMusicPath;
    private List<SceneMusicEntry> sceneMappings = new ArrayList<>();

    @Data
    public static class SceneMusicEntry {
        private String scenePath;
        private List<String> trackPaths = new ArrayList<>();
    }
}
```

Uses paths for JSON serialization, resolved to assets at runtime.

### MusicManager
- Registers as SceneLifecycleListener on init
- On scene load: finds matching entry, crossfades to random track from list
- Uses `AudioConfig.getMusicCrossfadeDuration()` for crossfade timing
- Priority system: MusicTrigger > MusicZone > SceneMapping > Default
- Static access: `MusicManager.get()`

### MusicConfigTab Editor
- Default music field with asset picker
- List of scene entries:
  - Scene field with scene asset picker
  - List of track fields with audio asset pickers
  - Add/remove buttons
- Uses existing FieldEditors patterns

### MusicZone Component
- Fields: AudioClip zoneMusic, float radius, int priority
- On listener enter: saves current, crossfades to zone music
- On listener exit: restores previous music
- Gizmo: circle showing radius

### MusicTrigger Component
- Fields: AudioClip triggerMusic, boolean restoreOnEnd
- Public methods: trigger(), restore()
- Highest priority - overrides zones and scene music

## Implementation Order

1. MusicConfig (data class)
2. MusicManager (runtime)
3. MusicConfigTab (editor)
4. MusicZone (component)
5. MusicTrigger (component)
6. Update ConfigurationPanel
7. Update encyclopedia
