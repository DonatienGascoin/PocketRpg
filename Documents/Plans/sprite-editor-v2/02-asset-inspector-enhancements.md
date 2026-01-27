# Asset Inspector Enhancements Plan

**Status: COMPLETE**

**Can be done in parallel with other plans (no hard dependencies)**

## Overview

Enhance the Asset Inspector to show editable fields for assets that have metadata, not just a preview. Each asset type can have a custom inspector view with:
- Preview (existing)
- Editable fields (new)
- Action buttons (new) - e.g., "Open Sprite Editor"

---

## Current State

```
┌─ Inspector ─────────────────────────────┐
│ 🖼 player.png                           │
│ ─────────────────────────────────────── │
│ Type: Sprite                            │
│ Path: spritesheets/player.png           │
│ ─────────────────────────────────────── │
│ Preview                                 │
│ ─────────────────────────────────────── │
│ ┌─────────────────────┐                 │
│ │                     │                 │
│ │      [Preview]      │                 │
│ │                     │                 │
│ └─────────────────────┘                 │
│                                         │
│         (No editable fields)            │
│         (No action buttons)             │
└─────────────────────────────────────────┘
```

---

## Proposed State

### Sprite Inspector (Single Mode)

```
┌─ Inspector ─────────────────────────────┐
│ 🖼 icon.png                             │
│ ─────────────────────────────────────── │
│ Type: Sprite                            │
│ Path: sprites/icon.png                  │
│ ─────────────────────────────────────── │
│ Mode: ● Single  ○ Multiple              │  ← MODE SWITCHER
│ ─────────────────────────────────────── │
│ Preview                                 │
│ ┌─────────────────────┐                 │
│ │         ⊕          │  ← pivot shown   │
│ └─────────────────────┘                 │
│ ─────────────────────────────────────── │
│ Properties                              │
│ ─────────────────────────────────────── │
│ Size:        32 x 32 px                 │
│ Pivot:       [0.50] x [0.00]   [⊕]     │
│ Pixels/Unit: [16____]                   │
│ ─────────────────────────────────────── │
│ [📝 Open Sprite Editor...]              │
│ ─────────────────────────────────────── │
│             [💾 Save]                   │
└─────────────────────────────────────────┘
```

### Sprite Inspector (Multiple Mode)

```
┌─ Inspector ─────────────────────────────┐
│ 🖼 player.png                           │
│ ─────────────────────────────────────── │
│ Type: Sprite                            │
│ Path: spritesheets/player.png           │
│ ─────────────────────────────────────── │
│ Mode: ○ Single  ● Multiple              │  ← MODE SWITCHER
│ ─────────────────────────────────────── │
│ Preview                                 │
│ ┌──┬──┬──┬──┐                           │
│ │  │  │  │  │   Grid: 4x4 = 16 sprites  │
│ ├──┼──┼──┼──┤                           │
│ │  │  │  │  │                           │
│ └──┴──┴──┴──┘                           │
│ ─────────────────────────────────────── │
│ Properties                              │
│ ─────────────────────────────────────── │
│ Texture:     64 x 64 px                 │
│ Sprite Size: 16 x 16 px                 │
│ Sprites:     16                         │
│ Default Pivot: [0.50] x [0.00]          │
│ ─────────────────────────────────────── │
│ [📝 Open Sprite Editor...]              │
│ ─────────────────────────────────────── │
│             [💾 Save]                   │
└─────────────────────────────────────────┘
```

### Mode Switching Behavior

**Single → Multiple:**
1. User clicks "Multiple" radio button
2. Dialog appears: "Configure grid for multiple mode"
3. Quick grid setup: sprite size (with presets 8/16/32)
4. Shows preview of resulting grid
5. On confirm: creates metadata with `spriteMode: multiple`

**Multiple → Single:**
1. User clicks "Single" radio button
2. Warning dialog: "This will discard grid settings and per-sprite data"
3. On confirm: converts to `spriteMode: single`, keeps sprite 0's pivot as default

### Animation Inspector

```
┌─ Inspector ─────────────────────────────┐
│ 🎬 walk_down.anim.json                  │
│ ─────────────────────────────────────── │
│ Type: Animation                         │
│ Path: animations/walk_down.anim.json    │
│ ─────────────────────────────────────── │
│ Preview                                 │
│ ┌─────────────────────┐                 │
│ │                     │                 │
│ │    [Animated]       │  ← plays anim   │
│ │                     │                 │
│ └─────────────────────┘                 │
│       [▶ Play] [⏹ Stop]                 │
│ ─────────────────────────────────────── │
│ Properties                              │
│ ─────────────────────────────────────── │
│ Frames:      4                          │
│ Duration:    0.5s (8 FPS)               │
│ Loop:        ☑                          │
│ ─────────────────────────────────────── │
│ [📝 Open Animation Editor...]           │
└─────────────────────────────────────────┘
```

### AudioClip Inspector (existing, for reference)

```
┌─ Inspector ─────────────────────────────┐
│ 🔊 footstep.ogg                         │
│ ─────────────────────────────────────── │
│ Type: AudioClip                         │
│ Path: audio/sfx/footstep.ogg            │
│ ─────────────────────────────────────── │
│ Preview                                 │
│ ┌─────────────────────┐                 │
│ │ ▶ Play   ⏹ Stop    │                 │
│ └─────────────────────┘                 │
│ ─────────────────────────────────────── │
│ Properties                              │
│ ─────────────────────────────────────── │
│ Duration:    0.3s                       │
│ Sample Rate: 44100 Hz                   │
│ Channels:    Stereo                     │
└─────────────────────────────────────────┘
```

---

## Architecture

### New Interface: AssetInspectorRenderer

```java
/**
 * Renders inspector UI for a specific asset type.
 * Provides both preview and editable properties.
 */
public interface AssetInspectorRenderer<T> {

    /**
     * Renders the full inspector for this asset.
     * @param asset The asset to inspect
     * @param assetPath Path to the asset (for saving)
     * @param maxPreviewSize Maximum size for preview
     * @return true if changes were made that need saving
     */
    boolean render(T asset, String assetPath, float maxPreviewSize);

    /**
     * Returns true if this asset type has editable properties.
     */
    default boolean hasEditableProperties() {
        return false;
    }

    /**
     * Saves any pending changes.
     * @param asset The asset
     * @param assetPath Path to save to
     */
    default void save(T asset, String assetPath) {
        // Default: no-op
    }
}
```

### New Registry: AssetInspectorRegistry

```java
/**
 * Central registry for asset inspector renderers.
 * Similar to AssetPreviewRegistry but for full inspectors.
 */
public final class AssetInspectorRegistry {

    private static final Map<Class<?>, AssetInspectorRenderer<?>> renderers = new HashMap<>();
    private static final DefaultAssetInspectorRenderer defaultRenderer = new DefaultAssetInspectorRenderer();

    static {
        register(Sprite.class, new SpriteInspectorRenderer());
        register(Animation.class, new AnimationInspectorRenderer());
        // AudioClip already has preview, could add inspector
    }

    public static <T> void register(Class<T> type, AssetInspectorRenderer<T> renderer);
    public static boolean render(Object asset, String path, float maxPreviewSize);
    public static boolean hasEditableProperties(Class<?> type);
    public static void save(Object asset, String path);
}
```

### Updated AssetInspector

```java
public class AssetInspector {

    private String assetPath;
    private Class<?> assetType;
    private Object cachedAsset;
    private boolean hasUnsavedChanges;

    public void render() {
        // Header (existing)
        renderHeader();

        // Use registry for type-specific inspector
        if (cachedAsset != null) {
            hasUnsavedChanges = AssetInspectorRegistry.render(
                cachedAsset, assetPath, PREVIEW_MAX_SIZE);
        }

        // Save button (if has editable properties)
        if (AssetInspectorRegistry.hasEditableProperties(assetType)) {
            renderSaveButton();
        }
    }

    private void renderSaveButton() {
        if (hasUnsavedChanges) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
        }
        if (ImGui.button("💾 Save")) {
            AssetInspectorRegistry.save(cachedAsset, assetPath);
            hasUnsavedChanges = false;
        }
        if (hasUnsavedChanges) {
            ImGui.popStyleColor();
        }
    }
}
```

---

## Implementation Phases

### Phase 1: Inspector Framework

Create the base framework for type-specific inspectors.

**Files:**

| File | Change |
|------|--------|
| `inspector/AssetInspectorRenderer.java` | **NEW** - Interface for asset inspectors |
| `inspector/AssetInspectorRegistry.java` | **NEW** - Registry for inspector renderers |
| `inspector/DefaultAssetInspectorRenderer.java` | **NEW** - Fallback using AssetPreviewRegistry |
| `inspector/AssetInspector.java` | Update to use registry |

**Tasks:**
- [x] Create `AssetInspectorRenderer` interface
- [x] Create `AssetInspectorRegistry` with registration and lookup
- [x] Create `DefaultAssetInspectorRenderer` that delegates to `AssetPreviewRegistry`
- [x] Update `AssetInspector` to use the registry
- [x] Add save button logic with unsaved changes tracking

---

### Phase 2: Sprite Inspector

Create the sprite-specific inspector with mode switching.

**Files:**

| File | Change |
|------|--------|
| `inspector/SpriteInspectorRenderer.java` | **NEW** - Sprite inspector with pivot editing and mode switching |
| `inspector/GridConfigDialog.java` | **NEW** - Quick dialog for configuring grid when switching to Multiple |

**Tasks:**
- [x] Create `SpriteInspectorRenderer` class
- [x] Show sprite preview with pivot marker
- [x] Show read-only info: size
- [x] Show editable fields: pivot X/Y, pixelsPerUnit
- [x] Add pivot preset grid (3x3)
- [x] Add "Open Sprite Editor..." button (via event bus)
- [x] Implement save functionality (updates `.meta` file)
- [x] Handle both Single and Multiple mode sprites
- [x] For Multiple mode: show grid preview, sprite count, default pivot
- [ ] ~~Add mode switcher (Single ↔ Multiple radio buttons)~~ - Deferred (use Sprite Editor for mode changes)
- [ ] ~~Create `GridConfigDialog` for Single → Multiple conversion~~ - Deferred
- [ ] ~~Implement Multiple → Single with confirmation dialog~~ - Deferred
- [ ] ~~Update TilesetRegistry when mode changes~~ - Deferred

**Mode Switcher Implementation:**

```java
// In SpriteInspectorRenderer
private void renderModeSwitcher(SpriteMetadata metadata, String assetPath) {
    boolean isSingle = metadata.spriteMode == SpriteMode.SINGLE;

    ImGui.text("Mode:");
    ImGui.sameLine();

    if (ImGui.radioButton("Single", isSingle)) {
        if (!isSingle) {
            // Multiple → Single: show warning
            showConvertToSingleDialog = true;
        }
    }
    ImGui.sameLine();
    if (ImGui.radioButton("Multiple", !isSingle)) {
        if (isSingle) {
            // Single → Multiple: show grid config
            showGridConfigDialog = true;
        }
    }
}
```

**GridConfigDialog:**

```
┌─────────────────────────────────────────┐
│     Configure Grid (Multiple Mode)      │
├─────────────────────────────────────────┤
│                                         │
│  Sprite Size:                           │
│  W: [16]  H: [16]    [8] [16] [32]      │
│                                         │
│  Preview:                               │
│  ┌──┬──┬──┬──┐                          │
│  │  │  │  │  │  4x4 = 16 sprites        │
│  └──┴──┴──┴──┘                          │
│                                         │
├─────────────────────────────────────────┤
│            [Cancel]  [Convert]          │
└─────────────────────────────────────────┘
```

**What to show for Sprite (Single):**

| Field | Editable | Notes |
|-------|----------|-------|
| Mode | Yes | Radio: Single/Multiple |
| Size | No | Read-only, from texture |
| Pivot X/Y | Yes | With preset button |
| Pixels Per Unit | Yes | Optional override |
| "Open Sprite Editor" | Button | Opens full editor |

**What to show for Sprite (Multiple):**

| Field | Editable | Notes |
|-------|----------|-------|
| Mode | Yes | Radio: Single/Multiple |
| Texture Size | No | Read-only |
| Sprite Size | No | Read-only (edit in Sprite Editor) |
| Sprite Count | No | Read-only |
| Default Pivot | Yes | Applies to all sprites |
| "Open Sprite Editor" | Button | Opens full editor |

**What NOT to show:**
- 9-slice data (too complex for quick edit, use Sprite Editor)
- Per-sprite pivot overrides (use Sprite Editor)
- Grid spacing/offset (use Sprite Editor for fine-tuning)

---

### Phase 3: Animation Inspector

Create the animation-specific inspector with animated preview.

**Files:**

| File | Change |
|------|--------|
| `inspector/AnimationInspectorRenderer.java` | **NEW** - Animation inspector with playback |

**Tasks:**
- [x] Create `AnimationInspectorRenderer`
  - Animated preview (plays frames)
  - Play/Pause/Stop/Restart controls
  - Shows frame count, duration, FPS
  - Loop checkbox (editable)
  - "Open Animation Editor..." button (via event bus)
- [x] Register renderer in `AssetInspectorRegistry`
- [x] Create `OpenAnimationEditorEvent`
- [x] Wire event to open Animation Editor panel

**What to show for Animation:**

| Field | Editable | Notes |
|-------|----------|-------|
| Frame Count | No | Read-only |
| Duration | No | Read-only (calculated) |
| FPS | No | Read-only (calculated) |
| Loop | Yes | Checkbox |
| "Open Animation Editor" | Button | Opens animation panel |

---

### Phase 4: Integration & Polish

Wire everything together and polish the UI.

**Files:**

| File | Change |
|------|--------|
| `AssetInspectorRegistry.java` | Register all inspectors |
| `EditorUIController.java` | Wire up "Open X Editor" buttons |
| `events/OpenAnimationEditorEvent.java` | **NEW** - Event for opening animation editor |

**Tasks:**
- [x] Register `SpriteInspectorRenderer` in registry
- [x] Register `AnimationInspectorRenderer` in registry
- [x] Wire "Open Sprite Editor" to open `SpriteEditorPanel(V2)`
- [x] Wire "Open Animation Editor" to open Animation panel
- [ ] Add keyboard shortcut for save (Ctrl+S when inspector focused?) - DEFERRED
- [ ] Add unsaved changes indicator in inspector header - Already exists via save button color
- [x] Test all asset types (compilation verified)

---

## File Change Summary

### New Files

| File | Purpose |
|------|---------|
| `inspector/AssetInspectorRenderer.java` | Interface for type-specific inspectors |
| `inspector/AssetInspectorRegistry.java` | Registry for inspector renderers |
| `inspector/DefaultAssetInspectorRenderer.java` | Fallback renderer |
| `inspector/SpriteInspectorRenderer.java` | Sprite-specific inspector with mode switching |
| `inspector/GridConfigDialog.java` | Quick grid config when switching to Multiple mode |
| `inspector/AnimationInspectorRenderer.java` | Animation-specific inspector |
| `assets/AnimationPreviewRenderer.java` | Animated preview for animations |

### Modified Files

| File | Change |
|------|--------|
| `inspector/AssetInspector.java` | Use registry, add save button |
| `assets/AssetPreviewRegistry.java` | Register animation preview |
| `EditorUIController.java` | Wire up editor open buttons |

---

## Testing Strategy

### Phase 1 Testing
- [ ] Default inspector still works for all asset types
- [ ] Registry lookup works correctly
- [ ] Save button appears for editable assets

### Phase 2 Testing (Sprite)
- [ ] Single mode sprite shows pivot fields
- [ ] Multiple mode sprite shows grid info
- [ ] Pivot editing works and saves correctly
- [ ] "Open Sprite Editor" opens correct panel
- [ ] Unsaved changes indicator works

### Phase 3 Testing (Animation)
- [ ] Animation preview plays correctly
- [ ] Play/Stop buttons work
- [ ] Loop checkbox edits and saves
- [ ] "Open Animation Editor" opens correct panel

### Phase 4 Testing (Integration)
- [ ] All asset types have appropriate inspector
- [ ] Save works for all editable assets
- [ ] No regressions in existing functionality

---

## Integration with Other Plans

This plan can be done **in parallel** with:
- `00-asset-model-unification.md` - Inspector will use `SpriteMetadata`
- `01-sprite-editor-v2.md` - "Open Sprite Editor" button opens V2

**After asset model unification:**
- Update `SpriteInspectorRenderer` to use `SpriteMetadata` for mode detection
- Remove any `SpriteSheet`-specific code

**Recommended order:**
1. Start this plan (Phase 1-2) while asset model work is in progress
2. Complete asset model unification
3. Finish this plan (Phase 3-4) with unified model
4. Complete Sprite Editor V2

---

## Code Review

After implementation:
- Write review to `Documents/Plans/sprite-editor-v2/review-asset-inspector.md`
