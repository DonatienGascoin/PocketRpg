# Trigger System Implementation Plan

## Overview

Implement a tile-based trigger system for WARP, DOOR, and STAIRS triggers, with proper editor integration and visual feedback.

**Scope**: Core triggers (WARP, DOOR, STAIRS, SPAWN_POINT) with editor UI, spawn-based arrival system
**Deferred**: AreaTrigger component, one-shot persistence, ScriptTrigger, visual connection lines, additional trigger types (DIALOGUE, TRAP, etc.)

**Important Terminology**:
- **Elevation** = Floor/layer level for collision (ground=0, upper floors=1+, basements=-1)
- **zIndex** = Sprite rendering order (separate concept, not used here)

---

## Plan Documents

| Document | Purpose |
|----------|---------|
| [01-collision-type-enhancements.md](01-collision-type-enhancements.md) | New types, categories, description field, auto-generated UI, icons |
| [02-trigger-data-architecture.md](02-trigger-data-architecture.md) | Typed trigger records with sealed interface, registry-based serialization |
| [03-trigger-runtime.md](03-trigger-runtime.md) | TriggerSystem, event dispatch, handlers |
| [04-editor-ui.md](04-editor-ui.md) | 3-column layout, trigger list, inspector integration |
| [05-scene-view-rendering.md](05-scene-view-rendering.md) | Icons for triggers, missing metadata warnings |
| [06-testing-and-review.md](06-testing-and-review.md) | Testing strategy, code review |
| [07-cross-scene-spawn.md](07-cross-scene-spawn.md) | Spawn point passing through scene transitions, tag system |

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Scene                                       │
├─────────────────────────────────────────────────────────────────────────┤
│  CollisionMap              TriggerDataMap           TriggerSystem        │
│  ┌──────────────┐         ┌──────────────────┐     ┌──────────────────┐ │
│  │ (5,10) WARP  │         │ (5,10) → WarpData│     │ Event dispatch   │ │
│  │ (3,3)  DOOR  │         │   targetScene    │     │ Handler registry │ │
│  │ (8,2) STAIRS │         │   spawnPointId   │     └──────────────────┘ │
│  │ (0,0) SPAWN  │         │ (8,2) → StairsD. │              │            │
│  └──────────────┘         │   directions     │              │            │
│         │                 │ (0,0) → SpawnD.  │              │            │
│         │                 │   id             │              │            │
│         │                 └──────────────────┘              │            │
│         │                          │                        │            │
│         │         ┌────────────────┴────────────────────────┘            │
│         │         │                                                      │
│         ▼         ▼                                                      │
│   GridMovement ───► onTileEnter(x,y,elev) / onTileExit(x,y,elev,dir)    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

### 1. Typed Trigger Data (No Map<String, Object>)

Instead of fragile `Map<String, Object>`, use **sealed interfaces with typed records**:

```java
sealed interface TriggerData permits WarpData, DoorData, StairsData, SpawnPointData {
    ActivationMode activationMode();
    boolean oneShot();
    boolean playerOnly();
    CollisionType collisionType();
    List<String> validate();
}

// Warps/doors use spawn point IDs for arrival position
record WarpData(
    String targetScene,
    String spawnPointId,          // References SpawnPointData.id in target scene
    TransitionType transition,
    ActivationMode activationMode,
    boolean oneShot,
    boolean playerOnly
) implements TriggerData {}

// Spawn points are named arrival markers (non-interactive)
record SpawnPointData(String id) implements TriggerData {
    @Override public ActivationMode activationMode() { return ActivationMode.NONE; }
    // ... other defaults
}

// Stairs use direction-based elevation (ON_EXIT mandatory)
record StairsData(
    Map<Direction, Integer> elevationChanges  // e.g., {NORTH: +1, SOUTH: -1}
) implements TriggerData {
    @Override public ActivationMode activationMode() { return ActivationMode.ON_EXIT; }
}
```

### 2. Auto-Generated Collision Type UI

CollisionType enum includes `category` and `description` fields. The UI iterates `CollisionType.values()` and groups by category automatically:

```java
enum CollisionType {
    NONE(0, "None", Category.MOVEMENT, "No collision - fully walkable", ...),
    SOLID(1, "Solid", Category.MOVEMENT, "Solid wall - blocks all movement", ...),
    STAIRS(13, "Stairs", Category.ELEVATION, "Bidirectional stairs - elevation based on exit direction", ...),
    WARP(10, "Warp", Category.TRIGGER, "Teleports to another scene", ...),
    SPAWN_POINT(14, "Spawn Point", Category.TRIGGER, "Named arrival marker for warps/doors", ...),
    // ...
}
```

### 3. Three-Column Panel Layout (No Scrolling, No Tabs)

CollisionPanel uses a 3-column layout:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Tool Size: [3]  │ Show: ☑  │ Elev: [0 ▼]                        │ 150 tiles │
├─────────────────────┬─────────────────────┬─────────────────────────────────┤
│ Movement            │ Elevation           │ ⚠ Triggers (4)                  │
│ [None] [Solid]      │ [Stairs]            │                                 │
│                     │                     │ ● Warp (5,10) → cave:entrance   │
│ Ledges              │ Triggers            │ ⚠ Warp (5,11) [!]               │
│ [↓][↑][←][→]        │ [Warp][Door]        │ ● Door (3,3) → house:main_door  │
│                     │ [Spawn]             │ ● Stairs (8,2) N:+1 S:-1        │
│ Terrain             ├─────────────────────┤ ● Spawn (0,0) "entrance"        │
│ [Water][Grass]      │ Selected: Warp      │                                 │
│ [Ice][Sand]         │ "Teleports to       │                                 │
│                     │  another scene"     │                                 │
└─────────────────────┴─────────────────────┴─────────────────────────────────┘
```

- **Column 1**: Basic types (Movement, Ledges, Terrain)
- **Column 2**: Metadata types (Elevation, Triggers) + Selected type info
- **Column 3**: Trigger list (with warning icon in header if unconfigured)

### 4. Visual Feedback for Missing Metadata

- **Scene View**: Orange warning icon on trigger tiles without data
- **Trigger List Header**: ⚠ icon when unconfigured triggers exist
- **Trigger List Items**: Warning indicator next to unconfigured triggers
- **Inspector**: "Configure trigger" prompt when selecting unconfigured tile

### 5. Inspector Integration

When a trigger tile is selected (from list or scene view), the Inspector panel shows `TriggerInspector` instead of entity properties.

### 6. Registry-Based Serialization

`TriggerDataTypeAdapter` uses `getPermittedSubclasses()` to automatically discover all trigger types - no switch statement needed:

```java
static {
    TYPE_REGISTRY = new HashMap<>();
    for (Class<?> permitted : TriggerData.class.getPermittedSubclasses()) {
        TYPE_REGISTRY.put(permitted.getSimpleName(), (Class<? extends TriggerData>) permitted);
    }
}
```

---

## Implementation Order

1. **Phase 1**: CollisionType enhancements (categories, description, new types, icons)
2. **Phase 2**: Trigger data architecture (typed records, TriggerDataMap, serialization)
3. **Phase 3**: Trigger runtime (TriggerSystem, handlers)
4. **Phase 4**: Editor UI (3-column layout, trigger list, inspector integration)
5. **Phase 5**: Scene view rendering (icons, missing metadata warnings)
6. **Phase 6**: Testing and code review
7. **Phase 7**: Cross-scene spawn system (tag system, spawn ID passing through transitions)

---

## Files Summary

### New Files (14)

| File | Purpose |
|------|---------|
| `collision/CollisionCategory.java` | Category enum for grouping types |
| `collision/trigger/TriggerData.java` | Sealed interface for trigger data |
| `collision/trigger/WarpData.java` | Warp-specific data record |
| `collision/trigger/DoorData.java` | Door-specific data record |
| `collision/trigger/StairsData.java` | Stairs data with direction-based elevation |
| `collision/trigger/SpawnPointData.java` | Named arrival marker |
| `collision/trigger/TriggerDataMap.java` | Storage for trigger data |
| `collision/trigger/TileCoord.java` | Coordinate record (x, y, elevation) |
| `collision/trigger/ActivationMode.java` | Activation mode enum |
| `collision/trigger/TransitionType.java` | Scene transition types |
| `collision/trigger/TriggerSystem.java` | Runtime dispatch |
| `editor/panels/collision/TriggerListSection.java` | Trigger list UI |
| `editor/ui/inspectors/TriggerInspector.java` | Trigger property editor |
| `editor/utils/SceneUtils.java` | Scene discovery and cross-scene spawn point loading |

### Modified Files (8)

| File | Changes |
|------|---------|
| `collision/CollisionType.java` | Add category, description, isTrigger(), icon fields; single STAIRS type |
| `collision/CollisionTypeSelector.java` | Auto-generate from enum by category |
| `scenes/Scene.java` | Add TriggerDataMap, TriggerSystem |
| `components/GridMovement.java` | Connect to TriggerSystem; pass exit direction to onTileExit |
| `editor/panels/CollisionPanel.java` | 3-column layout |
| `editor/rendering/CollisionOverlayRenderer.java` | Add icon rendering |
| `editor/panels/InspectorPanel.java` | Integrate TriggerInspector |
| `resources/Assets.java` | Add `scanAll(LoadOptions)` overload for scene discovery |

---

## Stairs Design: Direction-Based Elevation

Stairs use a single `STAIRS` collision type with **ON_EXIT activation only**. The elevation change is determined by the **exit direction**, not the entry direction. This solves the problem of the player going back to their original position.

### Why ON_EXIT?

**Problem with ON_ENTER**:
- Player at elevation 0 moves NORTH onto stairs
- ON_ENTER triggers: elevation becomes 1
- Player decides to go back SOUTH (without completing movement)
- Player is now at elevation 1 on the wrong side

**Solution with ON_EXIT**:
- Player moves onto stairs (no trigger)
- Player continues NORTH off the stairs
- ON_EXIT triggers with `exitDirection=NORTH`
- Handler looks up `elevationChanges.get(NORTH)` = +1
- Player arrives at elevation 1 on the correct side

### StairsData Configuration

```java
record StairsData(
    Map<Direction, Integer> elevationChanges  // {NORTH: +1, SOUTH: -1}
) implements TriggerData {
    @Override public ActivationMode activationMode() { return ActivationMode.ON_EXIT; }
}
```

**Editor UI**: Four checkboxes/spinners for N/S/E/W elevation changes. For a typical vertical staircase:
- North: +1 (going up)
- South: -1 (going down)
- East/West: 0 (no change, or disabled)

---

## Future Enhancements (Post-Phase 6)

### Visual Connection Lines

Draw lines in the scene view between:
- Warp/Door tiles and their target spawn points (cross-scene: arrow to scene icon)
- Stairs and their destination elevation markers

### ItemRegistry

Barebone registry for key items used by doors:
- Defined in JSON config file
- Door configurations can add items to the registry
- Used for dropdown selection in TriggerInspector

### Door Lock/Unlock Persistence (Save System Integration)

**Design**: Door configuration (locked, requiredKey, etc.) is stored in `TriggerDataMap` as **design-time data**. Runtime unlock state is stored in the **save system** as player progress.

**How it works**:
1. `DoorData` defines the door's design:
   - `locked` - whether door starts locked
   - `requiredKey` - item ID needed to unlock (e.g., "dungeon_key")
   - `consumeKey` - whether to remove key from inventory on use

2. At runtime, `DoorHandler` checks:
   - Is this door already unlocked? → `SaveManager.getGlobal("doors", doorId, false)`
   - If not, does player have required key? → Check player inventory
   - If key found, unlock and persist: `SaveManager.setGlobal("doors", doorId, true)`

3. Door ID is derived from position: `"door_" + x + "_" + y + "_" + elevation + "_" + sceneName`

**Benefits**:
- Design-time config and runtime state are cleanly separated
- Works with existing save system architecture (globalState map)
- Doors stay unlocked across save/load cycles
- Easy to query unlock status from anywhere: `SaveManager.getGlobal("doors", doorId, false)`

**Implementation**: When save-system branch is merged, update `DoorHandler` to use `SaveManager` for persistence.
