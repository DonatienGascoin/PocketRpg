# Animation System Design Analysis

Analysis of `animation-system-design.md` against existing PocketRpg architecture.

**Date:** January 2026
**Status:** Analysis complete, implementation pending

---

## Executive Summary

The animation system design document provides a solid foundation but requires adjustments to align with existing codebase patterns. Key issues identified:

1. **Loader interface mismatch** - Design's `AssetLoader` doesn't match existing API
2. **Missing serialization separation** - No distinction between JSON format and runtime data
3. **Incomplete integration** - Doesn't leverage existing `SpriteReference` utilities
4. **Existing component ignored** - `FrameAnimationComponent` needs deprecation plan

---

## What's Good in the Design

| Aspect | Assessment |
|--------|------------|
| Asset-based approach | Correct - animations as reusable `.anim.json` files |
| Component separation | Proper - Animation (data) vs AnimationComponent (behavior) |
| State management | Well thought out - STOPPED, PLAYING, PAUSED, FINISHED |
| Editor panel concept | Good workflow - visual timeline editor |
| Frame structure | Flexible - per-frame duration |
| Integration awareness | Good - mentions SpriteSheet, AssetManager, Component lifecycle |

---

## Issues & Recommended Changes

### 1. Loader Pattern Mismatch

**Problem:** Design shows simplified `AssetLoader` interface:
```java
// Design's version (incomplete)
public interface AssetLoader<T> {
    T load(String path);
    void save(T resource, String path);
    String[] getSupportedExtensions();
}
```

**Actual codebase interface includes:**
- `getPlaceholder()` - fallback for load failures
- `supportsHotReload()`, `reload()` - development iteration
- `canInstantiate()`, `instantiate()` - editor drag-to-scene
- `getPreviewSprite()`, `getIconCodepoint()` - asset browser display
- `getSubAsset()` - composite asset support

**Recommendation:** Use `GenericJSONLoader` OR implement full interface:

```java
// Option A: Quick setup
manager.registerLoader(Animation.class, new GenericJSONLoader<>(
    Animation::fromJSON,
    Animation::toJSON,
    new String[]{".anim", ".anim.json"}
));

// Option B: Full loader with editor support (recommended)
public class AnimationLoader implements AssetLoader<Animation> {
    // All methods including canInstantiate(), getPreviewSprite()
}
```

**Reference files:**
- `AssetLoader.java` - interface definition
- `GenericJSONLoader.java` - pattern for JSON assets
- `SpriteSheetLoader.java` - example with full editor integration

---

### 2. Runtime/Serialization Separation

**Problem:** Design doesn't separate JSON format from runtime data.

**Existing pattern (from `SpriteSheet`):**
- JSON stores paths as strings
- Runtime caches resolved `Sprite` objects lazily
- Uses `transient` for non-serialized state

**Recommended AnimationFrame structure:**

```java
// Serialization-friendly record
public record AnimationFrame(String spritePath, float duration) {}

// In Animation class
private List<AnimationFrame> frames;
private transient List<Sprite> cachedSprites; // Lazy-loaded

public Sprite getFrameSprite(int index) {
    if (cachedSprites == null) {
        cachedSprites = frames.stream()
            .map(f -> SpriteReference.fromPath(f.spritePath()))
            .toList();
    }
    return cachedSprites.get(index);
}
```

---

### 3. JSON Format Improvement

**Design's format:**
```json
{
  "frames": [
    { "spriteSheet": "sprites/player.spritesheet", "spriteIndex": 0, "duration": 0.1 },
    { "spriteSheet": "sprites/player.spritesheet", "spriteIndex": 1, "duration": 0.1 }
  ]
}
```

**Recommended format (using SpriteReference pattern):**
```json
{
  "name": "player_walk",
  "looping": true,
  "frames": [
    { "sprite": "spritesheets/player.spritesheet#0", "duration": 0.1 },
    { "sprite": "spritesheets/player.spritesheet#1", "duration": 0.1 },
    { "sprite": "spritesheets/player.spritesheet#2", "duration": 0.15 }
  ]
}
```

**Benefits:**
- Uses existing `SpriteReference.fromPath()` for resolution
- Supports both spritesheet sprites (`sheet.spritesheet#3`) and direct sprites (`sprite.png`)
- More flexible - can mix sources per frame
- Cleaner, more readable

**Note:** No `speed` field in asset - speed is controlled per-instance via AnimationComponent.

---

### 4. AnimationComponent Serialization

**Problem:** Design doesn't show proper scene serialization.

**Recommended structure:**

```java
public class AnimationComponent extends Component {
    // Serialized fields
    private String animationPath;  // Stored in scene JSON
    private boolean autoPlay = true;
    private float speed = 1.0f;

    // Runtime state - not serialized
    @HideInInspector private Animation animation;
    @HideInInspector private int currentFrame = 0;
    @HideInInspector private float timer = 0;
    @HideInInspector private AnimationState state = AnimationState.STOPPED;

    // Auto-resolved - not serialized
    @ComponentRef
    private SpriteRenderer spriteRenderer;

    @Override
    protected void onStart() {
        if (animationPath != null && !animationPath.isEmpty()) {
            animation = Assets.load(animationPath, Animation.class);
        }
        if (autoPlay && animation != null) {
            play();
        }
    }
}
```

**Scene serialization result:**
```json
{
  "type": "com.pocket.rpg.components.animations.AnimationComponent",
  "properties": {
    "animationPath": "animations/player_walk.anim",
    "autoPlay": true,
    "speed": 1.0
  }
}
```

---

### 5. Existing FrameAnimationComponent

**Current implementation (`FrameAnimationComponent.java`) limitations:**
- Stores `List<Sprite>` directly (serialization issues)
- Fixed `frameTime` for all frames (no per-frame duration)
- No play/pause/stop controls
- No looping toggle
- Fetches SpriteRenderer every frame via `getComponent()` (inefficient)

**Recommendation:**
1. Mark `FrameAnimationComponent` as `@Deprecated`
2. New `AnimationComponent` replaces it entirely
3. Add migration notes in deprecation comment

---

### 6. Editor Integration

**Add to AnimationLoader:**

```java
@Override
public boolean canInstantiate() {
    return true;  // Allow drag-to-scene
}

@Override
public EditorGameObject instantiate(Animation asset, String assetPath, Vector3f position) {
    EditorGameObject entity = new EditorGameObject(asset.getName(), position, false);

    SpriteRenderer renderer = new SpriteRenderer();
    if (asset.getFrameCount() > 0) {
        renderer.setSprite(asset.getFrameSprite(0));
    }
    entity.addComponent(renderer);

    AnimationComponent anim = new AnimationComponent();
    anim.setAnimationPath(assetPath);
    entity.addComponent(anim);

    return entity;
}

@Override
public Sprite getPreviewSprite(Animation asset) {
    return asset.getFrameCount() > 0 ? asset.getFrameSprite(0) : null;
}
```

---

### 7. Hot Reload Support

**Add to Animation class:**

```java
public void invalidateCache() {
    cachedSprites = null;  // Force re-resolution on next access
}
```

**In AnimationLoader.reload():**
```java
@Override
public Animation reload(Animation existing, String path) throws IOException {
    existing.invalidateCache();
    Animation reloaded = load(path);
    existing.copyFrom(reloaded);
    return existing;
}
```

---

## File Structure

```
src/main/java/com/pocket/rpg/
├── resources/
│   ├── Animation.java              # Data class
│   └── loaders/
│       └── AnimationLoader.java    # Asset loader
├── components/
│   ├── AnimationComponent.java     # New playback component
│   └── FrameAnimationComponent.java # Mark @Deprecated
└── editor/
    └── panels/
        └── AnimationEditorPanel.java  # Phase 3-4 of design
```

---

## Implementation Order

| Phase | Task | Notes |
|-------|------|-------|
| 1 | `Animation.java` | Data class with `fromJSON`/`toJSON`, lazy sprite caching |
| 2 | `AnimationLoader.java` | Full `AssetLoader` with editor support |
| 3 | Register in `AssetManager` | Add to `registerDefaultLoaders()` |
| 4 | `AnimationComponent.java` | New component with proper serialization |
| 5 | Deprecate `FrameAnimationComponent` | Add `@Deprecated` annotation |
| 6 | `AnimationEditorPanel` | Editor UI (can defer) |

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Speed field location | Component-only | Each instance controls its own playback speed |
| Frame events | Deferred to Phase 5 | Keep initial implementation focused |
| Animation asset format | Single `sprite` path per frame | Leverages existing SpriteReference pattern |

---

## Reference Files

| File | Purpose |
|------|---------|
| `AssetLoader.java` | Interface to implement |
| `GenericJSONLoader.java` | Pattern for JSON assets |
| `SpriteSheetLoader.java` | Example with editor integration |
| `SpriteReference.java` | Sprite path utilities |
| `FrameAnimationComponent.java` | Existing implementation to deprecate |
| `Component.java` | Base class lifecycle |
| `ComponentRef.java` | Auto-resolution annotation |
| `HideInInspector.java` | Exclude from editor |
