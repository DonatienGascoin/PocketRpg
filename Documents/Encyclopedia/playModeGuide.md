# Play Mode Guide

> **Summary:** Test your game directly in the editor. Play mode runs the game loop, processes input, and renders to the Game View panel while preserving your scene state.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Interface Overview](#interface-overview)
4. [Workflows](#workflows)
5. [Keyboard Shortcuts](#keyboard-shortcuts)
6. [Tips & Best Practices](#tips--best-practices)
7. [Troubleshooting](#troubleshooting)
8. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Start play mode | **Ctrl+P** or click **Play** (▶) |
| Pause | **Ctrl+P** while playing |
| Resume | **Ctrl+P** while paused |
| Stop | **Ctrl+Shift+P** or click **Stop** (■) |

---

## Overview

Play mode lets you test your game without leaving the editor. When you enter play mode:

1. The current scene state is **snapshot** (saved in memory)
2. The game loop starts — components run their `update()` methods
3. Game input is routed to the Game View panel
4. The Game View renders the game camera's perspective

When you stop play mode, the scene is **restored** to the snapshot, so any runtime changes (entity movement, state changes) are discarded. Your editor work is always preserved.

---

## Interface Overview

### Play Controls

Play controls are in the main toolbar:

```
[▶ Play] [⏸ Pause] [■ Stop]
```

| Button | State | Effect |
|--------|-------|--------|
| **▶ Play** | Stopped | Starts play mode |
| **⏸ Pause** | Playing | Freezes the game |
| **▶ Resume** | Paused | Continues from paused state |
| **■ Stop** | Playing/Paused | Stops and restores scene |

### Game View Panel

The Game View shows what the game camera sees — the player's perspective:

```
┌──────────────────────────────────────────────┐
│ [Scene] [Game] [Animation] [Animator]         │
├──────────────────────────────────────────────┤
│                                              │
│              ┌─────────┐                      │
│              │  Player  │                      │
│              └─────────┘                      │
│                                              │
│    ░░░░░░░░░░░░░░░░░░░░░░░░                  │
│    ░░░░░░░░░░░░░░░░░░░░░░░░                  │
│                                              │
└──────────────────────────────────────────────┘
```

The Game View automatically becomes the active tab when play mode starts.

---

## Workflows

### Testing Your Scene

1. Make sure your scene is saved (**Ctrl+S**) — play mode works on the current scene state
2. Press **Ctrl+P** to start play mode
3. Click the Game View to give it input focus
4. Test your game — move the player, trigger events, etc.
5. Press **Ctrl+Shift+P** to stop and return to editing

### Debugging with Pause

1. Start play mode (**Ctrl+P**)
2. When you see an issue, press **Ctrl+P** again to pause
3. Switch to the Scene View tab to inspect entity positions
4. Check component values in the Inspector
5. Press **Ctrl+P** to resume or **Ctrl+Shift+P** to stop

### Play Mode Lifecycle

```
STOPPED ──[Play]──→ PLAYING ──[Pause]──→ PAUSED
   ↑                   │                    │
   │                   │                    │
   └──────[Stop]───────┘────────[Stop]──────┘
                                            │
                                [Resume]────┘
                                    ↓
                                 PLAYING
```

**On Play:**
- Scene state is captured (snapshot)
- Game loop starts running
- Components receive `onStart()` calls
- Editor shortcuts are suppressed (game owns the keyboard)

**On Pause:**
- Game loop freezes
- Current state is preserved
- You can inspect but not edit

**On Stop:**
- Game loop stops
- Scene is restored from snapshot
- All runtime changes are discarded
- Editor shortcuts resume

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+P | Play / Pause (toggle) |
| Ctrl+Shift+P | Stop |

> Editor shortcuts (WASD, tool keys, etc.) are **disabled** during play mode. The game receives all keyboard input. Stop play mode to use editor shortcuts again.

---

## Tips & Best Practices

- **Save before playing**: Play mode snapshots the current state, but saving ensures you don't lose work if something goes wrong
- **Use the Game View**: Click the Game View tab to give it input focus — otherwise game input won't work
- **Pause to inspect**: Pause mode lets you examine runtime state without stopping the simulation
- **Changes are temporary**: Nothing you do in play mode persists — the scene resets on stop
- **Check the console**: Runtime errors and debug messages appear in the Console panel during play

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Player not moving | Click the Game View to give it input focus |
| Editor shortcuts not working | Stop play mode first (**Ctrl+Shift+P**) — shortcuts are suppressed during play |
| Scene changed after stopping | This shouldn't happen — play mode restores the snapshot. Report if it persists. |
| Play button not responding | Make sure a scene is loaded |
| Game View is blank | Check that the scene has a camera and entities with visible sprites |
| Audio not playing | Check AudioSource components and that audio files exist |

---

## Related

- [Scene View Guide](scene-view-guide.md) — The editor viewport (separate from Game View)
- [Shortcuts Guide](shortcuts-guide.md) — All editor shortcuts (disabled during play)
- [Components Guide](components-guide.md) — Components that run during play mode
- [Audio System Guide](audio-system-guide.md) — Audio playback during play
