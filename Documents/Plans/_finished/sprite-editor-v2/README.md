# Sprite Editor V2 - Implementation Plans

## Overview

This folder contains the implementation plans for the Sprite Editor V2 feature and related enhancements:

1. **Asset Model Unification** - Merge `Sprite` and `SpriteSheet` into a unified model
2. **Sprite Editor V2** - Unity-style editor with full texture view and click-to-select
3. **Asset Inspector Enhancements** - Editable fields in inspector for Sprite/Animation assets

## Plan Order

```
┌─────────────────────────────────────────┐
│  00-asset-model-unification.md          │  ← Start here
│  Unify Sprite/SpriteSheet into one      │
│  asset type with Single/Multiple mode   │
└─────────────────────────────────────────┘
            │
            │ (can run in parallel) ──────────────────┐
            │                                         │
            ▼                                         ▼
┌─────────────────────────────────────────┐  ┌─────────────────────────────────────────┐
│  01-sprite-editor-v2.md                 │  │  02-asset-inspector-enhancements.md     │
│  Build the new editor UI using the      │  │  Add editable fields to Asset Inspector │
│  unified asset model                    │  │  for Sprite, Animation, etc.            │
└─────────────────────────────────────────┘  └─────────────────────────────────────────┘
```

**Dependencies:**
- Plan 01 depends on Plan 00 (needs unified asset model)
- Plan 02 can start in parallel, but should finish after Plan 00

## Plan Files

| File | Description | Status | Phases |
|------|-------------|--------|--------|
| [00-asset-model-unification.md](00-asset-model-unification.md) | Unify Sprite/SpriteSheet | DRAFT | 9 |
| [01-sprite-editor-v2.md](01-sprite-editor-v2.md) | New editor implementation | DRAFT | 9 |
| [02-asset-inspector-enhancements.md](02-asset-inspector-enhancements.md) | Inspector editable fields | DRAFT | 4 |

## Summary of Changes

### Asset Model (Plan 00)

| Before | After |
|--------|-------|
| `Sprite` class | `Sprite` class (unchanged) |
| `SpriteSheet` class | **DELETED** |
| `.spritesheet` files | `.png.meta` with `spriteMode: multiple` |
| Two loaders | One loader handles both modes |

### Sprite Editor (Plan 01)

| Before | After |
|--------|-------|
| One sprite at a time | Full texture with grid overlay |
| Separate selector grid | Click-to-select in preview |
| Pivot/9-Slice tabs only | Slicing + Pivot + 9-Slice tabs |
| Can't modify slicing | Can modify grid any time |
| Can't create spritesheets | Mode switcher: Single ↔ Multiple |

### Asset Inspector (Plan 02)

| Before | After |
|--------|-------|
| Preview only | Preview + editable fields |
| No save button | Save button for metadata |
| No action buttons | "Open Sprite Editor" button |
| No mode switching | Single ↔ Multiple mode switcher |
| No animation preview | Animated preview with play/stop |

## What Each Inspector Shows

### Sprite (Single Mode)
```
Preview: Sprite with pivot marker
Mode:    ● Single  ○ Multiple  (switcher)
Fields:  Pivot X/Y (editable), Pixels/Unit (editable)
Actions: [Open Sprite Editor]
```

### Sprite (Multiple Mode)
```
Preview: Grid thumbnail
Mode:    ○ Single  ● Multiple  (switcher)
Fields:  Sprite count (read-only), Default pivot (editable)
Actions: [Open Sprite Editor]
```

### Animation
```
Preview: Animated playback with Play/Stop
Fields:  Frame count, Duration, Loop (editable)
Actions: [Open Animation Editor]
```

## Estimated Scope

| Plan | Phases | New Files | Modified Files | Deleted Files |
|------|--------|-----------|----------------|---------------|
| 00 - Asset Model | 9 | 2 | ~16 | 2 |
| 01 - Editor V2 | 9 | 6 | 1 | 4 |
| 02 - Inspector | 4 | 7 | 3 | 0 |
| **Total** | **22** | **15** | **~20** | **6** |

## Key Benefits

1. **Simpler mental model** - One asset type (Sprite) with mode
2. **Unity-familiar** - Similar workflow to Unity's Sprite Editor
3. **Better visual feedback** - See all sprites and pivots at once
4. **Quick edits in Inspector** - Don't need to open full editor for simple changes
5. **More powerful** - Can modify slicing after creation
6. **Cleaner files** - No separate `.spritesheet` files

## Migration

Existing `.spritesheet` files will be automatically migrated:
- `player.spritesheet` → `player.png.meta` (with `spriteMode: multiple`)
- All scene/prefab references updated automatically
- Backup created before migration

## Recommended Implementation Order

1. **Start Plan 00** (Asset Model) - Phase 1-4
2. **Start Plan 02** (Inspector) - Phase 1-2 (in parallel)
3. **Complete Plan 00** - Phase 5-9
4. **Complete Plan 02** - Phase 3-4 (uses unified model)
5. **Start Plan 01** (Editor V2) - All phases
6. **Final cleanup** - Remove V1, rename V2
