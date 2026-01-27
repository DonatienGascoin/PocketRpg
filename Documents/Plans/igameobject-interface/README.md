# IGameObject Interface Refactor

Extract common interface so Components work in both runtime and editor contexts.

## Documents

- `implementation-plan.md` - Full implementation plan

## Problem

Components stored in `EditorGameObject` have `gameObject = null`, causing:
- `getTransform()` throws NPE in editor
- `getComponent()` throws NPE in editor
- Developers must remember which methods are "safe"

## Solution

Create `IGameObject` interface implemented by both `GameObject` and `EditorGameObject`.
Change `Component.gameObject` to `Component.owner` of type `IGameObject`.

## Benefits

- Components can use `getTransform()` everywhere
- Components can use `getComponent()` everywhere
- No more null checks for common operations
- Clean separation between runtime-only and universal features

## Status

- [ ] Plan reviewed
- [ ] Implementation started
- [ ] Phase 1: IGameObject interface
- [ ] Phase 2: GameObject implements
- [ ] Phase 3: EditorGameObject implements
- [ ] Phase 4: Component uses IGameObject
- [ ] Phase 5-6: Wire up owner references
- [ ] Implementation complete
