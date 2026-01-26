# Animator State Machine System

## Overview

This plan introduces an animation state machine system with two key components:

1. **AnimatorController** - A reusable asset defining states and transitions between animations
2. **GridMovementAnimator** - A middleware component that translates GridMovement state to animation states

## Problem Statement

Currently, `AnimationComponent` plays individual animations but lacks:
- State management (which animation to play based on game state)
- Transitions (how to switch between animations)
- Direction-based animation selection (walk_up, walk_down, etc.)

Game code must manually call `playAnimation()` at the right times, which is error-prone and creates tight coupling between game logic and animation assets.

## Goals

1. **Data-driven animation logic** - Define states and transitions in an asset file, not code
2. **Reusable animator definitions** - Same AnimatorController used across multiple characters
3. **Automatic direction handling** - Walk animation automatically picks correct direction variant
4. **Clean GridMovement integration** - No manual animation calls needed for basic movement

---

## Part 1: Should AnimatorController Be an Asset?

### Analysis

| Approach | Pros | Cons |
|----------|------|------|
| **Asset-based** | Reusable, editor-editable, data-driven | More infrastructure (loader, file format, editor panel) |
| **Component-based** | Simpler, no new asset type | State/transitions defined per-instance, less reusable |
| **Hybrid** | Flexibility | Complexity in two ways to configure |

### Decision: Asset-Based

**Reasons:**
1. **Reusability** - Same AnimatorController for all NPCs/enemies of the same type
2. **Artist/Designer workflow** - Non-programmers can tweak animation timing without code changes
3. **Follows existing patterns** - Animation assets already work this way
4. **Hot reload support** - Change states/transitions without restarting game
5. **Future editor** - Can build visual state machine editor later

### Implications

| Requirement | Implementation |
|-------------|----------------|
| New asset type | `AnimatorController` class |
| New loader | `AnimatorControllerLoader` implementing `AssetLoader<AnimatorController>` |
| File format | `.animator.json` in `gameData/assets/animators/` |
| Component | `AnimatorComponent` that references an AnimatorController asset |
| Editor support | Asset browser shows `.animator.json`, eventually visual editor |

---

## Part 2: AnimatorController Architecture

### Core Concepts

```
AnimatorController (Asset)
├── States
│   ├── "idle" → Animation: idle.anim
│   ├── "walk" → Directional: walk_{direction}.anim
│   └── "attack" → Animation: attack.anim
│
├── Transitions
│   ├── idle → walk: INSTANT
│   ├── walk → idle: INSTANT
│   ├── * → attack: INSTANT
│   └── attack → *: WAIT_FOR_COMPLETION
│
├── Parameters (runtime values that drive transitions)
│   ├── "isMoving": boolean
│   ├── "direction": Direction (UP/DOWN/LEFT/RIGHT)
│   └── "isAttacking": boolean
│
└── Default State: "idle"
```

### State Types

| Type | Description | Use Case |
|------|-------------|----------|
| **Simple** | Single animation | Attack, jump, hurt |
| **Directional** | 4 animations (up/down/left/right) | Walk, idle with facing |
| **BlendTree** (future) | Blend between animations | Speed-based walk/run blend |

### Transition Types

| Type | Behavior | Use Case |
|------|----------|----------|
| **INSTANT** | Switch immediately | Walk ↔ Idle (snappy response) |
| **WAIT_FOR_COMPLETION** | Finish current animation first | Attack → anything |
| **WAIT_FOR_LOOP** | Switch at loop point | Seamless looping transitions |

### File Format

```json
{
  "name": "player_animator",
  "defaultState": "idle",
  "parameters": [
    { "name": "isMoving", "type": "bool", "default": false },
    { "name": "direction", "type": "direction", "default": "DOWN" }
  ],
  "states": [
    {
      "name": "idle",
      "type": "directional",
      "animations": {
        "UP": "animations/player/idle_up.anim",
        "DOWN": "animations/player/idle_down.anim",
        "LEFT": "animations/player/idle_left.anim",
        "RIGHT": "animations/player/idle_right.anim"
      }
    },
    {
      "name": "walk",
      "type": "directional",
      "animations": {
        "UP": "animations/player/walk_up.anim",
        "DOWN": "animations/player/walk_down.anim",
        "LEFT": "animations/player/walk_left.anim",
        "RIGHT": "animations/player/walk_right.anim"
      }
    },
    {
      "name": "attack",
      "type": "simple",
      "animation": "animations/player/attack.anim"
    }
  ],
  "transitions": [
    {
      "from": "idle",
      "to": "walk",
      "type": "INSTANT",
      "conditions": [
        { "parameter": "isMoving", "value": true }
      ]
    },
    {
      "from": "walk",
      "to": "idle",
      "type": "INSTANT",
      "conditions": [
        { "parameter": "isMoving", "value": false }
      ]
    },
    {
      "from": "*",
      "to": "attack",
      "type": "INSTANT"
    },
    {
      "from": "attack",
      "to": "*",
      "type": "WAIT_FOR_COMPLETION"
    }
  ]
}
```

---

## Part 3: Component Design

### AnimatorComponent

```java
@ComponentMeta(category = "Rendering")
public class AnimatorComponent extends Component {

    // Serialized: reference to AnimatorController asset
    private AnimatorController controller;

    // Runtime: state machine instance
    @HideInInspector
    private transient AnimatorStateMachine stateMachine;

    @ComponentRef
    private AnimationComponent animationComponent;

    @Override
    protected void onStart() {
        if (controller != null && animationComponent != null) {
            stateMachine = new AnimatorStateMachine(controller, animationComponent);
        }
    }

    @Override
    public void update(float deltaTime) {
        if (stateMachine != null) {
            stateMachine.update(deltaTime);
        }
    }

    // API for game code
    public void setBool(String param, boolean value);
    public void setDirection(Direction direction);
    public void trigger(String stateName); // Force transition to state

    public String getCurrentState();
    public boolean isInState(String state);
}
```

### GridMovementAnimator

Middleware component that automatically drives an AnimatorComponent based on GridMovement state.

```java
@ComponentMeta(category = "Rendering")
public class GridMovementAnimator extends Component {

    @ComponentRef
    private GridMovement gridMovement;

    @ComponentRef
    private AnimatorComponent animator;

    // Optional parameter name customization
    private String movingParam = "isMoving";
    private String directionParam = "direction";
    private String slidingParam = "isSliding";
    private String jumpingParam = "isJumping";

    @Override
    public void update(float deltaTime) {
        if (gridMovement == null || animator == null) return;

        // Sync parameters from GridMovement state
        animator.setBool(movingParam, gridMovement.isMoving());
        animator.setDirection(gridMovement.getFacingDirection());

        // Optional: sliding/jumping parameters if defined
        if (slidingParam != null) {
            animator.setBool(slidingParam, gridMovement.isSliding());
        }
        if (jumpingParam != null) {
            animator.setBool(jumpingParam, gridMovement.isJumping());
        }
    }
}
```

**Usage:**
1. Add `AnimatorComponent` with AnimatorController asset
2. Add `GridMovementAnimator` to same GameObject
3. Movement automatically animates correctly

---

## Part 4: Automatic Transitions

The state machine evaluates transition conditions each frame:

```java
public void update(float deltaTime) {
    // 1. Check for automatic transitions from current state
    for (Transition t : getTransitionsFrom(currentState)) {
        if (t.conditionsMet(parameters)) {
            requestTransition(t.to, t.type);
            break;
        }
    }

    // 2. Handle pending transitions (WAIT_FOR_COMPLETION)
    if (pendingTransition != null && animationComponent.isFinished()) {
        applyTransition(pendingTransition);
    }

    // 3. Update direction if in directional state
    if (currentStateConfig.isDirectional()) {
        Direction dir = parameters.getDirection("direction");
        if (dir != lastDirection) {
            // Switch to correct directional animation
            Animation dirAnim = currentStateConfig.getAnimation(dir);
            animationComponent.playAnimation(dirAnim);
            lastDirection = dir;
        }
    }
}
```

---

## Part 5: Implementation Phases

### Phase 1: Core Infrastructure
- [ ] `AnimatorController` class (asset data structure)
- [ ] `AnimatorControllerLoader` (load from JSON)
- [ ] `AnimatorStateMachine` (runtime logic)
- [ ] `TransitionType` enum
- [ ] `AnimatorParameter` class
- [ ] Register loader in `AssetManager`

### Phase 2: Component Integration
- [ ] `AnimatorComponent` (references AnimatorController)
- [ ] Directional state animation selection
- [ ] Automatic transition evaluation
- [ ] Inspector field editor for AnimatorController assets

### Phase 3: GridMovement Bridge
- [ ] `GridMovementAnimator` component
- [ ] Parameter mapping configuration
- [ ] Test with player character

### Phase 4: Events (Optional)
- [ ] Add `onComplete`, `onFrameChanged` callbacks to `AnimationComponent`
- [ ] Animation events in AnimatorController (sound triggers, etc.)

### Phase 5: Editor Support (Future)
- [ ] Visual state machine editor panel
- [ ] Transition preview
- [ ] Parameter debugging in play mode

---

## Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `animation/AnimatorController.java` | **NEW** | Asset class holding states, transitions, parameters |
| `animation/AnimatorState.java` | **NEW** | Single state definition (simple or directional) |
| `animation/AnimatorTransition.java` | **NEW** | Transition definition with conditions |
| `animation/AnimatorParameter.java` | **NEW** | Parameter definition (bool, int, direction) |
| `animation/TransitionType.java` | **NEW** | Enum: INSTANT, WAIT_FOR_COMPLETION, WAIT_FOR_LOOP |
| `animation/AnimatorStateMachine.java` | **NEW** | Runtime state machine logic |
| `resources/loaders/AnimatorControllerLoader.java` | **NEW** | JSON loader for .animator.json files |
| `components/AnimatorComponent.java` | **NEW** | Component wrapper for AnimatorStateMachine |
| `components/GridMovementAnimator.java` | **NEW** | Bridges GridMovement → AnimatorComponent |
| `components/AnimationComponent.java` | Modify | Add event callbacks (onComplete, etc.) |
| `resources/AssetManager.java` | Modify | Register AnimatorControllerLoader |

---

## Package Structure

```
com.pocket.rpg.animation/
├── Animation.java              # Existing
├── AnimationFrame.java         # Existing
├── AnimatorController.java     # NEW - Asset class
├── AnimatorState.java          # NEW - State definition
├── AnimatorTransition.java     # NEW - Transition definition
├── AnimatorParameter.java      # NEW - Parameter definition
├── AnimatorStateMachine.java   # NEW - Runtime logic
└── TransitionType.java         # NEW - Enum

com.pocket.rpg.components/
├── AnimationComponent.java     # Existing (add events)
├── AnimatorComponent.java      # NEW
└── GridMovementAnimator.java   # NEW

com.pocket.rpg.resources.loaders/
├── AnimationLoader.java        # Existing
└── AnimatorControllerLoader.java # NEW
```

---

## Example Usage

### Minimal Setup (GridMovement character)

```java
// In scene or prefab setup:
GameObject player = new GameObject("Player");
player.addComponent(new SpriteRenderer());
player.addComponent(new AnimationComponent());
player.addComponent(new GridMovement());

// Load animator controller asset
AnimatorComponent animator = new AnimatorComponent();
animator.setController(Assets.load("animators/player.animator", AnimatorController.class));
player.addComponent(animator);

// Auto-syncs GridMovement → Animator
player.addComponent(new GridMovementAnimator());

// That's it! Movement automatically animates.
```

### Manual State Control (for attacks, etc.)

```java
AnimatorComponent animator = player.getComponent(AnimatorComponent.class);

// Force transition to attack state
if (Input.isActionJustPressed("ATTACK")) {
    animator.trigger("attack");
}
```

---

## Open Questions

1. **Parameter types** - Start with bool and Direction only? Add int/float later?
2. **Any state transitions** - Use "*" wildcard for from/to? Or explicit "any" state?
3. **Priority** - When multiple transitions match, which wins? First defined? Explicit priority field?
4. **Exit time** - WAIT_FOR_COMPLETION works for non-looping. What about looping animations?

## Answers/Decisions

1. **Parameters**: Start with `bool` and `direction`. Add `int`/`float` when needed for blend trees.
2. **Wildcards**: Use `"*"` for "any state". `from: "*"` means "from any state", `to: "*"` means "return to previous state".
3. **Priority**: First matching transition wins. Define more specific transitions first in the list.
4. **Exit time**: Add optional `exitTime` field (0.0-1.0) for looping animations. Default behavior: switch immediately for looping animations with INSTANT.

---

## Testing Strategy

1. **Unit tests** for `AnimatorStateMachine` transition logic
2. **Integration test** with mock `AnimationComponent`
3. **Manual testing** with player character in editor play mode
4. **Verify hot reload** works when changing `.animator.json` files
