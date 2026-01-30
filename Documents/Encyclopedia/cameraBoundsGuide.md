# Camera Bounds Guide

> **Summary:** Camera bounds zones define rectangular regions that clamp the game camera, preventing it from scrolling beyond the intended area. Use multiple zones per scene for areas like outdoor regions and indoor rooms, switching bounds automatically when the player warps between them.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [CameraBoundsZone Component](#cameraboundszone-component)
4. [Setting Up Bounds](#setting-up-bounds)
5. [Initial Bounds (Scene Camera)](#initial-bounds-scene-camera)
6. [Warp-Based Bounds Switching](#warp-based-bounds-switching)
7. [Editor Tools](#editor-tools)
8. [Workflows](#workflows)
9. [Tips & Best Practices](#tips--best-practices)
10. [Troubleshooting](#troubleshooting)
11. [Code Integration](#code-integration)
12. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create a bounds zone | Add entity > Add `CameraBoundsZone` component > Set boundsId and min/max |
| Set initial bounds on scene load | Camera Inspector > Initial Bounds dropdown > Select zone |
| Switch bounds on warp | SpawnPoint Inspector > Camera section > Select cameraBoundsId |
| Edit bounds visually | Select CameraBoundsZone entity > Click "Edit Bounds" in inspector > Drag handles |
| Remove camera bounds | Camera Inspector > Initial Bounds > Select "(none)" |

---

## Overview

Camera bounds prevent the game camera from scrolling past the edges of a playable area. Without bounds, the player could see empty space beyond the level.

The system uses **CameraBoundsZone** components to define named rectangular regions. Each zone has a `boundsId` string (e.g., `"outdoor"`, `"playerHouse"`) and a rectangle defined by `minX`, `minY`, `maxX`, `maxY` in world coordinates.

### How Bounds Are Activated

```
Fresh game start:
  SceneData.initialBoundsId  -->  CameraBoundsZone.boundsId  -->  camera.setBounds()

Warping between areas:
  SpawnPoint.cameraBoundsId  -->  CameraBoundsZone.boundsId  -->  camera.setBounds()

Loading a save:
  SaveManager global state   -->  CameraBoundsZone.boundsId  -->  camera.setBounds()
```

The priority order on scene load is:
1. **Saved state** (`SaveManager.getGlobal("camera", "activeBoundsId")`) - if loading from a save file
2. **Initial bounds** (`SceneData.initialBoundsId`) - for fresh game starts

---

## CameraBoundsZone Component

Category: **Interaction**

### Inspector Fields

| Field | Type | Description |
|-------|------|-------------|
| boundsId | String (Required) | Unique identifier for this zone. Referenced by SpawnPoints and scene camera settings. |
| minX | float | Left edge of the bounds rectangle (world units) |
| minY | float | Bottom edge of the bounds rectangle (world units) |
| maxX | float | Right edge of the bounds rectangle (world units) |
| maxY | float | Top edge of the bounds rectangle (world units) |

### Inspector Layout

```
+--------------------------------------+
| v CameraBoundsZone                   |
|   Bounds Id   [ outdoor        ]     |
|                                      |
|   [ Edit Bounds ]   (toggle button)  |
|                                      |
|   Min X       [-22.9           ]     |
|   Min Y       [-14.9           ]     |
|   Max X       [ 14.9           ]     |
|   Max Y       [ 12.9           ]     |
+--------------------------------------+
```

The **Edit Bounds** button activates the BoundsZoneTool for interactive handle-based editing in the viewport. When active, the button turns red and reads "Stop Editing".

### Gizmo

The component always draws a red dashed rectangle outline at the bounds edges, with:
- A text label showing the boundsId at the top-left corner
- A diamond marker at the center

---

## Setting Up Bounds

### Creating a Bounds Zone Entity

1. Create a new entity in the Hierarchy panel (name it descriptively, e.g., `CameraBounds_Outdoor`)
2. Add the `CameraBoundsZone` component from the Interaction category
3. Set the **boundsId** to a unique identifier (e.g., `"outdoor"`)
4. Set min/max values to cover the playable area

The entity's Transform position does not affect the bounds - the bounds rectangle is defined purely by the minX/minY/maxX/maxY fields in world coordinates.

### Sizing the Bounds

The bounds rectangle should cover the entire playable area for a region. When the camera reaches an edge, it stops scrolling but the player can continue moving.

- **Too small**: Camera stops early, player sees clamped view before reaching the edge of the area
- **Too large**: Camera scrolls past the level edges, showing empty space
- **Just right**: Camera reaches the visual edge of the area as the player approaches it

---

## Initial Bounds (Scene Camera)

The scene camera inspector includes an **Initial Bounds** dropdown that determines which CameraBoundsZone is activated when the scene first loads (fresh game start, no save data).

### Setting Initial Bounds

1. Select the **Scene Camera** in the Hierarchy (camera icon)
2. In the Inspector, find the **Camera Bounds** section
3. Use the **Initial Bounds** dropdown to select a zone
4. Select `(none)` to start with no bounds

The dropdown automatically lists all CameraBoundsZone entities in the scene by their boundsId.

---

## Warp-Based Bounds Switching

When the player warps between areas within the same scene (e.g., entering a house), the SpawnPoint at the destination can switch the active camera bounds.

### SpawnPoint Camera Bounds

The SpawnPoint component has a **Camera** section in its inspector with a **Camera Bounds** dropdown. This lists all CameraBoundsZone entities in the scene.

| Field | Description |
|-------|-------------|
| cameraBoundsId | Bounds zone to activate on arrival. Leave empty for no change. |

### Example Setup

For a scene with outdoor and indoor areas:

```
Entity: CameraBounds_Outdoor
  CameraBoundsZone { boundsId: "outdoor", minX: -23, minY: -15, maxX: 15, maxY: 13 }

Entity: CameraBounds_House
  CameraBoundsZone { boundsId: "house", minX: 30, minY: 5, maxX: 40, maxY: 12 }

Entity: SpawnPoint_InHouse
  SpawnPoint { spawnId: "enter_house", cameraBoundsId: "house" }

Entity: SpawnPoint_OutHouse
  SpawnPoint { spawnId: "exit_house", cameraBoundsId: "outdoor" }

Entity: Warp_HouseDoor
  TriggerZone { ... }
  WarpZone { targetScene: "", targetSpawnId: "enter_house" }

Entity: Warp_HouseExit
  TriggerZone { ... }
  WarpZone { targetScene: "", targetSpawnId: "exit_house" }
```

When the player steps on the house door warp, they teleport to `SpawnPoint_InHouse`, which activates the `"house"` bounds. When they exit, `SpawnPoint_OutHouse` switches back to `"outdoor"` bounds.

### Save/Load Persistence

The active bounds ID is automatically persisted via SaveManager when a SpawnPoint activates it. On save file load, the saved bounds are restored instead of using the scene's initial bounds.

---

## Editor Tools

### BoundsZoneTool

When a CameraBoundsZone entity is selected, the BoundsZoneTool provides interactive drag handles in the viewport.

**Activating the tool:**
- Select a CameraBoundsZone entity (tool auto-activates)
- Or click **Edit Bounds** in the inspector

**Handles:**
- 4 corner handles (resize diagonally)
- 4 edge midpoint handles (resize one axis)
- Cursor changes to indicate resize direction

**Drag behavior:**
- Drag any handle to resize the bounds rectangle
- Minimum size of 1 unit in each dimension (prevents inversion)
- All changes support undo/redo

**Deactivating:**
- Click **Stop Editing** in the inspector
- Select a different entity
- Switch to another tool

---

## Workflows

### Adding Camera Bounds to an Existing Scene

1. Create a new entity named `CameraBounds_Main`
2. Add `CameraBoundsZone` component
3. Set boundsId to `"main"`
4. Click **Edit Bounds** and drag handles to cover the playable area
5. Select the Scene Camera
6. Set **Initial Bounds** to `"main"`

### Adding Indoor Area Bounds

1. Create entity `CameraBounds_Indoor`
2. Add `CameraBoundsZone`, set boundsId to `"indoor"`
3. Size the bounds to cover the indoor area
4. On the SpawnPoint that leads into the indoor area, set cameraBoundsId to `"indoor"`
5. On the SpawnPoint that leads back outside, set cameraBoundsId to the outdoor zone's boundsId

### Removing Bounds for a Scene

1. Select the Scene Camera
2. Set **Initial Bounds** to `(none)`
3. Clear cameraBoundsId on all SpawnPoints (or leave empty)
4. Optionally delete the CameraBoundsZone entities

---

## Tips & Best Practices

- **Use descriptive boundsId values**: `"outdoor"`, `"cave_level1"`, `"boss_room"` are clearer than `"zone1"`, `"zone2"`
- **One zone per distinct area**: Each region the player can be in should have its own bounds zone
- **Always set cameraBoundsId on warp SpawnPoints**: If you have multiple zones, every warp destination should specify which zone to activate
- **Test with play mode**: Warp between areas to verify bounds switch correctly
- **Bounds are in world space**: The entity's Transform position has no effect on the bounds rectangle
- **Red highlighting**: If boundsId is empty, the field shows a red required-field indicator
- **Broken references**: SpawnPoint inspector shows red if cameraBoundsId references a non-existent zone

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Camera not bounded on scene start | Set Initial Bounds in Camera Inspector to a valid zone |
| Bounds don't change when warping | Set cameraBoundsId on the destination SpawnPoint |
| SpawnPoint cameraBoundsId shows red | The referenced boundsId doesn't exist - check spelling or create the zone |
| Edit Bounds button does nothing | Make sure the entity is selected and has a CameraBoundsZone component |
| Drag handles not visible | Click "Edit Bounds" in the inspector to activate the BoundsZoneTool |
| Camera sees empty space | Increase bounds to cover the entire visual area, or decrease if bounds extend beyond level edges |
| Bounds reset after loading save | This is expected behavior - save files persist the active bounds via SaveManager |
| Old scenes crash after update | Old `useBounds`/`bounds` fields are ignored on load - add CameraBoundsZone entities manually |

---

## Code Integration

### Activating Bounds Programmatically

```java
// Find and apply a bounds zone by ID
for (GameObject obj : scene.getGameObjects()) {
    CameraBoundsZone zone = obj.getComponent(CameraBoundsZone.class);
    if (zone != null && "outdoor".equals(zone.getBoundsId())) {
        zone.applyBounds(camera);
        break;
    }
}
```

### Clearing Bounds

```java
// Remove camera bounds entirely
camera.clearBounds();
```

### SpawnPoint Auto-Application

SpawnPoint handles bounds switching automatically when `cameraBoundsId` is set:

```java
// In WarpZone after teleport:
SpawnPoint spawn = findSpawnPoint(targetSpawnId);
spawn.applyCameraBounds(camera);
// This searches the scene for the matching CameraBoundsZone
// and persists the active ID via SaveManager
```

---

## Related

- [Collision Entities Guide](collisionEntitiesGuide.md) - SpawnPoints, WarpZones, and other entity components
- [Components Guide](componentsGuide.md) - Creating and using components
- [Gizmos Guide](gizmosGuide.md) - Editor gizmo system
- [Custom Inspector Guide](customInspectorGuide.md) - How custom component inspectors work
