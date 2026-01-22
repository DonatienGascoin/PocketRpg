# Trigger System Implementation Plan

## Overview

Implement a tile-based trigger system for WARP and DOOR triggers, with proper editor integration and visual feedback.

**Scope**: Core triggers only (WARP, DOOR, STAIRS) with proper editor UI
**Deferred**: AreaTrigger component, one-shot persistence, additional trigger types (DIALOGUE, TRAP, etc.)

**Important Terminology**:
- **Elevation** = Floor/layer level for collision (ground=0, upper floors=1+, basements=-1)
- **zIndex** = Sprite rendering order (separate concept, not used here)

---

## Plan Documents

| Document | Purpose |
|----------|---------|
| [01-collision-type-enhancements.md](01-collision-type-enhancements.md) | New types, categories, auto-generated UI, icons |
| [02-trigger-data-architecture.md](02-trigger-data-architecture.md) | Typed trigger records (no Map<String, Object>) |
| [03-trigger-runtime.md](03-trigger-runtime.md) | TriggerSystem, event dispatch, handlers |
| [04-editor-ui.md](04-editor-ui.md) | Tabs layout, trigger list, inspector, visual feedback |
| [05-scene-view-rendering.md](05-scene-view-rendering.md) | Icons for triggers, missing metadata warnings |
| [06-testing-and-review.md](06-testing-and-review.md) | Testing strategy, code review |

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
│  │ (8,2) STAIRS │         │   targetX/Y      │     └──────────────────┘ │
│  └──────────────┘         └──────────────────┘              │            │
│         │                          │                        │            │
│         │         ┌────────────────┴────────────────────────┘            │
│         │         │                                                      │
│         ▼         ▼                                                      │
│   GridMovement ───► triggerEnter/Exit callbacks                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

### 1. Typed Trigger Data (No Map<String, Object>)

Instead of fragile `Map<String, Object>`, use **sealed interfaces with typed records**:

```java
sealed interface TriggerData permits WarpTriggerData, DoorTriggerData, StairsTriggerData {
    ActivationMode activationMode();
    boolean oneShot();
    boolean playerOnly();
}

record WarpTriggerData(
    String targetScene,
    int targetX,
    int targetY,
    TransitionType transition,
    ActivationMode activationMode,
    boolean oneShot,
    boolean playerOnly
) implements TriggerData {}
```

### 2. Auto-Generated Collision Type UI

CollisionType enum will include a `category` field. The UI iterates `CollisionType.values()` and groups by category automatically:

```java
enum CollisionType {
    NONE(0, "None", Category.MOVEMENT, ...),
    SOLID(1, "Solid", Category.MOVEMENT, ...),
    STAIRS_UP(13, "Stairs Up", Category.ELEVATION, ...),
    WARP(10, "Warp", Category.TRIGGER, ...),
    // ...
}
```

### 3. Tabbed Panel Layout (No Scrolling)

CollisionPanel uses tabs instead of scrolling:
- **Tab 1: Types** - Collision types grouped by category
- **Tab 2: Triggers** - List of configured triggers

### 4. Visual Feedback for Missing Metadata

- **Scene View**: Orange warning icon on trigger tiles without data
- **Trigger List**: Warning indicator next to unconfigured triggers
- **Inspector**: "Configure trigger" prompt when selecting unconfigured tile

### 5. Icons in Scene View

Trigger tiles render icons instead of just colors:
- WARP: Arrow icon (exit/entry)
- DOOR: Door icon
- STAIRS_UP/DOWN: Stair icons

---

## Implementation Order

1. **Phase 1**: CollisionType enhancements (categories, new types, icons)
2. **Phase 2**: Trigger data architecture (typed records, TriggerDataMap)
3. **Phase 3**: Trigger runtime (TriggerSystem, handlers)
4. **Phase 4**: Editor UI (tabs, auto-generated type selector, trigger list)
5. **Phase 5**: Scene view rendering (icons, missing metadata warnings)
6. **Phase 6**: Testing and code review

---

## Files Summary

### New Files (15)

| File | Purpose |
|------|---------|
| `collision/CollisionCategory.java` | Category enum for grouping types |
| `collision/trigger/TriggerData.java` | Sealed interface for trigger data |
| `collision/trigger/WarpTriggerData.java` | Warp-specific data record |
| `collision/trigger/DoorTriggerData.java` | Door-specific data record |
| `collision/trigger/StairsTriggerData.java` | Stairs-specific data record |
| `collision/trigger/TriggerDataMap.java` | Storage for trigger data |
| `collision/trigger/TileCoord.java` | Coordinate record (x, y, elevation) |
| `collision/trigger/TriggerSystem.java` | Runtime dispatch |
| `collision/trigger/handlers/WarpHandler.java` | Warp logic |
| `collision/trigger/handlers/DoorHandler.java` | Door logic |
| `collision/trigger/handlers/StairsHandler.java` | Elevation transition logic |
| `editor/panels/collision/TriggerListSection.java` | Trigger list UI |
| `editor/ui/inspectors/TriggerInspector.java` | Trigger property editor |
| `editor/undo/commands/TriggerEditCommand.java` | Undo support |
| `collision/trigger/ActivationMode.java` | Activation mode enum |

### Modified Files (8)

| File | Changes |
|------|---------|
| `collision/CollisionType.java` | Add category, isTrigger(), icon fields |
| `collision/CollisionTypeSelector.java` | Auto-generate from enum by category |
| `scenes/Scene.java` | Add TriggerDataMap, TriggerSystem |
| `components/GridMovement.java` | Connect to TriggerSystem |
| `editor/panels/CollisionPanel.java` | Add tabs layout |
| `editor/rendering/CollisionOverlayRenderer.java` | Add icon rendering |
| `editor/panels/InspectorPanel.java` | Integrate TriggerInspector |
| Scene serialization | Include trigger data |
