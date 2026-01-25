# Phase 6: Editor Integration

## Overview

This phase covers editor support for the Interactable system:
- Inspector UI for new components
- Prefab templates for common objects
- Scene view visualization
- Workflow improvements

---

## Inspector Support

All new components use standard fields that work with the existing Inspector:

### Door Component Inspector

```
┌─────────────────────────────────────────────────────────────────────┐
│  Door                                                      [Remove] │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ─── Lock Settings ───────────────────────────────────────────────  │
│  Locked:              [✓]                                          │
│  Required Key:        [dungeon_key        ]                        │
│  Consume Key:         [ ]                                          │
│  Locked Message:      [The door is locked.]                        │
│                                                                     │
│  ─── Destination ─────────────────────────────────────────────────  │
│  Target Scene:        [dungeon_floor_2    ] [Browse]               │
│  Target Spawn:        [stairs_up          ▼]                       │
│                                                                     │
│  ─── Visuals ─────────────────────────────────────────────────────  │
│  Closed Sprite:       [door_closed.png    ] [...]                  │
│  Open Sprite:         [door_open.png      ] [...]                  │
│                                                                     │
│  ─── Audio ───────────────────────────────────────────────────────  │
│  Open Sound:          [door_open.wav      ] [...]                  │
│  Close Sound:         [door_close.wav     ] [...]                  │
│  Unlock Sound:        [door_unlock.wav    ] [...]                  │
│  Locked Sound:        [door_locked.wav    ] [...]                  │
│                                                                     │
│  ─── Collision ───────────────────────────────────────────────────  │
│  Blocked Tiles:       [(0,0,0)]           [+] [-]                  │
│  Tiles Relative:      [✓]                                          │
│  Elevation:           [0]                                          │
│                                                                     │
│  ─── Interaction ─────────────────────────────────────────────────  │
│  Interaction Radius:  [1.5    ]                                    │
│  Required Facing:     [None              ▼]                        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Custom Field Editors Needed

| Field Type | Editor |
|------------|--------|
| `List<TileCoord>` | Tile coordinate list with add/remove |
| `Sprite` | Existing asset reference picker |
| `AudioClip` | Existing audio asset picker |
| `String` (scene) | Scene dropdown or text with browse |
| `String` (spawn) | Dropdown populated from target scene |

### TileCoord List Editor

```java
// In FieldEditors.java
public static void drawTileCoordList(String label, List<TileCoord> coords,
                                     Runnable onChange) {
    ImGui.text(label);

    for (int i = 0; i < coords.size(); i++) {
        TileCoord coord = coords.get(i);
        ImGui.pushID(i);

        // X, Y, Elevation inputs
        int[] values = {coord.x(), coord.y(), coord.elevation()};
        ImGui.setNextItemWidth(50);
        if (ImGui.inputInt3("##coord" + i, values)) {
            coords.set(i, new TileCoord(values[0], values[1], values[2]));
            onChange.run();
        }

        ImGui.sameLine();
        if (ImGui.smallButton("-")) {
            coords.remove(i);
            onChange.run();
        }

        ImGui.popID();
    }

    if (ImGui.smallButton("+")) {
        coords.add(new TileCoord(0, 0, 0));
        onChange.run();
    }
}
```

---

## Scene View Visualization

### Interaction Radius Display

When Door/Chest/etc is selected, show interaction radius:

```java
// In SelectionTool or dedicated overlay
public void renderInteractableOverlay(EditorGameObject entity, EditorCamera camera) {
    Interactable interactable = findInteractableComponent(entity);
    if (interactable == null) return;

    float radius = interactable.getInteractionRadius();
    Vector2f center = camera.worldToScreen(entity.getPosition());

    // Draw circle showing interaction radius
    ImDrawList drawList = ImGui.getForegroundDrawList();
    float screenRadius = radius * camera.getZoom();
    int color = ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 0.3f);
    int borderColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 0.8f);

    drawList.addCircleFilled(center.x, center.y, screenRadius, color);
    drawList.addCircle(center.x, center.y, screenRadius, borderColor, 32, 2);
}
```

### Blocked Tiles Display

For Door/SecretPassage, show which tiles will be blocked:

```java
public void renderBlockedTilesOverlay(Door door, EditorCamera camera) {
    Vector3f doorPos = door.getPosition();
    int baseX = (int) doorPos.x;
    int baseY = (int) doorPos.y;

    for (TileCoord tile : door.getBlockedTiles()) {
        int x = door.isTilesRelative() ? baseX + tile.x() : tile.x();
        int y = door.isTilesRelative() ? baseY + tile.y() : tile.y();

        Vector2f screenPos = camera.worldToScreen(x, y);
        Vector2f screenEnd = camera.worldToScreen(x + 1, y + 1);

        // Draw tile highlight
        int color = door.isOpen()
            ? ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.4f)  // Green = passable
            : ImGui.colorConvertFloat4ToU32(0.8f, 0.2f, 0.2f, 0.4f); // Red = blocked

        drawList.addRectFilled(screenPos.x, screenPos.y, screenEnd.x, screenEnd.y, color);
    }
}
```

### Lever Target Lines

Show connections from Lever to targets:

```java
public void renderLeverConnections(Lever lever, EditorCamera camera) {
    Vector2f leverPos = camera.worldToScreen(lever.getPosition());

    for (String targetName : lever.getTargetNames()) {
        GameObject target = scene.findGameObject(targetName);
        if (target == null) continue;

        Vector2f targetPos = camera.worldToScreen(target.getTransform().getPosition());

        // Draw dashed line
        int color = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.0f, 0.8f);
        drawList.addLine(leverPos.x, leverPos.y, targetPos.x, targetPos.y, color, 2);

        // Arrow at target
        // ...
    }
}
```

---

## Prefab Templates

Create prefab templates for common configurations.

### Directory Structure

```
gameData/prefabs/
├── interactables/
│   ├── door_simple.prefab
│   ├── door_locked.prefab
│   ├── door_teleport.prefab
│   ├── chest_wooden.prefab
│   ├── chest_locked.prefab
│   ├── lever_toggle.prefab
│   ├── lever_oneshot.prefab
│   ├── sign_readable.prefab
│   └── secret_wall.prefab
```

### Simple Door Prefab

```json
{
  "name": "SimpleDoor",
  "components": [
    {
      "type": "SpriteRenderer",
      "sprite": "sprites/doors/door_wooden.png"
    },
    {
      "type": "AudioSource"
    },
    {
      "type": "Door",
      "locked": false,
      "closedSprite": "sprites/doors/door_wooden.png",
      "openSprite": "sprites/doors/door_wooden_open.png",
      "openSound": "sounds/door_open.wav",
      "closeSound": "sounds/door_close.wav",
      "blockedTiles": [{"x": 0, "y": 0, "elevation": 0}],
      "tilesRelative": true
    }
  ]
}
```

### Locked Door Prefab

```json
{
  "name": "LockedDoor",
  "components": [
    {
      "type": "SpriteRenderer",
      "sprite": "sprites/doors/door_locked.png"
    },
    {
      "type": "AudioSource"
    },
    {
      "type": "Door",
      "locked": true,
      "requiredKey": "",
      "consumeKey": false,
      "lockedMessage": "This door is locked.",
      "closedSprite": "sprites/doors/door_locked.png",
      "openSprite": "sprites/doors/door_locked_open.png",
      "openSound": "sounds/door_open.wav",
      "unlockSound": "sounds/unlock.wav",
      "lockedSound": "sounds/door_rattle.wav",
      "blockedTiles": [{"x": 0, "y": 0, "elevation": 0}]
    }
  ]
}
```

### Wooden Chest Prefab

```json
{
  "name": "WoodenChest",
  "components": [
    {
      "type": "SpriteRenderer",
      "sprite": "sprites/objects/chest_closed.png"
    },
    {
      "type": "AudioSource"
    },
    {
      "type": "Chest",
      "locked": false,
      "contents": [],
      "closedSprite": "sprites/objects/chest_closed.png",
      "openSprite": "sprites/objects/chest_open.png",
      "openSound": "sounds/chest_open.wav"
    }
  ]
}
```

### Secret Wall Prefab

```json
{
  "name": "SecretWall",
  "components": [
    {
      "type": "SpriteRenderer",
      "sprite": "sprites/walls/wall_stone.png"
    },
    {
      "type": "AudioSource"
    },
    {
      "type": "SecretPassage",
      "blockedTiles": [{"x": 0, "y": 0, "elevation": 0}],
      "toggleable": false,
      "directlyInteractable": true,
      "revealSound": "sounds/stone_slide.wav"
    }
  ]
}
```

---

## Asset Browser Integration

Add Interactables category to prefab browser:

```java
// In AssetBrowserPanel
private void renderPrefabsSection() {
    // ...
    if (ImGui.treeNode("Interactables")) {
        renderPrefabList("gameData/prefabs/interactables");
        ImGui.treePop();
    }
}
```

### Quick Create Menu

Right-click in scene view:

```
Create >
  ├─ Empty GameObject
  ├─ Sprite
  ├─ Tilemap
  └─ Interactable >
      ├─ Simple Door
      ├─ Locked Door
      ├─ Teleport Door
      ├─ Wooden Chest
      ├─ Lever
      ├─ Sign
      └─ Secret Wall
```

---

## Component Registration

Register new components for Inspector and serialization:

```java
// In ComponentRegistry or similar
public static void registerInteractableComponents() {
    register(Door.class, "Door", "Interaction", MaterialIcons.Door);
    register(Chest.class, "Chest", "Interaction", MaterialIcons.Inventory);
    register(Lever.class, "Lever", "Interaction", MaterialIcons.ToggleOn);
    register(Sign.class, "Sign", "Interaction", MaterialIcons.Description);
    register(SecretPassage.class, "Secret Passage", "Interaction", MaterialIcons.Grid);
    register(Inventory.class, "Inventory", "Interaction", MaterialIcons.Backpack);
    register(InteractionController.class, "Interaction Controller", "Interaction", MaterialIcons.TouchApp);
}
```

### Add Component Menu

```
Add Component >
  ├─ Rendering
  │   └─ ...
  ├─ Physics
  │   └─ ...
  ├─ Audio
  │   └─ ...
  └─ Interaction
      ├─ Door
      ├─ Chest
      ├─ Lever
      ├─ Sign
      ├─ Secret Passage
      ├─ Inventory
      └─ Interaction Controller
```

---

## Testing in Editor

### Play Mode Testing

1. Enter play mode
2. Move player near interactable
3. Verify prompt appears
4. Press E to interact
5. Verify behavior (door opens, chest gives items, etc.)

### Validation Warnings

Show warnings in Inspector for invalid configurations:

```java
// In Door component or Inspector
public List<String> validate() {
    List<String> warnings = new ArrayList<>();

    if (locked && (requiredKey == null || requiredKey.isBlank())) {
        warnings.add("Door is locked but no required key set");
    }

    if (hasDestination() && (targetSpawnId == null || targetSpawnId.isBlank())) {
        warnings.add("Target scene set but no spawn point specified");
    }

    if (blockedTiles.isEmpty()) {
        warnings.add("No blocked tiles - door won't block movement");
    }

    return warnings;
}
```

Display in Inspector:

```
┌─────────────────────────────────────────────────────────────────────┐
│  ⚠ Door is locked but no required key set                          │
│  ⚠ No blocked tiles - door won't block movement                    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Files to Create/Modify

### New Files

| File | Purpose |
|------|---------|
| `gameData/prefabs/interactables/*.prefab` | Prefab templates |

### Modified Files

| File | Changes |
|------|---------|
| `editor/ui/fields/FieldEditors.java` | Add TileCoord list editor |
| `editor/panels/InspectorPanel.java` | Interactable visualization toggle |
| `editor/tools/SelectionTool.java` | Render interaction radius overlay |
| `editor/core/ComponentRegistry.java` | Register new components |

---

## Next Phase

Phase 7: Migration Guide
