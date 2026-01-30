# IGameObject Interface & Play Mode Inspection

Merged plan combining `igameobject-interface` and `play-mode-inspection` into a single implementation.

## Documents

- `implementation-plan.md` - Full merged implementation plan (8 phases)

## Problem

1. Components stored in `EditorGameObject` have `gameObject = null`, causing NPEs in editor
2. Hierarchy/Inspector panels can't display runtime GameObjects during play mode

## Solution

1. Create `IGameObject` interface implemented by both `GameObject` and `EditorGameObject`
2. Change `Component.gameObject` to `Component.owner` of type `IGameObject`
3. Create `HierarchyItem extends IGameObject` for hierarchy display
4. Create `RuntimeGameObjectAdapter` to bridge runtime GameObjects to hierarchy
5. Use existing `EditorEventBus` play mode events (`PlayModeStartedEvent`, etc.)

## Key Design Decisions

- **Event bus for play mode state** — `PlayModeController` already publishes events; panels use `playModeController.isActive()` for render branching
- **Per-frame hierarchy refresh** — Runtime hierarchy reads `getRootGameObjects()` each frame, so entity creation/deletion/reparenting is automatically reflected
- **Destroyed object pruning** — `PlayModeSelectionManager.pruneDestroyedObjects()` removes stale selections using `GameObject.isDestroyed()`

## Phases

| Phase | Content |
|-------|---------|
| 1 | IGameObject interface + GameObject/EditorGameObject implement it |
| 2 | Component owner refactor (IGameObject instead of GameObject) |
| 3 | HierarchyItem interface + RuntimeGameObjectAdapter |
| 4 | PlayModeSelectionManager + PlayModeController integration |
| 5 | Hierarchy panel play mode support |
| 6 | Inspector panel play mode support |
| 7 | Wiring in EditorUIController |
| 8 | Polish, visual indicators, edge cases |

## Status

- [ ] Plan reviewed
- [ ] Implementation started
- [ ] Phase 1: IGameObject interface
- [ ] Phase 2: Component owner refactor
- [ ] Phase 3: HierarchyItem + adapter
- [ ] Phase 4: Play mode selection
- [ ] Phase 5: Hierarchy panel
- [ ] Phase 6: Inspector panel
- [ ] Phase 7: Wiring
- [ ] Phase 8: Polish & edge cases
- [ ] Implementation complete
