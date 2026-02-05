# Phase 6: Animation Events & State Machine

## Overview

Phase 6 extends the animation system with:
1. **Animation Events** - Callbacks for animation lifecycle (completion, frame changes)
2. **Animation State Machine** - Manages transitions between animations with optional blending

---

## Part 1: Animation Events

### Motivation

Games often need to respond to animation milestones:
- Play footstep sound on specific frames
- Spawn particle effects at attack impact frame
- Trigger game logic when animation completes
- Chain animations together

### Event Types

| Event | When Fired | Use Cases |
|-------|------------|-----------|
| `onFrameChanged` | Each time frame index changes | Sound effects, particles at specific frames |
| `onComplete` | Animation finishes (non-looping) or completes one loop | Chain animations, trigger logic |
| `onLoop` | Looping animation restarts | Track loop count |

### Design Option A: Callback-Based (Simple)

Add callback fields directly to `AnimationComponent`:

```java
public class AnimationComponent extends Component {

    // Existing fields...

    // Event callbacks (not serialized)
    @HideInInspector
    private transient Runnable onComplete;

    @HideInInspector
    private transient Consumer<Integer> onFrameChanged; // receives frame index

    @HideInInspector
    private transient Runnable onLoop;

    // Setters
    public void setOnComplete(Runnable callback) {
        this.onComplete = callback;
    }

    public void setOnFrameChanged(Consumer<Integer> callback) {
        this.onFrameChanged = callback;
    }

    public void setOnLoop(Runnable callback) {
        this.onLoop = callback;
    }

    // In update(), fire events:
    @Override
    public void update(float deltaTime) {
        // ... existing frame advancement logic ...

        // When frame changes:
        if (previousFrame != currentFrame && onFrameChanged != null) {
            onFrameChanged.accept(currentFrame);
        }

        // When animation completes (non-looping):
        if (state == AnimationState.FINISHED && onComplete != null) {
            onComplete.run();
            onComplete = null; // Fire once
        }

        // When loop restarts:
        if (currentFrame == 0 && previousFrame > 0 && animation.isLooping()) {
            if (onLoop != null) {
                onLoop.run();
            }
        }
    }
}
```

**Usage:**
```java
AnimationComponent anim = player.getComponent(AnimationComponent.class);
anim.setOnComplete(() -> {
    anim.playAnimation("animations/idle.anim");
});
anim.setOnFrameChanged(frame -> {
    if (frame == 2) { // Impact frame
        spawnHitEffect();
    }
});
```

**Pros:** Simple, no new classes, works immediately
**Cons:** Callbacks cleared on animation change, no frame-specific events in animation data

### Design Option B: Frame Events in Animation Data (Rich)

Store events in the animation file itself:

```json
{
  "name": "player_attack",
  "looping": false,
  "frames": [
    { "sprite": "player.spritesheet#0", "duration": 0.1 },
    { "sprite": "player.spritesheet#1", "duration": 0.1 },
    {
      "sprite": "player.spritesheet#2",
      "duration": 0.1,
      "events": ["hit", "sound:sword_swing"]
    },
    { "sprite": "player.spritesheet#3", "duration": 0.15 }
  ]
}
```

**New classes:**

```java
// AnimationFrame becomes:
public record AnimationFrame(
    String spritePath,
    float duration,
    List<String> events  // Optional event tags
) {
    public AnimationFrame(String spritePath, float duration) {
        this(spritePath, duration, List.of());
    }
}

// Event listener interface
public interface AnimationEventListener {
    void onAnimationEvent(String eventName, int frameIndex);
}

// In AnimationComponent:
private List<AnimationEventListener> eventListeners = new ArrayList<>();

public void addEventlistener(AnimationEventListener listener) {
    eventListeners.add(listener);
}

// When frame changes, check for events:
AnimationFrame frame = animation.getFrame(currentFrame);
for (String event : frame.events()) {
    for (AnimationEventListener listener : eventListeners) {
        listener.onAnimationEvent(event, currentFrame);
    }
}
```

**Pros:** Events are data-driven, reusable, can edit in Animation Editor
**Cons:** More complex, requires Animation Editor UI for editing events

### Recommendation

**Start with Option A** (callbacks) for simplicity. Events can be added to animation data later if needed.

---

## Part 2: Animation Transitions & Blending

### Clarification: What is "Blending"?

**Sprite-based blending is limited.** Unlike skeletal animation (where you interpolate bone transforms), sprite animations can only:

1. **Crossfade** - Fade alpha between two sprites (one fading out, one fading in)
2. **Instant switch** - Jump directly to the new animation

True blending (interpolating between poses) isn't possible with pre-rendered sprites.

### Transition Types

| Type | Description | Use Case |
|------|-------------|----------|
| **Instant** | Switch immediately to new animation | Most common, snappy response |
| **Crossfade** | Alpha blend over duration | Smooth visual transition |
| **Wait for completion** | Play current to end, then switch | Non-interruptible attacks |
| **Wait for loop point** | Switch at next loop boundary | Seamless looping transitions |

### Design: AnimationStateMachine

The state machine manages which animation plays and how transitions occur.

```java
package com.pocket.rpg.animation;

import java.util.*;

/**
 * Manages animation states and transitions for a GameObject.
 * Sits between game logic and AnimationComponent.
 */
public class AnimationStateMachine {

    // States (each maps to an Animation asset path)
    private final Map<String, AnimationState> states = new HashMap<>();
    private String currentState;
    private String pendingState;

    // Transitions
    private final Map<TransitionKey, Transition> transitions = new HashMap<>();

    // Component reference
    private AnimationComponent animComponent;

    // Crossfade state
    private boolean isCrossfading = false;
    private float crossfadeTimer = 0;
    private float crossfadeDuration = 0;
    private Sprite outgoingSprite;

    // ========================================================================
    // STATE REGISTRATION
    // ========================================================================

    /**
     * Registers an animation state.
     * @param name State identifier (e.g., "idle", "walk", "attack")
     * @param animationPath Path to animation asset
     */
    public void addState(String name, String animationPath) {
        states.put(name, new AnimationState(name, animationPath));
    }

    /**
     * Defines a transition between two states.
     * @param from Source state name
     * @param to Target state name
     * @param type How to transition
     * @param duration Duration for crossfade (ignored for instant)
     */
    public void addTransition(String from, String to, TransitionType type, float duration) {
        transitions.put(new TransitionKey(from, to), new Transition(type, duration));
    }

    /**
     * Sets a default transition for any undefined transitions.
     */
    public void setDefaultTransition(TransitionType type, float duration) {
        this.defaultTransition = new Transition(type, duration);
    }

    // ========================================================================
    // STATE CHANGES
    // ========================================================================

    /**
     * Requests a state change. The actual change may be immediate
     * or deferred based on the transition type.
     */
    public void setState(String stateName) {
        if (!states.containsKey(stateName)) {
            System.err.println("[AnimationStateMachine] Unknown state: " + stateName);
            return;
        }

        if (stateName.equals(currentState)) {
            return; // Already in this state
        }

        Transition transition = getTransition(currentState, stateName);

        switch (transition.type()) {
            case INSTANT -> applyStateImmediate(stateName);
            case CROSSFADE -> startCrossfade(stateName, transition.duration());
            case WAIT_FOR_COMPLETION -> pendingState = stateName;
            case WAIT_FOR_LOOP -> pendingState = stateName; // Check at loop point
        }
    }

    /**
     * Forces immediate state change, ignoring transition rules.
     */
    public void forceState(String stateName) {
        applyStateImmediate(stateName);
    }

    private void applyStateImmediate(String stateName) {
        currentState = stateName;
        pendingState = null;
        isCrossfading = false;

        AnimationState state = states.get(stateName);
        animComponent.playAnimation(state.animationPath());
    }

    private void startCrossfade(String stateName, float duration) {
        // Capture current sprite for fade-out
        outgoingSprite = animComponent.getSpriteRenderer().getSprite();

        // Start new animation
        currentState = stateName;
        AnimationState state = states.get(stateName);
        animComponent.playAnimation(state.animationPath());

        // Begin crossfade
        isCrossfading = true;
        crossfadeTimer = 0;
        crossfadeDuration = duration;
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    /**
     * Call each frame to update transitions.
     */
    public void update(float deltaTime) {
        // Handle crossfade
        if (isCrossfading) {
            crossfadeTimer += deltaTime;
            float t = Math.min(1.0f, crossfadeTimer / crossfadeDuration);

            // Apply alpha blend (requires SpriteRenderer support for alpha)
            // Current sprite fades in, outgoing fades out
            animComponent.getSpriteRenderer().setAlpha(t);
            // Would need second sprite renderer for outgoing... see notes below

            if (t >= 1.0f) {
                isCrossfading = false;
                animComponent.getSpriteRenderer().setAlpha(1.0f);
            }
        }

        // Handle pending state (wait for completion)
        if (pendingState != null && animComponent.isFinished()) {
            applyStateImmediate(pendingState);
        }
    }

    // ========================================================================
    // QUERIES
    // ========================================================================

    public String getCurrentState() {
        return currentState;
    }

    public boolean isInState(String stateName) {
        return stateName.equals(currentState);
    }

    public boolean isTransitioning() {
        return isCrossfading || pendingState != null;
    }

    // ========================================================================
    // INTERNAL
    // ========================================================================

    private Transition getTransition(String from, String to) {
        Transition t = transitions.get(new TransitionKey(from, to));
        return t != null ? t : defaultTransition;
    }

    private Transition defaultTransition = new Transition(TransitionType.INSTANT, 0);

    // Records
    private record AnimationState(String name, String animationPath) {}
    private record TransitionKey(String from, String to) {}
    private record Transition(TransitionType type, float duration) {}
}

public enum TransitionType {
    INSTANT,
    CROSSFADE,
    WAIT_FOR_COMPLETION,
    WAIT_FOR_LOOP
}
```

### Crossfade Implementation Note

True crossfade requires rendering TWO sprites simultaneously (old fading out, new fading in). Options:

1. **Dual SpriteRenderer** - AnimationComponent manages a secondary overlay sprite
2. **Shader-based** - Custom shader that blends two textures
3. **Skip crossfade** - Just use instant for sprite animations (most common approach)

**Recommendation:** Start with `INSTANT` and `WAIT_FOR_COMPLETION`. Add crossfade later if needed.

### Usage Example

```java
// Setup state machine
AnimationStateMachine stateMachine = new AnimationStateMachine();
stateMachine.setAnimationComponent(player.getComponent(AnimationComponent.class));

// Register states
stateMachine.addState("idle", "animations/player_idle.anim");
stateMachine.addState("walk", "animations/player_walk.anim");
stateMachine.addState("attack", "animations/player_attack.anim");

// Define transitions
stateMachine.addTransition("idle", "walk", TransitionType.INSTANT, 0);
stateMachine.addTransition("walk", "idle", TransitionType.INSTANT, 0);
stateMachine.addTransition("*", "attack", TransitionType.INSTANT, 0);
stateMachine.addTransition("attack", "*", TransitionType.WAIT_FOR_COMPLETION, 0);

// Set initial state
stateMachine.setState("idle");

// In game logic:
if (Input.isActionPressed("MOVE_RIGHT")) {
    stateMachine.setState("walk");
} else if (Input.isActionJustPressed("ATTACK")) {
    stateMachine.setState("attack");
} else if (!Input.isActionPressed("MOVE_RIGHT")) {
    stateMachine.setState("idle");
}

// In update:
stateMachine.update(deltaTime);
```

---

## Part 3: AnimationStateMachineComponent

Wrap the state machine as a component for inspector editing.

```java
package com.pocket.rpg.components;

import com.pocket.rpg.components.animations.AnimationComponent;

/**
 * Component wrapper for AnimationStateMachine.
 * Allows visual editing of states and transitions in the inspector.
 */
public class AnimationStateMachineComponent extends Component {

    // Serialized state configuration
    private List<StateConfig> states = new ArrayList<>();
    private List<TransitionConfig> transitions = new ArrayList<>();
    private String initialState;

    // Runtime
    @HideInInspector
    private transient AnimationStateMachine stateMachine;

    @ComponentRef
    private AnimationComponent animationComponent;

    @Override
    protected void onStart() {
        stateMachine = new AnimationStateMachine();
        stateMachine.setAnimationComponent(animationComponent);

        for (StateConfig state : states) {
            stateMachine.addState(state.name, state.animationPath);
        }

        for (TransitionConfig t : transitions) {
            stateMachine.addTransition(t.from, t.to, t.type, t.duration);
        }

        if (initialState != null) {
            stateMachine.setState(initialState);
        }
    }

    @Override
    public void update(float deltaTime) {
        if (stateMachine != null) {
            stateMachine.update(deltaTime);
        }
    }

    // API for game code
    public void setState(String stateName) {
        if (stateMachine != null) {
            stateMachine.setState(stateName);
        }
    }

    public String getCurrentState() {
        return stateMachine != null ? stateMachine.getCurrentState() : null;
    }

    // Config records for serialization
    public record StateConfig(String name, String animationPath) {
    }

    public record TransitionConfig(String from, String to, TransitionType type, float duration) {
    }
}
```

---

## Implementation Phases

### Phase 6.1: Basic Events
1. Add `onComplete`, `onFrameChanged`, `onLoop` callbacks to `AnimationComponent`
2. Fire callbacks at appropriate points in `update()`
3. Add helper method: `playAndThen(Animation, Runnable onComplete)`

### Phase 6.2: Animation State Machine (Core)
1. Create `AnimationStateMachine` class
2. Implement `INSTANT` and `WAIT_FOR_COMPLETION` transitions
3. Create `TransitionType` enum
4. Unit tests for state transitions

### Phase 6.3: AnimationStateMachineComponent
1. Create component wrapper
2. Serialization support for states/transitions
3. Inspector rendering for state configuration
4. Test in editor

### Phase 6.4: Crossfade (Optional)
1. Research dual-sprite rendering approach
2. Add alpha support to SpriteRenderer if missing
3. Implement `CROSSFADE` transition type
4. Test visual blending

### Phase 6.5: Editor Tooling (Optional)
1. Visual state machine editor (node graph)
2. Transition preview in editor
3. Frame event editor in Animation Editor panel

---

## Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `AnimationComponent.java` | Modify | Add event callbacks |
| `AnimationStateMachine.java` | Create | State machine logic |
| `TransitionType.java` | Create | Enum for transition types |
| `AnimationStateMachineComponent.java` | Create | Component wrapper |
| `Animation.java` | Modify (optional) | Frame events in data |
| `AnimationFrame.java` | Modify (optional) | Add events list |

---

## Open Questions

1. **Should AnimationStateMachine be a Component or standalone?**
   - Component: Easy inspector editing, follows existing patterns
   - Standalone: More flexible, can be used outside ECS

2. **Crossfade priority?**
   - Most 2D games use instant transitions
   - Crossfade adds complexity (dual sprites)
   - Could defer to Phase 7+

3. **Frame events in animation data?**
   - Useful for sound/VFX triggers
   - Requires Animation Editor UI changes
   - Could start with code-only callbacks

4. **State machine editor?**
   - Node-based visual editor is complex
   - Could use simple list-based UI first
   - Full graph editor as separate phase
