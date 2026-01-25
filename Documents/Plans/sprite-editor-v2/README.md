# Sprite Editor V2 - Implementation Plans

## Overview

This folder contains the implementation plans for the Sprite Editor V2 feature, which includes:

1. **Asset Model Unification** - Merge `Sprite` and `SpriteSheet` into a unified model
2. **Sprite Editor V2** - Unity-style editor with full texture view and click-to-select

## Plan Order

**These plans MUST be implemented in order:**

```
┌─────────────────────────────────────────┐
│  00-asset-model-unification.md          │  ← Do this first
│  Unify Sprite/SpriteSheet into one      │
│  asset type with Single/Multiple mode   │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│  01-sprite-editor-v2.md                 │  ← Then this
│  Build the new editor UI using the      │
│  unified asset model                    │
└─────────────────────────────────────────┘
```

## Plan Files

| File | Description | Status |
|------|-------------|--------|
| [00-asset-model-unification.md](./00-asset-model-unification.md) | Unify Sprite/SpriteSheet | DRAFT |
| [01-sprite-editor-v2.md](./01-sprite-editor-v2.md) | New editor implementation | DRAFT |

## Summary of Changes

### Asset Model Changes

**Before:**
- `Sprite` class for single images
- `SpriteSheet` class for grid-sliced images
- `.spritesheet` files defining grid parameters

**After:**
- `Sprite` class (unchanged)
- `SpriteGrid` helper for grid calculations
- `SpriteMetadata` with `spriteMode: single/multiple`
- No separate `.spritesheet` files - all in `.meta`

### Editor Changes

**Before:**
- Shows one sprite at a time
- Separate selector grid at bottom
- No way to create spritesheets
- No slicing configuration after creation

**After:**
- Shows full texture with grid overlay
- Click sprites directly in preview to select
- Mode switcher (Single/Multiple)
- Slicing tab for grid configuration
- All pivots visible at once

## Estimated Scope

| Plan | Phases | New Files | Modified Files | Deleted Files |
|------|--------|-----------|----------------|---------------|
| Asset Model | 9 | 2 | ~16 | 2 |
| Editor V2 | 9 | 6 | 1 | 4 |
| **Total** | **18** | **8** | **~17** | **6** |

## Key Benefits

1. **Simpler mental model** - One asset type (Sprite) with mode
2. **Unity-familiar** - Similar workflow to Unity's Sprite Editor
3. **Better visual feedback** - See all sprites and pivots at once
4. **More powerful** - Can modify slicing after creation
5. **Cleaner files** - No separate `.spritesheet` files

## Migration

Existing `.spritesheet` files will be automatically migrated:
- `player.spritesheet` → `player.png.meta` (with `spriteMode: multiple`)
- All scene/prefab references updated automatically
- Backup created before migration
