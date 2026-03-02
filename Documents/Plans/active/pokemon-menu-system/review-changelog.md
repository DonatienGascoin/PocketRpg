# Pokemon Menu System — Review Changelog

## Round 1 — UI Expert + Senior Engineer

Issues addressed from UI expert and senior engineer reviews:

| # | Severity | Issue | Resolution |
|---|----------|-------|------------|
| E1 | CRITICAL | SelectableList can't access PlayerInput (different GO tree) | **Push model**: SelectableList has zero input coupling. MenuManager polls and pushes. |
| E2 | CRITICAL | IPausable conflict with dialogue | Documented: menu can't open during DIALOGUE (InputMode gate). Future: refactor to ref-counted pauses. |
| E3 | CRITICAL | Sub-controllers missing componentKey registration | Added componentKey to every SelectableList + controllers. Explicit KEY constants in builder. |
| U5 | CRITICAL | Double-fire on MENU press | SelectableList.onCancel is sole handler. No parallel onMenu(MENU) listener. |
| U18 | CRITICAL | Quit default = YES (destructive) | Changed to default = NO (index 1). |
| U21 | CRITICAL | SAVE_SUCCESS traps player | MENU/B also dismisses success/error messages. |
| E4 | IMPORTANT | ComponentKeyRegistry timing | `@ComponentReference(source = KEY)` resolves at scene load. No manual `resolveUI()` needed. |
| E5 | IMPORTANT | Tween cleanup for multiple transforms | killAllMenuTweens() on ALL transforms at every state transition. |
| E6 | IMPORTANT | MENU button not routed in InputMode.MENU | MenuManager.update() polls isMenuPressed() directly. |
| E7 | IMPORTANT | Package location contradictory | Fixed: `components/ui/menu/PokemonMenuUIBuilder.java`. |
| E8 | IMPORTANT | Multiple lists need single-active management | setActiveList() pattern: only one list active at a time. |
| E10 | IMPORTANT | LEFT/RIGHT conflicts with SelectableList | Push model resolves: MenuManager routes L/R to category, U/D to items. |
| U1 | IMPORTANT | Missing player name on main menu | ~~Added player name header.~~ Later removed per user decision — menu has no player name header. |
| U9 | IMPORTANT | Sub-screen transitions unspecified | Added transition table: instant swap for sub-screens, dim for dialogs. |
| U11 | IMPORTANT | Save screen lacks game state summary | Added save info: player name + play time above confirmation. |
| U13 | IMPORTANT | HP thresholds wrong | Fixed: >50% green, 20-50% yellow, <20% red. |
| U15 | IMPORTANT | Scroll indicator not mandatory | Made mandatory: ▲/▼ shown at list boundaries. |
| U16 | IMPORTANT | Seven empty categories bad UX | Filter to non-empty categories only. |
| U22 | IMPORTANT | Menu during scene transition | Guard in openMenu() checks for active transitions. |
| U25 | IMPORTANT | Missing trainer card/Pokedex acknowledgment | Added "Future Menu Options" section. |
| E14 | SUGGESTION | Missing animation states | Added OPENING/CLOSING/SAVING to MenuState. |
| U2 | SUGGESTION | Empty slot visual noise | Dimmed single-line, no full border. |
| U3 | SUGGESTION | Pokemon icons missing | Icon slot reserved (8% width, placeholder). |
| U4 | SUGGESTION | Status conditions not shown | Added status label with abbreviation + color. |
| U7 | SUGGESTION | Wrap-around defaults | Explicit defaults per list documented. |
| U8 | SUGGESTION | Sound effects | Phase 7 hook for future audio. |
| U10 | SUGGESTION | Slide duration too slow | Reduced from 0.3s to 0.2s. |
| U12 | SUGGESTION | Description panel height | Fixed at 70px. |
| U19 | SUGGESTION | Two-stage save feedback | "Saving..." 0.5s → "Game saved!" + OK. |
| U20 | SUGGESTION | Save error unhandled | Added SAVE_FAILED state + error dialog. |
| U26 | SUGGESTION | Money not shown | Added money display to inventory header. |
| U27 | SUGGESTION | Horizontal SelectableList | Added Orientation enum (VERTICAL/HORIZONTAL). |
| U28 | SUGGESTION | HP bar background track | Two-layer: dark track + colored fill. |
| E15 | SUGGESTION | Testing gaps | Added rapid transition, empty list, tween callback tests. |

---

## Round 2 — Senior Engineer, QA, Product Owner, Pokemon Gen 2/3 Expert

### Senior Software Engineer

| # | Severity | Issue | Source |
|---|----------|-------|--------|
| SE1 | IMPORTANT | **MenuManager God object risk** — 12+ injected dependencies, will grow with Pokedex/Card. Extract screen logic into sub-controllers via a `MenuScreen` interface (`show()`, `hide()`, `onActivated()`, `killTweens()`). | class-designs.md |
| SE2 | IMPORTANT | **Dual pause system fragility** — Both MenuManager and PlayerDialogueManager have private `pauseAll()`/`resumeAll()`. If a future system pauses IPausables while menu is open, `resumeAll()` on menu close resumes everything incorrectly. Extract to shared `PauseManager` or reference-counted system. | overview.md |
| SE3 | IMPORTANT | **PokedexScreenController uses manual resolveUI()** — Contradicts the plan's rule of zero manual resolution. Still has `transient boolean uiResolved` and `resolveUI()`. Should use `@ComponentReference(source=KEY)` like all other controllers. | screen-designs-pokedex.md |
| SE4 | IMPORTANT | **setItems(List\<String\>) too limiting** — Consumers must maintain parallel `List<T>` for actual data. If lists get out of sync, bugs follow. Consider `setItems(List<String> labels, List<T> data)` or document the parallel-list contract explicitly. | class-designs.md |
| SE5 | IMPORTANT | **POKEDEX_DETAIL breaks activeList pattern** — No SelectableList for the detail view, but LEFT/RIGHT must cycle species. `routeDirectionInput()` has no case for POKEDEX_DETAIL. Need explicit routing to `PokedexScreenController.cycleSpecies()`. | screen-designs-pokedex.md |
| SE6 | IMPORTANT | **No MenuManager.onDestroy() cleanup** — If menu is open during scene change: InputMode left as MENU, IPausables stay paused, tweens run on stale transforms, callback leaks on PlayerInput. Must force-close menu in `onDestroy()`. | class-designs.md |
| SE7 | IMPORTANT | **PlayerInput callback accumulation** — `onMenu()` only appends, no removal API. Editor play-mode start/stop cycles will accumulate duplicate callbacks. Need `removeCallbacks(owner)` or clear-on-start. | PlayerInput.java |
| SE8 | IMPORTANT | **Tween onComplete fires after component destruction** — TweenManager has no lifecycle awareness. Scene unload can leave stale tween callbacks. Must kill tweens in `onDestroy()` and add state guards to all onComplete lambdas. | class-designs.md |
| SE9 | IMPORTANT | **review-changelog E4 is stale** — E4 says "Lazy resolveUI() with uiResolved flag" but class-designs.md says "zero manual resolveUI()". The @ComponentReference approach is correct. E4 and Pokedex screen design need updating. | review-changelog.md |
| SE10 | SUGGESTION | **Missing getItemCount() on SelectableList** — No way to query item count. Needed for BACK-button logic (team screen index 6, inventory dynamic last index). Add `int getItemCount()`. | class-designs.md |
| SE11 | SUGGESTION | **setItems() reset behavior unspecified** — Should it reset selectedIndex to 0? Reset scrollOffset? Fire onSelectionChanged? All yes — make this explicit. | class-designs.md |
| SE12 | SUGGESTION | **SAVING state delay tween not killable** — If `Tweens.delayedCall()` is used, `killAllMenuTweens()` (which kills by target) cannot cancel it. Assign a target or add state guard in callback. | class-designs.md |
| SE13 | SUGGESTION | **No window resize handling** — Tween animations capture absolute pixel positions. Resize during menu open could misalign slide-out animation. | screen-designs.md |
| SE14 | SUGGESTION | **ComponentKeyRegistry overwrites on collision** — Prints warning but overwrites. Duplicate keys from PrefabGenerator bugs would be silently masked. Add a unit test that validates key uniqueness in the built hierarchy. | ComponentKeyRegistry.java |
| SE15 | PRAISE | Push model for SelectableList is excellent — zero coupling to input system, genuinely reusable. | class-designs.md |
| SE16 | PRAISE | SelectableSlot inheritance hierarchy is appropriate — flat, narrowly scoped, never overrides base contract. | class-designs.md |
| SE17 | PRAISE | Animation guard pattern (OPENING/CLOSING/SAVING + killAllMenuTweens) is robust and systematic. | class-designs.md |
| SE18 | PRAISE | Phase 0 (Nested Prefabs) is cleanly isolated as a framework capability with no menu-specific logic. | phase-0-nested-prefabs.md |

### QA Engineer

| # | Severity | Issue | Source |
|---|----------|-------|--------|
| QA1 | CRITICAL | **FNT (Fainted) status missing from StatusCondition enum** — screen-designs.md lists FNT=red but `StatusCondition.java` has no FAINT value. `PokemonSlot.updateStatusLabel()` switch omits it. Fainted Pokemon will show no status. | screen-designs.md, class-designs.md |
| QA2 | CRITICAL | **HP display overflow** — If `currentHp > maxHp` (data bug), `hpPercent` exceeds 1.0. HP bar renders beyond track bounds. Clamp to `[0.0, 1.0]`: `Math.min(1f, Math.max(0f, (float)currentHp/maxHp))`. | class-designs.md |
| QA3 | CRITICAL | **SAVING state has no timeout** — If tween/timer callback fails to fire, player is permanently trapped (input blocked). Add fallback timeout (2s max) or allow MENU press to escape SAVING. | implementation-phases.md |
| QA4 | CRITICAL | **PlayerPauseUI removal could break deserialization** — If existing prefabs/scenes reference `PlayerPauseUI` by class name and the class is deleted, deserialization produces errors. Verify `ComponentRegistry` handles unknown `_type` gracefully. | implementation-phases.md |
| QA5 | CRITICAL | **Inventory BACK behavior ambiguous** — "included as last item in the item list, or as 9th special slot" — plan doesn't commit. Each approach has different UX implications (scrolling, wrap-around, description panel). Must choose one. | implementation-phases.md |
| QA6 | IMPORTANT | **No unit tests for save failure path** — Missing: `save()` returns false → SAVE_FAILED transition, error dialog shown, OK/MENU dismisses. | implementation-phases.md |
| QA7 | IMPORTANT | **No cleanup on scene transition while menu open** — openMenu() guards against opening during transitions but nothing guards against transitions WHILE open. Need `onBeforeSceneUnload` handler to force-close. | implementation-phases.md |
| QA8 | IMPORTANT | **Inventory stale data if items removed externally** — Refresh only on screen entry. Future item-use or external removal could desync the display. Document refresh policy. | screen-designs.md |
| QA9 | IMPORTANT | **Money display edge cases** — Negative money displays "$-1,000". No max cap defined. Large numbers may overflow text width. Clamp to `Math.max(0, money)`. | screen-designs-player-card.md |
| QA10 | IMPORTANT | **POKEDEX_DETAIL input routing undefined** — LEFT/RIGHT for species cycling has no `routeDirectionInput()` case. Will be discovered during implementation. | screen-designs-pokedex.md |
| QA11 | IMPORTANT | **Selecting unseen species in Pokedex has undefined behavior** — onSelect fires for any index. No guard prevents opening detail for unseen "???" entries. Could crash or show blank. | screen-designs-pokedex.md |
| QA12 | IMPORTANT | **Phase 0 regression risk** — Modifies 5 core engine files. No explicit regression tests for existing non-nested prefabs. All existing prefabs/scenes must still work. | phase-0-nested-prefabs.md |
| QA13 | IMPORTANT | **Team screen BACK is 7th item but mixed slot types** — 6 PokemonSlots + 1 plain SelectableSlot in same `List<SelectableSlot>`. Polymorphism works but JSON serialization of mixed types needs verification. | screen-designs.md |
| QA14 | IMPORTANT | **No integration test for full menu cycle** — Existing patterns (SceneGameObjectIntegrationTest) support this. Missing: open → navigate → save → dismiss → close → verify InputMode. | implementation-phases.md |
| QA15 | SUGGESTION | **Add componentKey uniqueness validation test** — Programmatic test: build hierarchy, collect all keys, assert unique + all start with `menu_` prefix. | implementation-phases.md |
| QA16 | SUGGESTION | **Play time formatting overflow** — 1000+ hours produces "1000:00" which may exceed text width. Pokemon caps at 999:59. | screen-designs-player-card.md |
| QA17 | SUGGESTION | **setItems() reset behavior needs test** — Verify selectedIndex and scrollOffset reset to 0 on category change. | implementation-phases.md |
| QA18 | SUGGESTION | **List<SelectableSlot> @KEY resolution needs framework verification** — Confirm list-of-component resolution from JSON string arrays is already supported or needs Phase 1 implementation. | class-designs.md |
| QA19 | SUGGESTION | **Manual tests should include gamepad testing** — Project has GamepadListenerTest; menu tests only reference keyboard. | implementation-phases.md |
| QA20 | SUGGESTION | **SaveManager.save("save1") hardcoded** — If player loaded a different slot, this overwrites the wrong save. Use current slot from SaveManager. | implementation-phases.md |

### Product Owner

| # | Priority | Issue | Source |
|---|----------|-------|--------|
| PO1 | HIGH | **OPTIONS/SETTINGS menu absent** — Every Pokemon game has it. At minimum reserve a menu item position. Better: define a stub or minimal settings (text speed, sound). | overview.md |
| PO2 | HIGH | **Phase 0 scope risk** — Engine-level nested prefab changes could take as long as Phases 1-5 combined. Define a fallback (flat prefab or programmatic inject without nested prefabs). Consider splitting into its own plan. | phase-0-nested-prefabs.md |
| PO3 | HIGH | **MVP not explicitly defined** — 9 phases with no stated minimum shippable set. Recommend: Phases 0-3 + 6 = MVP (menu + save + quit). Team/Inventory as fast-follow. | implementation-phases.md |
| PO4 | HIGH | **CLOSE option is unnecessary** — B/Escape already closes the menu. Pokemon Gen 2 had no CLOSE option. Gen 3 had EXIT. Consider removing or renaming to EXIT. Reduces menu from 7 to 6 items. | screen-designs.md |
| PO5 | HIGH | **No per-phase Definition of Done** — Phases list tasks but no observable acceptance criteria. Add 2-3 sentence Done criteria per phase. | implementation-phases.md |
| PO6 | MEDIUM | **Sound effects phase is vague** — Phase 7 says "audio integration points" but doesn't specify which sounds (cursor move, confirm, cancel, menu open/close, save jingle). Define them even if audio system doesn't exist yet. | implementation-phases.md |
| PO7 | MEDIUM | **No path documented for team/inventory sub-menus** — "INTERACT: No action for now" is fine for MVP but fans will immediately try to switch Pokemon or use items. Add a "Post-MVP Interactions" section. | screen-designs.md |
| PO8 | MEDIUM | **Inventory category has no position indicator** — No "2/4" or dots to show where you are in the category cycle. Minor but aids discoverability. | screen-designs.md |
| PO9 | MEDIUM | **Localization not mentioned** — All strings hardcoded. Not blocking but document the debt. | overview.md |
| PO10 | MEDIUM | **Phase 8 (Code Review) is not a real phase** — It's a quality gate, not a work unit. Merge into Phase 6 (Integration & Polish & Review). Reduces total to 8 phases. | implementation-phases.md |
| PO11 | MEDIUM | **Pokedex/Card are "designed but not phased"** — Add explicit future phase numbers with prerequisites, or note they'll be phased when data dependencies are met. | overview.md |
| PO12 | MEDIUM | **Phases 3/4/5 are parallelizable** — They depend on Phase 2 but not each other. Note this for resource planning. | implementation-phases.md |
| PO13 | LOW | **No "return to title screen" option** — QUIT calls System.exit(0). If a title screen exists or is planned, QUIT should return there instead. | screen-designs.md |
| PO14 | LOW | **FNT status missing from PokemonSlot** — Fainted Pokemon show no indicator. Add FAINT handling. | class-designs.md |

### Pokemon Gen 2/3 Expert

| # | Tag | Issue | Source |
|---|-----|-------|--------|
| PK1 | ACCURACY | **"CARD" label never existed in Gen 2/3** — The trainer card was accessed by selecting the **player's name** as a menu option (e.g., "RED"). This was a distinctive Pokemon convention. | screen-designs.md |
| PK2 | ACCURACY | **Menu options should be conditional** — POKeDEX only appears after receiving it. POKeMON only when you have one. Static 7-item list is un-Pokemon. Dynamic list based on game progress made earning the Pokedex feel meaningful. | overview.md |
| PK3 | ACCURACY | **Menu appeared instantly in Gen 2/3** — No slide animation. Compact bordered text box, sized to contents. 0.2s slide is modern but doesn't feel like Pokemon. Fans would subconsciously notice something "off." | screen-designs.md |
| PK4 | ACCURACY | **Gen 3 team screen had asymmetric layout** — First Pokemon in a large panel on the left, Pokemon 2-6 in smaller panels stacked on the right. Not a uniform vertical list. Plan's list is closer to Gen 2 style. | screen-designs.md |
| PK5 | ACCURACY | **Missing "overwrite save" confirmation** — Gen 2/3 always showed "There is already a save file. Is it OK to overwrite?" when a save existed. This two-step confirmation IS the Pokemon save experience. | screen-designs.md |
| PK6 | ACCURACY | **Missing sub-menus on team/inventory select** — Gen 2/3: Select Pokemon → STATS/SWITCH/ITEM/CANCEL. Select item → USE/GIVE/TOSS/CANCEL. Core interactions fans will immediately try. | screen-designs.md |
| PK7 | ACCURACY | **Gen 2 called the bag "PACK", not "BAG"** — Gen 3 used "BAG". Plan uses BAG which matches Gen 3. | screen-designs.md |
| PK8 | ACCURACY | **"MEDICINE" category not from Gen 2/3** — Gen 2: ITEMS, BALLS, KEY ITEMS, TM/HM. Gen 3: ITEMS, KEY ITEMS, POKE BALLS, TMs & HMs, BERRIES. MEDICINE is Gen 5+. | screen-designs.md |
| PK9 | ACCURACY | **Missing gender symbol** — Gen 2 introduced gender display next to Pokemon name. Gen 3 continued it. Not mentioned in PokemonSlot design. | class-designs.md |
| PK10 | ACCURACY | **Missing held item indicator** — Gen 2/3 showed small icon next to Pokemon holding items. Not mentioned. | class-designs.md |
| PK11 | ACCURACY | **Missing item registration (SELECT shortcut)** — Key Items could be registered for quick-use via SELECT button on the overworld. Beloved QoL feature. | screen-designs.md |
| PK12 | ACCURACY | **Gen 3 trainer card had front/back flip** — Front: player info. Back: 8 badge slots. Press A to flip with animation. Plan combines everything on one screen, losing the iconic flip interaction. | screen-designs-player-card.md |
| PK13 | ACCURACY | **Save info should include more data** — Gen 3 showed badges, Pokedex count, play time, and location before saving. Plan only shows player name + play time. | screen-designs.md |
| PK14 | ACCURACY | **"Game saved!" auto-dismissed in originals** — No OK button needed. Brief message then auto-return to menu. | screen-designs.md |
| PK15 | MISSING | **OPTIONS menu absent** — Gen 2/3 OPTIONS: text speed, battle scene ON/OFF, battle style SHIFT/SET, sound mode. Gen 2 also had frame border customization (~10 border patterns!). | overview.md |
| PK16 | MISSING | **No input auto-repeat for long lists** — `getMovementDirectionUp()` is edge-triggered. For 150+ Pokedex entries, held-direction repeat is essential. Original games had ~0.3s initial delay then moderate repeat rate. | class-designs.md |
| PK17 | MISSING | **No Pokedex search/sort** — Gen 3 introduced search by name, type, color. Not planned. Understandable for initial implementation. | screen-designs-pokedex.md |
| PK18 | FEEL | **Sound effects are critical for Pokemon feel** — Every cursor move, confirm, cancel, menu open had distinct sounds. A silent menu feels lifeless. Consider promoting audio from Phase 7 to Phase 6. | implementation-phases.md |
| PK19 | FEEL | **No overworld dimming is correct** — Gen 2/3 did not dim the overworld when the menu opened. However, dialog dimming (50% alpha) for confirmation popups is a modern convention not in the originals (dialogs just appeared on top). | screen-designs.md |
| PK20 | DEVIATION | **"QUIT" never existed in Gen 2/3 menus** — Handheld games had no quit-to-desktop. Reasonable for PC fan-game but breaks authenticity. Consider hiding in OPTIONS sub-menu. | screen-designs.md |
| PK21 | DEVIATION | **"CLOSE" should be "EXIT" for Gen 3** — Gen 3 RSE/FRLG used "EXIT". Gen 2 had no close option (B-button only). | screen-designs.md |
| PK22 | DEVIATION | **Uniform party list vs Gen 3 asymmetric layout** — Gen 3's large-first-slot layout is one of the most visually distinctive RPG screens. Uniform list feels flat to Gen 3 fans. | screen-designs.md |
| PK23 | PRAISE | **HP bar color thresholds (>50% green, 20-50% yellow, <20% red) are exactly correct.** | class-designs.md |
| PK24 | PRAISE | **Confirmation defaults (save=YES, quit=NO) match the originals exactly.** | screen-designs.md |
| PK25 | PRAISE | **LEFT/RIGHT to cycle species in Pokedex detail matches Gen 3.** | screen-designs-pokedex.md |
| PK26 | PRAISE | **Description panel at bottom of bag screen matches Gen 2/3.** | screen-designs.md |
| PK27 | PRAISE | **Overworld continues rendering behind menu — correct for both Gen 2 and Gen 3.** | overview.md |
| PK28 | PRAISE | **Push-model SelectableList enables reuse across menu, shop, battle — mirrors how the originals reused list widgets everywhere.** | class-designs.md |

**Authenticity rating: 7/10** — Good foundation with solid architecture. Top 3 changes for authenticity: (1) Use player name as menu option instead of "CARD", (2) Make menu options conditional on game progress, (3) Add sound effects earlier.

---

## Round 2 — Resolutions

All Round 2 issues were addressed in the plan files. Summary by reviewer:

### Senior Engineer Resolutions

| # | Status | Resolution |
|---|--------|------------|
| SE1 | RESOLVED | Added `MenuScreen` interface (`show`, `hide`, `onActivated`, `killTweens`) in class-designs.md. Sub-controllers implement it. |
| SE2 | RESOLVED | `PauseManager` (reference-counted) added to Phase 0. Replaces per-consumer `pauseAll()`/`resumeAll()`. `PlayerDialogueManager` migrated in Phase 0, `MenuManager` uses it from Phase 2. |
| SE3 | RESOLVED | Rewrote `PokedexScreenController` to use `@ComponentReference(source=KEY)`. Removed `resolveUI()`/`uiResolved`. |
| SE4 | RESOLVED | Documented `setItems()` parallel-list contract. Added `getItemCount()`. |
| SE5 | RESOLVED | Added explicit `routeDirectionInput()` POKEDEX_DETAIL case calling `cycleSpecies(delta)`. |
| SE6 | RESOLVED | Added `MenuManager.onDestroy()` — force-close, kill tweens, resume pausables, reset InputMode. |
| SE7 | RESOLVED | Documented: callback removal note added to class-designs.md. |
| SE8 | RESOLVED | Added state guards to all tween `onComplete` lambdas. `onDestroy()` kills all tweens. |
| SE9 | RESOLVED | Fixed stale E4 in Round 1 table. Updated Pokedex screen design. |
| SE10 | RESOLVED | Added `getItemCount()` to SelectableList. |
| SE11 | RESOLVED | Documented: `setItems()` resets selectedIndex=0, scrollOffset=0. Does NOT fire onSelectionChanged (avoids spurious callbacks during setup). |
| SE12 | RESOLVED | Documented: SAVING tween uses state guard in callback. |
| SE13 | DEFERRED | Window resize during animation — edge case, not addressed. |
| SE14 | RESOLVED | Added componentKey uniqueness validation test to implementation-phases.md. |

### QA Resolutions

| # | Status | Resolution |
|---|--------|------------|
| QA1 | RESOLVED | FNT is a derived state from `!pokemon.isAlive()`, not a StatusCondition enum value. Shows "FNT" in red, overrides status display. |
| QA2 | RESOLVED | HP clamped to `[0.0, 1.0]` via `Math.min(1f, Math.max(0f, ...))`. |
| QA3 | RESOLVED | Added 2s watchdog timer as safety fallback for SAVING state. |
| QA4 | RESOLVED | Noted: verify `ComponentRegistry` handles unknown `_type` gracefully. |
| QA5 | RESOLVED | BACK is last item in SelectableList. Not a separate slot. |
| QA6 | RESOLVED | Added save failure path test to implementation-phases.md. |
| QA7 | RESOLVED | Covered by `MenuManager.onDestroy()` (SE6). |
| QA8 | RESOLVED | Documented refresh policy: refresh on screen entry only. |
| QA9 | RESOLVED | Money clamped with `Math.max(0, money)`. Play time capped at 999 hours. |
| QA10 | RESOLVED | Same as SE5 — explicit POKEDEX_DETAIL routing. |
| QA11 | RESOLVED | Added guard: `onSelect` checks `isSeen(speciesId)` before transitioning to POKEDEX_DETAIL. |
| QA12 | RESOLVED | Added Phase 0 regression tests for existing non-nested prefabs. |
| QA13 | RESOLVED | Documented: mixed slot types in same list. JSON polymorphism via `_type`. |
| QA14 | RESOLVED | Added integration test for full menu cycle. |
| QA15 | RESOLVED | Added componentKey uniqueness validation test. |
| QA16 | RESOLVED | Play time capped at 999:59 (`Math.min(999, hours)`). |
| QA17 | RESOLVED | Added `setItems()` reset behavior test. |
| QA18 | RESOLVED | Added `List<T>` @KEY verification task to Phase 1. |
| QA19 | RESOLVED | Added gamepad testing to manual test list. |
| QA20 | RESOLVED | Changed to use current save slot from SaveManager. |

### Product Owner Resolutions

| # | Status | Resolution |
|---|--------|------------|
| PO1 | DEFERRED | OPTIONS menu deferred to Post-MVP. Not blocking. |
| PO2 | RESOLVED | Added Phase 0 fallback note (flat prefab or programmatic inject). |
| PO3 | RESOLVED | MVP defined as Phases 0–3 + Phase 6. |
| PO4 | WON'T FIX | User decision: keep CLOSE. It stays. |
| PO5 | RESOLVED | Added per-phase "Done when" criteria. |
| PO6 | RESOLVED | Added Sound Effect Hooks section with specific sound list. Promoted to Phase 6. |
| PO7 | RESOLVED | Added Post-MVP Interactions section (team sub-menu, inventory sub-menu). |
| PO8 | RESOLVED | Added category position indicator ("2/4"). |
| PO9 | RESOLVED | Added localization debt note to overview.md. |
| PO10 | RESOLVED | Merged Phase 8 into Phase 6. |
| PO11 | RESOLVED | Pokedex/Card noted as Post-MVP with prerequisites. |
| PO12 | RESOLVED | Phases 3/4/5 parallelizability noted. |
| PO13 | RESOLVED | QUIT returns to title screen via `SceneManager.loadScene()`. |
| PO14 | RESOLVED | Same as QA1 — FNT handling added. |

### Pokemon Gen 2/3 Expert Resolutions

| # | Status | Resolution |
|---|--------|------------|
| PK1 | WON'T FIX | User: "False — Gen 3 FRLG has CARD showing name+money+badge." CARD label stays. |
| PK2 | RESOLVED | Added conditional menu options (POKéDEX when obtained, POKéMON when party non-empty). |
| PK3 | WON'T FIX | User: "Keep the slide animation." |
| PK4 | DEFERRED | User: "Layout will be reviewed later." |
| PK5 | RESOLVED | Added CONFIRM_OVERWRITE state and overwrite save dialog. |
| PK6 | RESOLVED | Documented sub-menus for team and inventory in Post-MVP section. |
| PK7 | N/A | BAG matches Gen 3. No change needed. |
| PK8 | RESOLVED | Changed "MEDICINE" to "ITEMS". |
| PK9 | DEFERRED | User: "Don't care for now." |
| PK10 | RESOLVED | Added held item "♦" indicator on PokemonSlot. |
| PK11 | RESOLVED | Added item registration mention for future. |
| PK12 | RESOLVED | Trainer card combined on one frame (no flip). |
| PK13 | RESOLVED | Save info expanded: BADGES count, POKéDEX count, player name, play time. |
| PK14 | RESOLVED | Auto-dismiss save success after 1s. No OK button. |
| PK15 | DEFERRED | User: "Not for now." OPTIONS deferred to Post-MVP. |
| PK16 | RESOLVED | Added input auto-repeat: 0.3s initial delay, ~8 moves/sec. |
| PK17 | DEFERRED | User: "Not planned." Pokedex search deferred. |
| PK18 | RESOLVED | Sound effects promoted to Phase 6 with specific hook list. |
| PK19 | N/A | User: "On top is fine." No change needed. |
| PK20 | RESOLVED | QUIT returns to title/main menu, not `System.exit(0)`. |
| PK21 | WON'T FIX | User: "Keep CLOSE." |
| PK22 | DEFERRED | User: "Design will come later." |

---

## Round 3 — Verification Review (Senior Engineer + QA)

Re-ran SE and QA reviews to verify Round 2 fixes. All original issues verified as resolved. New issues found and addressed:

### Senior Engineer — New Issues

| # | Severity | Issue | Resolution |
|---|----------|-------|------------|
| NEW-SE1 | IMPORTANT | `PlayerCardController` still used `resolveUI()`/`uiResolved` | RESOLVED — Rewritten to `@ComponentReference(source=KEY)` + `implements MenuScreen` |
| NEW-SE2 | IMPORTANT | `MenuState` enum missing `POKEDEX_LIST`, `POKEDEX_DETAIL`, `PLAYER_CARD` | RESOLVED — Added to enum with "Future screens" comment |
| NEW-SE3 | IMPORTANT | `routeDirectionInput()` missing `POKEDEX_DETAIL` case in code | RESOLVED — Added case routing LEFT/RIGHT to `PokedexScreenController.cycleSpecies()` |
| NEW-SE4 | SUGGESTION | Controllers don't show `implements MenuScreen` | RESOLVED — Both `PokedexScreenController` and `PlayerCardController` now show `implements MenuScreen` with method stubs |
| NEW-SE5 | SUGGESTION | `MenuScreen.java` not in Files to Create | RESOLVED — Added to implementation-phases.md |
| NEW-SE6 | SUGGESTION | `PokedexScreenController.java` and `PlayerCardController.java` not in Files to Create | RESOLVED — Added as "Future" phase entries |
| NEW-SE7 | SUGGESTION | `SAVE_SUCCESS` enum comment says "with OK" but design says no OK | RESOLVED — Updated comment to "auto-dismiss after 1s (no OK button)" |
| NEW-SE8 | SUGGESTION | `setItems()` "Does NOT fire" contradicts SE11 resolution wording | RESOLVED — Clarified Javadoc and SE11 resolution to be consistent |

### QA — New Issues

| # | Severity | Issue | Resolution |
|---|----------|-------|------------|
| NEW-QA1 | IMPORTANT | Same as NEW-SE1 — `PlayerCardController` uses banned pattern | RESOLVED — Same fix as NEW-SE1 |
| NEW-QA2 | IMPORTANT | Inventory `categoryList` bypasses `activeList` abstraction | RESOLVED — Documented dual-list routing in `routeDirectionInput()`: categoryList stays active in INVENTORY_SCREEN state |
| NEW-QA3 | IMPORTANT | No test for SAVING → SAVE_SUCCESS input re-enable | RESOLVED — Added test to MenuManager unit tests |
| NEW-QA4 | SUGGESTION | CONFIRM_OVERWRITE defaults YES (destructive) vs quit defaults NO | RESOLVED — Documented as intentional (matches original Pokemon games) |
| NEW-QA5 | SUGGESTION | `MenuScreen` interface not shown in MenuManager code | RESOLVED — Added `screens` map, `showScreen()`, `hideScreen()` delegation methods, and `killAllMenuTweens()` delegation to MenuManager |
| NEW-QA6 | SUGGESTION | No test for conditional menu option counts | RESOLVED — Added test: 5/6/7 items based on Pokedex + Pokemon availability |
| NEW-QA7 | SUGGESTION | No test for `maxHp = 0` division by zero | RESOLVED — Added test to PokemonSlot section |
| NEW-QA8 | SUGGESTION | Pokedex `List<UIText>` no size validation | RESOLVED — Added "List Slot Size Validation" section to screen-designs-pokedex.md |

### QA — Incomplete from Round 2

| # | Status | Issue | Resolution |
|---|--------|-------|------------|
| QA13 | NOW COMPLETE | Mixed slot types in team `SelectableList` not documented | RESOLVED — Added explicit note about `_type` discriminator and Java polymorphism to TeamScreenController section |
