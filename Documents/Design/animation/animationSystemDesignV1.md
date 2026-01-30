# Animation System Design V1

A sprite-based frame animation system for PocketRPG.

**Version:** 1.0
**Status:** Ready for implementation

---

## Overview

The animation system provides:
- JSON-based animation definitions stored in `gameData/assets/animations/`
- Component-based playback via `AnimationComponent`
- Visual editor panel for creating and editing animations
- Full integration with existing asset, serialization, and editor systems

## Goals

1. **Simple workflow** - Designers create animations from existing SpriteSheets without code
2. **Reusable** - Animations are assets that can be shared across multiple GameObjects
3. **Editor-friendly** - Visual timeline editor with playback preview
4. **Performant** - Uses existing sprite batching, minimal overhead
5. **Consistent** - Follows existing codebase patterns for assets, components, and serialization

---

## Architecture

### File Structure

```
src/main/java/com/pocket/rpg/
├── animation/
│   ├── Animation.java              # Data class (asset)
│   └── AnimationFrame.java         # Frame record
├── resources/loaders/
│   └── AnimationLoader.java        # AssetLoader implementation
├── components/
│   └── AnimationComponent.java     # Playback component
└── editor/panels/
    └── AnimationEditorPanel.java   # Editor UI (Phase 3+)

gameData/assets/animations/
└── *.anim.json                     # Animation asset files
```

**Note:** `FrameAnimationComponent.java` will be deleted (not deprecated).

---

## Core Classes

### AnimationFrame

Immutable record representing a single frame.

**Supported sprite sources:**
- **Spritesheet sprites:** `spritesheets/player.spritesheet#0` (index into spritesheet)
- **Direct image files:** `sprites/player.png`, `sprites/icon.jpg` (any image format supported by SpriteLoader: `.png`, `.jpg`, `.jpeg`, `.bmp`, `.tga`)

Both formats work interchangeably in animations.

```java
package com.pocket.rpg.animation;

/**
 * Single frame in an animation sequence.
 * Uses sprite path format compatible with SpriteReference.
 *
 * @param spritePath Path to sprite (e.g., "spritesheets/player.spritesheet#0" or "sprites/icon.png")
 * @param duration Time in seconds to display this frame
 */
public record AnimationFrame(String spritePath, float duration) {

    public AnimationFrame {
        if (spritePath == null || spritePath.isBlank()) {
            throw new IllegalArgumentException("spritePath is required");
        }
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }
}
```

### Animation

Pure data class representing an animation asset. No serialization logic here.

```java
package com.pocket.rpg.animation;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.SpriteReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Animation asset containing a sequence of frames.
 * Frames are stored as paths for serialization, sprites resolved lazily at runtime.
 */
public class Animation {

    private String name;
    private boolean looping;
    private List<AnimationFrame> frames = new ArrayList<>();

    // Runtime cache - not serialized
    private transient List<Sprite> cachedSprites;

    public Animation() {
        this.looping = true; // Default, but required in JSON
    }

    public Animation(String name) {
        this();
        this.name = name;
    }

    // ========================================================================
    // FRAME ACCESS
    // ========================================================================

    public int getFrameCount() {
        return frames.size();
    }

    public AnimationFrame getFrame(int index) {
        return frames.get(index);
    }

    public List<AnimationFrame> getFrames() {
        return Collections.unmodifiableList(frames);
    }

    /**
     * Gets the resolved Sprite for a frame index.
     * Sprites are lazily loaded and cached on first access.
     */
    public Sprite getFrameSprite(int index) {
        ensureSpritesResolved();
        return cachedSprites.get(index);
    }

    private void ensureSpritesResolved() {
        if (cachedSprites == null) {
            cachedSprites = new ArrayList<>(frames.size());
            for (AnimationFrame frame : frames) {
                Sprite sprite = SpriteReference.fromPath(frame.spritePath());
                cachedSprites.add(sprite);
            }
        }
    }

    /**
     * Invalidates the sprite cache. Called on hot reload.
     */
    public void invalidateCache() {
        cachedSprites = null;
    }

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isLooping() { return looping; }
    public void setLooping(boolean looping) { this.looping = looping; }

    /**
     * Calculates total duration of all frames.
     */
    public float getTotalDuration() {
        float total = 0;
        for (AnimationFrame frame : frames) {
            total += frame.duration();
        }
        return total;
    }

    // ========================================================================
    // FRAME MANIPULATION (for editor)
    // ========================================================================

    public void addFrame(AnimationFrame frame) {
        frames.add(frame);
        invalidateCache();
    }

    public void addFrame(int index, AnimationFrame frame) {
        frames.add(index, frame);
        invalidateCache();
    }

    public void removeFrame(int index) {
        frames.remove(index);
        invalidateCache();
    }

    public void setFrame(int index, AnimationFrame frame) {
        frames.set(index, frame);
        invalidateCache();
    }

    public void moveFrame(int fromIndex, int toIndex) {
        AnimationFrame frame = frames.remove(fromIndex);
        frames.add(toIndex, frame);
        invalidateCache();
    }

    public void clearFrames() {
        frames.clear();
        invalidateCache();
    }

    public void setFrames(List<AnimationFrame> frames) {
        this.frames.clear();
        this.frames.addAll(frames);
        invalidateCache();
    }

    /**
     * Copies data from another animation (for hot reload).
     */
    public void copyFrom(Animation other) {
        this.name = other.name;
        this.looping = other.looping;
        this.frames.clear();
        this.frames.addAll(other.frames);
        invalidateCache();
    }
}
```

### AnimationLoader

Full `AssetLoader` implementation. Handles all JSON serialization.

```java
package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.components.AnimationComponent;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.AssetLoader;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Asset loader for Animation files (.anim, .anim.json).
 * Handles JSON serialization and editor integration.
 */
public class AnimationLoader implements AssetLoader<Animation> {

    private static final String[] EXTENSIONS = {".anim", ".anim.json"};
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static Animation placeholder;

    // ========================================================================
    // LOADING
    // ========================================================================

    @Override
    public Animation load(String path) throws IOException {
        try {
            String jsonContent = Files.readString(Paths.get(path));
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            return fromJSON(json, path);
        } catch (Exception e) {
            throw new IOException("Failed to load animation: " + path, e);
        }
    }

    private Animation fromJSON(JsonObject json, String path) throws IOException {
        Animation anim = new Animation();

        // Required: name
        if (!json.has("name") || json.get("name").isJsonNull()) {
            throw new IOException("Animation missing required field 'name': " + path);
        }
        anim.setName(json.get("name").getAsString());

        // Required: looping
        if (!json.has("looping")) {
            throw new IOException("Animation missing required field 'looping': " + path);
        }
        anim.setLooping(json.get("looping").getAsBoolean());

        // Required: frames (must have at least one)
        if (!json.has("frames") || !json.get("frames").isJsonArray()) {
            throw new IOException("Animation missing required field 'frames': " + path);
        }

        JsonArray framesArray = json.getAsJsonArray("frames");
        if (framesArray.isEmpty()) {
            throw new IOException("Animation must have at least one frame: " + path);
        }

        for (int i = 0; i < framesArray.size(); i++) {
            JsonObject frameJson = framesArray.get(i).getAsJsonObject();

            // Required: sprite
            if (!frameJson.has("sprite")) {
                throw new IOException("Frame " + i + " missing required field 'sprite': " + path);
            }
            String spritePath = frameJson.get("sprite").getAsString();

            // Required: duration
            if (!frameJson.has("duration")) {
                throw new IOException("Frame " + i + " missing required field 'duration': " + path);
            }
            float duration = frameJson.get("duration").getAsFloat();

            anim.addFrame(new AnimationFrame(spritePath, duration));
        }

        return anim;
    }

    // ========================================================================
    // SAVING
    // ========================================================================

    @Override
    public void save(Animation animation, String path) throws IOException {
        try {
            JsonObject json = toJSON(animation);
            String jsonString = gson.toJson(json);

            Path filePath = Paths.get(path);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(filePath, jsonString);
        } catch (Exception e) {
            throw new IOException("Failed to save animation: " + path, e);
        }
    }

    private JsonObject toJSON(Animation anim) {
        JsonObject json = new JsonObject();

        json.addProperty("name", anim.getName());
        json.addProperty("looping", anim.isLooping());

        JsonArray framesArray = new JsonArray();
        for (AnimationFrame frame : anim.getFrames()) {
            JsonObject frameJson = new JsonObject();
            frameJson.addProperty("sprite", frame.spritePath());
            frameJson.addProperty("duration", frame.duration());
            framesArray.add(frameJson);
        }
        json.add("frames", framesArray);

        return json;
    }

    // ========================================================================
    // ASSET LOADER INTERFACE
    // ========================================================================

    @Override
    public Animation getPlaceholder() {
        if (placeholder == null) {
            placeholder = new Animation("placeholder");
            placeholder.setLooping(true);
        }
        return placeholder;
    }

    @Override
    public String[] getSupportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    @Override
    public Animation reload(Animation existing, String path) throws IOException {
        existing.invalidateCache();
        Animation reloaded = load(path);
        existing.copyFrom(reloaded);
        return existing;
    }

    // ========================================================================
    // EDITOR INTEGRATION
    // ========================================================================

    @Override
    public boolean canInstantiate() {
        return true;
    }

    @Override
    public EditorGameObject instantiate(Animation asset, String assetPath, Vector3f position) {
        String baseName = asset.getName() != null ? asset.getName() : extractEntityName(assetPath);

        EditorGameObject entity = new EditorGameObject(baseName, position, false);

        // Add SpriteRenderer with first frame
        SpriteRenderer spriteRenderer = new SpriteRenderer();
        if (asset.getFrameCount() > 0) {
            spriteRenderer.setSprite(asset.getFrameSprite(0));
        }
        entity.addComponent(spriteRenderer);

        // Add AnimationComponent
        AnimationComponent animComponent = new AnimationComponent();
        animComponent.setAnimation(asset);
        animComponent.setAutoPlay(true);
        entity.addComponent(animComponent);

        return entity;
    }

    @Override
    public Sprite getPreviewSprite(Animation asset) {
        if (asset != null && asset.getFrameCount() > 0) {
            return asset.getFrameSprite(0);
        }
        return null;
    }

    @Override
    public String getIconCodepoint() {
        return FontAwesomeIcons.Film;
    }

    private String extractEntityName(String path) {
        String name = Paths.get(path).getFileName().toString();
        for (String ext : EXTENSIONS) {
            if (name.endsWith(ext)) {
                return name.substring(0, name.length() - ext.length());
            }
        }
        return name;
    }
}
```

### AnimationComponent

Component for playing animations. Uses `Animation` field directly (serialized as path via `AssetReferenceTypeAdapterFactory`).

```java
package com.pocket.rpg.components;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.HideInInspector;

/**
 * Component that plays animations on a GameObject.
 * Requires a SpriteRenderer component on the same GameObject.
 *
 * The Animation field is automatically serialized as a path string
 * via AssetReferenceTypeAdapterFactory, and can be selected using
 * the asset picker in the inspector.
 */
public class AnimationComponent extends Component {

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /**
     * The animation asset to play.
     * Serialized as path string (e.g., "animations/player_walk.anim")
     * via AssetReferenceTypeAdapterFactory.
     */
    private Animation animation;

    /** Whether to start playing automatically when scene loads */
    private boolean autoPlay = true;

    /** Playback speed multiplier (1.0 = normal speed) */
    private float speed = 1.0f;

    // ========================================================================
    // RUNTIME STATE (not serialized)
    // ========================================================================

    @HideInInspector
    private int currentFrame = 0;

    @HideInInspector
    private float timer = 0;

    @HideInInspector
    private AnimationState state = AnimationState.STOPPED;

    // ========================================================================
    // COMPONENT REFERENCE (auto-resolved, not serialized)
    // ========================================================================

    @ComponentRef
    private SpriteRenderer spriteRenderer;

    // ========================================================================
    // STATE ENUM
    // ========================================================================

    public enum AnimationState {
        STOPPED,    // No animation, timer at 0
        PLAYING,    // Advancing frames
        PAUSED,     // Timer frozen, current frame visible
        FINISHED    // Non-looping animation completed (stays on last frame)
    }

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public AnimationComponent() {}

    public AnimationComponent(Animation animation) {
        this.animation = animation;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    protected void onStart() {
        if (autoPlay && animation != null && animation.getFrameCount() > 0) {
            play();
        }
    }

    @Override
    public void update(float deltaTime) {
        if (state != AnimationState.PLAYING) return;
        if (animation == null || animation.getFrameCount() == 0) return;
        if (spriteRenderer == null) return;

        // Advance timer
        timer += deltaTime * speed;

        AnimationFrame frame = animation.getFrame(currentFrame);

        // Check if frame duration exceeded
        while (timer >= frame.duration()) {
            timer -= frame.duration();
            currentFrame++;

            // Handle end of animation
            if (currentFrame >= animation.getFrameCount()) {
                if (animation.isLooping()) {
                    currentFrame = 0;
                } else {
                    currentFrame = animation.getFrameCount() - 1;
                    state = AnimationState.FINISHED;
                    return;
                }
            }

            frame = animation.getFrame(currentFrame);
        }

        // Update sprite
        spriteRenderer.setSprite(animation.getFrameSprite(currentFrame));
    }

    // ========================================================================
    // PLAYBACK CONTROL
    // ========================================================================

    /**
     * Starts or restarts the animation from the beginning.
     */
    public void play() {
        if (animation == null || animation.getFrameCount() == 0) return;

        currentFrame = 0;
        timer = 0;
        state = AnimationState.PLAYING;

        // Set initial frame
        if (spriteRenderer != null) {
            spriteRenderer.setSprite(animation.getFrameSprite(0));
        }
    }

    /**
     * Plays a different animation.
     */
    public void playAnimation(Animation animation) {
        this.animation = animation;
        play();
    }

    /**
     * Plays an animation by path.
     */
    public void playAnimation(String path) {
        if (path == null || path.isBlank()) {
            stop();
            return;
        }
        this.animation = Assets.load(path, Animation.class);
        play();
    }

    /**
     * Pauses playback at current frame.
     */
    public void pause() {
        if (state == AnimationState.PLAYING) {
            state = AnimationState.PAUSED;
        }
    }

    /**
     * Resumes playback from current position.
     */
    public void resume() {
        if (state == AnimationState.PAUSED) {
            state = AnimationState.PLAYING;
        }
    }

    /**
     * Stops playback and resets to first frame.
     */
    public void stop() {
        currentFrame = 0;
        timer = 0;
        state = AnimationState.STOPPED;

        if (animation != null && animation.getFrameCount() > 0 && spriteRenderer != null) {
            spriteRenderer.setSprite(animation.getFrameSprite(0));
        }
    }

    /**
     * Restarts the animation from the beginning.
     */
    public void restart() {
        play();
    }

    // ========================================================================
    // QUERIES
    // ========================================================================

    public boolean isPlaying() {
        return state == AnimationState.PLAYING;
    }

    public boolean isPaused() {
        return state == AnimationState.PAUSED;
    }

    public boolean isFinished() {
        return state == AnimationState.FINISHED;
    }

    public AnimationState getState() {
        return state;
    }

    public Animation getAnimation() {
        return animation;
    }

    public int getCurrentFrameIndex() {
        return currentFrame;
    }

    public float getTimer() {
        return timer;
    }

    /**
     * Gets normalized progress (0.0 to 1.0) through the animation.
     */
    public float getProgress() {
        if (animation == null || animation.getFrameCount() == 0) return 0;

        float totalDuration = animation.getTotalDuration();
        if (totalDuration <= 0) return 0;

        float elapsed = 0;
        for (int i = 0; i < currentFrame; i++) {
            elapsed += animation.getFrame(i).duration();
        }
        elapsed += timer;

        return Math.min(1.0f, elapsed / totalDuration);
    }

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    public void setAnimation(Animation animation) {
        this.animation = animation;
    }

    public boolean isAutoPlay() {
        return autoPlay;
    }

    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.01f, speed);
    }
}
```

---

## Asset File Format

### JSON Structure (`.anim.json` or `.anim`)

```json
{
  "name": "player_walk",
  "looping": true,
  "frames": [
    { "sprite": "spritesheets/player.spritesheet#0", "duration": 0.1 },
    { "sprite": "spritesheets/player.spritesheet#1", "duration": 0.1 },
    { "sprite": "spritesheets/player.spritesheet#2", "duration": 0.1 },
    { "sprite": "spritesheets/player.spritesheet#3", "duration": 0.15 }
  ]
}
```

### Field Descriptions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | **Yes** | Display name for the animation |
| `looping` | boolean | **Yes** | Whether to loop after last frame |
| `frames` | array | **Yes** | List of animation frames (min 1) |
| `frames[].sprite` | string | **Yes** | Sprite path (supports `sheet.spritesheet#N`) |
| `frames[].duration` | float | **Yes** | Frame duration in seconds |

### Sprite Path Format

Uses existing `SpriteReference` format:
- **Spritesheet sprite:** `"spritesheets/player.spritesheet#0"`
- **Direct sprite:** `"sprites/icon.png"`
- **Mixed:** Frames can reference different sources

---

## Scene Serialization

The `Animation` field is serialized as a path string automatically by `AssetReferenceTypeAdapterFactory`:

```json
{
  "type": "com.pocket.rpg.components.AnimationComponent",
  "properties": {
    "animation": "animations/player_walk.anim",
    "autoPlay": true,
    "speed": 1.0
  }
}
```

---

## Registration

Add to `AssetManager.registerDefaultLoaders()`:

```java
registerLoader(Animation.class, new AnimationLoader());
```

---

## Usage Examples

### In Code

```java
// Create GameObject with animation
GameObject player = new GameObject("Player");
SpriteRenderer renderer = player.addComponent(new SpriteRenderer());
AnimationComponent anim = player.addComponent(new AnimationComponent());

// Set animation (can also be done via inspector)
Animation walkAnim = Assets.load("animations/player_walk.anim", Animation.class);
anim.setAnimation(walkAnim);
anim.play();

// Or play directly by path
anim.playAnimation("animations/player_run.anim");

// Control playback
anim.setSpeed(2.0f);
anim.pause();
anim.resume();
anim.stop();

// Query state
if (anim.isPlaying()) {
    System.out.println("Frame: " + anim.getCurrentFrameIndex());
}
```

### In Inspector

The `Animation` field appears as an asset picker:
- Click to open asset browser filtered to `.anim` files
- Drag-and-drop animation files from asset browser
- **Auto Play** checkbox
- **Speed** slider (0.1 to 5.0)

---

## Implementation Phases

### Phase 1: Core Data Model
1. Create `com.pocket.rpg.animation` package
2. Implement `AnimationFrame` record
3. Implement `Animation` class
4. Implement `AnimationLoader` with JSON serialization
5. Register loader in `AssetManager`
6. Unit tests for serialization round-trip

### Phase 2: Component
1. Implement `AnimationComponent`
2. Delete `FrameAnimationComponent`
3. Test playback in game runtime
4. Test scene serialization (save/load)
5. Unit tests for component behavior

### Phase 3: Editor Panel (Basic)
1. Create `AnimationEditorPanel` with layout including preview area
2. Animation list browser
3. New/Delete/Save functionality
4. Embedded preview panel with playback
5. Frame timeline rendering

### Phase 4: Editor Panel (Advanced)
1. Sprite picker integration
2. Drag-and-drop frame reordering
3. Keyboard shortcuts
4. Preview zoom/fit controls

### Phase 4.5: Editor Enhancements
See `Documents/Design/animation/phase4Point5Enhancements.md` for full details.

**Layout Changes:**
1. Remove File/Edit menu bar (keep toolbar only)
2. Move Properties panel to left (under animation list)
3. Preview takes right side (larger area)

**Visual Improvements:**
1. Play/Stop buttons colored (green/red) with state indication
2. Unsaved animations in yellow with asterisk
3. Timeline: frame numbers, playhead marker, clearer selection
4. Drop zones visible between frames during drag

**Features:**
1. Insert frames between existing frames (not just swap)
2. Right-click frame → "Change Sprite..." option
3. Drag sprite from Asset Browser to timeline to add frame
4. Duration field uses PrimitiveEditors (label on left)

### Phase 5: Editor Integration ✓
**Status:** Complete

1. **Double-click to open animation** ✓
   - Added `EditorPanel` enum to define editor panels for asset types
   - Added `getEditorPanel()` method to `AssetLoader` interface
   - `AnimationLoader` returns `EditorPanel.ANIMATION_EDITOR`
   - `AssetBrowserPanel` queries `Assets.getEditorPanel(type)` on double-click
   - Panel handlers registered via `registerPanelHandler(EditorPanel, Consumer<String>)`
   - `AnimationEditorPanel.selectAnimationByPath()` focuses window and loads animation

2. **Auto-open sprite picker on new frame** ✓
   - Adding frame auto-opens sprite picker immediately
   - Frame created with 1.0s default duration
   - If cancelled, frame remains with empty sprite (can delete manually)

3. **Enhanced New Animation dialog** ✓
   - Name input field
   - Inline sprite picker with preview thumbnail
   - Browse button closes modal, opens picker, re-opens modal with selection
   - Create button disabled until sprite is selected
   - Animation only created after valid sprite selection

**Files changed:**
- `EditorPanel.java` (new) - Enum for editor panels
- `AssetLoader.java` - Added `getEditorPanel()` default method
- `AssetContext.java` - Added `getEditorPanel(Class<?>)` interface method
- `AssetManager.java` - Implemented `getEditorPanel()`
- `Assets.java` - Added static `getEditorPanel()` facade
- `AnimationLoader.java` - Returns `EditorPanel.ANIMATION_EDITOR`
- `AssetBrowserPanel.java` - Panel handler registry, double-click handling
- `AnimationEditorPanel.java` - `selectAnimationByPath()`, sprite picker integration
- `EditorUIController.java` - Registers animation editor handler

### Phase 6: Animation Events & State Machine (Future)
See `Documents/Design/animation/phase6EventsAndStateMachine.md` for detailed design.

---

## Editor Panel Design

### Layout with Embedded Preview

The preview is embedded in the panel itself, not the scene view. This allows editing animations without needing a scene object.

```
┌─────────────────────────────────────────────────────────────────┐
│ Animation Editor                                       [x Close]│
├─────────────────────────────────────────────────────────────────┤
│ [New] [Delete] [Duplicate] [Save]                               │
├───────────────────┬─────────────────────────────────────────────┤
│ Animation List    │  Preview              │ Properties          │
│ ┌───────────────┐ │ ┌───────────────────┐ │ Name: [player_walk] │
│ │ player_walk * │ │ │                   │ │ Looping: [x]        │
│ │ player_idle   │ │ │                   │ │                     │
│ │ player_run    │ │ │      ┌───┐        │ │ Total Duration:     │
│ │ enemy_attack  │ │ │      │ 🖼 │        │ │ 0.45s               │
│ │               │ │ │      └───┘        │ │                     │
│ │               │ │ │                   │ │ Frame Count: 4      │
│ │               │ │ │                   │ │                     │
│ │               │ │ └───────────────────┘ │                     │
│ │               │ │ [0.5x] [Fit]          │                     │
│ └───────────────┘ │ BG: [Checker ▼]       │                     │
├───────────────────┴─────────────────────────────────────────────┤
│ Timeline                                                        │
│ [▶][⏸][■]  Speed: [====1.0====]  Frame 2/4  Time: 0.15s        │
│ ┌────┬────┬────┬────┬────┐                                      │
│ │ 🖼  │ 🖼  │ ▼🖼 │ 🖼  │ +  │   ◄── selected frame marker (▼)   │
│ │0.1s│0.1s│0.1s│0.15│    │                                      │
│ └────┴────┴────┴────┴────┘                                      │
├─────────────────────────────────────────────────────────────────┤
│ Frame Inspector (Frame 3)               │ Sprite Picker         │
│ Duration: [0.10    ] s                  │ Sheet: [player.ss ▼]  │
│ Sprite: spritesheets/player.ss#2       │ ┌─────────────────┐   │
│ [Delete Frame]                          │ │ 🖼 🖼 🖼 🖼 🖼 🖼 🖼 │   │
│                                         │ │ 🖼 🖼 🖼 🖼 🖼 🖼 🖼 │   │
│                                         │ └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Preview Panel Features

| Feature | Description |
|---------|-------------|
| **Embedded preview** | Self-contained, doesn't require scene view |
| **Zoom controls** | 0.5x and Fit - always shows full animation |
| **Background options** | Checker (transparency), solid color picker |
| **Auto-fit** | Centers sprite in available space |
| **Playback sync** | Shows current frame during timeline playback |

### Preview Sizing Strategy

**Reference size:** The maximum sprite dimensions across all frames in the animation. This keeps the preview stable during playback even when frames have different sizes.

```java
// Calculate reference size from all frames (cached, recalculated when frames change)
float refWidth = 0;
float refHeight = 0;
for (int i = 0; i < animation.getFrameCount(); i++) {
    Sprite sprite = animation.getFrameSprite(i);
    refWidth = Math.max(refWidth, sprite.getPixelWidth());
    refHeight = Math.max(refHeight, sprite.getPixelHeight());
}

// Calculate fit scale (how much to scale reference size to fit preview area)
float fitScale = Math.min(
    previewAreaWidth / refWidth,
    previewAreaHeight / refHeight
);

// Apply zoom mode (both relative to fit - always shows full animation)
float scale = switch (zoomMode) {
    case ZOOM_0_5X -> fitScale * 0.5f;  // Half size - overview
    case FIT       -> fitScale;          // Full fit (default)
};

// Calculate display size for current frame's sprite
Sprite currentSprite = animation.getFrameSprite(currentFrame);
float displayWidth = currentSprite.getPixelWidth() * scale;
float displayHeight = currentSprite.getPixelHeight() * scale;

// Center in preview area
float offsetX = (previewAreaWidth - displayWidth) / 2;
float offsetY = (previewAreaHeight - displayHeight) / 2;
```

**Zoom levels:**

| Zoom | Behavior |
|------|----------|
| **0.5x** | Half of fit scale - smaller preview, more breathing room |
| **Fit** | Largest frame fills preview area (default) |

**Note:** When frames have different sizes, smaller sprites appear centered with empty space around them. This shows actual relative sizes between frames.

### Panel Sections

| Section | Purpose |
|---------|---------|
| **Animation List** | Browse/select animations, `*` = unsaved |
| **Preview** | Live animation playback with zoom controls |
| **Properties** | Name, looping, read-only stats |
| **Timeline** | Playback controls, frame strip with thumbnails |
| **Frame Inspector** | Edit selected frame's duration |
| **Sprite Picker** | Select sprite from loaded spritesheets |

---

## Technical Considerations

### Performance
- No allocation during playback (sprites cached on first access)
- Sprite batching handles rendering efficiently
- Animation assets loaded once, shared across instances

### Memory
- Animations are lightweight metadata (~200 bytes + frame list)
- Sprites cached lazily, shared via SpriteSheet

### Error Handling

| Error | Behavior |
|-------|----------|
| Missing SpriteRenderer | Log warning, component does nothing |
| Invalid animation file | Throw IOException with details |
| Invalid sprite path in frame | Skip frame, log warning |
| Zero frames | Throw IOException (frames required) |

---

## Key Reference Files

| File | Purpose |
|------|---------|
| `AssetLoader.java` | Interface to implement |
| `SpriteSheetLoader.java` | Example with editor integration |
| `SpriteReference.java` | Sprite path resolution |
| `AssetReferenceTypeAdapterFactory.java` | Auto-serializes Animation field as path |
| `Component.java` | Base component lifecycle |
| `ComponentRef.java` | Auto-resolution annotation |
| `HideInInspector.java` | Exclude fields from inspector |
