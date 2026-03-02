# Phase 0: Nested Prefabs, PauseManager & PrefabCodeGenerator

## Summary

Phase 0 delivers three foundational capabilities needed before the menu system:

1. **Nested Prefab Runtime Support** — Allow prefab nodes to reference other prefabs. The menu UI (`pokemon_menu_ui.prefab.json`) is a nested prefab within the player prefab, containing further nested sub-prefabs (pokemon slots, confirmation dialogs).

2. **PauseManager (Reference-Counted)** — Replace the fragile per-consumer `pauseAll()`/`resumeAll()` pattern with a centralized, reference-counted pause system. Prevents the bug where one consumer resuming (e.g., menu closing) incorrectly unpauses systems still needed by another consumer (e.g., active dialogue).

3. **PrefabCodeGenerator Framework** — Extract the `DialogueUISceneInjector` pattern into a reusable utility for programmatically building prefab JSON from code. Used by `PokemonMenuPrefabGenerator` (Phase 2) and available for future UI systems.

## Current State

The `GameObjectData` class already has `prefab`, `componentOverrides`, and `childOverrides` fields. However, these are only used at the **scene level** (for top-level prefab instances in scene files). Inside a prefab's own `gameObjects` list, every node is treated as a scratch entity — only `components` is read, never `prefab`.

### What works today

```
Scene file (.scene.json)
└── GameObjectData (prefab: "player.prefab") ← WORKS: RuntimeSceneLoader handles this
    └── childOverrides for child nodes     ← WORKS: overrides applied after instantiation
```

### What doesn't work

```
player.prefab.json → gameObjects list:
├── rootNode (components: [...])
├── childNode1 (components: [...])                ← works (scratch node)
└── childNode2 (prefab: "menu_ui.prefab")         ← IGNORED: treated as empty scratch node
    └── componentOverrides / childOverrides         ← IGNORED: never read
```

## Design

### Data Model — No changes needed

`GameObjectData` already has all required fields:
- `prefab: String` — asset path to the nested prefab
- `componentOverrides: Map<String, Map<String, Object>>` — root overrides on the nested instance
- `childOverrides: Map<String, ChildNodeOverrides>` — child node overrides on the nested instance
- `components: List<Component>` — unused when `prefab` is set (the nested prefab provides components)

A node in a prefab's `gameObjects` list can be either:
1. **Scratch node** — has `components`, no `prefab` (current behavior)
2. **Nested prefab node** — has `prefab` path, optionally `componentOverrides`/`childOverrides`, no `components`

### Runtime Instantiation — `Prefab.instantiateChildren()`

Currently, `instantiateChildren()` iterates nodes and clones their `components`. For nested prefab nodes, it must instead load and instantiate the referenced prefab.

**Modified algorithm:**

```java
private void instantiateChildren(GameObject root) {
    List<GameObjectData> nodes = getGameObjects();
    if (nodes == null) return;

    GameObjectData rootNode = getRootNode();
    if (rootNode == null) return;

    Map<String, GameObject> nodeToGameObject = new HashMap<>();
    nodeToGameObject.put(rootNode.getId(), root);

    for (GameObjectData node : nodes) {
        if (node == rootNode) continue;
        if (node.getParentId() == null) continue;

        GameObject parent = nodeToGameObject.get(node.getParentId());
        if (parent == null) continue;

        GameObject child;

        if (node.isPrefabInstance()) {
            // ★ NESTED PREFAB: load and instantiate the referenced prefab
            child = instantiateNestedPrefab(node);
            if (child == null) {
                // Fallback: create empty placeholder
                child = new GameObject(node.getName() != null ? node.getName() : "NestedPrefab");
            }
        } else {
            // Existing behavior: scratch node with components
            child = createFromComponents(node);
        }

        parent.addChild(child);
        if (node.getId() != null) {
            nodeToGameObject.put(node.getId(), child);
        }
    }
}

private static GameObject instantiateNestedPrefab(GameObjectData node) {
    String prefabPath = node.getPrefab();
    if (prefabPath == null || prefabPath.isEmpty()) return null;

    JsonPrefab nestedPrefab = Assets.load(prefabPath, JsonPrefab.class);
    if (nestedPrefab == null) return null;

    // Get position from node's Transform override (if any)
    float[] pos = node.getPosition();
    Vector3f position = new Vector3f(pos[0], pos[1], pos[2]);

    // Instantiate the nested prefab (this recursively handles its own children,
    // including further nested prefabs)
    GameObject instance = nestedPrefab.instantiate(position, node.getComponentOverrides());

    // Apply name override
    if (node.getName() != null && !node.getName().isBlank()) {
        instance.setName(node.getName());
    }

    // Apply active state
    instance.setEnabled(node.isActive());

    // Apply child overrides on the nested prefab's children
    if (node.getChildOverrides() != null && !node.getChildOverrides().isEmpty()) {
        applyNestedChildOverrides(instance, nestedPrefab, node.getChildOverrides());
    }

    return instance;
}
```

**Key point:** `nestedPrefab.instantiate()` calls `instantiateChildren()` internally, which will recursively handle any further nested prefabs. No depth limit needed for practical use (menu → slot, 2 levels deep).

### Editor Expansion — `PrefabHierarchyHelper.expandChildren()`

Currently creates `EditorGameObject` instances from scratch nodes only. Must also handle nested prefab nodes.

**Modified algorithm:**

```java
public static List<EditorGameObject> expandChildren(EditorGameObject root, Prefab prefab) {
    // ... existing setup ...

    for (GameObjectData node : nodes) {
        if (node == rootNode) continue;
        if (node.getParentId() == null) continue;

        EditorGameObject parentEntity = nodeToEntity.get(node.getParentId());
        if (parentEntity == null) continue;

        if (node.isPrefabInstance()) {
            // ★ NESTED PREFAB NODE
            // Create an EditorGameObject that is both:
            //   - A child node of the outer prefab (has prefabNodeId from the outer prefab)
            //   - A prefab instance root itself (has its own prefabId from the nested prefab)
            String nestedPrefabPath = node.getPrefab();
            JsonPrefab nestedPrefab = Assets.load(nestedPrefabPath, JsonPrefab.class);

            if (nestedPrefab != null) {
                Vector3f position = getNodePosition(node);
                EditorGameObject nestedRoot = new EditorGameObject(
                    nestedPrefab.getId(), position
                );
                nestedRoot.setPrefabNodeId(node.getId());  // Track which outer node this is
                nestedRoot.setNestedPrefabPath(nestedPrefabPath);  // Track nested prefab source
                nestedRoot.setName(node.getName() != null ? node.getName() : nestedPrefab.getDisplayName());
                nestedRoot.setParent(parentEntity);
                nestedRoot.setOrder(node.getOrder());

                nodeToEntity.put(node.getId(), nestedRoot);
                descendants.add(nestedRoot);

                // Recursively expand the nested prefab's own children
                List<EditorGameObject> nestedDescendants = expandChildren(nestedRoot, nestedPrefab);
                descendants.addAll(nestedDescendants);
            }
        } else {
            // Existing behavior: scratch node
            // ... existing code ...
        }
    }

    return descendants;
}
```

### Editor Serialization — `EditorSceneSerializer`

When saving a scene that contains a prefab instance with nested prefab children, the nested prefab node should be serialized as part of the outer prefab's `childOverrides` — but with its `prefab` reference preserved, not flattened.

**`buildChildOverrides` changes:**
- For nested prefab child nodes, the override should capture:
  - The nested prefab's own root `componentOverrides` (if any fields differ from nested prefab defaults)
  - The nested prefab's own `childOverrides` (if any of its children have overrides)
  - Name and active overrides (same as regular nodes)

This is already structurally supported by `ChildNodeOverrides.componentOverrides`. The nested prefab's child overrides would need a new field or convention. For Phase 0, **nested prefab children are read-only** — overrides on the nested prefab's internal children are not supported (they use prefab defaults). This keeps the serialization simple and can be extended later.

### `EditorGameObject` Changes

Add a field to track that a child node is a nested prefab instance:

```java
// New field on EditorGameObject
private String nestedPrefabPath;  // Non-null if this child node references a nested prefab

public boolean isNestedPrefabInstance() {
    return nestedPrefabPath != null && !nestedPrefabPath.isEmpty();
}
```

This allows the editor to:
- Show a distinct icon/indicator for nested prefab nodes in the hierarchy panel
- Prevent editing components that come from the nested prefab (they're read-only, defined in the nested prefab file)
- Know which prefab file to look up defaults from

### `RuntimeSceneLoader.buildNodeIdMap` Changes

The `buildNodeIdMap` method matches prefab nodes to instantiated GameObjects by traversal order. For nested prefab nodes, the instantiated subtree is larger than a single node — it includes the entire nested prefab hierarchy.

**Change:** When a node is a nested prefab instance, skip into its subtree (the nested prefab's own children) rather than trying to match them against the outer prefab's node list. The nodeId map entry for the nested node points to the nested prefab's root `GameObject`. The nested prefab's internal children are not mapped in the outer nodeId map — they are only accessible through the nested prefab's own structure.

```java
private void mapChildrenRecursive(String nodeId, GameObject go,
                                   Map<String, List<GameObjectData>> childrenByParent,
                                   Map<String, GameObject> result,
                                   List<GameObjectData> allNodes) {
    List<GameObjectData> childNodes = childrenByParent.get(nodeId);
    if (childNodes == null) return;

    List<GameObject> goChildren = go.getChildren();
    int goChildIndex = 0;

    for (GameObjectData childNode : childNodes) {
        if (goChildIndex >= goChildren.size()) break;

        GameObject childGo = goChildren.get(goChildIndex);
        if (childNode.getId() != null) {
            result.put(childNode.getId(), childGo);
        }

        if (childNode.isPrefabInstance()) {
            // Nested prefab: the childGo is the nested root.
            // Its children belong to the nested prefab, not the outer one.
            // Do NOT recurse into childGo's children for outer nodeId mapping.
        } else {
            // Regular node: recurse normally
            mapChildrenRecursive(childNode.getId(), childGo, childrenByParent, result, allNodes);
        }

        goChildIndex++;
    }
}
```

### Reconciliation

`PrefabHierarchyHelper.reconcileInstance()` creates missing children when a prefab is updated. For nested prefab nodes:
- If the outer prefab gains a new nested prefab node, create the nested root + expand its children
- If the outer prefab removes a nested prefab node, flag the orphan (same as today)
- If the nested prefab itself changes (adds/removes internal nodes), re-expansion is needed — for Phase 0, this requires re-dropping the outer prefab instance

## JSON Format Example

### Outer prefab with nested prefab node

```json
{
  "id": "overworld_player",
  "displayName": "Player",
  "gameObjects": [
    {
      "id": "root001",
      "name": "Player",
      "components": [
        {"_type": "Transform", "localPosition": [0, 0, 0]},
        {"_type": "PlayerInput"},
        {"_type": "MenuManager"}
      ]
    },
    {
      "id": "dialogueui_001",
      "name": "DialogueUI Canvas",
      "parentId": "root001",
      "order": 0,
      "components": [
        {"_type": "Transform"},
        {"_type": "UICanvas", "sortOrder": 10}
      ]
    },
    {
      "id": "menuui_001",
      "name": "PokemonMenuUI",
      "parentId": "root001",
      "order": 2,
      "prefab": "gameData/assets/prefabs/pokemon_menu_ui.prefab.json",
      "componentOverrides": {
        "com.pocket.rpg.components.ui.UICanvas": {
          "sortOrder": 15
        }
      }
    }
  ]
}
```

Note how `menuui_001` has:
- `parentId: "root001"` — it's a child of the player root
- `prefab: "gameData/assets/prefabs/pokemon_menu_ui.prefab.json"` — references the menu prefab
- `componentOverrides` — overrides the UICanvas sort order
- No `components` — components come from the nested prefab

### Nested prefab with further nesting

```json
{
  "id": "pokemon_menu_ui",
  "displayName": "Pokemon Menu UI",
  "gameObjects": [
    {
      "id": "menu_root",
      "name": "MenuCanvas",
      "components": [
        {"_type": "Transform"},
        {"_type": "UICanvas", "sortOrder": 15}
      ]
    },
    {
      "id": "team_slot_0",
      "name": "PokemonSlot0",
      "parentId": "team_screen_001",
      "order": 0,
      "prefab": "gameData/assets/prefabs/pokemon_slot.prefab.json"
    },
    {
      "id": "confirm_dialog",
      "name": "ConfirmDialog",
      "parentId": "menu_root",
      "order": 3,
      "prefab": "gameData/assets/prefabs/confirm_dialog.prefab.json"
    }
  ]
}
```

---

## PauseManager (Reference-Counted)

### Problem

`PlayerDialogueManager` currently has private `pauseAll()`/`resumeAll()` methods that iterate `scene.getComponentsImplementing(IPausable.class)`. The menu system needs the exact same pattern. If both systems are active simultaneously (unlikely but possible in future edge cases), the first to resume will unpause everything — including systems still paused by the other consumer.

**Example bug without PauseManager:**
```
1. Menu opens → pauseAll() → GridMovement.paused = true
2. Cutscene triggers dialogue → pauseAll() → already paused, no-op
3. Menu closes → resumeAll() → GridMovement.paused = false  ← BUG: dialogue still active
4. Player can now move during dialogue
```

### Design

```java
/**
 * Centralized pause management with reference counting.
 * Located in: com.pocket.rpg.scenes (or com.pocket.rpg, near IPausable)
 */
public class PauseManager {

    private final Scene scene;
    private final Set<Object> activePauseOwners = new LinkedHashSet<>();

    public PauseManager(Scene scene) { this.scene = scene; }

    /** Request a pause. First caller triggers onPause() on all IPausables. */
    public void requestPause(Object owner) {
        boolean wasEmpty = activePauseOwners.isEmpty();
        activePauseOwners.add(owner);
        if (wasEmpty) {
            for (IPausable p : scene.getComponentsImplementing(IPausable.class)) {
                p.onPause();
            }
        }
    }

    /** Release a pause. Last release triggers onResume() on all IPausables. */
    public void releasePause(Object owner) {
        activePauseOwners.remove(owner);
        if (activePauseOwners.isEmpty()) {
            for (IPausable p : scene.getComponentsImplementing(IPausable.class)) {
                p.onResume();
            }
        }
    }

    /** True if any system has an active pause. */
    public boolean isPaused() { return !activePauseOwners.isEmpty(); }

    /** Number of active pause owners (for debugging). */
    public int getPauseCount() { return activePauseOwners.size(); }
}
```

### Access Pattern

`PauseManager` is owned by `Scene` and accessible via `scene.getPauseManager()`. Components access it through `getGameObject().getScene().getPauseManager()`.

### Migration

1. Add `PauseManager` field to `Scene`, initialized in constructor
2. Refactor `PlayerDialogueManager`:
   - Remove private `pauseAll()` / `resumeAll()` methods
   - Replace with `scene.getPauseManager().requestPause(this)` / `releasePause(this)`
3. `MenuManager` (Phase 2) uses the same API: `scene.getPauseManager().requestPause(this)` / `releasePause(this)`
4. Any future pause consumer follows the same pattern

### Tests

- Single owner: requestPause → IPausables paused. releasePause → IPausables resumed.
- Two owners: requestPause(A), requestPause(B), releasePause(A) → still paused. releasePause(B) → resumed.
- Duplicate request: requestPause(A), requestPause(A) → single entry (Set). releasePause(A) → resumed.
- isPaused() returns correct state at each step.
- getPauseCount() returns correct count.

---

## PrefabCodeGenerator Framework

### Problem

`DialogueUISceneInjector` builds UI hierarchies as raw Gson `JsonObject` trees with repeated boilerplate:
```java
private static JsonObject gameObject(String id, String name, boolean active, int order, String parentId, JsonObject... components) { ... }
private static JsonObject uiTransform(...) { ... }
private static JsonObject uiText(...) { ... }
private static JsonObject uiImage(...) { ... }
```

`PokemonMenuPrefabGenerator` (Phase 2) needs the exact same helpers. Future systems (shop UI, battle UI, PC storage UI) will too. Rather than copy-pasting, extract a reusable framework.

### Design

```java
/**
 * Base utility for programmatically building JSON prefab files.
 * Located in: com.pocket.rpg.tools (alongside DialogueUISceneInjector)
 */
public class PrefabCodeGenerator {

    private final String prefabId;
    private final String displayName;
    private final List<JsonObject> gameObjects = new ArrayList<>();
    private int idCounter = 0;

    public PrefabCodeGenerator(String prefabId, String displayName) {
        this.prefabId = prefabId;
        this.displayName = displayName;
    }

    // ── ID generation ──

    /** Generate a unique node ID. */
    public String newId() { return prefabId + "_" + String.format("%03d", idCounter++); }

    // ── Node builders ──

    /** Add a root node (no parentId). */
    public JsonObject addRootNode(String id, String name, JsonObject... components);

    /** Add a child node with components. */
    public JsonObject addNode(String id, String name, String parentId, int order, JsonObject... components);

    /** Add a nested prefab reference node (no components — components come from nested prefab). */
    public JsonObject addNestedPrefabNode(String id, String name, String parentId, int order, String prefabPath);

    // ── Component builders ──

    public static JsonObject transform(float x, float y, float z);
    public static JsonObject uiTransform(float anchorMinX, float anchorMinY, float anchorMaxX, float anchorMaxY,
                                          float offsetMinX, float offsetMinY, float offsetMaxX, float offsetMaxY);
    public static JsonObject uiCanvas(int sortOrder);
    public static JsonObject uiImage(String spritePath, String imageType);
    public static JsonObject uiText(String text, String fontPath, int fontSize, String alignment);
    public static JsonObject uiVerticalLayoutGroup(float spacing, boolean expandWidth);
    public static JsonObject component(String type, Map<String, Object> fields);

    // ── Serialization ──

    /** Serialize the built hierarchy to a prefab JSON string. */
    public String toJson();

    /** Write the prefab to a file path. */
    public void writeTo(Path outputPath) throws IOException;
}
```

### Usage Example (PokemonMenuPrefabGenerator)

```java
public class PokemonMenuPrefabGenerator {
    public static void main(String[] args) throws IOException {
        PrefabCodeGenerator gen = new PrefabCodeGenerator("pokemon_menu_ui", "Pokemon Menu UI");

        String rootId = gen.newId();
        gen.addRootNode(rootId, "MenuCanvas",
            PrefabCodeGenerator.transform(0, 0, 0),
            PrefabCodeGenerator.uiCanvas(15)
        );

        String mainPanelId = gen.newId();
        gen.addNode(mainPanelId, "MainMenuPanel", rootId, 0,
            PrefabCodeGenerator.uiTransform(0.7f, 0, 1, 1, 0, 0, 0, 0),
            PrefabCodeGenerator.uiImage("ui/menu_bg.png", "SLICED")
        );

        // ... more nodes ...

        gen.writeTo(Path.of("gameData/assets/prefabs/pokemon_menu_ui.prefab.json"));
    }
}
```

### Relationship to DialogueUISceneInjector

`DialogueUISceneInjector` injects into **scene files** (read scene → modify → write back). `PrefabCodeGenerator` creates **standalone prefab files**. They share the same component builder helpers but differ in output target. Options:
1. **Extract shared helpers only** — `PrefabCodeGenerator` for prefabs, `DialogueUISceneInjector` keeps its scene injection logic but calls shared helpers
2. **Full refactor** — `DialogueUISceneInjector` also uses `PrefabCodeGenerator` internally (optional, not required for MVP)

Option 1 is the MVP approach.

---

## Scope & Constraints

### In scope for Phase 0

**Nested Prefabs:**
- [x] Runtime instantiation of nested prefabs (`Prefab.instantiateChildren`)
- [x] Editor expansion of nested prefabs (`PrefabHierarchyHelper.expandChildren`)
- [x] Correct `buildNodeIdMap` traversal for nested nodes
- [x] `EditorGameObject.nestedPrefabPath` tracking
- [x] Scene serialization: nested prefab nodes saved as part of outer `childOverrides`
- [x] Basic reconciliation for outer prefab changes
- [x] Unit tests for nested instantiation

**PauseManager:**
- [x] `PauseManager` class with reference-counted `requestPause(owner)` / `releasePause(owner)`
- [x] `Scene.getPauseManager()` accessor
- [x] Migrate `PlayerDialogueManager` from private `pauseAll()`/`resumeAll()` to `PauseManager`
- [x] Unit tests for reference counting (single owner, dual owner, duplicate request)

**PrefabCodeGenerator:**
- [x] `PrefabCodeGenerator` utility class with node builders and component builders
- [x] JSON serialization to standalone `.prefab.json` files
- [x] Extract common helpers from `DialogueUISceneInjector` pattern
- [x] Unit test: build simple hierarchy → verify JSON structure

### Out of scope (future)

- Override editing of nested prefab internal children in the editor
- Drag-and-drop nested prefab reassignment in hierarchy panel
- Circular reference detection (not needed — prefabs don't reference themselves)
- Deep override cascading (outer → nested → nested's children)

## Files to Create / Modify

### New Files

| File | Description |
|------|-------------|
| `scenes/PauseManager.java` | Reference-counted pause system. Owned by `Scene`. |
| `tools/PrefabCodeGenerator.java` | Reusable utility for building prefab JSON from code. |

### Modified Files

| File | Change |
|------|--------|
| `prefab/Prefab.java` | `instantiateChildren()` — handle `node.isPrefabInstance()` by loading + instantiating nested prefab |
| `prefab/PrefabHierarchyHelper.java` | `expandChildren()` — create nested prefab root + recursively expand for nested nodes |
| `editor/scene/RuntimeSceneLoader.java` | `buildNodeIdMap()` / `mapChildrenRecursive()` — skip nested prefab subtrees for outer mapping |
| `editor/serialization/EditorSceneSerializer.java` | `buildChildOverrides()` — preserve nested prefab reference in child override data |
| `editor/scene/EditorGameObject.java` | Add `nestedPrefabPath` field + `isNestedPrefabInstance()` |
| `scenes/Scene.java` | Add `PauseManager` field + `getPauseManager()` accessor |
| `components/dialogue/PlayerDialogueManager.java` | Replace private `pauseAll()`/`resumeAll()` with `scene.getPauseManager().requestPause(this)` / `releasePause(this)` |

## Implementation Checklist

- [ ] Add `nestedPrefabPath` field + getter/setter + `isNestedPrefabInstance()` to `EditorGameObject`
- [ ] Modify `Prefab.instantiateChildren()`:
  - [ ] Check `node.isPrefabInstance()` before cloning components
  - [ ] Load nested prefab via `Assets.load()`
  - [ ] Call `nestedPrefab.instantiate()` with node's overrides
  - [ ] Apply name, active state, child overrides
  - [ ] Add nested root as child of parent GO
- [ ] Modify `PrefabHierarchyHelper.expandChildren()`:
  - [ ] Detect nested prefab nodes
  - [ ] Create `EditorGameObject` with both outer `prefabNodeId` and nested `prefabId`
  - [ ] Set `nestedPrefabPath` on the entity
  - [ ] Recursively expand nested prefab's own children
- [ ] Modify `RuntimeSceneLoader.mapChildrenRecursive()`:
  - [ ] Skip recursion into nested prefab subtrees when building outer nodeId map
- [ ] Modify `EditorSceneSerializer.buildChildOverrides()`:
  - [ ] Handle nested prefab child nodes — preserve prefab reference
- [ ] Modify `PrefabHierarchyHelper.reconcileInstance()`:
  - [ ] Handle missing nested prefab nodes (create + expand)
- [ ] Nested prefab unit tests:
  - [ ] Instantiate prefab with nested prefab node → verify full hierarchy created
  - [ ] Nested prefab with its own children → verify recursive expansion
  - [ ] Component overrides on nested prefab root → verify applied
  - [ ] Child overrides on nested prefab children → verify applied
  - [ ] `buildNodeIdMap` with nested prefab → verify outer nodes mapped correctly
  - [ ] Editor expansion with nested prefab → verify `EditorGameObject` tree correct
- [ ] Create `PauseManager` class:
  - [ ] `requestPause(Object owner)` — first request triggers `onPause()` on all IPausables
  - [ ] `releasePause(Object owner)` — last release triggers `onResume()` on all IPausables
  - [ ] `isPaused()` / `getPauseCount()` query methods
- [ ] Add `PauseManager` to `Scene`:
  - [ ] Field + constructor initialization
  - [ ] `getPauseManager()` accessor
- [ ] Migrate `PlayerDialogueManager`:
  - [ ] Remove private `pauseAll()` and `resumeAll()` methods
  - [ ] Replace with `getGameObject().getScene().getPauseManager().requestPause(this)` / `releasePause(this)`
  - [ ] Verify dialogue still pauses/resumes correctly
- [ ] PauseManager unit tests:
  - [ ] Single owner: request → paused, release → resumed
  - [ ] Dual owner: request(A), request(B), release(A) → still paused, release(B) → resumed
  - [ ] Duplicate request: request(A), request(A) → single entry, release(A) → resumed
  - [ ] isPaused() / getPauseCount() return correct state
- [ ] Create `PrefabCodeGenerator` utility:
  - [ ] Node builders: `addRootNode()`, `addNode()`, `addNestedPrefabNode()`
  - [ ] Component builders: `transform()`, `uiTransform()`, `uiCanvas()`, `uiImage()`, `uiText()`, `uiVerticalLayoutGroup()`, `component()`
  - [ ] ID generation: `newId()`
  - [ ] Serialization: `toJson()`, `writeTo(Path)`
- [ ] PrefabCodeGenerator unit tests:
  - [ ] Build simple hierarchy → verify JSON structure matches expected format
  - [ ] Nested prefab reference node → verify `prefab` field in JSON, no `components`
  - [ ] Component builder output matches existing Gson format

## Testing Strategy

### Unit Tests

**Nested instantiation:**
- Create a `JsonPrefab` with a node that has `prefab` field pointing to another `JsonPrefab`
- Call `instantiate()` on the outer prefab
- Verify the nested prefab's root and children appear in the hierarchy
- Verify component overrides on the nested root are applied
- Verify the nested prefab's own children have their components

**Two-level nesting:**
- Outer prefab → nested prefab → further nested prefab
- Verify all three levels instantiate correctly

**Override application:**
- Set `componentOverrides` on the nested node in the outer prefab
- Verify the nested prefab's root components have the overridden values

**Editor expansion:**
- Call `expandChildren()` on a prefab with nested prefab nodes
- Verify the returned `EditorGameObject` list includes nested children
- Verify `nestedPrefabPath` is set on nested roots

**PauseManager:**
- Single owner pause/resume cycle
- Dual owner with staggered release (A pauses, B pauses, A releases → still paused, B releases → resumed)
- Duplicate `requestPause(same owner)` → single entry in set, single release clears
- `isPaused()` and `getPauseCount()` at each step
- Integration: `PlayerDialogueManager` with PauseManager → dialogue pauses on start, resumes on end

**PrefabCodeGenerator:**
- Build a hierarchy with root + 2 children → verify JSON has correct `id`, `parentId`, `components` structure
- Add nested prefab reference node → verify `prefab` field present, no `components` array
- Component builders (`uiText`, `uiImage`, etc.) → verify `_type` and fields match engine format
- `writeTo(Path)` → verify file written with pretty-printed JSON

### Manual Tests

- Create a test prefab with a nested prefab node in JSON
- Drop it in a scene in the editor → verify hierarchy appears correctly
- Save the scene → verify the nested node is preserved in scene data
- Reload the scene → verify the nested hierarchy is restored
- Run the scene → verify the nested prefab instantiates at runtime
- Open dialogue → verify PauseManager pauses. Close dialogue → verify PauseManager resumes.
- Run PrefabCodeGenerator test builder → verify output `.prefab.json` loads in editor
