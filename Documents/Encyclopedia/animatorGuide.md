# Animator Guide

> **Summary:** Design state machine-based animation controllers that manage complex animation flows through states, transitions, and parameters. Use the visual graph editor to wire up idle/walk/attack sequences driven by game logic.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [Workflows](#workflows)
6. [Parameters](#parameters)
7. [States](#states)
8. [Transitions](#transitions)
9. [Preview Panel](#preview-panel)
10. [Keyboard Shortcuts](#keyboard-shortcuts)
11. [Tips & Best Practices](#tips--best-practices)
12. [Troubleshooting](#troubleshooting)
13. [Code Integration](#code-integration)
14. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create animator | Click **New**, enter name |
| Add state | Right-click canvas → **Add State** |
| Create transition | Right-click source node → **Make Transition** → click target node |
| Add parameter | Click **+ Bool**, **+ Trigger**, or **+ Direction** |
| Set default state | Right-click node → **Set as Default** |
| Preview playback | Toggle **Preview** panel, click **Play** |
| Save | **Ctrl+S** or click **Save** |

---

## Overview

The Animator system lets you build state machines that control which animation plays on a GameObject and when to switch between them. Instead of manually calling `play("walk")` in code, you define states (idle, walk, attack) and transitions with conditions (when `isMoving` becomes true, go from idle to walk).

**Core concepts:**
- **States** — Each state plays an animation. States can be *simple* (one animation) or *directional* (four animations, one per direction).
- **Transitions** — Rules for moving between states. Can fire instantly, wait for the current animation to finish, or wait for a loop point.
- **Parameters** — Runtime variables (bool, trigger, direction) that your game code sets to drive transitions.

**Workflow context:**
1. Create animations in the [Animation Editor](animation-editor-guide.md)
2. Create an animator controller in the Animator panel
3. Add states, assign animations, wire transitions
4. Add an `AnimatorComponent` to your GameObject and assign the controller
5. Set parameters from game code to drive the state machine

Animator files are saved as `.animator.json` in `gameData/assets/animators/`.

---

## Opening the Panel

**Method 1:** Click the **Animator** tab (next to Scene, Game, Animation Editor)

**Method 2:** Double-click any `.animator.json` file in the Asset Browser

---

## Interface Overview

```
┌──────────────────────────────────────────────────────────────────┐
│ [+New] [Delete] [Save*] [↻Refresh]          [↶Undo] [↷Redo]    │
│ [Controller Dropdown ▼]                      [☰Preview]         │
├────────────────┬────────────────────────────────┬────────────────┤
│  PARAMETERS    │                                │   PREVIEW      │
│                │      GRAPH EDITOR              │   (toggleable) │
│  ● isMoving    │                                │                │
│  ● facing      │   ┌──────────┐                 │   [▶] [⏸] [⏹]  │
│  ⚡ attack      │   │ ⭐ idle   │──────→┌──────┐ │   State: idle  │
│                │   └──────────┘       │ walk │ │   Frame: 1/4   │
│  [+ Bool]      │       ↑             └──┬───┘ │                │
│  [+ Trigger]   │       └───────────────-┘     │   Direction:   │
│  [+ Direction] │                                │   [DOWN ▼]     │
│                │              Minimap (↙)       │                │
└────────────────┴────────────────────────────────┴────────────────┘
```

| Section | Description |
|---------|-------------|
| **Toolbar** | New, Delete, Save, Refresh, Undo/Redo, controller selector, preview toggle |
| **Parameters** | List of runtime parameters with type icons. Add/rename/delete. |
| **Graph Editor** | Visual node graph (ImNodes). States as nodes, transitions as arrows. |
| **Preview** | Live playback simulation with parameter editing. Toggle via toolbar. |

---

## Workflows

### Creating a New Animator

1. Click **New** in the toolbar (or **Ctrl+N**)
2. Enter a name (e.g., `player`)
3. The file is created at `animators/player.animator.json`
4. A default "idle" state is auto-created
5. Add more states, parameters, and transitions

### Adding a State

1. Right-click the graph canvas → **Add State**
2. A new node appears with an auto-generated name
3. Select the node → edit in the Inspector:
   - **Name**: Unique identifier (e.g., "walk")
   - **Type**: Simple or Directional
   - **Animation**: Pick an animation asset (or four for directional)
4. Right-click → **Set as Default** to make it the entry state (marked with ⭐)

### Creating a Transition

1. Right-click the source state node → **Make Transition**
2. A line follows your cursor
3. Click the target state node to complete the transition
4. Press **Escape** or right-click to cancel
5. Select the transition arrow → edit in the Inspector:
   - **Type**: Instant, Wait for Completion, or Wait for Loop
   - **Conditions**: Add parameter conditions

### Adding Conditions to a Transition

1. Select a transition arrow in the graph
2. In the Inspector, click **Add Condition**
3. Select a parameter from the dropdown
4. Set the expected value (true/false for bool, direction for direction)
5. Add multiple conditions — all must be true for the transition to fire

### Renaming a Parameter

1. Double-click the parameter name in the list
2. Type the new name and press Enter
3. All transitions and directional states referencing the old name are automatically updated

### Deleting States or Transitions

1. Select the node or arrow in the graph
2. Press **Delete** or right-click → **Delete**
3. Deleting a state also removes all its transitions

---

## Parameters

Parameters are runtime variables that drive the state machine. Your game code sets them, and transition conditions evaluate them.

### Parameter Types

| Type | Icon | Default | Description |
|------|------|---------|-------------|
| **Bool** | ☑ | `false` | On/off toggle. Use for continuous states (isMoving, isRunning). |
| **Trigger** | ⚡ | `false` | One-shot flag. Auto-resets after a transition consumes it. Use for events (attack, jump). |
| **Direction** | 🧭 | `DOWN` | Cardinal direction (UP, DOWN, LEFT, RIGHT). Drives directional states. |

### Adding Parameters

Click one of the buttons below the parameter list:
- **+ Bool** — Creates a boolean parameter
- **+ Trigger** — Creates a trigger parameter
- **+ Direction** — Creates a direction parameter

Names are auto-generated (`new_bool`, `new_bool_1`, etc.). Double-click to rename.

---

## States

### Simple States

Play a single animation regardless of direction. Use for attacks, jumps, or any animation that looks the same from all angles.

- Set **Type** to **Simple** in the Inspector
- Pick one animation asset

### Directional States

Play one of four animations based on a direction parameter. Use for walk cycles and idle poses where the character faces different directions.

- Set **Type** to **Directional** in the Inspector
- Pick four animation assets (UP, DOWN, LEFT, RIGHT)
- Set the **Direction Parameter** to the parameter that controls which direction to use
- The state automatically switches animations when the direction changes mid-playback

### Default State

The state machine enters the default state on startup. Marked with ⭐ in the graph.

Right-click a node → **Set as Default** to change it.

---

## Transitions

### Transition Types

| Type | Behavior |
|------|----------|
| **Instant** | Fire immediately when conditions are met |
| **Wait for Completion** | Wait for the current animation to finish, then transition |
| **Wait for Loop** | Wait until the animation loops back to frame 0 |

### Transition Colors in Graph

| Color | Type |
|-------|------|
| Green | Instant |
| Orange | Wait for Completion |
| Blue | Wait for Loop |

### Wildcard Transitions

- **Any State (from: \*)** — Transition can fire from any state. Useful for global interrupts like "take damage".
- **Return to Previous (to: \*)** — Transition returns to the state the machine was in before the current one. Useful for "flinch then resume".

### Evaluation Order

Transitions are checked in list order each frame. The first one whose conditions are all met fires. Reorder transitions to control priority.

---

## Preview Panel

Toggle via the **☰Preview** button in the toolbar.

The preview panel simulates the state machine without running the game:

1. Click **Play** (▶) to start the simulation
2. Edit parameter values in the left panel:
   - Toggle bools with checkboxes
   - Fire triggers with buttons
   - Change direction via dropdown
3. Watch the graph update — the active state pulses green
4. The sprite preview shows the current animation frame
5. Click **Stop** (⏹) to reset to the default state

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+S | Save animator |
| Ctrl+N | New animator |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z | Redo |
| Ctrl+Y | Redo (alternative) |
| F5 | Refresh animator list |
| Delete | Delete selected state or transition |
| Escape | Cancel transition creation |

> **Note:** All shortcuts are rebindable via **Edit > Shortcuts**. These shortcuts only activate when the Animator panel is focused. See the [Shortcuts Guide](shortcuts-guide.md).

---

## Tips & Best Practices

- **Start with parameters**: Define all your parameters before wiring states. It makes adding conditions easier.
- **Use triggers for one-shot actions**: Attack, jump, and damage should be triggers, not bools. Triggers auto-reset so you don't forget to clear them.
- **Use direction parameters for movement**: One direction parameter can drive multiple directional states (idle, walk, run all face the same way).
- **Keep transition conditions simple**: If a transition needs many conditions, consider adding an intermediate state.
- **Set meaningful state names**: "player_idle" is better than "state_0" — names appear in code and debug logs.
- **Save often**: Animator files are not auto-saved. The **\*** suffix in the toolbar indicates unsaved changes.
- **Use the preview panel**: Test your state machine logic before running the game. It's faster to iterate.
- **Wildcard transitions for interrupts**: Use "Any State → damaged" for effects that can interrupt any animation.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Animation not playing | Check that the state has an animation assigned (✓ vs ⚠ in Inspector) |
| Transition not firing | Verify conditions match — check parameter names and expected values |
| Wrong direction animation | Check the **Direction Parameter** on the directional state matches the parameter you're setting in code |
| State machine stuck | Check for missing transitions out of the current state. Use preview to debug. |
| Trigger fires multiple times | Triggers auto-reset after one transition. If it fires repeatedly, you may be calling `setTrigger()` every frame instead of once. |
| Unsaved changes lost | Always **Ctrl+S** before switching controllers or closing the panel |
| Node positions reset | Layout is saved separately — check that the `.layout` file is writable |
| Preview sprite blank | Verify animation files exist and have valid sprite paths |

---

## Code Integration

Add an `AnimatorComponent` to any GameObject that needs animated state machine control.

### Basic Setup (Inspector)

1. Add **AnimatorComponent** to a GameObject
2. Set **Controller** to your `.animator.json` file
3. Enable **Auto Play** to start on scene load
4. Adjust **Speed** for playback rate (default 1.0)

The GameObject must also have a **SpriteRenderer** component — the animator updates it each frame.

### Setting Parameters at Runtime

```java
AnimatorComponent animator = gameObject.getComponent(AnimatorComponent.class);

// Bool: toggle continuous states
animator.setBool("isMoving", true);
animator.setBool("isMoving", false);

// Direction: control facing
animator.setDirection("facing", Direction.LEFT);

// Trigger: fire one-shot events
animator.setTrigger("attack");
```

### Querying State

```java
// Check current state
String state = animator.getCurrentState();       // "walk"
boolean walking = animator.isInState("walk");    // true

// Check previous state
String prev = animator.getPreviousState();       // "idle"

// Check pending transition (waiting for completion)
boolean pending = animator.hasPendingTransition();
animator.cancelPendingTransition();  // cancel if needed
```

### Forcing State Changes

```java
// Skip conditions, jump directly to a state
animator.forceState("idle");
```

### Playback Control

```java
AnimationPlayer player = animator.getPlayer();
player.pause();
player.resume();
player.setSpeed(2.0f);  // 2x speed
```

### Example: Player Movement Controller

```java
public class PlayerController extends Component {
    @ComponentReference(source = Source.SELF)
    private AnimatorComponent animator;

    @ComponentReference(source = Source.SELF)
    private GridMovement movement;

    @Override
    public void update(float deltaTime) {
        boolean moving = movement.isMoving();
        animator.setBool("isMoving", moving);

        if (moving) {
            animator.setDirection("facing", movement.getFacingDirection());
        }

        if (Input.isActionPressed("ATTACK")) {
            animator.setTrigger("attack");
        }
    }
}
```

---

## Related

- [Animation Editor Guide](animation-editor-guide.md) — Creating the animations that states play
- [Shortcuts Guide](shortcuts-guide.md) — Viewing and rebinding keyboard shortcuts
- [Components Guide](components-guide.md) — Adding components to GameObjects
