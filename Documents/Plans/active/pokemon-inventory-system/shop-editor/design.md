# Plan: Shop Registry Editor Panel

## Context

The shop system plan created all data models (`ShopInventory`, `ShopEntry`, `ShopRegistry`) and a `ShopRegistryLoader` that loads `.shops.json` files via the asset pipeline. However, there is no editor panel to visually edit shop data — currently the Asset Browser shows the file with a store icon but clicking it only displays raw JSON via the `ReflectionEditorContent` fallback. This plan adds a `ShopRegistryEditorContent` following the same two-column pattern used by `ItemRegistryEditorContent` and `PokedexEditorContent`.

## Dependencies

- **shop-system** (completed) — `ShopInventory`, `ShopEntry`, `ShopRegistry`, `ShopRegistryLoader`
- **item-inventory** (completed) — `ItemDefinition`, `ItemRegistry` (for item name/price lookup in the shop entry table)
- **item-editor** (completed) — `ItemRegistryEditorContent` reference implementation

## Overview

A single `ShopRegistryEditorContent` that plugs into the existing `AssetEditorPanel` shell via the `@EditorContentFor(ShopRegistry.class)` annotation. The content provides:

- **Left column**: Searchable list of shops with add/delete buttons
- **Right column**: Detail editor for the selected shop's identity fields and item entries table

Double-clicking a `.shops.json` file in the Asset Browser opens the Asset Editor panel and loads the `ShopRegistryEditorContent` for that file.

The layout is a straightforward list + detail split. The right column contains an identity section (shopId, shopName) and an items table where each row shows the item name (resolved from `ItemRegistry`), itemId, stock value, and a remove button. Items with stock = -1 display "Unlimited".

```
+--[ Asset Editor: shops.shops.json ]--[Save][Undo][Redo]----------[=]--+
|                                                                        |
|  +--- Shop List --------+  +--- Shop Editor ----------------------+   |
|  |                       |  |                                      |   |
|  | [Search...         ]  |  |  > Identity                         |   |
|  |                       |  |  Shop ID   [viridian_pokemart     ] |   |
|  | > Viridian City       |  |  Shop Name [Viridian City Pokemart] |   |
|  |   Pewter City         |  |                                      |   |
|  |                       |  |  > Items                 [+ Add Item]|   |
|  |                       |  |  +----------------------------------+|   |
|  |                       |  |  |  Item          | Stock    |  x   ||   |
|  |                       |  |  |  Potion        | oo       |  x   ||   |
|  |                       |  |  |  Poke Ball     | oo       |  x   ||   |
|  |                       |  |  |  Antidote      | oo       |  x   ||   |
|  |                       |  |  |  Repel         | 10       |  x   ||   |
|  |                       |  |  +----------------------------------+|   |
|  |                       |  |                                      |   |
|  +-[+ Add]----[x Delete]-+  +--------------------------------------+   |
|                                                                        |
+------------------------------------------------------------------------+

oo = unlimited (-1), rendered as "Unlimited" text or infinity symbol
```

---

## Data Model Changes

No data model changes are needed. The existing classes are already editor-ready:

- `ShopInventory` has `@Getter` and `@Setter` (Lombok) for `shopId`, `shopName`, and `items`
- `ShopInventory.ShopEntry` has `@Getter` and `@Setter` for `itemId` and `stock`
- `ShopRegistry` has `addShop()`, `removeShop()`, `copyFrom()`, and `getAll()`
- `ShopRegistryLoader` supports `load()`, `save()`, `copyInto()` (hot-reload)

However, `ShopInventory.getItems()` currently returns an unmodifiable list. The editor needs to mutate the items list directly (add/remove entries). Two approaches:

**Option A — Add mutation methods to `ShopInventory`:**
```java
public void addEntry(ShopEntry entry) { items.add(entry); }
public void removeEntry(int index) { items.remove(index); }
```

**Option B — Expose the mutable list internally:**
Add a package-private or setter-accessible method for the editor to use. Since `@Setter` on the `items` field allows replacing the entire list, and undo works via full-registry snapshots (replace all items at once), either approach works.

**Recommended: Option A** — Cleaner API, matches the `addItem`/`removeItem` pattern on `ItemRegistry` and `ShopRegistry`.

---

## Detailed Class Designs

### `ShopRegistryEditorContent`

Implements `AssetEditorContent`, annotated with `@EditorContentFor(ShopRegistry.class)` for auto-discovery.

```
@EditorContentFor(ShopRegistry.class)
public class ShopRegistryEditorContent implements AssetEditorContent {

    // --- State ---
    private ShopRegistry editingRegistry;
    private String editingPath;
    private AssetEditorShell shell;
    private ShopInventory selectedShop;       // Currently selected shop
    private String selectedShopId;            // Stable reference for re-resolving after undo
    private ImString searchFilter;            // Search text buffer
    private boolean showDeleteShopPopup;      // Delete shop confirmation
    private boolean showDeleteEntryPopup;     // Delete entry confirmation
    private int pendingDeleteEntryIndex;      // Entry index to delete on confirm

    // --- Lifecycle ---
    +initialize(): void                        // Subscribe to asset change events
    +destroy(): void                           // Unsubscribe from events
    +onAssetLoaded(path, asset, shell): void   // Cast asset, reset selection
    +onAssetUnloaded(): void                   // Clear all state
    +getAssetClass(): Class<?>                 // Returns ShopRegistry.class

    // --- Rendering ---
    +render(): void                            // Two-column layout dispatch
    -renderShopList(): void                    // Left column: search, list, add/delete buttons
    -renderShopEditor(): void                  // Right column: identity + items table
    -renderIdentitySection(): void             // shopId, shopName fields
    -renderItemsSection(): void                // Items table with add/remove
    -renderItemRow(index, entry): void         // Single row: item name, itemId combo, stock, remove
    +renderPopups(): void                      // Delete confirmation modals

    // --- CRUD (Shops) ---
    -addNewShop(): void                        // Create with defaults, select it
    -deleteSelectedShop(): void                // Remove from registry, clear selection
    -generateUniqueShopId(base): String        // "new_shop", "new_shop_1", etc.

    // --- CRUD (Entries) ---
    -addNewEntry(): void                       // Add empty ShopEntry to selected shop
    -deleteEntry(index): void                  // Remove entry at index

    // --- Undo ---
    -captureStructuralUndo(desc, mutation): void  // SnapshotCommand.capture wrapper
    +onAfterUndoRedo(): void                      // Re-resolve selectedShop from selectedShopId

    // --- Save ---
    +hasCustomSave(): boolean                  // true
    +customSave(path): void                    // ShopRegistryLoader.save() + Assets.reload()

    // --- Asset creation ---
    +getCreationInfo(): AssetCreationInfo       // ("data/shops/", ".shops.json")
}
```

### `ShopRegistrySnapshot` (private inner class)

Deep-copies all `ShopInventory` entries in the registry for undo/redo. Stored as a `Map<String, ShopInventory>` where each shop is cloned via Gson round-trip (same pattern as `ItemRegistrySnapshot` and `PokedexSnapshot`).

```
private static class ShopRegistrySnapshot {
    private Map<String, ShopInventory> shops;

    +capture(registry): ShopRegistrySnapshot   // static — deep-copy all shops
    +restore(registry): void                   // Clear registry, re-add all cloned shops
}
```

Deep-copy approach: Serialize each `ShopInventory` to `JsonObject` via Gson, then deserialize back. This avoids manual field-by-field cloning and automatically handles future field additions. All fields are plain data (strings, ints, lists of entries) — no asset references to worry about.

---

## Field Editor Layout

### Left Column — Shop List

| Element | Widget | Notes |
|---------|--------|-------|
| Search box | `ImGui.inputTextWithHint()` | Filters by shopId or shopName (case-insensitive) |
| Shop list | `ImGui.selectable()` in scrolling child | Shows `shopName` per entry, sorted alphabetically |
| Add button | `FieldEditorUtils.accentButton(MaterialIcons.Add)` | Creates new shop with defaults |
| Delete button | `ImGui.button(MaterialIcons.Delete)` | Opens confirmation popup |

### Right Column — Shop Editor

Organized into collapsible sections using `ImGui.collapsingHeader()`:

#### Identity Section (MaterialIcons.Store)

| Field | Widget | Notes |
|-------|--------|-------|
| `shopId` | `PrimitiveEditors.drawString()` | Rename updates registry key. Validated: no empty, no duplicates |
| `shopName` | `PrimitiveEditors.drawString()` | Display name shown in the shop list |

#### Items Section (MaterialIcons.ShoppingCart)

A table with columns: **Item**, **Stock**, and **Remove**.

| Element | Widget | Notes |
|---------|--------|-------|
| Item name | `ImGui.text()` | Resolved from `ItemRegistry` — shows `def.getName()` or raw itemId if not found |
| Item ID | `ImGui.inputText()` or `ImGui.combo()` | Editable item reference. Could use a combo populated from `ItemRegistry.getAll()` for discoverability |
| Stock | `ImGui.inputInt()` | -1 = unlimited (displayed as "Unlimited" toggle or special value). Greater than 0 = limited |
| Unlimited toggle | `ImGui.checkbox()` | When checked, stock = -1. When unchecked, stock reverts to a positive default (e.g., 10) |
| Remove button | `ImGui.button(MaterialIcons.Close)` | Removes entry from the shop's items list |
| Add Item button | `FieldEditorUtils.accentButton(MaterialIcons.Add)` | Adds a new `ShopEntry` with empty itemId and stock = -1 |

**Stock editing UX:**

Each row shows a checkbox for "Unlimited" and a stock input field:
- When unlimited is checked, the stock input is disabled and displays -1
- When unlimited is unchecked, the stock input is enabled for editing

Alternatively, a simpler approach: just show the integer field where -1 means unlimited, with a tooltip explaining the convention. This matches the JSON format directly and avoids extra UI complexity.

**Recommended:** Simple integer field with -1 = unlimited convention and a tooltip. The checkbox approach can be added as polish later if needed.

**Item ID selection:**

The itemId field can be either:
- A plain text input (simple, matches JSON directly)
- A combo dropdown populated from `ItemRegistry.getAll()` (better UX, prevents typos)

**Recommended:** Combo dropdown with all items from the registry, showing `name (itemId)` per option. Falls back to text input if `ItemRegistry` is not loaded. This provides a better editing experience since the user doesn't need to memorize item IDs.

---

## Undo/Redo Strategy

Follows the `ItemRegistryEditorContent` pattern — full-registry snapshots via `SnapshotCommand.capture()`:

1. **Structural mutations** (add shop, delete shop, rename shopId, add entry, delete entry): `captureStructuralUndo(description, mutation)` wraps the mutation in a `SnapshotCommand.capture()` call that deep-copies the entire registry before and after.

2. **Field edits** (shopName, entry itemId, entry stock): Use `PrimitiveEditors` with getter/setter lambdas. The setter lambda calls `captureStructuralUndo()` to wrap the field mutation, ensuring every field edit is undoable.

3. **After undo/redo** (`onAfterUndoRedo`): Re-resolve `selectedShop` from `selectedShopId` since the registry contents have been replaced. If the shop no longer exists (e.g., undoing an add), clear the selection.

4. **Dirty tracking**: Every `captureStructuralUndo()` call also invokes `shell.markDirty()`.

---

## Save & Hot-Reload

- `hasCustomSave()` returns `true`
- `customSave(path)` creates a `ShopRegistryLoader` instance, calls `save(editingRegistry, fullPath)`, then `Assets.reload(path)` to update the cached registry in-place via `copyInto()`
- Status messages via `shell.showStatus()` for success/failure

---

## Phases

### Phase 1: Panel Skeleton + Data Model Tweak

- [ ] Add `addEntry(ShopEntry)` and `removeEntry(int)` methods to `ShopInventory`
- [ ] Create `ShopRegistryEditorContent` implementing `AssetEditorContent`
- [ ] Add `@EditorContentFor(ShopRegistry.class)` annotation
- [ ] Implement lifecycle methods: `initialize()`, `destroy()`, `onAssetLoaded()`, `onAssetUnloaded()`, `getAssetClass()`
- [ ] Implement `hasCustomSave()` + `customSave()` using `ShopRegistryLoader`
- [ ] Implement `getCreationInfo()` returning `("data/shops/", ".shops.json")`
- [ ] Stub out `render()` with two-column layout (empty left/right panes)
- [ ] Verify: double-clicking `.shops.json` in Asset Browser opens the editor with the two-column skeleton

### Phase 2: Shop List (Left Column)

- [ ] Search box with `ImGui.inputTextWithHint()` — filters by shopId or shopName (case-insensitive)
- [ ] Scrollable shop list using `ImGui.selectable()` — shows `shopName` per row
- [ ] Selection tracking via `selectedShop` + `selectedShopId`
- [ ] Sort shops alphabetically by shopName
- [ ] "New Shop" button — creates shop with generated unique ID and default name, selects it
- [ ] "Delete" button — opens confirmation popup (renders in `renderPopups()`)
- [ ] Implement `ShopRegistrySnapshot` inner class for undo
- [ ] Implement `captureStructuralUndo()` helper
- [ ] Wire add/delete through `captureStructuralUndo()` for undo support

### Phase 3: Shop Editor (Right Column)

- [ ] Identity section:
  - `shopId` via `PrimitiveEditors.drawString()` with rename logic (validate no duplicates, update registry key)
  - `shopName` via `PrimitiveEditors.drawString()`
- [ ] Items section header with "Add Item" button
- [ ] Items table using `ImGui.beginTable()` with columns: Item, Stock, Remove
- [ ] Each row:
  - Item column: combo dropdown populated from `ItemRegistry.getAll()` showing `name (itemId)`, or text input fallback
  - Stock column: int input field (-1 = unlimited, with tooltip)
  - Remove column: small button to remove entry
- [ ] Add item button creates new `ShopEntry("", -1)` appended to the shop's items list
- [ ] All field edits routed through `captureStructuralUndo()` for undo support
- [ ] Implement `onAfterUndoRedo()` to re-resolve `selectedShop` from `selectedShopId`

### Phase 4: Polish & Edge Cases

- [ ] Delete confirmation popups in `renderPopups()` using `ImGui.beginPopupModal()`:
  - Shop deletion: "Delete shop 'shopName'?"
  - Entry deletion (optional): could be immediate without confirmation since entries are lightweight
- [ ] Empty state: show help text when no shop is selected ("Select a shop to edit")
- [ ] Empty registry state: show prompt to create first shop
- [ ] Empty items list state: show prompt to add first item
- [ ] Status bar messages for save, delete, add operations
- [ ] Validate `shopId` on rename: no empty string, no whitespace, no duplicate IDs
- [ ] Visual indicator for items that don't exist in `ItemRegistry` (red text or warning icon)
- [ ] Tooltip on stock field explaining -1 = unlimited

### Phase 5: Testing

- [ ] Manual test: open `.shops.json` from Asset Browser — editor opens with shop list
- [ ] Manual test: add new shop — appears in list with default values, auto-selected
- [ ] Manual test: edit shopId and shopName — values persist in editor, dirty indicator shown
- [ ] Manual test: add item entry — new row appears in items table
- [ ] Manual test: change item entry's itemId via combo dropdown
- [ ] Manual test: change item entry's stock value
- [ ] Manual test: remove item entry — row removed from table
- [ ] Manual test: save — valid JSON written, hot-reload updates cached registry
- [ ] Manual test: delete shop with confirmation — shop removed, selection cleared
- [ ] Manual test: undo/redo through add shop, edit fields, add/remove entries, delete shop
- [ ] Manual test: search filter narrows shop list
- [ ] Manual test: rename shopId — registry key updated, no duplicates allowed
- [ ] Manual test: create new `.shops.json` via Ctrl+N — empty registry created
- [ ] Manual test: hot-reload shop data — verify changes reflected

---

## Files to Change

| File | Change | Phase |
|------|--------|-------|
| `shop/ShopInventory.java` | Add `addEntry()` and `removeEntry()` methods | 1 |
| `editor/panels/content/ShopRegistryEditorContent.java` | **NEW** — Full editor content | 1–4 |

---

## Key Patterns to Follow

- **@EditorContentFor annotation**: Auto-discovered by `AssetEditorContentRegistry` via Reflections — no manual wiring needed
- **Two-column layout**: `ImGui.beginChild()` for left/right split — left at 25% width (min 200px), right fills remainder
- **Getter/setter field editors**: `PrimitiveEditors.drawString()`, `PrimitiveEditors.drawInt()` — all accept `Supplier`/`Consumer` lambdas
- **Snapshot undo**: Full registry deep-copy before/after each mutation via `SnapshotCommand.capture()` — same as `ItemRegistryEditorContent`
- **Custom save**: `hasCustomSave()` = true, `customSave()` calls `ShopRegistryLoader.save()` then `Assets.reload()` for hot-reload
- **ID stability**: Track `selectedShopId` (String) separately from `selectedShop` (reference) — re-resolve in `onAfterUndoRedo()` since undo replaces the entire registry contents
- **Item combo dropdown**: Load `ItemRegistry` via `Assets.load()` to populate the item selection dropdown with actual item names — provides better UX than raw text input
- **No-arg constructor**: Required by `AssetEditorContentRegistry` for reflective instantiation

## Acceptance Criteria

- [ ] Double-clicking `.shops.json` in Asset Browser opens the Shop Registry Editor
- [ ] Shops can be added with auto-generated unique IDs and default names
- [ ] Shops can be deleted with a confirmation popup
- [ ] `shopId` and `shopName` are editable
- [ ] `shopId` rename updates registry key and rejects duplicates/empty values
- [ ] Shop entries can be added and removed
- [ ] Entry `itemId` is selectable via combo dropdown from `ItemRegistry`
- [ ] Entry `stock` is editable as integer (-1 = unlimited)
- [ ] Items not found in `ItemRegistry` display a warning indicator
- [ ] Save writes valid JSON readable by `ShopRegistryLoader`
- [ ] Hot-reload updates cached `ShopRegistry` after save
- [ ] Undo/redo works across add/delete shops, field edits, and add/remove entries
- [ ] Search filter narrows shop list by shopId or shopName
- [ ] New `.shops.json` files can be created via Ctrl+N

## Testing Plan

### Manual Tests

| Test | Steps | Expected |
|------|-------|----------|
| Open from Asset Browser | Double-click `shops.shops.json` | Editor opens, shop list populated |
| Add shop | Click add button | New shop appears with unique ID, auto-selected |
| Edit identity | Modify shopId, shopName | Dirty indicator shown, values update live |
| Add item entry | Click "Add Item" in items section | New row with empty itemId and unlimited stock |
| Edit entry itemId | Use combo to select "potion" | Item name column updates to "Potion" |
| Edit entry stock | Change stock to 10 | Value updates, dirty shown |
| Remove entry | Click remove button on row | Row removed from table |
| Save | Ctrl+S or save button | JSON written, status message, dirty cleared |
| Reload verification | Save, then re-open file | All edits preserved in JSON |
| Delete shop | Click delete, confirm | Shop removed from list, selection cleared |
| Undo add shop | Add shop → Ctrl+Z | Shop removed |
| Undo delete shop | Delete shop → Ctrl+Z | Shop restored with all entries |
| Undo entry edit | Edit stock → Ctrl+Z | Stock reverted |
| Redo | Ctrl+Z → Ctrl+Shift+Z | Change re-applied |
| Search filter | Type "viridian" in search | Only matching shops shown |
| Rename shopId | Change shopId to "cerulean_pokemart" | Registry key updated |
| Duplicate ID rejected | Rename to existing shopId | Validation prevents, error feedback |
| Invalid item warning | Set itemId to "nonexistent" | Warning indicator on row |
| Create new file | Ctrl+N in editor | Empty `.shops.json` created in `data/shops/` |
| Hot-reload | Edit JSON externally, save | Editor reflects changes without restart |
