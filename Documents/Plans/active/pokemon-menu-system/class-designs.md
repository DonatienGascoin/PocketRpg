# Pokemon Menu System — Class Designs

All UI references use `@ComponentReference(source = Source.KEY)` for declarative resolution. No manual `resolveUI()` / `uiResolved` lazy patterns — the framework resolves key references at scene load time automatically.

## `MenuState` (enum)

```java
public enum MenuState {
    CLOSED,             // Menu not visible, OVERWORLD mode
    OPENING,            // Slide-in animation playing (input blocked)
    MAIN_MENU,          // Main menu panel visible, accepting input
    TEAM_SCREEN,        // Pokemon team screen
    INVENTORY_SCREEN,   // Bag/inventory screen
    CONFIRM_SAVE,       // Save confirmation dialog
    CONFIRM_OVERWRITE,  // "Already a save file, overwrite?" dialog
    CONFIRM_QUIT,       // Quit confirmation dialog
    SAVING,             // "Saving..." message (0.5s, input blocked)
    SAVE_SUCCESS,       // "Game saved!" auto-dismiss after 1s (no OK button)
    SAVE_FAILED,        // "Save failed" with OK
    CLOSING,            // Slide-out animation playing (input blocked)

    // ── Future screens (defined in screen-designs-pokedex.md / screen-designs-player-card.md) ──
    POKEDEX_LIST,       // Pokedex species list view
    POKEDEX_DETAIL,     // Pokedex species detail overlay (LEFT/RIGHT cycles species)
    PLAYER_CARD,        // Trainer card view (view-only, no sub-navigation)
}
```

---

## `SelectableSlot` (Component — reusable, in `components/ui/`)

A single slot in a selectable list. Each slot owns its own arrow and label UI elements. The slot is a self-contained unit placed on its own GameObject.

```java
@ComponentMeta(category = "UI")
public class SelectableSlot extends Component {

    // UI references (resolved by framework at load time via componentKey)
    @ComponentReference(source = Source.KEY)
    private UIText arrowText;

    @ComponentReference(source = Source.KEY)
    private UIText labelText;

    // ── API (called by SelectableList) ──

    /** Show or hide the selection arrow. */
    public void setSelected(boolean selected) {
        if (arrowText != null) {
            arrowText.setText(selected ? "<" : "");
        }
    }

    /** Update the display label. */
    public void setLabel(String text) {
        if (labelText != null) {
            labelText.setText(text);
        }
    }

    /** Get the current label text. */
    public String getLabel() {
        return labelText != null ? labelText.getText() : "";
    }

    /** Show or hide the entire slot (for scrolling — slots beyond visible range are hidden). */
    public void setVisible(boolean visible) {
        getGameObject().setEnabled(visible);
    }
}
```

No `resolveUI()`, no `uiResolved` flag, no string key fields. The `@ComponentReference(source = KEY)` annotation tells the framework to serialize the key string in JSON and resolve it at load time.

**Hierarchy per slot:**
```
SlotN (GameObject) — SelectableSlot component (componentKey: "menu_main_slot_0")
├── Arrow (GameObject) — UIText (componentKey: "menu_main_slot_0_arrow")
└── Label (GameObject) — UIText (componentKey: "menu_main_slot_0_label")
```

In the prefab JSON, the `SelectableSlot` component serializes as:
```json
{
  "_type": "SelectableSlot",
  "componentKey": "menu_main_slot_0",
  "arrowText": "menu_main_slot_0_arrow",
  "labelText": "menu_main_slot_0_label"
}
```

---

## `SelectableList` (Component — reusable, in `components/ui/`)

Manages a list of `SelectableSlot` components. Zero coupling to `PlayerInput` — input pushed by owning controller.

```java
@ComponentMeta(category = "UI")
public class SelectableList extends Component {

    public enum Orientation { VERTICAL, HORIZONTAL }

    // Slot references (resolved by framework — serialized as JSON string array of keys)
    @ComponentReference(source = Source.KEY)
    private List<SelectableSlot> slots;

    // Scroll indicators (optional — resolved by framework)
    @ComponentReference(source = Source.KEY, required = false)
    private UIText scrollUpIndicator;

    @ComponentReference(source = Source.KEY, required = false)
    private UIText scrollDownIndicator;

    // Configuration (serialized for editor)
    private int maxVisibleItems;
    private boolean wrapAround = false;
    private Orientation orientation = Orientation.VERTICAL;

    // State (transient — runtime only)
    private transient List<String> items = List.of();
    private transient int selectedIndex = 0;
    private transient int scrollOffset = 0;
    private transient boolean active = false;

    // Callbacks (transient)
    private transient Consumer<Integer> onSelect;
    private transient Runnable onCancel;
    private transient Consumer<Integer> onSelectionChanged;

    // ── PUSH API (called by owning controller, NOT by polling) ──

    /** Move selection in primary direction. +1 = down/right, -1 = up/left. */
    public void movePrimary(int delta) {
        if (!active || items.isEmpty()) return;
        int newIndex = selectedIndex + delta;
        if (wrapAround) {
            newIndex = ((newIndex % items.size()) + items.size()) % items.size();
        } else {
            newIndex = Math.max(0, Math.min(newIndex, items.size() - 1));
        }
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex;
            adjustScroll();
            updateVisuals();
            if (onSelectionChanged != null) onSelectionChanged.accept(selectedIndex);
        }
    }

    public void moveUp()   { movePrimary(-1); }
    public void moveDown() { movePrimary(+1); }

    /** Fires onSelect with current selectedIndex. */
    public void select() {
        if (!active || items.isEmpty()) return;
        if (onSelect != null) onSelect.accept(selectedIndex);
    }

    /** Fires onCancel. */
    public void cancel() {
        if (!active) return;
        if (onCancel != null) onCancel.run();
    }

    // ── CONFIGURATION API ──

    /** Set item labels. Resets selectedIndex to 0, scrollOffset to 0, calls updateVisuals().
     *  Does NOT fire onSelectionChanged — avoids spurious callbacks during setup.
     *  Callers should call onSelectionChanged manually after setItems() if needed. */
    public void setItems(List<String> items);
    public void setSelectedIndex(int index);
    public int getSelectedIndex();
    public int getItemCount();                // Current number of items
    public void setActive(boolean active);
    public void setOnSelect(Consumer<Integer> callback);
    public void setOnCancel(Runnable callback);
    public void setOnSelectionChanged(Consumer<Integer> callback);

    // ── INTERNAL ──

    private void adjustScroll() {
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + maxVisibleItems) {
            scrollOffset = selectedIndex - maxVisibleItems + 1;
        }
    }

    private void updateVisuals() {
        if (slots == null) return;
        for (int i = 0; i < slots.size(); i++) {
            int itemIndex = scrollOffset + i;
            SelectableSlot slot = slots.get(i);
            if (itemIndex < items.size()) {
                slot.setVisible(true);
                slot.setLabel(items.get(itemIndex));
                slot.setSelected(itemIndex == selectedIndex);
            } else {
                slot.setVisible(false);
            }
        }
        updateScrollIndicators();
    }

    private void updateScrollIndicators() {
        if (scrollUpIndicator != null) {
            scrollUpIndicator.setText(scrollOffset > 0 ? "▲" : "");
        }
        if (scrollDownIndicator != null) {
            scrollDownIndicator.setText(scrollOffset + maxVisibleItems < items.size() ? "▼" : "");
        }
    }
}
```

**Key design:** `List<SelectableSlot> slots` is resolved via `@ComponentReference(source = KEY)`. In JSON, it serializes as a string array of componentKeys:
```json
{
  "_type": "SelectableList",
  "componentKey": "menu_main_list",
  "slots": ["menu_main_slot_0", "menu_main_slot_1", "menu_main_slot_2", "menu_main_slot_3", "menu_main_slot_4", "menu_main_slot_5", "menu_main_slot_6"],
  "scrollUpIndicator": "menu_main_scroll_up",
  "scrollDownIndicator": "menu_main_scroll_down",
  "maxVisibleItems": 7,
  "wrapAround": false,
  "orientation": "VERTICAL"
}
```

**Interface summary:**

```java
// Push API
public void movePrimary(int delta);    // +1 = down/right, -1 = up/left
public void moveUp();                  // Alias for movePrimary(-1)
public void moveDown();                // Alias for movePrimary(+1)
public void select();                  // Fires onSelect(selectedIndex)
public void cancel();                  // Fires onCancel

// Configuration
public void setItems(List<String>);    // Set item labels, reset scroll
public void setSelectedIndex(int);     // Jump to index
public int getSelectedIndex();         // Current index
public void setActive(boolean);        // Enable/disable input
public void setOnSelect(Consumer<Integer>);
public void setOnCancel(Runnable);
public void setOnSelectionChanged(Consumer<Integer>);
```

---

## `InventorySlot` (Component — extends SelectableSlot, in `components/ui/`)

Specialized slot for inventory items. Adds a quantity label next to the item name.

```java
@ComponentMeta(category = "UI")
public class InventorySlot extends SelectableSlot {

    @ComponentReference(source = Source.KEY)
    private UIText quantityText;

    /** Set the item quantity display. */
    public void setQuantity(int quantity) {
        if (quantityText != null) {
            quantityText.setText(quantity > 1 ? "x" + quantity : "");
        }
    }

    /** Clear the quantity display. */
    public void clearQuantity() {
        if (quantityText != null) quantityText.setText("");
    }
}
```

**Hierarchy per inventory slot:**
```
InvSlotN (GameObject) — InventorySlot component (componentKey: "menu_inv_slot_0")
├── Arrow (GameObject) — UIText (componentKey: "menu_inv_slot_0_arrow")
├── ItemName (GameObject) — UIText (componentKey: "menu_inv_slot_0_label")
└── Quantity (GameObject) — UIText (componentKey: "menu_inv_slot_0_qty")
```

---

## `PokemonSlot` (Component — in `components/ui/`)

Specialized slot for the team screen. Extends `SelectableSlot` to add HP bar, level, status, and icon elements. Each is a child within the slot's nested prefab (`pokemon_slot.prefab.json`).

```java
@ComponentMeta(category = "UI")
public class PokemonSlot extends SelectableSlot {

    @ComponentReference(source = Source.KEY)
    private UIText levelText;

    @ComponentReference(source = Source.KEY)
    private UIText statusText;

    @ComponentReference(source = Source.KEY)
    private UIImage hpBarTrack;

    @ComponentReference(source = Source.KEY)
    private UIImage hpBarFill;

    @ComponentReference(source = Source.KEY)
    private UIText hpText;

    @ComponentReference(source = Source.KEY, required = false)
    private UIImage iconImage;    // Future: species sprite

    @ComponentReference(source = Source.KEY, required = false)
    private UIText heldItemIndicator;    // Shows "♦" when Pokemon holds an item

    // ── Pokemon-specific API ──

    /** Populate slot from a live PokemonInstance. */
    public void setPokemon(PokemonInstance pokemon) {
        setLabel(pokemon.getDisplayName());
        setSelected(false);

        if (levelText != null) levelText.setText("Lv." + pokemon.getLevel());

        updateStatusLabel(pokemon.getStatusCondition());

        // Fainted state overrides status condition display
        if (!pokemon.isAlive()) {
            if (statusText != null) {
                statusText.setText("FNT");
                statusText.setColor(0.9f, 0.2f, 0.2f, 1f); // Red
            }
        }

        int currentHp = pokemon.getCurrentHp();
        int maxHp = pokemon.calcMaxHp();
        float hpPercent = maxHp > 0 ? Math.min(1f, Math.max(0f, (float) currentHp / maxHp)) : 0;
        updateHpBar(hpPercent);

        if (hpText != null) hpText.setText(currentHp + "/" + maxHp);

        // Icon (future: species sprite)
        // if (iconImage != null) iconImage.setSprite(...)

        // Held item indicator
        if (heldItemIndicator != null) {
            heldItemIndicator.setText(pokemon.getHeldItem() != null ? "♦" : "");
        }

        setVisible(true);
    }

    /** Show empty/unused slot state. */
    public void setEmpty() {
        setLabel("───");
        setSelected(false);
        if (levelText != null) levelText.setText("");
        if (statusText != null) statusText.setText("");
        if (hpBarTrack != null) hpBarTrack.getGameObject().setEnabled(false);
        if (hpBarFill != null) hpBarFill.getGameObject().setEnabled(false);
        if (hpText != null) hpText.setText("");
        if (iconImage != null) iconImage.getGameObject().setEnabled(false);
        if (heldItemIndicator != null) heldItemIndicator.setText("");
    }

    // ── Internal ──

    private void updateHpBar(float hpPercent) {
        if (hpBarTrack != null) hpBarTrack.getGameObject().setEnabled(true);
        if (hpBarFill == null) return;
        hpBarFill.getGameObject().setEnabled(true);
        hpBarFill.setFillAmount(hpPercent);
        if (hpPercent > 0.5f) {
            hpBarFill.setColor(0.2f, 0.8f, 0.2f, 1f);   // Green
        } else if (hpPercent > 0.2f) {
            hpBarFill.setColor(0.9f, 0.8f, 0.1f, 1f);   // Yellow
        } else {
            hpBarFill.setColor(0.9f, 0.2f, 0.2f, 1f);   // Red
        }
    }

    private void updateStatusLabel(StatusCondition status) {
        if (statusText == null) return;
        if (status == null || status == StatusCondition.NONE) {
            statusText.setText("");
            return;
        }
        switch (status) {
            case POISON   -> { statusText.setText("PSN"); statusText.setColor(0.6f, 0.2f, 0.8f, 1f); }
            case PARALYZE -> { statusText.setText("PAR"); statusText.setColor(0.9f, 0.8f, 0.1f, 1f); }
            case SLEEP    -> { statusText.setText("SLP"); statusText.setColor(0.5f, 0.5f, 0.5f, 1f); }
            case BURN     -> { statusText.setText("BRN"); statusText.setColor(0.9f, 0.5f, 0.1f, 1f); }
            case FREEZE   -> { statusText.setText("FRZ"); statusText.setColor(0.2f, 0.5f, 0.9f, 1f); }
        }
    }
}
```

**Hierarchy per Pokemon slot (nested prefab `pokemon_slot.prefab.json`):**
```
PokemonSlotN (GameObject) — PokemonSlot component (componentKey: "menu_team_slot_0")
├── Arrow (GameObject) — UIText (componentKey: "menu_team_slot_0_arrow")
├── Icon (GameObject) — UIImage (componentKey: "menu_team_slot_0_icon")
├── Name (GameObject) — UIText (componentKey: "menu_team_slot_0_label")
├── Status (GameObject) — UIText (componentKey: "menu_team_slot_0_status")
├── HeldItem (GameObject) — UIText (componentKey: "menu_team_slot_0_held")
├── Level (GameObject) — UIText (componentKey: "menu_team_slot_0_level")
├── HpBar (GameObject)
│   ├── Track (GameObject) — UIImage (componentKey: "menu_team_slot_0_hp_track")
│   └── Fill (GameObject) — UIImage FILLED (componentKey: "menu_team_slot_0_hp_fill")
└── HpText (GameObject) — UIText (componentKey: "menu_team_slot_0_hp_text")
```

---

## `MenuScreen` (Interface — in `components/ui/menu/`)

Interface for sub-screens to decouple screen-specific logic from `MenuManager`.

```java
public interface MenuScreen {
    /** Show this screen (animations, visibility). */
    void show(Runnable onComplete);

    /** Hide this screen. */
    void hide(Runnable onComplete);

    /** Called when this screen becomes the active screen. Set up activeList, refresh data. */
    void onActivated(MenuManager manager);

    /** Kill any active tweens on this screen's transforms. */
    void killTweens();
}
```

`TeamScreenController`, `InventoryScreenController`, `PokedexScreenController`, and `PlayerCardController` implement this interface. `MenuManager` holds a `Map<MenuState, MenuScreen>` and delegates `show()`/`hide()`/`killTweens()` instead of managing individual UITransform references.

---

## `MenuManager` (Component — on Player)

Central controller for the entire menu system. Replaces `PlayerPauseUI`.

```java
@ComponentMeta(category = "Player")
public class MenuManager extends Component {

    // ── Same-GO references ──

    @ComponentReference(source = Source.SELF)
    private PlayerInput playerInput;

    @ComponentReference(source = Source.SELF)
    private PlayerPartyComponent playerParty;

    @ComponentReference(source = Source.SELF)
    private PlayerInventoryComponent playerInventory;

    // ── SelectableList references (resolved by key) ──

    @ComponentReference(source = Source.KEY)
    private SelectableList mainMenuList;

    @ComponentReference(source = Source.KEY)
    private SelectableList confirmList;

    @ComponentReference(source = Source.KEY)
    private SelectableList categoryList;     // Inventory categories (HORIZONTAL)

    @ComponentReference(source = Source.KEY)
    private SelectableList itemList;          // Inventory items (VERTICAL)

    @ComponentReference(source = Source.KEY)
    private SelectableList teamList;          // Team slot navigation

    // ── MenuScreen sub-controllers (resolved by key) ──

    @ComponentReference(source = Source.KEY)
    private TeamScreenController teamScreen;

    @ComponentReference(source = Source.KEY)
    private InventoryScreenController inventoryScreen;

    // Future: PokedexScreenController, PlayerCardController

    // Map populated in onStart() from resolved references above
    private transient Map<MenuState, MenuScreen> screens;

    // ── Panel transforms for tween targets (resolved by key) ──

    @ComponentReference(source = Source.KEY)
    private UITransform mainMenuPanelTransform;

    @ComponentReference(source = Source.KEY)
    private UITransform confirmDialogTransform;

    // ── Active list management — only ONE list receives input at a time ──

    private transient SelectableList activeList;

    // ── State ──

    private transient MenuState state = MenuState.CLOSED;

    // Input auto-repeat for scrollable lists
    private transient float repeatDelay = 0.3f;     // Initial delay before repeat starts
    private transient float repeatRate = 0.125f;     // 8 moves/second
    private transient float repeatTimer = 0f;
    private transient boolean repeatActive = false;
    private transient Direction lastDirection = null;

    private void setActiveList(SelectableList list) {
        if (activeList != null) activeList.setActive(false);
        activeList = list;
        if (activeList != null) activeList.setActive(true);
    }

    @Override
    protected void onStart() {
        // Populate screen map for MenuScreen delegation
        screens = new HashMap<>();
        if (teamScreen != null) screens.put(MenuState.TEAM_SCREEN, teamScreen);
        if (inventoryScreen != null) screens.put(MenuState.INVENTORY_SCREEN, inventoryScreen);
        // Future: screens.put(MenuState.POKEDEX_LIST, pokedexScreen);
        //         screens.put(MenuState.PLAYER_CARD, playerCardScreen);

        if (playerInput != null) {
            playerInput.onMenu(InputMode.OVERWORLD, this::openMenu);
        }
    }

    // NOTE: PlayerInput.onMenu() appends callbacks with no removal API.
    // To prevent callback accumulation across editor play-mode cycles,
    // either (a) add removeCallbacks(owner) to PlayerInput and call from onDestroy(),
    // or (b) clear all callbacks in PlayerInput.onStart().

    @Override
    public void update(float deltaTime) {
        if (state == MenuState.CLOSED) return;
        if (state == MenuState.OPENING || state == MenuState.CLOSING || state == MenuState.SAVING) return;
        // SAVING state uses a delayed tween callback. If the callback fails to fire,
        // a 2s watchdog timer transitions to SAVE_FAILED as a safety fallback.
        // The delay tween is assigned to confirmDialogTransform so killAllMenuTweens() can cancel it.

        // Auto-repeat: if direction is held (checked via playerInput.isMoving(dir)),
        // after repeatDelay, fire movePrimary at repeatRate intervals.
        // Only active for scrollable states (INVENTORY_SCREEN, POKEDEX_LIST).
        // Short lists (MAIN_MENU, CONFIRM_*) use edge-triggered input only.

        if (activeList != null && playerInput != null) {
            Direction dir = playerInput.getMovementDirectionUp();
            if (dir != null) {
                routeDirectionInput(dir);
            }
            if (playerInput.isInteractPressed()) activeList.select();
            if (playerInput.isMenuPressed()) activeList.cancel();
        }
    }

    private void routeDirectionInput(Direction dir) {
        if (state == MenuState.INVENTORY_SCREEN) {
            // Dual-list routing: categoryList (HORIZONTAL) handles LEFT/RIGHT,
            // itemList (VERTICAL, the activeList) handles UP/DOWN.
            // categoryList is always kept active in INVENTORY_SCREEN state so
            // movePrimary's active guard passes. setActiveList() only deactivates
            // the PREVIOUS activeList, so categoryList stays active alongside itemList.
            if (dir == Direction.LEFT || dir == Direction.RIGHT) {
                int delta = (dir == Direction.RIGHT) ? 1 : -1;
                categoryList.movePrimary(delta);
            } else {
                activeList.movePrimary(dir == Direction.DOWN ? 1 : -1);
            }
        } else if (state == MenuState.POKEDEX_DETAIL) {
            // Detail view: LEFT/RIGHT cycles species, UP/DOWN ignored.
            // No activeList in detail view — route directly to controller.
            if (dir == Direction.LEFT || dir == Direction.RIGHT) {
                int delta = (dir == Direction.RIGHT) ? 1 : -1;
                MenuScreen screen = screens.get(MenuState.POKEDEX_LIST);
                if (screen instanceof PokedexScreenController pokedex) {
                    pokedex.cycleSpecies(delta);
                }
            }
        } else {
            if (dir == Direction.UP) activeList.movePrimary(-1);
            else if (dir == Direction.DOWN) activeList.movePrimary(+1);
        }
    }

    private void killAllMenuTweens() {
        TweenManager.kill(mainMenuPanelTransform, false);
        TweenManager.kill(confirmDialogTransform, false);
        // Delegate to sub-screens — each screen kills its own tweens
        for (MenuScreen screen : screens.values()) {
            screen.killTweens();
        }
    }

    /** Transition to a sub-screen via MenuScreen delegation. */
    private void showScreen(MenuState targetState) {
        MenuScreen screen = screens.get(targetState);
        if (screen == null) return;
        killAllMenuTweens();
        state = targetState;
        screen.show(() -> screen.onActivated(this));
        // onActivated sets the activeList and refreshes data
    }

    /** Return from a sub-screen to main menu. */
    private void hideScreen(MenuState currentState) {
        MenuScreen screen = screens.get(currentState);
        if (screen == null) return;
        screen.hide(() -> {
            state = MenuState.MAIN_MENU;
            setActiveList(mainMenuList);
        });
    }

    private void openMenu() {
        killAllMenuTweens();
        state = MenuState.OPENING;
        getGameObject().getScene().getPauseManager().requestPause(this);
        playerInput.setMode(InputMode.MENU);
        // Slide panel in, onComplete → state = MAIN_MENU, setActiveList(mainMenuList)
    }

    // Save flow: if SaveManager.hasSave() → state = CONFIRM_OVERWRITE first,
    //   then on YES → state = CONFIRM_SAVE.
    // If no existing save → state = CONFIRM_SAVE directly.

    // SAVE_SUCCESS: "Game saved!" displayed for 1s via delayed callback, then auto-returns
    // to MAIN_MENU. INTERACT or MENU/B also dismisses immediately. No OK button.

    // QUIT YES → SceneManager.loadScene(titleScenePath) — returns to title screen.
    // Do NOT call System.exit(0).

    // NOTE: All tween onComplete callbacks include a state guard:
    //   if (state != MenuState.OPENING) return;  // Stale callback guard
    // This prevents callbacks from corrupting state after killAllMenuTweens() or scene unload.

    private void closeMenu() {
        killAllMenuTweens();
        state = MenuState.CLOSING;
        setActiveList(null);
        // Slide panel out, onComplete → state = CLOSED, releasePause(this), set OVERWORLD mode
    }

    @Override
    protected void onDestroy() {
        if (state != MenuState.CLOSED) {
            killAllMenuTweens();
            getGameObject().getScene().getPauseManager().releasePause(this);
            if (playerInput != null) {
                playerInput.setMode(InputMode.OVERWORLD);
            }
            state = MenuState.CLOSED;
        }
    }
}
```

No `uiResolved` flag, no `resolveUI()` method. Every reference is declarative. The framework resolves all `@ComponentReference(source = KEY)` fields at scene load time.

---

## `TeamScreenController` (Component — on TeamScreen GameObject)

Manages the Pokemon team display. `PokemonSlot` references resolved declaratively.

```java
@ComponentMeta(category = "UI")
public class TeamScreenController extends Component {

    private static final int MAX_SLOTS = 6;

    @ComponentReference(source = Source.KEY)
    private List<PokemonSlot> pokemonSlots;    // 6 PokemonSlot components

    /** Populate all slots from party data. */
    public void refresh(PlayerPartyComponent party) {
        if (pokemonSlots == null) return;
        List<PokemonInstance> team = party.getTeam();
        for (int i = 0; i < pokemonSlots.size() && i < MAX_SLOTS; i++) {
            PokemonSlot slot = pokemonSlots.get(i);
            if (i < team.size() && team.get(i) != null) {
                slot.setPokemon(team.get(i));
            } else {
                slot.setEmpty();
            }
        }
    }
}
```

Serialized as:
```json
{
  "_type": "TeamScreenController",
  "pokemonSlots": ["menu_team_slot_0", "menu_team_slot_1", "menu_team_slot_2",
                    "menu_team_slot_3", "menu_team_slot_4", "menu_team_slot_5"]
}
```

**Mixed slot types in team SelectableList:** The team list contains 6 `PokemonSlot` components plus 1 plain `SelectableSlot` (BACK). Since `PokemonSlot extends SelectableSlot`, Java polymorphism handles this. JSON serialization uses the `_type` discriminator field (e.g., `"_type": "PokemonSlot"` vs `"_type": "SelectableSlot"`), which the `ComponentRegistry` already handles for all Component subclasses.

---

## `InventoryScreenController` (Component — on InventoryScreen GameObject)

Manages the inventory display with category switching.

```java
@ComponentMeta(category = "UI")
public class InventoryScreenController extends Component {

    @ComponentReference(source = Source.KEY)
    private UIText moneyText;

    @ComponentReference(source = Source.KEY)
    private UIText descriptionText;

    // State (transient)
    private transient List<ItemCategory> availableCategories;
    private transient int currentCategoryIndex = 0;

    public void refresh(PlayerInventoryComponent inventory);
    public void onCategoryChanged(int newIndex);
    public void onItemSelectionChanged(int newIndex);
    private void rebuildCategoryList(PlayerInventoryComponent inventory);
    private void updateItemDisplay();
}
```

---

## `PokemonMenuUIBuilder` (static utility)

Builds the complete menu UI hierarchy. Located in `components/ui/menu/` package. Sets componentKeys on all components so `@ComponentReference(source = KEY)` can resolve them.

```java
package com.pocket.rpg.components.ui.menu;

public class PokemonMenuUIBuilder {

    // ── Component Keys ──

    // Panels / screens (UITransform keys for tween targets)
    public static final String KEY_MAIN_PANEL_TRANSFORM = "menu_main_panel_transform";
    public static final String KEY_TEAM_SCREEN_TRANSFORM = "menu_team_screen_transform";
    public static final String KEY_INV_SCREEN_TRANSFORM = "menu_inv_screen_transform";
    public static final String KEY_CONFIRM_DIALOG_TRANSFORM = "menu_confirm_dialog_transform";

    // SelectableList keys
    public static final String KEY_MAIN_LIST = "menu_main_list";
    public static final String KEY_TEAM_LIST = "menu_team_list";
    public static final String KEY_INV_CATEGORY_LIST = "menu_inv_category_list";
    public static final String KEY_INV_ITEM_LIST = "menu_inv_item_list";
    public static final String KEY_CONFIRM_LIST = "menu_confirm_list";

    // Text keys
    public static final String KEY_INV_MONEY = "menu_inv_money";
    public static final String KEY_INV_DESCRIPTION = "menu_inv_description";
    public static final String KEY_CONFIRM_MESSAGE = "menu_confirm_message";
    public static final String KEY_CONFIRM_INFO = "menu_confirm_info";

    // Slot key patterns:
    // Main menu slots:   "menu_main_slot_0" .. "_6" (SelectableSlot componentKeys)
    //   children:        "menu_main_slot_0_arrow", "menu_main_slot_0_label"
    // Team slots:        "menu_team_slot_0" .. "_5" (PokemonSlot componentKeys)
    //   children:        "_arrow", "_label", "_level", "_status", "_held", "_hp_track", "_hp_fill", "_hp_text", "_icon"
    // Inventory slots:   "menu_inv_slot_0" .. "_7" (InventorySlot componentKeys)
    //   children:        "_arrow", "_label", "_qty"
    // Confirm slots:     "menu_confirm_slot_0", "_1" (SelectableSlot componentKeys)
    // Scroll indicators: "menu_inv_item_list_scroll_up", "_scroll_down"

    // ── Layout Constants ──
    public static final String FONT = "fonts/Pokemon-Red.ttf";
    public static final int FONT_SIZE = 20;
    public static final int FONT_SIZE_SMALL = 18;
    public static final int TITLE_FONT_SIZE = 24;
    public static final float MAIN_MENU_WIDTH_PERCENT = 30f;
    public static final int MENU_ITEM_HEIGHT = 40;
    public static final int MENU_ITEM_SPACING = 5;
    public static final int TEAM_SLOT_HEIGHT = 70;
    public static final int INV_ITEM_HEIGHT = 35;
    public static final int MAX_VISIBLE_INV_ITEMS = 8;
    public static final float SLIDE_DURATION = 0.2f;
    public static final String PANEL_SPRITE = "sprites/UIs/Corners and Tiles/Panels/panel - dark - fill.png";
    public static final int DESCRIPTION_PANEL_HEIGHT = 70;

    // ── Build Methods ──
    public static GameObject build();
    private static GameObject buildMainMenu();
    private static GameObject buildTeamScreen();
    private static GameObject buildInventoryScreen();
    private static GameObject buildConfirmDialog();

    // ── Reusable Sub-builders ──

    /** Creates a selectable slot: GO with SelectableSlot + Arrow UIText + Label UIText children.
     *  Sets componentKeys on all components for @ComponentReference resolution. */
    private static GameObject buildSelectableSlot(String slotKey, int height);

    /** Creates an inventory slot: extends selectable slot with Quantity UIText child. */
    private static GameObject buildInventorySlot(String slotKey, int height);

    /** Creates a Pokemon slot: extends selectable slot with HP bar, level, status, icon children. */
    private static GameObject buildPokemonSlot(String slotKey, int height);

    /** Creates HP bar: parent GO with dark track UIImage + colored fill UIImage (FILLED horizontal). */
    private static GameObject buildHpBar(String trackKey, String fillKey);
}
```

---

## `PokemonMenuPrefabGenerator` (development utility)

```java
package com.pocket.rpg.components.ui.menu;

public class PokemonMenuPrefabGenerator {
    public static void main(String[] args) {
        // 1. Call PokemonMenuUIBuilder.build() to create the GameObject hierarchy
        // 2. Serialize to GameObjectData list (flat, with parentId references)
        // 3. Write to gameData/assets/prefabs/pokemon_menu_ui.prefab.json
    }
}
```

### Reusable Sub-Prefab Generators

| Generator | Output | Description |
|-----------|--------|-------------|
| `PokemonMenuPrefabGenerator` | `pokemon_menu_ui.prefab.json` | Complete menu UI hierarchy |
| (part of same generator) | `pokemon_slot.prefab.json` | Single Pokemon slot (PokemonSlot + children) |
| (part of same generator) | `confirm_dialog.prefab.json` | Confirmation dialog (message + SelectableList with slots) |

---

## Component Hierarchy Summary

```
components/ui/
├── SelectableSlot.java        — Base slot: @KEY arrow + label
├── SelectableList.java        — @KEY List<SelectableSlot>, scrolling, selection
├── InventorySlot.java         — Extends SelectableSlot: @KEY quantity
├── PokemonSlot.java           — Extends SelectableSlot: @KEY HP bar, level, status, icon
└── menu/
    ├── PokemonMenuUIBuilder.java      — Builds the full hierarchy, sets all componentKeys
    └── PokemonMenuPrefabGenerator.java — Serializes to JSON prefab

components/pokemon/
├── MenuManager.java           — @KEY all lists + transforms, @SELF input/party/inventory
├── MenuState.java             — State enum
├── TeamScreenController.java  — @KEY List<PokemonSlot>
└── InventoryScreenController.java — @KEY money + description texts
```

**Zero manual `resolveUI()` / `uiResolved` patterns.** Every component reference is declared with `@ComponentReference` and resolved by the framework at scene load time.
