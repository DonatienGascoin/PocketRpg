# Nested Hierarchy Prefabs

## Overview

Prefabs currently store a flat `List<Component>` representing a single entity. This plan adds hierarchy support so prefabs can define a tree of entities (root + children), enabling compound objects like "Player with Shadow" or "NPC with DialogueZone".

### Key Design Decision

Reuse `GameObjectData` for the prefab's internal hierarchy -- the same format used by scene files. No new data classes. The prefab stores a flat list of `GameObjectData` entries with `parentId` references, exactly like a scene's `gameObjects` array.

---

## Prefab Format

### Current (single entity)
```json
{
  "id": "test_barrel",
  "displayName": "Test Barrel",
  "components": [
    {"type": "...Transform", "properties": {...}},
    {"type": "...SpriteRenderer", "properties": {...}}
  ]
}
```

### New (hierarchy via `gameObjects`)
```json
{
  "id": "player_with_shadow",
  "displayName": "Player With Shadow",
  "category": "Characters",
  "gameObjects": [
    {
      "id": "root01",
      "name": "Player With Shadow",
      "components": [
        {"type": "...Transform", "properties": {...}},
        {"type": "...SpriteRenderer", "properties": {...}}
      ]
    },
    {
      "id": "shadow01",
      "name": "Shadow",
      "parentId": "root01",
      "order": 0,
      "components": [
        {"type": "...Transform", "properties": {...}},
        {"type": "...SpriteRenderer", "properties": {...}}
      ]
    }
  ]
}
```

### Format Migration

The existing prefab JSON files (`poke_player`, `test_barrel`, `entity_1`) were created for testing only and will be deleted. No backward compatibility with the old `components`-only format is needed.

- All prefabs use the `gameObjects` format. The old `components` top-level field is removed from `JsonPrefab`.
- `Prefab.getComponents()` delegates to `getRootNode().getComponents()`.
- Entries in `gameObjects` must be scratch (no `prefabId` set). This is validated on load.

### Node ID Stability

Each `GameObjectData` in the prefab's `gameObjects` list has an `id` field (8-char UUID). This serves as the **stable node identifier** that scene instances reference via `prefabNodeId`.

**Rules:**
- Node IDs are generated when a child is first created (via `UUID.randomUUID().substring(0,8)`).
- Node IDs are preserved when saving/reloading the prefab (the `PrefabEditController` maintains a `workingEntityNodeIds` map).
- Node IDs are never reused across different nodes.
- When the prefab is saved, existing node IDs are preserved for unchanged children. New children get fresh IDs.
- `findNode(nodeId)` uses a `Map<String, GameObjectData>` lookup (built lazily, cached) for O(1) access.

---

## Instance Model

When a prefab instance is placed in a scene, **every node in the prefab tree becomes a real `EditorGameObject`**, parented via the existing hierarchy system.

### Why `prefabNodeId` is needed

A prefab instance's child entity needs to know **which node in the prefab definition** it corresponds to. This is essential for:
1. **Component resolution**: `getMergedComponents()` must clone components from the correct prefab node (child's components, not root's).
2. **Default values**: `getFieldDefault()` must return the correct node's defaults for the "bold field" and "reset to default" features.
3. **Override detection**: `isFieldOverridden()` compares the instance value against the correct node's defaults.
4. **Instance sync**: When the prefab changes, the engine can match each scene entity to its corresponding prefab node.

Without `prefabNodeId`, there is no way to link a scene child entity back to its definition in the prefab.

### Root entity
- `prefabId = "player_with_shadow"` (existing)
- `prefabNodeId = null` (root)
- `componentOverrides = {Transform: {localPosition: [5, 3, 0]}}` (existing)

### Child entity
- `prefabId = "player_with_shadow"` (same prefab)
- `prefabNodeId = "shadow01"` (NEW -- references `GameObjectData.id` from the prefab)
- `parentId = root.id` (existing hierarchy)
- `componentOverrides = {}` (per-child overrides, same mechanism)

### Scene file
```json
[
  {"id": "abc123", "prefabId": "player_with_shadow", "componentOverrides": {...}},
  {"id": "def456", "prefabId": "player_with_shadow", "prefabNodeId": "shadow01",
   "parentId": "abc123", "componentOverrides": {}}
]
```

---

## Grandchild Example (Depth > 1)

This example validates that the flat-list + `parentId` approach supports arbitrary nesting depth.

### Prefab definition
```json
{
  "id": "guard_tower",
  "displayName": "Guard Tower",
  "category": "Structures",
  "gameObjects": [
    {
      "id": "root0001",
      "name": "Guard Tower",
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.0, "y": 0.0, "z": 0.0},
          "localScale": {"x": 1.0, "y": 1.0, "z": 1.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 5}}
      ]
    },
    {
      "id": "guard001",
      "name": "Guard",
      "parentId": "root0001",
      "order": 0,
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.0, "y": 1.0, "z": 0.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 10}}
      ]
    },
    {
      "id": "helm0001",
      "name": "Helmet",
      "parentId": "guard001",
      "order": 0,
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.0, "y": 0.3, "z": 0.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 12}}
      ]
    },
    {
      "id": "weap0001",
      "name": "Spear",
      "parentId": "guard001",
      "order": 1,
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.3, "y": 0.0, "z": 0.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 11}}
      ]
    },
    {
      "id": "flag0001",
      "name": "Flag",
      "parentId": "root0001",
      "order": 1,
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.5, "y": 2.0, "z": 0.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 15}}
      ]
    }
  ]
}
```

### Hierarchy
```
Guard Tower (root0001)           ← root, no parentId
├── Guard (guard001)             ← child, parentId = root0001
│   ├── Helmet (helm0001)        ← grandchild, parentId = guard001
│   └── Spear (weap0001)        ← grandchild, parentId = guard001
└── Flag (flag0001)              ← child, parentId = root0001
```

### Scene file with overrides at every level
```json
[
  {
    "id": "abc1",
    "prefabId": "guard_tower",
    "componentOverrides": {
      "com.pocket.rpg.components.core.Transform": {
        "localPosition": {"x": 5.0, "y": 3.0, "z": 0.0}
      }
    }
  },
  {
    "id": "abc2",
    "prefabId": "guard_tower",
    "prefabNodeId": "guard001",
    "parentId": "abc1",
    "componentOverrides": {
      "com.pocket.rpg.components.rendering.SpriteRenderer": {
        "zIndex": 15
      }
    }
  },
  {
    "id": "abc3",
    "prefabId": "guard_tower",
    "prefabNodeId": "helm0001",
    "parentId": "abc2",
    "componentOverrides": {
      "com.pocket.rpg.components.rendering.SpriteRenderer": {
        "sprite": "com.pocket.rpg.rendering.resources.Sprite:equipment.png#gold_helmet",
        "zIndex": 17
      }
    }
  },
  {
    "id": "abc4",
    "prefabId": "guard_tower",
    "prefabNodeId": "weap0001",
    "parentId": "abc2",
    "componentOverrides": {}
  },
  {
    "id": "abc5",
    "prefabId": "guard_tower",
    "prefabNodeId": "flag0001",
    "parentId": "abc1",
    "componentOverrides": {
      "com.pocket.rpg.components.core.Transform": {
        "localPosition": {"x": -0.5, "y": 2.0, "z": 0.0}
      },
      "com.pocket.rpg.components.rendering.SpriteRenderer": {
        "zIndex": 20
      }
    }
  }
]
```

### Override resolution

| Entity | Field | Prefab Default | Override | Resolved |
|--------|-------|---------------|----------|----------|
| **Guard Tower** (root) | Transform.localPosition | 0, 0, 0 | **5, 3, 0** | **5, 3, 0** |
| **Guard** (child) | SpriteRenderer.zIndex | 10 | **15** | **15** |
| **Guard** (child) | Transform.localPosition | 0, 1, 0 | — | 0, 1, 0 |
| **Helmet** (grandchild) | SpriteRenderer.sprite | *(none)* | **equipment.png#gold_helmet** | **gold_helmet** |
| **Helmet** (grandchild) | SpriteRenderer.zIndex | 12 | **17** | **17** |
| **Helmet** (grandchild) | Transform.localPosition | 0, 0.3, 0 | — | 0, 0.3, 0 |
| **Spear** (grandchild) | *(all fields)* | *(from prefab)* | — | *(all defaults)* |
| **Flag** (child) | Transform.localPosition | 0.5, 2, 0 | **-0.5, 2, 0** | **-0.5, 2, 0** |
| **Flag** (child) | SpriteRenderer.zIndex | 15 | **20** | **20** |

Each entity's `getFieldDefault()` resolves against **its own node** via `prefab.findNode(prefabNodeId)`. Resetting the Helmet's zIndex returns 12 (from `helm0001`), not 5 (from `root0001`) or 10 (from `guard001`). The override system is uniform regardless of depth.

Note: `parentId` values in the scene file reference **scene entity IDs** (abc1, abc2...), not prefab node IDs. The `prefabNodeId` values reference back to the prefab definition for component resolution. This separation is what makes arbitrary depth work.

---

## Override System for Children

Overrides work **identically** for root and child entities because each is its own `EditorGameObject` with its own `componentOverrides` map. A parent and child can both have `SpriteRenderer` with different overrides -- there is no conflict because overrides are per-entity, not per-prefab.

### Complete override lifecycle for a child entity

1. **Component resolution** (`getMergedComponents()`):
   - Calls `prefab.findNode(prefabNodeId)` to get the child's `GameObjectData`
   - Clones the node's components
   - Applies `componentOverrides` on top
   - Caches result in `cachedMergedComponents`

2. **Reading a field value** (`getFieldValue(componentType, fieldName)`):
   - Checks `componentOverrides[componentType][fieldName]` first
   - Falls back to `prefab.getChildFieldDefault(prefabNodeId, componentType, fieldName)` for defaults from the correct child node

3. **Writing a field value** (`setFieldValue(componentType, fieldName, value)`):
   - Stores in `componentOverrides[componentType][fieldName]`
   - Must call `invalidateComponentCache()` to refresh merged components

4. **Override detection** (`isFieldOverridden(componentType, fieldName)`):
   - Checks if `componentOverrides` has the field
   - Compares against `getFieldDefault()` which resolves to the child node's default (not root's)
   - Returns true only if the value differs from the child node's default

5. **Reset to default** (`resetFieldToDefault(componentType, fieldName)`):
   - Removes the field from `componentOverrides`
   - Invalidates component cache
   - Value reverts to the child node's default from the prefab

6. **Reset all overrides** (`resetAllOverrides()`):
   - Clears all `componentOverrides`
   - Only affects this entity (root or child independently)

### Stale node handling

If `prefab.findNode(prefabNodeId)` returns null (node was removed from prefab):
- `getMergedComponents()` returns an empty list
- A broken-node indicator is shown (similar to broken prefab link)
- Console warning logged: "Prefab node 'shadow01' not found in 'player_with_shadow'"

---

## Instance Synchronization

When a prefab definition changes (children added/removed/reordered), existing scene instances may be out of sync.

### Strategy: Lazy reconciliation on scene load

When a scene is loaded and hierarchy is resolved:
1. For each prefab root instance, compare the scene's child entities against the prefab's `gameObjects` list.
2. **Missing children** (node exists in prefab but no scene entity has matching `prefabNodeId`): auto-create a new `EditorGameObject` with `prefabNodeId` set, parented to the root, with no overrides. Log: "Added missing prefab child 'shadow01' to instance 'abc123'."
3. **Orphaned children** (scene entity has `prefabNodeId` that no longer exists in prefab): mark as broken (same as stale node handling above). Do NOT auto-delete -- the user may have overrides they want to preserve. Show warning icon.
4. **Order changes**: Reorder children to match prefab's order field if the user hasn't manually reordered.

This reconciliation runs in `EditorScene.resolveHierarchy()` (or a new `reconcilePrefabInstances()` called after it).

### For runtime

`RuntimeSceneLoader` performs the same reconciliation during `loadGameObjectsWithHierarchy()` -- if a prefab node is missing from the scene data, it creates a default `GameObject` from the prefab node's components.

---

## Constraints & Scope

- Children in a prefab are always inline scratch (no nested prefab-in-prefab refs -- deferred).
- Prefab children in scenes cannot be reparented away from their root or deleted individually.
- Scratch entities CAN be parented under a prefab root (they coexist as "loose children" without `prefabNodeId`). Only entities with `prefabNodeId` are locked.
- Structure changes (add/remove children) happen in prefab edit mode only.
- "Unpack Prefab" (converting a prefab instance + children to independent scratch entities) is deferred to a future plan.

---

## Phase 1: Prefab Data Model

Add `gameObjects` field to `JsonPrefab`.

### Modified Files

**`src/main/java/com/pocket/rpg/prefab/JsonPrefab.java`**
- [ ] Replace `private List<Component> components` with `private List<GameObjectData> gameObjects`
- [ ] Add `private transient Map<String, GameObjectData> nodeIdIndex` -- lazy cached lookup map
- [ ] `getGameObjects()` -- returns `gameObjects`
- [ ] `setGameObjects(List<GameObjectData>)` -- sets `gameObjects`, clears `nodeIdIndex`
- [ ] `hasChildren()` -- true if `gameObjects` has entries with non-null `parentId`
- [ ] `findNode(String nodeId)` -- uses `nodeIdIndex` for O(1) lookup. Builds index lazily on first call.
- [ ] `getRootNode()` -- returns the `GameObjectData` with null `parentId`
- [ ] `getComponents()` -- delegates to `getRootNode().getComponents()`
- [ ] Delete old prefab test files (`poke_player.prefab.json`, `test_barrel.prefab.json`, `entity_1.prefab.json`)

**`src/main/java/com/pocket/rpg/prefab/Prefab.java`**
- [ ] Add `getGameObjects()` -- returns the prefab's `List<GameObjectData>`
- [ ] Add default `hasChildren()` → `false`
- [ ] Add default `findNode(String nodeId)` → `null`
- [ ] Add default `getChildFieldDefault(nodeId, componentType, fieldName)` -- finds node, gets field via `ComponentReflectionUtils.getFieldValue()`
- [ ] Add default `getNodeComponentsCopy(nodeId)` -- deep-clones a node's components
- [ ] Update `instantiate()` to also create child GameObjects when `hasChildren()`, parent them to root

### Unit Tests (`JsonPrefabHierarchyTest`)

Uses guard_tower fixture (root + 2 children + 2 grandchildren).

- [ ] `findNode_returnsChild` -- `findNode("guard001")` returns Guard node
- [ ] `findNode_returnsGrandchild` -- `findNode("helm0001")` returns Helmet node
- [ ] `findNode_invalidId_returnsNull` -- `findNode("doesNotExist")` returns null, no crash
- [ ] `getRootNode_returnsNullParentId` -- `getRootNode().getId() == "root0001"`
- [ ] `getRootNode_ignoresChildrenAndGrandchildren` -- root is not guard001 or helm0001
- [ ] `emptyGameObjects_getRootNode_returnsNull` -- empty list, no crash
- [ ] `hasChildren_withHierarchy_returnsTrue` -- guard_tower fixture
- [ ] `hasChildren_singleRoot_returnsFalse` -- prefab with only root node
- [ ] `getComponents_delegatesToRoot` -- returns root0001's components (not guard001's or helm0001's)
- [ ] `nodeIdIndex_builtLazily` -- first `findNode()` call builds index, subsequent calls reuse it
- [ ] `setGameObjects_clearsNodeIdIndex` -- after `setGameObjects()`, index is rebuilt on next `findNode()`

### Manual Verify
- [ ] `mvn compile`
- [ ] Create guard_tower test prefab JSON (as shown in Grandchild Example section)

---

## Phase 2: Instance Model -- `prefabNodeId`

Add `prefabNodeId` to `EditorGameObject` and `GameObjectData`. Update all override-related methods.

### Modified Files

**`src/main/java/com/pocket/rpg/serialization/GameObjectData.java`**
- [ ] Add `private String prefabNodeId` with getter/setter
- [ ] Ensure `children` field is null in prefab `gameObjects` entries (add note / validation)

**`src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java`**
- [ ] Add `private final String prefabNodeId` (null in existing constructors)
- [ ] New constructor: `EditorGameObject(String prefabId, String prefabNodeId, String name, Vector3f position)`
- [ ] Update private deserialization constructor to accept `prefabNodeId`
- [ ] Add `isPrefabChildNode()` → `prefabNodeId != null && !prefabNodeId.isEmpty()`
- [ ] **Update `getMergedComponents()`**: if `prefabNodeId != null`:
  - Call `prefab.findNode(prefabNodeId)` → if null, log warning, return empty list
  - Otherwise clone node's components and apply overrides (same logic as root)
- [ ] **Update `getFieldValue()`**: for prefab instances with `prefabNodeId != null`, fallback calls `prefab.getChildFieldDefault(prefabNodeId, ...)` instead of `prefab.getFieldDefault(...)`
- [ ] **Update `getFieldDefault()`**: same delegation based on `prefabNodeId`
- [ ] **Update `isFieldOverridden()`**: compares against correct node defaults (via updated `getFieldDefault()`)
- [ ] **Update `resetFieldToDefault()`**: resets to correct node defaults (already works because it removes the override and cache refresh pulls from correct node)
- [ ] **Update `toData()`**: set `data.setPrefabNodeId(prefabNodeId)`
- [ ] **Update `fromData()`**: read `data.getPrefabNodeId()`, pass to constructor
- [ ] **`setFieldValue()`**: call `invalidateComponentCache()` after storing override (currently missing for prefab instances)

### Unit Tests (`EditorGameObjectPrefabNodeTest`)

Uses guard_tower fixture. Tests child (guard001, zIndex default 10) and grandchild (helm0001, zIndex default 12) independently.

- [ ] `childNode_getMergedComponents_resolvesChildComponents` -- `prefabNodeId = "guard001"`, merged components include SpriteRenderer with zIndex 10
- [ ] `grandchildNode_getMergedComponents_resolvesGrandchildComponents` -- `prefabNodeId = "helm0001"`, merged components include SpriteRenderer with zIndex 12
- [ ] `childNode_getFieldDefault_returnsChildDefault` -- Guard's Transform.localPosition default is (0, 1, 0), not root's (0, 0, 0)
- [ ] `grandchildNode_getFieldDefault_returnsGrandchildDefault` -- Helmet's SpriteRenderer.zIndex default is 12, not Guard's 10 or root's 5
- [ ] `childNode_isFieldOverridden_comparesAgainstChildDefault` -- override Guard zIndex to 15 → overridden; set to 10 (matches default) → not overridden
- [ ] `grandchildNode_isFieldOverridden_comparesAgainstGrandchildDefault` -- override Helmet zIndex to 12 (matches default) → not overridden; set to 17 → overridden
- [ ] `childNode_resetFieldToDefault_revertsToChildDefault` -- reset Guard zIndex → 10 (not root's 5)
- [ ] `grandchildNode_resetFieldToDefault_revertsToGrandchildDefault` -- reset Helmet zIndex → 12 (not Guard's 10)
- [ ] `toData_fromData_roundTrip_preservesPrefabNodeId` -- round-trip for both child and grandchild
- [ ] `staleNodeId_getMergedComponents_returnsEmpty` -- `prefabNodeId = "removed"`, returns empty list, warning logged, no NPE
- [ ] `nullNodeId_behavesAsRoot` -- `prefabNodeId == null` resolves root components unchanged

### Manual Verify
- [ ] Regression: existing scenes load and display correctly after `prefabNodeId` addition

---

## Phase 3: Editor Instantiation

When placing a prefab with children, create all child entities automatically.

### New File

**`src/main/java/com/pocket/rpg/prefab/PrefabHierarchyHelper.java`**
- [ ] `expandChildren(root, prefab)` -- creates child `EditorGameObject` instances from `prefab.getGameObjects()`. Sets parent relationships. Returns flat list of descendants (parent-first order).
- [ ] `captureHierarchy(root)` -- reverse: walks root + children, calls `toData()` for each. Returns `List<GameObjectData>` for `prefab.setGameObjects()`.
- [ ] `collectAll(root)` -- collects root + all descendants in **parent-first order** (critical for `AddEntitiesCommand` undo correctness).
- [ ] `reconcileInstance(root, prefab, scene)` -- compares scene children against prefab nodes. Creates missing children, flags orphans.

### Modified Files

**`src/main/java/com/pocket/rpg/prefab/JsonPrefabLoader.java`** (`instantiate()`)
- [ ] After creating root entity, call `PrefabHierarchyHelper.expandChildren(entity, asset)` to create children
- [ ] Children are parented to root via existing hierarchy system

**`src/main/java/com/pocket/rpg/editor/assets/SceneViewportDropTarget.java`**
- [ ] After `handleDrop()`, check `entity.hasChildren()`
- [ ] If children: `PrefabHierarchyHelper.collectAll(entity)` → `AddEntitiesCommand`
- [ ] If no children: existing `AddEntityCommand`

**`src/main/java/com/pocket/rpg/editor/assets/HierarchyDropTarget.java`**
- [ ] Same pattern in all 4 static drop methods

**`src/main/java/com/pocket/rpg/editor/panels/hierarchy/EntityCreationService.java`** (`cloneEntity()`)
- [ ] Add `isPrefabChildNode()` case: `new EditorGameObject(prefabId, prefabNodeId, name, position)` + copy overrides
- [ ] `duplicateChildrenRecursive()` already handles children -- no change

**`src/main/java/com/pocket/rpg/editor/scene/EditorScene.java`** (or new reconciliation step)
- [ ] After `resolveHierarchy()`, call `reconcilePrefabInstances()` to auto-create missing children and flag orphans

**`src/main/java/com/pocket/rpg/editor/PrefabEditController.java`** (`invalidateInstanceCaches()`, `getInstanceCount()`)
- [ ] `invalidateInstanceCaches()`: already iterates by `prefabId` -- children share the same `prefabId` so they're naturally included
- [ ] `getInstanceCount()`: filter to `!isPrefabChildNode()` to avoid double-counting children as separate instances

### Unit Tests (`PrefabHierarchyHelperTest`)

Uses guard_tower fixture (5 nodes, 3 levels deep).

- [ ] `expandChildren_createsAllDescendants` -- returns 4 descendants (Guard, Helmet, Spear, Flag) in parent-first order
- [ ] `expandChildren_grandchildParentedToChild` -- Helmet's parent is Guard, not root
- [ ] `expandChildren_setsCorrectPrefabNodeId` -- each expanded entity has matching `prefabNodeId` (guard001, helm0001, weap0001, flag0001)
- [ ] `expandChildren_flatPrefab_returnsEmpty` -- single-root prefab returns empty list
- [ ] `captureHierarchy_roundTrips` -- `expandChildren()` → `captureHierarchy()` produces equivalent `gameObjects` list
- [ ] `collectAll_parentFirstOrder` -- root before Guard before Helmet/Spear, root before Flag
- [ ] `reconcile_missingGrandchild_autoCreated` -- remove helm0001 from scene, reconcile → Helmet re-created with correct parentId under Guard and `prefabNodeId = "helm0001"`
- [ ] `reconcile_missingChildWithGrandchildren_autoCreatesAll` -- remove guard001 + Helmet + Spear from scene, reconcile → Guard + Helmet + Spear all re-created with correct hierarchy
- [ ] `reconcile_orphanedGrandchild_flaggedAsBroken` -- scene has entity with `prefabNodeId` pointing to removed node, flagged as broken

### Manual Verify
- [ ] Drop guard_tower onto viewport. Hierarchy shows: Guard Tower → Guard → Helmet + Spear, Guard Tower → Flag.
- [ ] Undo removes all 5 entities. Redo restores all with parent-child links intact (Helmet still under Guard).
- [ ] Duplicate guard_tower instance: fresh scene IDs, same `prefabNodeId` values, grandchildren parented to cloned Guard (not original).
- [ ] Drop single-root prefab: single entity (regression).
- [ ] Multiple rapid drops of same prefab: no ID collisions.
- [ ] Scene with missing grandchild (prefab gained helm0001 since save): auto-created under Guard on load.
- [ ] Scene with orphaned grandchild (prefab removed helm0001 since save): flagged with warning, no crash.

---

## Phase 4: Runtime Scene Loading

Handle `prefabNodeId` during runtime instantiation.

### Modified Files

**`src/main/java/com/pocket/rpg/editor/scene/RuntimeSceneLoader.java`** (`createPrefabInstance()`)
- [ ] If `goData.getPrefabNodeId() != null`:
  - `prefab.findNode(nodeId)` → if null, log error, return null
  - Use node's components as template, clone + apply overrides
  - Extract helper: `instantiateFromNode(name, position, nodeComponents, overrides, active)`
- [ ] If `goData.getPrefabNodeId() == null`: existing behavior
- [ ] During hierarchy resolution: if a prefab root has missing children (present in prefab def but not scene data), auto-create them from prefab defaults

### Unit Tests (`RuntimeSceneLoaderPrefabNodeTest`)
- [ ] `childNode_resolvesChildComponents` -- `prefabNodeId = "guard001"`, instantiated GameObject has Guard's components (zIndex 10)
- [ ] `grandchildNode_resolvesGrandchildComponents` -- `prefabNodeId = "helm0001"`, instantiated GameObject has Helmet's components (zIndex 12)
- [ ] `grandchildOverrides_applied` -- Helmet with zIndex override 17, resolved zIndex is 17 (not 12)
- [ ] `staleNodeId_logsWarning` -- `prefabNodeId = "removed"`, returns null, warning logged, no crash
- [ ] `missingGrandchild_autoCreatedFromPrefabDefaults` -- scene data lacks helm0001 entry, runtime creates Helmet from prefab defaults with correct parent

### Manual Verify
- [ ] Play mode with guard_tower: hierarchy correct (Guard Tower → Guard → Helmet + Spear, Guard Tower → Flag)
- [ ] Play mode with grandchild overrides (Helmet zIndex 17): override applied, renders at correct z-order
- [ ] Play mode with single-root prefabs: unchanged (regression)
- [ ] @ComponentReference from root component to grandchild component resolves correctly

---

## Phase 5: Prefab Edit Mode

Update `PrefabEditController` to edit the full hierarchy.

### Modified Files

**`src/main/java/com/pocket/rpg/editor/PrefabEditController.java`**
- [ ] **`buildWorkingEntity()`**: use `prefab.getGameObjects()` to create scratch entities via `EditorGameObject.fromData()`. All added to `workingScene`. Call `resolveHierarchy()`.
- [ ] Add `Map<EditorGameObject, String> workingEntityNodeIds` -- maps working entities to their original node IDs.
- [ ] **`save()`**: call `PrefabHierarchyHelper.captureHierarchy(workingEntity)`, using `workingEntityNodeIds` to preserve existing IDs. New children get fresh IDs. Set `targetPrefab.setGameObjects()`.
- [ ] Add `List<GameObjectData> savedGameObjects` field. Set in `enterEditMode()` and after save. Used by `resetToSaved()`.
- [ ] **`resetToSaved()`**: rebuild working entities from `savedGameObjects`.
- [ ] Prefab edit mode's working scene supports: creating children (via `AddEntityCommand`), deleting children (via `RemoveEntityCommand`), reordering children (via drag-drop).

**`src/main/java/com/pocket/rpg/editor/panels/inspector/PrefabInspector.java`**
- [ ] Render whichever entity is selected in the working scene (not always root).
- [ ] Metadata section (display name, category) only when root is selected.
- [ ] Component list for any selected entity (root or child).

### Manual Verify
- [ ] Open guard_tower in edit mode. Hierarchy shows root → Guard → Helmet + Spear, root → Flag.
- [ ] Select grandchild (Helmet). Inspector shows Helmet's components (Transform + SpriteRenderer with zIndex 12).
- [ ] Select child (Guard). Inspector shows Guard's components. Metadata section (displayName, category) NOT shown (only on root).
- [ ] Add component to grandchild (Helmet), save, verify `helm0001` entry in JSON has new component.
- [ ] Add new child under Guard (creating a new grandchild), save, verify new entry in `gameObjects` with fresh nodeId and `parentId = "guard001"`.
- [ ] Delete Guard (which has grandchildren Helmet + Spear): all 3 removed. Save, verify guard001/helm0001/weap0001 gone from JSON.
- [ ] Undo previous delete: Guard + Helmet + Spear restored with correct hierarchy.
- [ ] Reorder Guard's children (Spear before Helmet), save, verify order fields updated in JSON.
- [ ] Revert restores original hierarchy including grandchild order.
- [ ] Undo/redo works for add/remove/modify on grandchildren.
- [ ] Single-root prefab editing unchanged (regression).
- [ ] Edit prefab, close without saving (discard): no changes persisted.
- [ ] Two consecutive saves produce identical JSON (idempotency).

---

## Phase 6: Hierarchy Panel & Inspector UX

Visual distinction, interaction restrictions, and Save As Prefab updates.

### Modified Files

**`src/main/java/com/pocket/rpg/editor/utils/IconUtils.java`**
- [ ] Add icon for prefab child nodes (e.g., `MaterialIcons.AccountTree`)

**`src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyTreeRenderer.java`**
- [ ] Prefab child icon for `isPrefabChildNode()` entities
- [ ] Context menu: hide "Unparent", "Delete", "Duplicate" for prefab children. Show "Edit Prefab".
- [ ] Delete key handler: if selection includes prefab children, ignore them (only delete roots/scratch entities).

**`src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyDragDropHandler.java`**
- [ ] Skip drag initiation for `isPrefabChildNode()` entities
- [ ] Multi-select drag: exclude `isPrefabChildNode()` entities from the reparent action

**`src/main/java/com/pocket/rpg/editor/panels/inspector/EntityInspector.java`**
- [ ] For `isPrefabChildNode()`: show prefab name, node name ("Shadow in Player With Shadow"), override count
- [ ] "Edit Prefab" button → `RequestPrefabEditEvent`
- [ ] "Reset All" on a child resets only that child's overrides (existing behavior, but verify)

**`src/main/java/com/pocket/rpg/editor/panels/SavePrefabPopup.java`**
- [ ] Capture children: `PrefabHierarchyHelper.captureHierarchy(sourceEntity)` → `prefab.setGameObjects()`
- [ ] Component summary: show collapsible tree (root + each child with their component lists)
- [ ] If source entity has a mix of prefab children and scratch children: bake prefab children into scratch (deep-clone merged components, strip `prefabId`)

### Manual Verify
- [ ] Prefab children and grandchildren show with distinct icon in hierarchy.
- [ ] Drag prefab grandchild (Helmet): blocked.
- [ ] Delete key on prefab grandchild: no-op.
- [ ] No Delete/Unparent/Duplicate in context menu for grandchildren.
- [ ] Delete root: all children + grandchildren removed (5 entities total). Confirmation mentions descendant count. Undo restores all with correct nesting.
- [ ] Override grandchild field (Helmet zIndex → 17): bold display. "Reset to default" reverts to 12 (Helmet's default), not 10 (Guard's) or 5 (root's).
- [ ] Override child field (Guard zIndex → 15): bold display. "Reset to default" reverts to 10 (Guard's default), not 5 (root's).
- [ ] "Reset All" on grandchild (Helmet): resets only Helmet's overrides. Guard and root overrides untouched.
- [ ] Save entity with grandchildren as new prefab: `gameObjects` in JSON with correct `parentId` nesting, tree summary in popup shows 3 levels.
- [ ] Multi-select root + grandchild + external entity → delete: root deleted (with all descendants), external entity deleted, grandchild-only selection ignored.
- [ ] Selecting a prefab grandchild in the **scene viewport** (click on Helmet sprite) selects the Helmet entity.
- [ ] Inspector for grandchild shows: prefab name, node name ("Helmet in Guard Tower"), override count.

---

## Files Summary

| Phase | File | Action |
|-------|------|--------|
| 1 | `src/.../prefab/JsonPrefab.java` | MODIFY -- `gameObjects` field, node index, hierarchy queries |
| 1 | `src/.../prefab/Prefab.java` | MODIFY -- default methods, update `instantiate()` |
| 2 | `src/.../serialization/GameObjectData.java` | MODIFY -- add `prefabNodeId` |
| 2 | `src/.../editor/scene/EditorGameObject.java` | MODIFY -- `prefabNodeId`, merged components, override methods |
| 3 | `src/.../prefab/PrefabHierarchyHelper.java` | **NEW** -- expand, capture, collect, reconcile |
| 3 | `src/.../prefab/JsonPrefabLoader.java` | MODIFY -- expand children on instantiate |
| 3 | `src/.../editor/assets/SceneViewportDropTarget.java` | MODIFY -- multi-entity add |
| 3 | `src/.../editor/assets/HierarchyDropTarget.java` | MODIFY -- multi-entity add |
| 3 | `src/.../editor/panels/hierarchy/EntityCreationService.java` | MODIFY -- clone prefab children |
| 3 | `src/.../editor/scene/EditorScene.java` | MODIFY -- reconcile prefab instances after resolveHierarchy |
| 3 | `src/.../editor/PrefabEditController.java` | MODIFY -- instance count filter |
| 4 | `src/.../editor/scene/RuntimeSceneLoader.java` | MODIFY -- handle `prefabNodeId`, auto-create missing children |
| 5 | `src/.../editor/PrefabEditController.java` | MODIFY -- hierarchy edit/save/revert |
| 5 | `src/.../editor/panels/inspector/PrefabInspector.java` | MODIFY -- render selected entity |
| 6 | `src/.../editor/utils/IconUtils.java` | MODIFY -- prefab child icon |
| 6 | `src/.../editor/panels/hierarchy/HierarchyTreeRenderer.java` | MODIFY -- visual distinction, restrictions |
| 6 | `src/.../editor/panels/hierarchy/HierarchyDragDropHandler.java` | MODIFY -- block child drag |
| 6 | `src/.../editor/panels/inspector/EntityInspector.java` | MODIFY -- child node info |
| 6 | `src/.../editor/panels/SavePrefabPopup.java` | MODIFY -- capture children, tree summary |

## Existing Utilities to Reuse

| Utility | Location | Reuse For |
|---------|----------|-----------|
| `GameObjectData` | `serialization/GameObjectData.java` | Prefab hierarchy representation |
| `EditorGameObject.toData()/fromData()` | `editor/scene/EditorGameObject.java` | Working entities in prefab edit |
| `EditorScene.resolveHierarchy()` | `editor/scene/EditorScene.java` | Rebuild parent-child from parentId |
| `ComponentReflectionUtils.cloneComponent()` | `serialization/ComponentReflectionUtils.java` | Deep-cloning components |
| `AddEntitiesCommand` | `editor/undo/commands/AddEntitiesCommand.java` | Atomic add of root + children |
| `ComponentTypeAdapterFactory` | `serialization/custom/ComponentTypeAdapterFactory.java` | GameObjectData.components serialization |
| `RemoveEntityCommand` | `editor/undo/commands/RemoveEntityCommand.java` | Already handles children recursively |

## End-to-End Verification

Uses guard_tower fixture (root → Guard → Helmet + Spear, root → Flag) throughout.

1. `mvn compile` + `mvn test` after each phase.
2. Create guard_tower test prefab JSON. Load in editor — `hasChildren() == true`, 5 nodes in hierarchy.
3. Drop guard_tower in editor — full 3-level hierarchy appears (root, children, grandchildren).
4. Override root field (Transform.localPosition → 5,3,0) — bold display, reset reverts to (0,0,0).
5. Override child field (Guard SpriteRenderer.zIndex → 15) — bold display, reset reverts to 10 (Guard's default, not root's 5).
6. Override grandchild field (Helmet SpriteRenderer.zIndex → 17) — bold display, reset reverts to 12 (Helmet's default, not Guard's 10 or root's 5).
7. Override grandchild sprite (Helmet sprite → gold_helmet) — bold display, reset clears override.
8. "Reset All" on Helmet — only Helmet's overrides cleared, Guard and root overrides untouched.
9. Save scene, reload — all 5 entities persist with correct `prefabNodeId`, `parentId`, and `componentOverrides` at every level.
10. Enter play mode — runtime hierarchy correct. Guard parented to root, Helmet parented to Guard. All overrides applied.
11. Open guard_tower in prefab edit mode — full hierarchy editable. Select grandchild (Helmet), inspector shows Helmet's components.
12. In edit mode: add new grandchild under Guard, save. Load scene with existing instances. New grandchild auto-created on all instances.
13. In edit mode: remove Helmet (grandchild), save. Load scene. Orphaned Helmet entity flagged with warning, no crash.
14. In edit mode: delete Guard (has grandchildren). Helmet + Spear also removed. Undo restores all 3.
15. Save As Prefab on entity with grandchildren — `gameObjects` in JSON with correct `parentId` nesting, tree summary in popup shows 3 levels.
16. Two instances of guard_tower in same scene with different grandchild overrides (Helmet zIndex 17 vs 20) — independent, no cross-contamination.
17. Regression: single-root prefabs work unchanged.

## Deferred / Future Work

- **Nested prefab refs** (child is itself a prefab instance): requires extension of `gameObjects` entries to support `prefabId`
- **Unpack Prefab**: convert instance + children to independent scratch entities
- **Prefab variants**: prefab inheriting from another with some defaults changed
- **Asset browser preview**: show hierarchy badge/tooltip for prefabs with children
- **Side-by-side override comparison**: diff view for current values vs prefab defaults

## Code Review

- [ ] Review all modified files for consistency with existing patterns
- [ ] Verify undo/redo for: place, delete, duplicate, override, reset, revert
- [ ] Serialization round-trip: save → load → save produces identical JSON
- [ ] `AddEntitiesCommand` entity ordering: root always before children (parent-first)
- [ ] No NPEs from stale `prefabNodeId` or missing prefab
- [ ] `getInstanceCount()` excludes child nodes
- [ ] `invalidateComponentCache()` called on all override mutations
- [ ] `GameObjectData.children` field is null in prefab entries (no conflict with PersistentEntitySnapshot)
- [ ] Update `architecture.md` and `common-pitfalls.md` with prefab hierarchy info
- [ ] Ask user about `Documents/Encyclopedia/` update after implementation
