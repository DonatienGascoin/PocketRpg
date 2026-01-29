# Animation Editor Guide

> **Summary:** Create and edit sprite-based frame animations. Define frame sequences, durations, and preview playback directly in the editor.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [Workflows](#workflows)
6. [Keyboard Shortcuts](#keyboard-shortcuts)
7. [Tips & Best Practices](#tips--best-practices)
8. [Troubleshooting](#troubleshooting)
9. [Code Integration](#code-integration)
10. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create animation | Click **New**, enter name, select first sprite, click **Create** |
| Add frame | Click **+** button in timeline, select sprite from picker |
| Change frame sprite | Select frame, click **Browse...** in Frame Properties |
| Adjust duration | Select frame, edit **Duration** field |
| Reorder frames | Drag and drop frames in the timeline |
| Preview animation | Click **Play** button or press **Space** |
| Save changes | Click **Save** button or press **Ctrl+S** |
| Delete frame | Select frame, click **Delete Frame** or press **Delete** |

---

## Overview

The Animation Editor creates reusable animation assets (`.anim.json` files) from sprites. Animations consist of frames, each with a sprite and duration.

**Use the Animation Editor to:**
- Build walk cycles, attack animations, idle loops
- Preview animations at different speeds
- Adjust frame timing for the right feel

**Workflow context:**
1. Create sprites/spritesheets in your image editor
2. Import them into the Asset Browser
3. Create animations in the Animation Editor
4. Assign animations to GameObjects via AnimationComponent

Animations are assets stored in `gameData/assets/animations/`. They can be shared across multiple GameObjects.

---

## Opening the Panel

**Method 1:** Click the **Animation Editor** tab (next to Scene, Game, UI Designer)

**Method 2:** Double-click any `.anim.json` file in the Asset Browser

---

## Interface Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ Animation Editor                                                │
├─────────────────────────────────────────────────────────────────┤
│ [New] [Save] [Delete]                              [Undo] [Redo]│
├───────────────────┬─────────────────────────────────────────────┤
│ ANIMATION LIST    │                 PREVIEW                     │
│ ┌───────────────┐ │        ┌─────────────────┐                  │
│ │ player_idle   │ │        │                 │                  │
│ │ player_walk * │ │        │      ┌───┐      │                  │
│ │ player_run    │ │        │      │ 🖼 │      │                  │
│ │ enemy_attack  │ │        │      └───┘      │                  │
│ └───────────────┘ │        │                 │                  │
│                   │        └─────────────────┘                  │
│ PROPERTIES        │  Zoom: [0.5x] [Fit]  BG: [Checker ▼]        │
│ ┌───────────────┐ │                                             │
│ │ Name: [____]  │ ├─────────────────────────────────────────────┤
│ │ Looping: [x]  │ │ TIMELINE                                    │
│ │ Duration: 0.4s│ │ [▶][⏸][■]  Speed: [===1.0===]  Frame 2/4   │
│ │ Frames: 4     │ │ ┌────┬────┬────┬────┬────┐                  │
│ └───────────────┘ │ │ 🖼  │ 🖼  │▼🖼 │ 🖼  │ +  │                  │
├───────────────────┤ │0.1s│0.1s│0.1s│0.15│    │                  │
│ FRAME PROPERTIES  │ └────┴────┴────┴────┴────┘                  │
│ Duration: [0.10]s ├─────────────────────────────────────────────┤
│ Sprite: sheet#2   │                                             │
│ [Browse...      ] │                                             │
│ [Delete Frame   ] │                                             │
└───────────────────┴─────────────────────────────────────────────┘
```

| Section | Description |
|---------|-------------|
| **Toolbar** | New, Save, Delete animation; Undo/Redo |
| **Animation List** | All animations in `animations/` folder. `*` indicates unsaved changes |
| **Properties** | Animation name, looping toggle, read-only stats |
| **Preview** | Live playback of current animation with zoom and background options |
| **Timeline** | Playback controls, frame strip with thumbnails. Selected frame marked with ▼ |
| **Frame Properties** | Edit selected frame's duration and sprite |

---

## Workflows

### Creating a New Animation

1. Click **New** in the toolbar
2. Enter a name for the animation (e.g., `player_walk`)
3. Click **Browse...** to select the first frame's sprite
4. Select a sprite from the picker (spritesheet or image file)
5. Click **Create**
6. The animation is created with one frame (1 second duration)
7. Add more frames and adjust durations as needed
8. Click **Save** when done

### Adding Frames

1. Select an animation from the list
2. Click the **+** button at the end of the timeline
3. The sprite picker opens automatically
4. Select a sprite for the new frame
5. Adjust the duration in Frame Properties (default: 1 second)

### Changing a Frame's Sprite

1. Click on a frame in the timeline to select it
2. In Frame Properties, click **Browse...**
3. Select a new sprite from the picker

### Reordering Frames

1. Click and drag a frame in the timeline
2. Drop it at the desired position
3. Drop zones appear between frames during drag

### Adjusting Timing

1. Select a frame in the timeline
2. Edit the **Duration** field in Frame Properties
3. Duration is in seconds (e.g., 0.1 = 100ms)

### Previewing Animation

1. Click **Play** (▶) or press **Space** to start playback
2. Use **Speed** slider to preview faster/slower
3. Click **Stop** (■) to return to first frame
4. Click **Pause** (⏸) to freeze at current frame

### Deleting Frames

1. Select a frame in the timeline
2. Click **Delete Frame** or press **Delete**
3. Animation must have at least one frame

### Saving Changes

1. Click **Save** or press **Ctrl+S**
2. The `*` next to the animation name disappears
3. Unsaved changes prompt appears when switching animations

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+S | Save current animation |
| Ctrl+N | Create new animation |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z | Redo |
| Ctrl+Y | Redo (alternative) |
| F5 | Refresh animation list |
| Space | Play/Pause preview |
| Left Arrow | Previous frame |
| Right Arrow | Next frame |
| Home | First frame |
| End | Last frame |
| Delete | Delete selected frame |

> **Note:** All shortcuts are rebindable via **Edit > Shortcuts**. These shortcuts only activate when the Animation Editor panel is focused.

---

## Tips & Best Practices

- **Consistent frame sizes:** Use sprites of the same size for smoother animations
- **Test at game speed:** Preview at 1.0x speed to see actual in-game timing
- **Short durations:** Most game animations use 0.05s-0.15s per frame
- **Loop points:** For looping animations, ensure first and last frames connect smoothly
- **Naming convention:** Use `character_action` format (e.g., `player_walk`, `enemy_attack`)
- **Save often:** Animation files are not auto-saved

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Animation not playing in game | Check that AnimationComponent has **Auto Play** enabled |
| Sprite appears stretched | Ensure all frames use same pixel dimensions |
| Can't find animation in picker | Animation must be saved first |
| Preview shows wrong frame | Click **Stop** to reset to frame 0 |
| Changes not appearing in game | Save the animation and reload the scene |

---

## Code Integration

Most animation work is done through the inspector by adding an **AnimationComponent** to a GameObject and selecting an animation asset. Code integration is only needed for dynamic animation switching.

### Switching Animations at Runtime

```java
// Get the AnimationComponent
AnimationComponent anim = gameObject.getComponent(AnimationComponent.class);

// Play a different animation by path
anim.playAnimation("animations/player_run.anim");

// Or load and set explicitly
Animation walkAnim = Assets.load("animations/player_walk.anim", Animation.class);
anim.setAnimation(walkAnim);
anim.play();
```

### Responding to Animation Completion

```java
// Check if non-looping animation finished
if (anim.isFinished()) {
    anim.playAnimation("animations/player_idle.anim");
}

// Query current state
if (anim.isPlaying()) {
    int frame = anim.getCurrentFrameIndex();
    float progress = anim.getProgress(); // 0.0 to 1.0
}
```

### Playback Control

```java
anim.play();           // Start from beginning
anim.pause();          // Freeze at current frame
anim.resume();         // Continue from paused position
anim.stop();           // Reset to frame 0
anim.setSpeed(2.0f);   // 2x speed
```

---

## Related

- [Asset Browser Guide](asset-browser-guide.md) - Importing sprites and spritesheets
- [Inspector Panel Guide](inspector-panel-guide.md) - Configuring AnimationComponent
- [Spritesheet Guide](spritesheet-guide.md) - Creating spritesheets for animations
