# Plan: Item Registry Editor Panel

## Context

The Item & Inventory system plan created all data models (`ItemDefinition`, `ItemCategory`, `ItemEffect`, `ItemRegistry`) and an `ItemRegistryLoader` that loads `.items.json` files via the asset pipeline. However, there is no editor panel to visually edit item data — currently the Asset Browser shows the file with a pharmacy icon but clicking it only displays raw JSON via the `ReflectionEditorContent` fallback. This plan adds an `ItemRegistryEditorContent` following the same two-column pattern used by `PokedexEditorContent` and `DialogueEditorContent`.

## Dependencies

- **item-inventory** (completed) — `ItemDefinition`, `ItemCategory`, `ItemEffect`, `ItemRegistry`, `ItemRegistryLoader`
- **pokemon-data** (completed) — `PokedexEditorContent` reference implementation

## Overview

A single `ItemRegistryEditorContent` that plugs into the existing `AssetEditorPanel` shell via the `@EditorContentFor(ItemRegistry.class)` annotation. The content provides:

- **Left column**: Searchable, filterable list of items with add/delete buttons and category filter
- **Right column**: Detail editor for the selected item's fields, with conditional visibility based on `ItemEffect`

Double-clicking a `.items.json` file in the Asset Browser opens the Asset Editor panel and loads the `ItemRegistryEditorContent` for that file.

Unlike the Pokedex editor, no tab bar is needed — the item registry contains only one entity type (`ItemDefinition`), so the layout is a straightforward list + detail split.

```
+--[ Asset Editor: items.items.json ]--[Save][Undo][Redo]----------[=]--+
|                                                                        |
|  +--- Item List --------+  +--- Item Editor ----------------------+   |
|  |                       |  |                                      |   |
|  | [Search...         ]  |  |  > Identity                         |   |
|  | [All Categories   v]  |  |    Item ID    [potion             ] |   |
|  |                       |  |    Name       [Potion              ] |   |
|  | > Antidote            |  |    Description[Restores 20 HP.     ] |   |
|  |   Burn Heal           |  |                                      |   |
|  |   Great Ball          |  |  > Category & Pricing                |   |
|  |   Ice Heal            |  |    Category   [MEDICINE           v] |   |
|  | * Potion              |  |    Price      [  200              ] |   |
|  |   Poke Ball           |  |    Sell Price [  100              ] |   |
|  |   Revive              |  |                                      |   |
|  |   Super Potion        |  |  > Behavior                         |   |
|  |   TM24                |  |    Battle Use [x]  Outside Use [x]  |   |
|  |   Thunder Stone       |  |    Consumable [x]  Stack Limit[99] |   |
|  |                       |  |                                      |   |
|  |                       |  |  > Effect                            |   |
|  |                       |  |    Effect     [HEAL_HP            v] |   |
|  |                       |  |    Value      [   20              ] |   |
|  |                       |  |                                      |   |
|  |                       |  |  > Sprite                            |   |
|  |                       |  |    Sprite    [potion.png#0] [...]   |   |
|  |                       |  |                                      |   |
|  +-[+ Add]----[x Delete]-+  +--------------------------------------+   |
|                                                                        |
+------------------------------------------------------------------------+

Conditional fields shown based on Effect value:

  Effect = TEACH_MOVE:                Effect = HEAL_STATUS:
  +----------------------------+      +----------------------------+
  |  > Effect                  |      |  > Effect                  |
  |    Effect  [TEACH_MOVE  v] |      |    Effect  [HEAL_STATUS v] |
  |    Move    [thunderbolt  ] |      |    Status  [POISON      v] |
  +----------------------------+      +----------------------------+
```

---

## Data Model Changes

The data model is already editor-ready:

- `ItemDefinition` already has `@Getter` and `@Setter` (Lombok)
- `ItemRegistry` already has `addItem()`, `removeItem()`, `copyFrom()`, and `getAll()`
- `ItemRegistryLoader` already supports `load()`, `save()`, `copyInto()` (hot-reload)

The only changes needed are to the editor infrastructure:

- Add `ITEM_REGISTRY_EDITOR("Item Registry Editor")` to `EditorPanelType`
- Override `getEditorPanelType()` in `ItemRegistryLoader` to return the new enum value

---

## Detailed Class Designs

### `ItemRegistryEditorContent`

Implements `AssetEditorContent`, annotated with `@EditorContentFor(ItemRegistry.class)` for auto-discovery.

```
@EditorContentFor(ItemRegistry.class)
public class ItemRegistryEditorContent implements AssetEditorContent {

    // --- State ---
    private ItemRegistry editingRegistry;
    private String editingPath;
    private AssetEditorShell shell;
    private ItemDefinition selectedItem;       // Currently selected item (may go stale after undo)
    private String selectedItemId;             // Stable reference for re-resolving after undo
    private ImString searchFilter;             // Search text buffer
    private ItemCategory categoryFilter;       // null = show all categories
    private boolean showDeletePopup;           // Triggers delete confirmation modal

    // --- Lifecycle ---
    +initialize(): void                        // Subscribe to asset change events
    +destroy(): void                           // Unsubscribe from events
    +onAssetLoaded(path, asset, shell): void   // Cast asset, reset selection/filters
    +onAssetUnloaded(): void                   // Clear all state
    +getAssetClass(): Class<?>                 // Returns ItemRegistry.class

    // --- Rendering ---
    +render(): void                            // Two-column layout dispatch
    -renderItemList(): void                    // Left column: search, filter, list, buttons
    -renderItemEditor(): void                  // Right column: field editors for selected item
    -renderIdentitySection(): void             // itemId, name, description
    -renderCategorySection(): void             // category, price, sellPrice
    -renderBehaviorSection(): void             // usableInBattle, usableOutside, consumable, stackLimit
    -renderEffectSection(): void               // effect, effectValue, conditional fields
    -renderSpriteSection(): void               // sprite (Sprite asset picker)
    +renderPopups(): void                      // Delete confirmation modal

    // --- CRUD ---
    -addNewItem(): void                        // Create with defaults, select it
    -deleteSelectedItem(): void                // Remove from registry, clear selection
    -generateUniqueId(base): String            // "new_item", "new_item_1", etc.

    // --- Undo ---
    -captureStructuralUndo(desc, mutation): void  // SnapshotCommand.capture wrapper
    +onAfterUndoRedo(): void                      // Re-resolve selectedItem from selectedItemId

    // --- Save ---
    +hasCustomSave(): boolean                  // true
    +customSave(path): void                    // ItemRegistryLoader.save() + Assets.reload()

    // --- Asset creation ---
    +getCreationInfo(): AssetCreationInfo       // ("data/items/", ".items.json")
}
```

### `ItemRegistrySnapshot` (private inner class)

Deep-copies all `ItemDefinition` entries in the registry for undo/redo. Stored as a `Map<String, ItemDefinition>` where each definition is cloned via Gson round-trip (same pattern as `PokedexSnapshot`).

```
private static class ItemRegistrySnapshot {
    private Map<String, ItemDefinition> items;

    +capture(registry): ItemRegistrySnapshot   // static — deep-copy all items
    +restore(registry): void                   // Clear registry, re-add all cloned items
}
```

Deep-copy approach: Serialize each `ItemDefinition` to `JsonObject` via Gson, then deserialize back. This avoids manual field-by-field cloning and automatically handles any future field additions. The `sprite` field (a `Sprite` object reference) is not owned data — it's an asset reference that survives cloning as-is (same object, loaded from the asset cache).

---

## Field Editor Layout

### Left Column — Item List

| Element | Widget | Notes |
|---------|--------|-------|
| Search box | `ImGui.inputTextWithHint()` | Filters by itemId or name (case-insensitive) |
| Category filter | `ImGui.combo()` | "All Categories" + 7 `ItemCategory` values |
| Item list | `ImGui.selectable()` in scrolling child | Shows `name` (itemId) per entry, sorted alphabetically |
| Add button | `FieldEditorUtils.accentButton(MaterialIcons.Add)` | Creates new item with defaults |
| Delete button | `ImGui.button(MaterialIcons.Delete)` | Opens confirmation popup |

### Right Column — Item Editor

Organized into collapsible sections using `ImGui.collapsingHeader()`:

#### Identity Section (MaterialIcons.Badge)

| Field | Widget | Notes |
|-------|--------|-------|
| `itemId` | `PrimitiveEditors.drawString()` | Editable — rename updates registry key. Validated: no spaces, no duplicates |
| `name` | `PrimitiveEditors.drawString()` | Display name |
| `description` | `PrimitiveEditors.drawString()` | Multiline or single-line |

#### Category & Pricing Section (MaterialIcons.Category)

| Field | Widget | Notes |
|-------|--------|-------|
| `category` | `EnumEditor.drawEnum()` | Dropdown of 7 `ItemCategory` values |
| `price` | `PrimitiveEditors.drawInt()` | Buy price, min 0 |
| `sellPrice` | `PrimitiveEditors.drawInt()` | Sell price, min 0 |

#### Behavior Section (MaterialIcons.Settings)

| Field | Widget | Notes |
|-------|--------|-------|
| `usableInBattle` | `PrimitiveEditors.drawBoolean()` | Checkbox |
| `usableOutside` | `PrimitiveEditors.drawBoolean()` | Checkbox |
| `consumable` | `PrimitiveEditors.drawBoolean()` | Checkbox |
| `stackLimit` | `PrimitiveEditors.drawInt()` | Min 1, max 999 |

#### Effect Section (MaterialIcons.AutoFixHigh)

| Field | Widget | Visibility |
|-------|--------|------------|
| `effect` | `EnumEditor.drawEnum()` | Always visible |
| `effectValue` | `PrimitiveEditors.drawInt()` | Visible when effect uses a numeric value (HEAL_HP, REVIVE, CAPTURE, REPEL, BOOST_*) |
| `teachesMove` | `PrimitiveEditors.drawString()` | Visible only when `effect == TEACH_MOVE` |
| `targetStatus` | `ImGui.combo()` with StatusCondition values | Visible only when `effect == HEAL_STATUS` |

**Conditional visibility logic:**

```
effectValue:    shown when effect is HEAL_HP, REVIVE, CAPTURE, REPEL,
                BOOST_ATK, BOOST_DEF, BOOST_SP_ATK, BOOST_SP_DEF,
                BOOST_SPD, BOOST_ACCURACY, BOOST_CRIT
teachesMove:    shown when effect is TEACH_MOVE
targetStatus:   shown when effect is HEAL_STATUS
```

The `targetStatus` field uses a custom combo dropdown rather than a raw text input:
- Options: "Cure All" (null/empty), "BURN", "PARALYZE", "SLEEP", "POISON", "FREEZE"
- Maps to the `StatusCondition` enum names (or null/empty for "cure all")

#### Sprite Section (MaterialIcons.Image)

| Field | Widget | Notes |
|-------|--------|-------|
| `sprite` | `AssetEditor.drawAsset()` | Sprite asset picker — uses getter/setter variant: `AssetEditor.drawAsset("Sprite", key, item::getSprite, val -> captureStructuralUndo(..., () -> item.setSprite(val)), Sprite.class)`. Renders clickable asset name + `[...]` picker button. |

---

## Undo/Redo Strategy

Follows the `PokedexEditorContent` pattern — full-registry snapshots via `SnapshotCommand.capture()`:

1. **Structural mutations** (add item, delete item, rename itemId): `captureStructuralUndo(description, mutation)` wraps the mutation in a `SnapshotCommand.capture()` call that deep-copies the entire registry before and after.

2. **Field edits** (name, price, effect, etc.): Use `PrimitiveEditors`, `EnumEditor`, and `AssetEditor` with getter/setter lambdas. The setter lambda calls `captureStructuralUndo()` to wrap the field mutation, ensuring every field edit is undoable.

3. **After undo/redo** (`onAfterUndoRedo`): Re-resolve `selectedItem` from `selectedItemId` since the registry contents have been replaced. If the item no longer exists (e.g., undoing an add), clear the selection.

4. **Dirty tracking**: Every `captureStructuralUndo()` call also invokes `shell.markDirty()`.

---

## Save & Hot-Reload

- `hasCustomSave()` returns `true`
- `customSave(path)` creates an `ItemRegistryLoader` instance, calls `save(editingRegistry, fullPath)`, then `Assets.reload(path)` to update the cached registry in-place via `copyInto()`
- Status messages via `shell.showStatus()` for success/failure

---

## Phases

### Phase 1: Infrastructure Wiring

- [ ] Add `ITEM_REGISTRY_EDITOR("Item Registry Editor")` to `EditorPanelType`
- [ ] Override `getEditorPanelType()` in `ItemRegistryLoader` to return `EditorPanelType.ITEM_REGISTRY_EDITOR`

### Phase 2: Panel Skeleton

- [ ] Create `ItemRegistryEditorContent` implementing `AssetEditorContent`
- [ ] Add `@EditorContentFor(ItemRegistry.class)` annotation
- [ ] Implement lifecycle methods: `initialize()`, `destroy()`, `onAssetLoaded()`, `onAssetUnloaded()`, `getAssetClass()`
- [ ] Implement `hasCustomSave()` + `customSave()` using `ItemRegistryLoader`
- [ ] Implement `getCreationInfo()` returning `("data/items/", ".items.json")`
- [ ] Stub out `render()` with two-column layout (empty left/right panes)
- [ ] Verify: double-clicking `.items.json` in Asset Browser opens the editor with the two-column skeleton

### Phase 3: Item List (Left Column)

- [ ] Search box with `ImGui.inputTextWithHint()` — filters by itemId or name (case-insensitive)
- [ ] Category filter dropdown — "All Categories" + 7 `ItemCategory` values
- [ ] Scrollable item list using `ImGui.selectable()` — shows `name (itemId)` per row
- [ ] Selection tracking via `selectedItem` + `selectedItemId`
- [ ] Sort items alphabetically by name
- [ ] "New Item" button — creates item with generated unique ID and default values, selects it
- [ ] "Delete" button — opens confirmation popup (renders in `renderPopups()`)
- [ ] Implement `ItemRegistrySnapshot` inner class for undo
- [ ] Implement `captureStructuralUndo()` helper
- [ ] Wire add/delete through `captureStructuralUndo()` for undo support

### Phase 4: Item Editor (Right Column)

- [ ] Identity section: `itemId`, `name`, `description` — using `PrimitiveEditors.drawString()` with getter/setter lambdas
- [ ] `itemId` edit with rename logic: validate no duplicates, update registry key (remove old, add with new key)
- [ ] Category & Pricing section: `category` enum dropdown, `price`/`sellPrice` int fields
- [ ] Behavior section: `usableInBattle`, `usableOutside`, `consumable` checkboxes, `stackLimit` int field
- [ ] Effect section: `effect` enum dropdown, `effectValue` int field
- [ ] Conditional field: `teachesMove` string field (visible when `effect == TEACH_MOVE`)
- [ ] Conditional field: `targetStatus` combo dropdown (visible when `effect == HEAL_STATUS`)
  - Options: "Cure All (any)", "BURN", "PARALYZE", "SLEEP", "POISON", "FREEZE"
  - Maps to null/empty or the StatusCondition enum name string
- [ ] Sprite section: `sprite` field using `AssetEditor.drawAsset()` with getter/setter variant and `Sprite.class` type
- [ ] All field edits routed through `captureStructuralUndo()` for undo support
- [ ] Implement `onAfterUndoRedo()` to re-resolve `selectedItem` from `selectedItemId`

### Phase 5: Polish & Edge Cases

- [ ] Delete confirmation popup in `renderPopups()` using `ImGui.beginPopupModal()`
- [ ] Empty state: show help text when no item is selected ("Select an item to edit")
- [ ] Empty registry state: show prompt to create first item
- [ ] Status bar messages for save, delete, add operations
- [ ] Validate `itemId` on rename: no empty string, no whitespace, no duplicate IDs
- [ ] Effect change side-effects: when switching `effect` away from `TEACH_MOVE`, clear `teachesMove`; when switching away from `HEAL_STATUS`, clear `targetStatus`

### Phase 6: Testing

- [ ] Manual test: open `.items.json` from Asset Browser — editor opens with item list
- [ ] Manual test: add new item — appears in list with default values, auto-selected
- [ ] Manual test: edit all fields — values persist in editor, dirty indicator shown
- [ ] Manual test: save — valid JSON written, hot-reload updates cached registry
- [ ] Manual test: delete item with confirmation — item removed, selection cleared
- [ ] Manual test: undo/redo through add, edit, delete operations
- [ ] Manual test: search filter narrows list, category filter narrows list, both combine
- [ ] Manual test: conditional fields appear/disappear based on effect selection
- [ ] Manual test: rename itemId — registry key updated, no duplicates allowed
- [ ] Manual test: create new `.items.json` via Ctrl+N — empty registry created

---

## Files to Change

| File | Change | Phase |
|------|--------|-------|
| `editor/EditorPanelType.java` | Add `ITEM_REGISTRY_EDITOR` | 1 |
| `resources/loaders/ItemRegistryLoader.java` | Override `getEditorPanelType()` | 1 |
| `editor/panels/ItemRegistryEditorContent.java` | **NEW** — Full editor content | 2–5 |

---

## Key Patterns to Follow

- **@EditorContentFor annotation**: Auto-discovered by `AssetEditorContentRegistry` via Reflections — no manual wiring in `EditorUIController` needed (unlike the older `EditorPanel` pattern)
- **Two-column layout**: `ImGui.beginChild()` for left/right split — left at 25% width (min 200px), right fills remainder
- **Getter/setter field editors**: `PrimitiveEditors.drawString()`, `PrimitiveEditors.drawInt()`, `PrimitiveEditors.drawBoolean()`, `EnumEditor.drawEnum()`, `AssetEditor.drawAsset()` — all accept `Supplier`/`Consumer` lambdas for asset editors without reflection
- **Snapshot undo**: Full registry deep-copy before/after each mutation via `SnapshotCommand.capture()` — same as `PokedexEditorContent`
- **Custom save**: `hasCustomSave()` = true, `customSave()` calls `ItemRegistryLoader.save()` then `Assets.reload()` for hot-reload
- **Sprite asset field**: `ItemDefinition.sprite` is a `Sprite` object (not a String path). Use `AssetEditor.drawAsset("Sprite", key, getter, setter, Sprite.class)` which renders a clickable asset name + picker button. The `Sprite` reference is an asset cache object — safe to share across snapshot copies
- **Conditional visibility**: Check `selectedItem.getEffect()` before rendering effect-specific fields — same ImGui frame, no push/pop asymmetry risk
- **ID stability**: Track `selectedItemId` (String) separately from `selectedItem` (reference) — re-resolve in `onAfterUndoRedo()` since undo replaces the entire registry contents

## Acceptance Criteria

- [ ] Double-clicking `.items.json` in Asset Browser opens the Item Registry Editor
- [ ] Items can be added with auto-generated unique IDs and default values
- [ ] Items can be deleted with a confirmation popup
- [ ] All `ItemDefinition` fields are editable: itemId, name, description, category, price, sellPrice, usableInBattle, usableOutside, consumable, stackLimit, sprite, effect, effectValue, teachesMove, targetStatus
- [ ] `teachesMove` field only shown when `effect == TEACH_MOVE`
- [ ] `targetStatus` field only shown when `effect == HEAL_STATUS`
- [ ] `effectValue` field only shown when `effect` uses a numeric parameter
- [ ] Enum dropdowns work for `ItemCategory` and `ItemEffect`
- [ ] `targetStatus` uses a combo dropdown with StatusCondition names + "Cure All" option
- [ ] `itemId` rename updates registry key and rejects duplicates/empty values
- [ ] Save writes valid JSON readable by `ItemRegistryLoader`
- [ ] Hot-reload updates cached `ItemRegistry` after save
- [ ] Undo/redo works across add, delete, and field-edit operations
- [ ] Search filter narrows item list by name or itemId
- [ ] Category filter narrows item list by `ItemCategory`
- [ ] New `.items.json` files can be created via Ctrl+N

## Testing Plan

### Manual Tests

| Test | Steps | Expected |
|------|-------|----------|
| Open from Asset Browser | Double-click `items.items.json` | Editor opens, item list populated |
| Add item | Click add button | New item appears with unique ID, auto-selected |
| Edit fields | Modify name, price, effect, etc. | Dirty indicator shown, values update live |
| Save | Ctrl+S or save button | JSON written, status message, dirty cleared |
| Reload verification | Save, then re-open file | All edits preserved in JSON |
| Delete item | Click delete, confirm | Item removed from list, selection cleared |
| Undo add | Add item → Ctrl+Z | Item removed |
| Undo delete | Delete item → Ctrl+Z | Item restored |
| Undo field edit | Edit name → Ctrl+Z | Name reverted |
| Redo | Ctrl+Z → Ctrl+Shift+Z | Change re-applied |
| Search filter | Type "pot" in search | Only items matching "pot" in name/id shown |
| Category filter | Select "MEDICINE" | Only medicine items shown |
| Combined filters | Search "heal" + category "MEDICINE" | Both filters applied |
| Conditional: TEACH_MOVE | Set effect to TEACH_MOVE | `teachesMove` field appears |
| Conditional: HEAL_STATUS | Set effect to HEAL_STATUS | `targetStatus` dropdown appears |
| Conditional: other effect | Set effect to HEAL_HP | `effectValue` shown, `teachesMove`/`targetStatus` hidden |
| Rename itemId | Change itemId to "super_potion" | Registry key updated, no stale references |
| Duplicate ID rejected | Rename to existing itemId | Validation prevents save, error feedback |
| Create new file | Ctrl+N in editor | Empty `.items.json` created in `data/items/` |
| Hot-reload | Edit JSON externally, save | Editor reflects changes without restart |
