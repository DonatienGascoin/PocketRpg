# Plan 01: EditorEventBus Listener Leak

## Overview

**Problem:** Subscribers call `EditorEventBus.get().subscribe()` across many editor classes but never unsubscribe. The bus holds all listeners in a `HashMap<Class, List<Consumer>>`. When panels close or are recreated, old listeners accumulate, causing unbounded memory growth and duplicate event handling.

**Severity:** CRITICAL

**Approach:** Add unsubscribe support to `EditorEventBus`, then add cleanup calls in every subscriber class that registers listeners.

## Phase 1: Add Unsubscribe API to EditorEventBus

- [ ] Add `unsubscribe(Class<T> eventType, Consumer<T> handler)` method that removes a specific handler from the subscriber list
- [ ] Add `unsubscribeAll(Object owner)` method — requires changing subscribe to track owner references (e.g. `subscribe(Class<T>, Consumer<T>, Object owner)`)
- [ ] Alternatively, have `subscribe()` return a `Subscription` token with an `unsubscribe()` method (cleaner pattern)
- [ ] Add `getSubscriberCount(Class<?> eventType)` for diagnostics

## Phase 2: Fix All Subscribers

Each file below stores its listener references as fields and unsubscribes in its `destroy()` or cleanup method:

- [ ] `EditorUIController.java` — 8 subscriptions (lines 186, 192, 199, 208, 215, 218, 221, 226)
- [ ] `EditorToolController.java` — 3 subscriptions (lines 207, 216, 230)
- [ ] `AssetBrowserPanel.java` — 1 subscription (line 116)
- [ ] `CollisionPanel.java` — 1 subscription (line 89)
- [ ] `ShortcutRegistry.java` — 2 subscriptions (lines 50-51)
- [ ] `SceneViewport.java` — 2 subscriptions (lines 61-62)
- [ ] `StatusBar.java` — 1 subscription (line 41)
- [ ] `SceneViewToolbar.java` — 2 subscriptions (lines 78-79)
- [ ] `TilesetPalettePanel.java` — 1 subscription (line 397)
- [ ] `PlayModeController.java` — 1 subscription (line 110)

## Phase 3: Diagnostic Logging

- [ ] Add a debug method to EditorEventBus that logs subscriber counts per event type
- [ ] Optionally log warnings when subscriber count exceeds a threshold

## Files to Modify

| File | Change |
|------|--------|
| `editor/events/EditorEventBus.java` | Add unsubscribe API and owner tracking |
| `editor/EditorUIController.java` | Store subscription refs, unsubscribe in cleanup |
| `editor/EditorToolController.java` | Store subscription refs, unsubscribe in cleanup |
| `editor/panels/AssetBrowserPanel.java` | Store subscription ref, unsubscribe in cleanup |
| `editor/panels/CollisionPanel.java` | Store subscription ref, unsubscribe in cleanup |
| `editor/shortcut/ShortcutRegistry.java` | Store subscription refs, unsubscribe in cleanup |
| `editor/ui/SceneViewport.java` | Store subscription refs, unsubscribe in cleanup |
| `editor/ui/StatusBar.java` | Store subscription ref, unsubscribe in cleanup |
| `editor/ui/SceneViewToolbar.java` | Store subscription refs, unsubscribe in cleanup |
| `editor/panels/TilesetPalettePanel.java` | Store subscription ref, unsubscribe in cleanup |
| `editor/PlayModeController.java` | Store subscription ref, unsubscribe in cleanup |

## Testing Strategy

- Unit test: subscribe, fire event, verify handler called; unsubscribe, fire event, verify handler NOT called
- Unit test: `unsubscribeAll(owner)` removes all handlers for that owner
- Manual test: open/close editor panels repeatedly, verify subscriber counts stay stable via diagnostic log

## Code Review

- [ ] Verify no subscriber classes were missed (grep for `subscribe(` in editor package)
- [ ] Verify all cleanup paths are covered (destroy, dispose, close)
- [ ] Verify existing `reset()` method still works correctly
