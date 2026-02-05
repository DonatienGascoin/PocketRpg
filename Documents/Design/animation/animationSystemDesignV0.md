# Animation System Design

## Overview

A sprite-based frame animation system for PocketRPG that supports:
- JSON-based animation definitions stored in `gameData/assets/animations/`
- Component-based playback via `AnimationComponent`
- Visual editor panel for creating and editing animations
- Integration with existing SpriteSheet system

## Goals

1. **Simple workflow**: Designers create animations from existing SpriteSheets without code
2. **Reusable**: Animations are assets that can be shared across multiple GameObjects
3. **Editor-friendly**: Visual timeline editor with playback preview
4. **Performant**: Uses existing sprite batching, minimal overhead

## Architecture

### Core Classes

#### `Animation`
Data class representing an animation asset.

```java
public class Animation {
    private String name;
    private List<AnimationFrame> frames;
    private boolean looping;
    private float speed; // Multiplier, default 1.0
}
```

#### `AnimationFrame`
Single frame in an animation sequence.

```java
public class AnimationFrame {
    private String spriteSheetPath; // Asset path to SpriteSheet
    private int spriteIndex;        // Index within that sheet
    private float duration;         // Seconds to display this frame
}
```

#### `AnimationComponent`
Component that plays animations on a GameObject. Requires `SpriteRenderer` component.

```java
public class AnimationComponent extends Component {
    public void playAnimation(String animationPath);
    public void playAnimation(Animation animation);
    public void pause();
    public void resume();
    public void stop();
    public void setSpeed(float multiplier);
    public boolean isPlaying();
    public Animation getCurrentAnimation();
    public int getCurrentFrameIndex();
}
```

#### `AnimationLoader`
AssetLoader implementation for `.anim.json` files.

```java
public class AnimationLoader implements AssetLoader<Animation> {
    @Override
    public Animation load(String path);
    @Override
    public void save(Animation animation, String path);
    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".anim.json", ".anim"};
    }
}
```

#### `AnimationEditorPanel`
ImGui panel for creating and editing animations in the editor.

Features:
- Animation list browser
- New/Delete/Duplicate animations
- Frame timeline with thumbnails
- Add/Remove/Reorder frames
- Per-frame duration editing
- Sprite picker (from loaded SpriteSheets)
- Playback controls (play/pause/step/loop toggle)
- Live preview in Scene viewport

### File Format

JSON structure for `.anim.json` files:

```json
{
  "name": "player_walk",
  "looping": true,
  "speed": 1.0,
  "frames": [
    {
      "spriteSheet": "sprites/player.spritesheet",
      "spriteIndex": 0,
      "duration": 0.1
    },
    {
      "spriteSheet": "sprites/player.spritesheet",
      "spriteIndex": 1,
      "duration": 0.1
    },
    {
      "spriteSheet": "sprites/player.spritesheet",
      "spriteIndex": 2,
      "duration": 0.15
    }
  ]
}
```

**Design notes:**
- `spriteSheet`: Relative path from asset root (resolved via `Assets.load()`)
- `spriteIndex`: Index into the SpriteSheet's frames
- `duration`: Time in seconds to display this frame
- `looping`: Whether animation repeats after last frame
- `speed`: Global speed multiplier (can be overridden at runtime)

## Component Behavior

### Lifecycle

1. **Initialization**: Component validates required `SpriteRenderer` exists
2. **Play**: Load animation, set first frame sprite, start timer
3. **Update**: Advance frame timer, switch sprites when duration expires
4. **Stop**: Reset to first frame (or clear sprite)
5. **Destroy**: Clean up animation reference

### Frame Advancement

```
timer += deltaTime * speed
if (timer >= currentFrame.duration) {
    timer -= currentFrame.duration
    frameIndex = (frameIndex + 1) % frames.size()
    renderer.setSprite(frames[frameIndex].sprite)
}
```

### State Management

- **STOPPED**: No animation, timer at 0
- **PLAYING**: Advancing frames
- **PAUSED**: Timer frozen, current frame visible
- **FINISHED**: Non-looping animation completed (stays on last frame)

## Editor Panel Design

### Layout

```
┌─────────────────────────────────────────────┐
│ Animation Editor                             │
├─────────────────────────────────────────────┤
│ [New] [Delete] [Duplicate]         [x Close]│
├─────────────────────────────────────────────┤
│ Animation List        │ Timeline             │
│ ┌─────────────────┐  │ ┌────────────────┐  │
│ │ player_walk  *  │  │ │[▶][⏸][■] Loop  │  │
│ │ player_idle     │  │ └────────────────┘  │
│ │ enemy_attack    │  │                     │
│ │                 │  │ Frame Timeline:     │
│ │                 │  │ ┌──┬──┬──┬──┬──┐  │
│ │                 │  │ │🖼│🖼│🖼│🖼│+ │  │
│ │                 │  │ └──┴──┴──┴──┴──┘  │
│ │                 │  │ Duration: 0.10s    │
│ │                 │  │                     │
│ │                 │  │ Sprite Picker:      │
│ │                 │  │ SpriteSheet: [v]    │
│ │                 │  │ ┌─────────────┐    │
│ │                 │  │ │ 🖼 🖼 🖼 🖼    │    │
│ │                 │  │ │ 🖼 🖼 🖼 🖼    │    │
│ └─────────────────┘  │ └─────────────┘    │
└─────────────────────────────────────────────┘
```

### Features

**Animation List**:
- Shows all `.anim.json` files in `gameData/assets/animations/`
- `*` indicates unsaved changes
- Click to select/edit
- Right-click for context menu (rename, duplicate, delete)

**Timeline**:
- Horizontal frame strip showing sprite thumbnails
- Click frame to select
- Drag to reorder
- `+` button to add new frame
- Delete key to remove selected frame
- Scrub playhead during preview

**Playback Controls**:
- Play/Pause button
- Stop button (reset to frame 0)
- Loop checkbox
- Speed slider (0.1x - 5.0x)
- Current frame indicator

**Frame Inspector** (when frame selected):
- Duration input (seconds)
- Sprite picker (dropdown of loaded SpriteSheets + grid of sprites)
- Preview of selected sprite

**Auto-save**:
- Changes auto-save to JSON file
- Dirty indicator (`*`) shows unsaved state
- Ctrl+S to force save

## Implementation Phases

### Phase 1: Core Data Model
1. `AnimationFrame` class
2. `Animation` class
3. `AnimationLoader` (load/save JSON)
4. Register loader in `AssetManager`
5. Unit tests for serialization

### Phase 2: Component
1. `AnimationComponent` implementation
2. Integration with `SpriteRenderer`
3. Playback logic (timer, frame advancement)
4. Public API (play, pause, stop, setSpeed)
5. Unit tests for component behavior

### Phase 3: Editor Panel
1. `AnimationEditorPanel` basic layout
2. Animation list browser
3. New/Delete/Save functionality
4. Timeline rendering (frame thumbnails)
5. Frame selection/manipulation

### Phase 4: Advanced Editor
1. Sprite picker integration
2. Playback preview in editor
3. Drag-and-drop reordering
4. Keyboard shortcuts (Space=play, Delete=remove frame)
5. Integration with Scene viewport preview

### Phase 5: Polish
1. Animation events (onComplete, onLoop callbacks)
2. Blend/crossfade support (future)
3. Animation state machine (future)
4. Performance profiling and optimization

## Usage Examples

### In Code

```java
// Create GameObject with animation
GameObject player = new GameObject("Player", new Vector3f(0, 0, 0));
SpriteRenderer renderer = player.addComponent(new SpriteRenderer());
AnimationComponent anim = player.addComponent(new AnimationComponent());

// Play animation
anim.playAnimation("animations/player_walk.anim");

// Control playback
anim.setSpeed(2.0f); // Double speed
anim.pause();
anim.resume();

// Query state
if (anim.isPlaying()) {
    System.out.println("Frame: " + anim.getCurrentFrameIndex());
}
```

### In Editor

1. Open Animation Editor panel
2. Click "New" → Enter name "player_walk"
3. Select SpriteSheet from dropdown
4. Click sprites to add frames
5. Adjust duration for each frame
6. Enable "Loop"
7. Click Play to preview
8. Auto-saves to `gameData/assets/animations/player_walk.anim.json`

### In Inspector

When GameObject has `AnimationComponent`:
- Dropdown to select animation asset
- Playback controls (for testing)
- Speed multiplier slider
- Auto-play on start checkbox

## Integration Points

### With Existing Systems

**SpriteSheet**: Animations reference sprites by sheet path + index

**AssetManager**: Animations are loaded/cached like other assets

**Component System**: AnimationComponent follows standard lifecycle

**Editor**: New panel in dockable UI, uses existing ImGui helpers

**Serialization**: AnimationComponent serializes current animation path + playback state

### Scene Serialization

```json
{
  "components": [
    {
      "type": "com.pocket.rpg.components.animations.AnimationComponent",
      "fields": {
        "animationPath": "animations/player_walk.anim",
        "autoPlay": true,
        "speed": 1.0,
        "looping": true
      }
    }
  ]
}
```

## Future Enhancements

1. **Animation Events**: Trigger code at specific frames (footstep sounds, particle effects)
2. **State Machine**: Transition between animations with conditions
3. **Blend Trees**: Smooth transitions between animations
4. **Root Motion**: Animation drives GameObject movement
5. **Animation Layers**: Multiple simultaneous animations (body + facial)
6. **Curve Editor**: Non-linear timing curves for frames

## Technical Considerations

**Performance**:
- No allocation during playback (reuse sprite references)
- Sprite batching handles rendering efficiently
- Animation assets loaded once, shared across instances

**Memory**:
- Animations are lightweight (just metadata + sprite references)
- SpriteSheets cache sprites, animations reference them
- Unload unused animations via AssetManager

**Thread Safety**:
- Animations are immutable after load
- Component state is local to GameObject
- AssetManager handles concurrent access

**Error Handling**:
- Missing SpriteRenderer → log error, disable component
- Invalid sprite index → log warning, skip frame
- Missing SpriteSheet → use placeholder, log error
