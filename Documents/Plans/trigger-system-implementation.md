# Trigger System Implementation Plan

## Overview

Implement a tile-based trigger system for WARP and DOOR triggers, with editor UI integration for configuring trigger properties.

**Scope**: Core triggers only (WARP, DOOR) with editor Inspector and List panel
**AreaTrigger**: Deferred to future implementation
**One-shot Persistence**: Deferred until save system is implemented

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Scene                                       │
├─────────────────────────────────────────────────────────────────────────┤
│  CollisionMap              TriggerDataMap           TriggerSystem        │
│  ┌──────────────┐         ┌──────────────────┐     ┌──────────────────┐ │
│  │ (5,10) WARP  │         │ (5,10) → Data    │     │ Event dispatch   │ │
│  │ (3,3)  DOOR  │         │   targetScene    │     │ Handler registry │ │
│  └──────────────┘         │   targetX/Y      │     └──────────────────┘ │
│         │                 └──────────────────┘              │            │
│         │                          │                        │            │
│         └──────────────────────────┼────────────────────────┘            │
│                                    ▼                                     │
│                           GridMovement                                   │
│                     (calls triggerEnter/Exit)                            │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Core Data Structures

### 1.1 TriggerData Record

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerData.java`

```java
public record TriggerData(
    TriggerType type,
    ActivationMode activationMode,
    Map<String, Object> properties,
    boolean oneShot,
    boolean playerOnly
) {
    public enum TriggerType {
        WARP, DOOR, CUSTOM
    }

    public enum ActivationMode {
        ON_ENTER,      // Fire when entity steps on tile
        ON_INTERACT,   // Fire when player presses interact
        ON_EXIT        // Fire when entity leaves tile
    }
}
```

### 1.2 TriggerDataMap

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerDataMap.java`

- Chunk-based storage matching CollisionMap pattern
- Key: packed long from (x, y, z) coordinates
- Serialization: JSON format (human-readable for debugging)
- Methods: `get()`, `set()`, `remove()`, `clear()`, `getAllTriggers()`

### 1.3 CollisionType Enhancement

**File**: `src/main/java/com/pocket/rpg/collision/CollisionType.java`

Add helper method:
```java
public boolean isTrigger() {
    return this == WARP || this == DOOR || this == SCRIPT_TRIGGER;
}
```

---

## Phase 2: Trigger Event System

### 2.1 TriggerEvent Interface

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerEvent.java`

```java
public interface TriggerEvent {
    GameObject entity();
    int tileX();
    int tileY();
    int tileZ();
    TriggerData triggerData();
}
```

### 2.2 Event Classes

**Files**: `src/main/java/com/pocket/rpg/collision/trigger/events/`

- `WarpTriggerEvent` - contains targetScene, targetX, targetY, transition
- `DoorTriggerEvent` - contains locked, keyItem, destination

### 2.3 TriggerHandler Interface

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerHandler.java`

```java
public interface TriggerHandler<T extends TriggerEvent> {
    Class<T> getEventType();
    void handle(T event);
}
```

### 2.4 Handler Implementations

**Files**: `src/main/java/com/pocket/rpg/collision/trigger/handlers/`

- `WarpHandler` - uses TransitionManager + SceneManager
- `DoorHandler` - checks locked state, key inventory, then warps

---

## Phase 3: TriggerSystem Runtime

### 3.1 TriggerSystem Class

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerSystem.java`

Responsibilities:
- Hold reference to TriggerDataMap
- Registry of handlers (Map<Class, Handler>)
- `onTileEnter(entity, x, y, z)` - check activation mode, fire if ON_ENTER
- `onTileExit(entity, x, y, z)` - fire if ON_EXIT
- `tryInteract(entity, x, y, z, facing)` - check current tile + facing tile for ON_INTERACT
- `registerHandler()` / `dispatchEvent()`

### 3.2 Scene Integration

**File**: `src/main/java/com/pocket/rpg/scenes/Scene.java`

Changes:
- Add `TriggerDataMap triggerDataMap` field
- Add `TriggerSystem triggerSystem` field
- Initialize in constructor
- Add getters: `getTriggerSystem()`, `getTriggerDataMap()`

### 3.3 GridMovement Integration

**File**: `src/main/java/com/pocket/rpg/components/GridMovement.java`

Changes:
- In `onMovementComplete()`: call `triggerSystem.onTileEnter()`
- Before movement starts: call `triggerSystem.onTileExit()`
- Already has callbacks wired, just need to connect to TriggerSystem

---

## Phase 4: Serialization

### 4.1 TriggerDataMap Serialization

- Format: JSON object with coordinate keys
- Integrate with existing scene serialization
- Load/save alongside CollisionMap

**Example JSON**:
```json
{
  "triggers": {
    "5,10,0": {
      "type": "WARP",
      "activationMode": "ON_ENTER",
      "properties": {
        "targetScene": "cave_entrance",
        "targetX": 3,
        "targetY": 5,
        "transition": "FADE"
      },
      "oneShot": false,
      "playerOnly": true
    }
  }
}
```

### 4.2 EditorScene Integration

- Save triggers with scene
- Load triggers on scene open
- Mark scene dirty when triggers modified

---

## Phase 5: Editor UI

### 5.1 UI Layout

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Collision Panel                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│ [Tools: Brush | Eraser | Fill | Rect | Picker | SELECT ]                │
│                                                                          │
│ Collision Types                                                          │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ [NONE] [SOLID] [WATER] [ICE] [SAND] ...                             │ │
│ │ [WARP] [DOOR] [SCRIPT_TRIGGER]                                      │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│ ▼ Trigger List                                                          │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ ● WARP (5, 10) → cave_entrance                                      │ │
│ │ ○ WARP (5, 11) → cave_entrance                                      │ │
│ │ ○ DOOR (3, 3) → house_interior [locked]                             │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ Inspector (when trigger tile selected)                                   │
├─────────────────────────────────────────────────────────────────────────┤
│ Trigger: WARP at (5, 10, 0)                                             │
│ ─────────────────────────────                                           │
│                                                                          │
│ Target Scene: [cave_entrance     ▼]                                     │
│ Target X: [3    ]  Target Y: [5    ]                                    │
│ Transition: [Fade ▼]                                                    │
│                                                                          │
│ ─────────────────────────────                                           │
│ ☑ Player Only                                                           │
│ ☐ One Shot                                                              │
│                                                                          │
│ [Delete Trigger]                                                        │
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.2 TriggerListSection (embedded in CollisionPanel)

**File**: `src/main/java/com/pocket/rpg/editor/panels/collision/TriggerListSection.java`

Features:
- Collapsible section in CollisionPanel
- Lists all trigger tiles with type icon and summary
- Click to select (syncs with scene view)
- Context menu: Edit, Go To, Delete
- Hover tooltip with trigger details
- Filter toggles: show/hide WARP, DOOR, SCRIPT

### 5.3 TriggerInspector (Inspector integration)

**File**: `src/main/java/com/pocket/rpg/editor/ui/inspectors/TriggerInspector.java`

Features:
- Renders when trigger tile selected in scene view
- Type-specific property editors:
  - WARP: scene dropdown, position fields, transition selector
  - DOOR: locked checkbox, key item field, destination fields
- Common options: playerOnly, oneShot
- Delete button with confirmation
- Undo/redo support via existing UndoManager

### 5.4 CollisionSelectTool Enhancement

**File**: `src/main/java/com/pocket/rpg/editor/panels/collision/tools/CollisionSelectTool.java`

Changes:
- When clicking on trigger tile, select it
- Notify TriggerListSection and Inspector
- Visual highlight on selected trigger tile (pulsing yellow outline)

### 5.5 CollisionOverlayRenderer Enhancement

**File**: `src/main/java/com/pocket/rpg/editor/rendering/CollisionOverlayRenderer.java`

Changes:
- Add `selectedTriggerTile` field
- Render pulsing highlight on selected trigger
- Different highlight color for configured vs unconfigured triggers

---

## Phase 6: Undo/Redo Support

### 6.1 TriggerEditCommand

**File**: `src/main/java/com/pocket/rpg/editor/undo/commands/TriggerEditCommand.java`

```java
public class TriggerEditCommand implements UndoableCommand {
    private final TileCoord coord;
    private final TriggerData oldData;
    private final TriggerData newData;

    @Override
    public void execute() {
        if (newData != null) {
            triggerDataMap.set(coord, newData);
        } else {
            triggerDataMap.remove(coord);
        }
    }

    @Override
    public void undo() {
        if (oldData != null) {
            triggerDataMap.set(coord, oldData);
        } else {
            triggerDataMap.remove(coord);
        }
    }
}
```

---

## Phase 7: Testing

### 7.1 Unit Tests

- TriggerDataMap: get/set/remove, serialization roundtrip
- TriggerSystem: event dispatch, handler registration
- WarpHandler: scene transition logic
- DoorHandler: locked/unlocked paths

### 7.2 Integration Tests

- GridMovement + TriggerSystem: enter/exit events
- Editor: trigger creation, editing, deletion with undo

### 7.3 Manual Testing Checklist

- [ ] Draw WARP tile, configure target, test in play mode
- [ ] Draw DOOR tile, configure as locked, test with/without key
- [ ] Save scene, reload, verify triggers persist
- [ ] Undo/redo trigger edits
- [ ] Delete trigger, verify removed from list and map

---

## Phase 8: Code Review

After implementation, review all changed/added files and write findings to:
`Documents/Reviews/trigger-system-review.md`

---

## File Summary

### New Files (13)

| File | Purpose |
|------|---------|
| `collision/trigger/TriggerData.java` | Data record with type, mode, properties |
| `collision/trigger/TriggerDataMap.java` | Chunk-based trigger storage |
| `collision/trigger/TriggerEvent.java` | Event interface |
| `collision/trigger/TriggerHandler.java` | Handler interface |
| `collision/trigger/TriggerSystem.java` | Runtime dispatch |
| `collision/trigger/events/WarpTriggerEvent.java` | Warp event |
| `collision/trigger/events/DoorTriggerEvent.java` | Door event |
| `collision/trigger/handlers/WarpHandler.java` | Warp logic |
| `collision/trigger/handlers/DoorHandler.java` | Door logic |
| `editor/panels/collision/TriggerListSection.java` | UI list in CollisionPanel |
| `editor/ui/inspectors/TriggerInspector.java` | Property editor |
| `editor/undo/commands/TriggerEditCommand.java` | Undo support |
| `collision/trigger/TileCoord.java` | Coordinate record |

### Modified Files (7)

| File | Changes |
|------|---------|
| `collision/CollisionType.java` | Add `isTrigger()` method |
| `scenes/Scene.java` | Add TriggerDataMap and TriggerSystem fields |
| `components/GridMovement.java` | Connect to TriggerSystem callbacks |
| `editor/panels/CollisionPanel.java` | Add TriggerListSection |
| `editor/rendering/CollisionOverlayRenderer.java` | Selection highlight |
| `editor/panels/InspectorPanel.java` | Integrate TriggerInspector |
| Scene serialization files | Include trigger data |

---

## Encyclopedia Documentation

After implementation, create or update:
`Documents/Encyclopedia/collision-and-triggers-guide.md`

Sections:
1. Overview of collision system
2. Collision types and their behaviors
3. Setting up triggers (WARP, DOOR)
4. Configuring trigger properties in editor
5. Common patterns and examples
6. Troubleshooting

---

## Implementation Order

1. **Phase 1**: TriggerData + TriggerDataMap (core data)
2. **Phase 2**: Event system (interfaces + classes)
3. **Phase 3**: TriggerSystem + Scene integration
4. **Phase 4**: Serialization
5. **Phase 5**: Editor UI (TriggerListSection, TriggerInspector)
6. **Phase 6**: Undo/redo
7. **Phase 7**: Testing
8. **Phase 8**: Code review + encyclopedia

---

## Dependencies

- Existing: CollisionMap, CollisionSystem, GridMovement, Scene
- Existing: UndoManager, InspectorPanel, CollisionPanel
- Existing: TransitionManager, SceneManager (for WarpHandler)

No new external dependencies required.

---

## Future Enhancements (Out of Scope)

- AreaTrigger component for multi-tile triggers
- ON_STAY activation mode with tick intervals
- One-shot persistence with save system
- Additional trigger types: DIALOGUE, TRAP, CHECKPOINT, CUTSCENE
- Conditional triggers (requires item, flag, quest)
- Custom script triggers