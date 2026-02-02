# Tween System Guide

> **Summary:** The tween system smoothly animates values over time using easing curves. Use it to animate UI elements (position, size, color, opacity) or any arbitrary float/vector value in your game components.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Core Classes](#core-classes)
4. [Workflows](#workflows)
5. [Easing Functions](#easing-functions)
6. [Tips & Best Practices](#tips--best-practices)
7. [Troubleshooting](#troubleshooting)
8. [Code Integration](#code-integration)
9. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Slide a UI element | `Tweens.offsetX(transform, targetX, duration)` |
| Fade in/out | `Tweens.alpha(colorVec, targetAlpha, duration)` |
| Animate any float | `Tweens.value(start, end, duration, setter)` |
| Add easing | `.setEase(Ease.OUT_BACK)` |
| Run something after tween | `.onComplete(() -> { ... })` |
| Delay before starting | `.setDelay(seconds)` |
| Loop a tween | `.setLoops(count)` or `.setLoops(-1)` for infinite |
| Ping-pong loop | `.setLoops(-1).setYoyo(true)` |
| Cancel tweens on a target | `TweenManager.kill(target, false)` |
| Cancel and jump to end | `TweenManager.kill(target, true)` |
| Run action after delay | `Tweens.delayedCall(seconds, () -> { ... })` |

---

## Overview

The tween system lives in `com.pocket.rpg.animation.tween` and consists of four classes:

- **`Tween<T>`** - The core interpolator. Generic over the value type (Float, Vector2f, Vector4f). Tweens auto-register with the manager on construction, so you never need to manually add them.
- **`Tweens`** - Static factory with ready-made tweens for UI transforms, colors, and preset animations. This is your main entry point.
- **`TweenManager`** - Static singleton that updates all active tweens each frame. Call `TweenManager.update(deltaTime)` in your game loop.
- **`Ease`** - Enum of 25 easing functions (linear, quad, cubic, back, bounce, elastic, etc.).

The default easing is `OUT_QUAD` (starts fast, decelerates).

---

## Core Classes

### Tween\<T\> Configuration (Fluent API)

All setters return `this`, so you can chain them:

| Method | Description | Default |
|--------|-------------|---------|
| `.setEase(Ease)` | Easing curve | `OUT_QUAD` |
| `.setDelay(float)` | Seconds to wait before starting | `0` |
| `.setLoops(int)` | Loop count (`0` = none, `-1` = infinite) | `0` |
| `.setYoyo(boolean)` | Reverse direction on each loop | `false` |
| `.onStart(Runnable)` | Called once after delay, when tween begins | none |
| `.onComplete(Runnable)` | Called when tween finishes all loops | none |
| `.onUpdate(Consumer<T>)` | Called each frame with the current interpolated value | none |
| `.setTarget(Object)` | Associate with an object for batch kill/pause | none |
| `.setId(String)` | Unique string ID for targeted kill | none |

### Tween\<T\> Control

| Method | Description |
|--------|-------------|
| `.pause()` | Pause the tween |
| `.resume()` | Resume the tween |
| `.complete()` | Jump to end value, fire `onComplete` |
| `.kill()` | Stop immediately without completing |
| `.getProgress()` | Returns `0.0` to `1.0` |
| `.isCompleted()` | Whether tween has finished |
| `.isPaused()` | Whether tween is paused |

### TweenManager (Static)

| Method | Description |
|--------|-------------|
| `update(float deltaTime)` | Advance all tweens. Call once per frame. |
| `kill(Object target, boolean complete)` | Kill all tweens on a target |
| `kill(String id, boolean complete)` | Kill all tweens with a given ID |
| `killAll(boolean complete)` | Kill every active tween |
| `pause(Object target)` / `resume(Object target)` | Pause/resume tweens by target |
| `pauseAll()` / `resumeAll()` | Pause/resume all tweens |
| `getActiveCount()` | Number of active tweens |
| `hasActiveTweens(Object target)` | Check if target has running tweens |

### Tweens Factory Methods

**Float:**

| Method | Description |
|--------|-------------|
| `value(start, end, duration, setter)` | Tween any float value |

**UITransform position:**

| Method | Description |
|--------|-------------|
| `offset(transform, endVec2, duration)` | Tween X and Y offset |
| `offsetX(transform, endX, duration)` | Tween X offset only |
| `offsetY(transform, endY, duration)` | Tween Y offset only |

**UITransform size:**

| Method | Description |
|--------|-------------|
| `size(transform, endVec2, duration)` | Tween width and height |
| `width(transform, endWidth, duration)` | Tween width only |
| `height(transform, endHeight, duration)` | Tween height only |
| `scale(transform, endScale, duration)` | Uniform scale (multiplier, `1.0` = original) |

**UITransform anchor/pivot:**

| Method | Description |
|--------|-------------|
| `anchor(transform, endVec2, duration)` | Tween anchor point |
| `pivot(transform, endVec2, duration)` | Tween pivot point |

**Color:**

| Method | Description |
|--------|-------------|
| `color(currentVec4, endVec4, duration, setter)` | Tween RGBA color |
| `alpha(colorVec4, endAlpha, duration)` | Tween opacity only |

**Delay/sequence:**

| Method | Description |
|--------|-------------|
| `delay(duration, onComplete)` | Empty tween that waits, then calls callback |
| `delayedCall(delay, action)` | Shorthand for `delay()` |

**Preset animations:**

| Method | Description |
|--------|-------------|
| `punchScale(transform, punch, duration)` | Quick scale up then back to original |
| `shake(transform, intensity, duration)` | Decaying oscillation on offset |
| `slideIn(transform, direction, distance, duration)` | Slide element in from LEFT/RIGHT/TOP/BOTTOM |
| `slideOut(transform, direction, distance, duration)` | Slide element out to a direction |

---

## Workflows

### Creating a Simple Tween

```java
UITransform transform = myPanel.getComponent(UITransform.class);

// Slide panel to X=0 over 0.3 seconds with overshoot
Tweens.offsetX(transform, 0f, 0.3f)
      .setEase(Ease.OUT_BACK);
```

That's it. The tween auto-registers and starts on the next frame.

### Sequencing Tweens

Chain tweens using `onComplete`:

```java
// Slide in, wait 2 seconds, then slide out
Tweens.offsetX(transform, 0f, 0.3f)
      .setEase(Ease.OUT_BACK)
      .onComplete(() -> {
          Tweens.offsetX(transform, 400f, 0.3f)
                .setDelay(2f)
                .setEase(Ease.IN_BACK);
      });
```

Or use `delayedCall` for simple delayed actions:

```java
Tweens.delayedCall(1.5f, () -> {
    showNotification("Ready!");
});
```

### Looping and Yoyo

```java
// Pulse opacity forever: 1.0 -> 0.3 -> 1.0 -> 0.3 ...
Tweens.alpha(iconColor, 0.3f, 0.8f)
      .setEase(Ease.IN_OUT_SINE)
      .setLoops(-1)
      .setYoyo(true);
```

### Tweening Arbitrary Values

Use `Tweens.value()` with a setter lambda for anything:

```java
// Animate a custom field from 0 to 100
Tweens.value(0f, 100f, 1f, val -> {
    myComponent.healthBarWidth = val;
});
```

### Cancelling Tweens

Always kill previous tweens before starting new ones on the same target to avoid conflicts:

```java
// Kill any existing tweens on this transform
TweenManager.kill(transform, false);

// Start a new tween
Tweens.offsetX(transform, 200f, 0.3f)
      .setEase(Ease.OUT_QUAD);
```

### Preset Animations

```java
// Bounce effect on button press
Tweens.punchScale(buttonTransform, 0.2f, 0.3f);

// Shake on error
Tweens.shake(inputTransform, 5f, 0.4f);

// Slide menu in from the left
Tweens.slideIn(menuTransform, Tweens.Direction.LEFT, 300f, 0.4f)
      .setEase(Ease.OUT_BACK);
```

---

## Easing Functions

Visual reference: [easings.net](https://easings.net/)

| Category | In | Out | InOut |
|----------|----|-----|-------|
| **Quad** (gentle) | `IN_QUAD` | `OUT_QUAD` | `IN_OUT_QUAD` |
| **Cubic** (moderate) | `IN_CUBIC` | `OUT_CUBIC` | `IN_OUT_CUBIC` |
| **Quart** (strong) | `IN_QUART` | `OUT_QUART` | `IN_OUT_QUART` |
| **Sine** (smooth) | `IN_SINE` | `OUT_SINE` | `IN_OUT_SINE` |
| **Expo** (dramatic) | `IN_EXPO` | `OUT_EXPO` | `IN_OUT_EXPO` |
| **Back** (overshoot) | `IN_BACK` | `OUT_BACK` | `IN_OUT_BACK` |
| **Bounce** (bouncy) | `IN_BOUNCE` | `OUT_BOUNCE` | `IN_OUT_BOUNCE` |
| **Elastic** (springy) | `IN_ELASTIC` | `OUT_ELASTIC` | `IN_OUT_ELASTIC` |

Plus `LINEAR` for constant speed.

**When to use what:**

- **UI panels sliding in** - `OUT_BACK` (overshoots then settles)
- **UI panels sliding out** - `IN_BACK` (pulls back then exits)
- **Fading** - `OUT_QUAD` or `IN_OUT_SINE` (smooth)
- **Notifications appearing** - `OUT_ELASTIC` (springy pop)
- **Continuous pulsing** - `IN_OUT_SINE` with yoyo

---

## Tips & Best Practices

- **Kill before re-tweening.** If a tween is already running on a target and you start a new one on the same property, both will fight. Call `TweenManager.kill(target, false)` first.
- **Use `.setTarget()`** on custom tweens so you can batch-kill them later. The `Tweens` factory methods for UITransform already set the target automatically.
- **Keep durations short for UI.** 0.15-0.4 seconds feels snappy. Anything above 0.5s feels sluggish.
- **`OUT_*` easings** feel responsive for entrances (fast start, gentle stop). **`IN_*` easings** work for exits (slow start, fast end).
- **Avoid allocations in `onUpdate`.** The callback runs every frame, so don't create new objects inside it.
- **Use `delayedCall` instead of timers.** It integrates cleanly with the tween system's pause/resume and kill-all support.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Tween does nothing | `TweenManager.update()` is called by `GameLoop` automatically. Check that your component's `update()` is actually running (GameObject enabled, scene loaded). |
| Two animations fighting | Kill existing tweens on the target before starting new ones: `TweenManager.kill(target, false)` |
| Tween jumps to end instantly | Duration might be 0 or near 0. Minimum duration is clamped to `0.001f` |
| Yoyo only plays once | `setYoyo(true)` only reverses direction, you also need `setLoops(-1)` or a loop count > 0 |
| Tween continues after object destroyed | Call `TweenManager.kill(target, false)` in your cleanup/destroy logic |
| Color tween not visible | Make sure you're passing the actual `Vector4f` the renderer reads from, not a copy |

---

## Code Integration

### Game Loop Integration

`TweenManager.update(deltaTime)` is called automatically every frame by `GameLoop.update()`, before scene and component updates. You don't need to call it yourself.

### Example: Animated Pause Menu

```java
public class PlayerPauseUI extends Component {
    private boolean isPaused = false;
    private UITransform menuTransform;

    @Override
    public void update(float deltaTime) {
        if (Input.isActionPressed(InputAction.MENU)) {
            isPaused = !isPaused;

            // Kill any in-progress animation
            TweenManager.kill(menuTransform, false);

            if (isPaused) {
                Tweens.slideIn(menuTransform, Tweens.Direction.LEFT, 300f, 0.3f)
                      .setEase(Ease.OUT_BACK);
            } else {
                Tweens.slideOut(menuTransform, Tweens.Direction.LEFT, 300f, 0.2f)
                      .setEase(Ease.IN_QUAD);
            }
        }
    }
}
```

### Example: Custom Tween with Interpolator

For types beyond Float/Vector2f/Vector4f, provide your own interpolator:

```java
// Tween an integer (e.g., score counter)
new Tween<>(
    0,                        // start
    1000,                     // end
    2f,                       // duration (seconds)
    (start, end, t) ->        // interpolator
        (int)(start + (end - start) * t),
    value ->                  // setter
        scoreLabel.setText(String.valueOf(value))
).setEase(Ease.OUT_QUAD);
```

---

## Related

- [Animation Editor Guide](animationEditorGuide.md)
- [Animator Guide](animatorGuide.md)
- [Components Guide](componentsGuide.md)
- [UI Designer Guide](uiDesignerGuide.md)
