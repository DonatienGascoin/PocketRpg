# Component Key Reference — Generalize @UiKeyReference to any Component

## Overview

**Problem:** `@UiKeyReference` only works with `UIComponent` subclasses because the key system (`uiKey` field) lives exclusively on `UIComponent`. Non-UI components like `AlphaGroup` or `BattlerDetailsUI` cannot be referenced by key, making it impossible for `BattleUI` to reference specific instances of these components in nested children.

**Solution:** Move the `key` field from `UIComponent` to base `Component`. Rename the annotation, registry, resolver, and editor to be component-generic instead of UI-specific. Keep `@ComponentRef` (hierarchy-based) as a separate system — it solves a different problem.

### Naming Changes

| Old Name | New Name |
|----------|----------|
| `@UiKeyReference` | `@ComponentKeyReference` |
| `UIComponent.uiKey` | `Component.key` (keep `uiKey` as deprecated alias on UIComponent) |
| `UIManager` | `ComponentKeyRegistry` |
| `UiKeyRefMeta` | `ComponentKeyRefMeta` |
| `UiKeyRefResolver` | `ComponentKeyRefResolver` |
| `UiKeyReferenceEditor` | `ComponentKeyReferenceEditor` |
| `UIKeyField` | `ComponentKeyField` |

### Backward Compatibility

- Existing `.scene` files serialize `"uiKey"` on UIComponents. The deserializer must read both `"uiKey"` (legacy) and `"key"` (new) field names.
- `UIComponent.getUiKey()` / `setUiKey()` kept as deprecated delegates to `Component.getKey()`.
- `UIManager` convenience methods (`getText()`, `getImage()`, etc.) remain as static delegates to the new registry.

---

## Phase 1: Core Infrastructure

Move the key field and create the new registry.

- [ ] **Component.java** — Add `@Getter @Setter protected String key;` field
- [ ] **UIComponent.java** — Remove `uiKey` field. Add deprecated `getUiKey()`/`setUiKey()` that delegate to `super.key`. This keeps existing code compiling during migration.
- [ ] **ComponentKeyRegistry.java** (NEW) — Replace `UIManager` registry. Changes:
  - `Map<String, Component>` instead of `Map<String, UIComponent>`
  - `register(String key, Component component)` — accepts any Component
  - `get(String key, Class<T> type)` with `T extends Component` bound
  - Keep the `UITransform` cache for backward compat (only populated when component is UIComponent)
  - Convenience methods: `getText()`, `getImage()`, etc. delegate to typed `get()`
- [ ] **UIManager.java** — Deprecate entire class. All methods delegate to `ComponentKeyRegistry`. No behavior change for existing callers.

## Phase 2: Metadata & Annotation Rename

- [ ] **ComponentKeyReference.java** (NEW, replaces `UiKeyReference.java`) — Same annotation, new name. `@Retention(RUNTIME) @Target(FIELD)`, `required()` attribute.
- [ ] **UiKeyReference.java** — Delete (or keep as deprecated alias that just re-exports `@ComponentKeyReference` — but cleaner to delete since it's only used in 4 component files).
- [ ] **ComponentKeyRefMeta.java** (NEW, replaces `UiKeyRefMeta.java`) — Change `Class<? extends UIComponent> componentType` → `Class<? extends Component> componentType`. Same methods otherwise.
- [ ] **UiKeyRefMeta.java** — Delete.
- [ ] **ComponentMeta.java** — Rename `uiKeyRefs()` → `keyRefs()` (record field).
- [ ] **ComponentRegistry.java** (line 445) — Remove `UIComponent.class.isAssignableFrom()` check. Accept any `Component` subclass field annotated with `@ComponentKeyReference`. Update to create `ComponentKeyRefMeta` instead of `UiKeyRefMeta`.

## Phase 3: Resolution & Serialization

- [ ] **ComponentKeyRefResolver.java** (NEW, replaces `UiKeyRefResolver.java`) — Same logic, but:
  - Line 125: `ComponentKeyRegistry.get(key, refMeta.componentType())` instead of `UIManager.get()`
  - Use `ComponentKeyRefMeta` instead of `UiKeyRefMeta`
- [ ] **UiKeyRefResolver.java** — Delete.
- [ ] **Scene.java** — Registration/unregistration (lines 346-437): Change `component instanceof UIComponent` check to check `component.getKey()` on any Component. Call `ComponentKeyRegistry.register()` instead of `UIManager.register()`.
- [ ] **RuntimeSceneLoader.java** — Update resolver call from `UiKeyRefResolver` to `ComponentKeyRefResolver`. Resolution order stays the same (ComponentRef first, then KeyRef, then start()).
- [ ] **ComponentTypeAdapterFactory.java** — Serialization changes:
  - Write: use `ComponentKeyRefResolver.getPendingKey()` instead of `UiKeyRefResolver`
  - Read: use `ComponentKeyRefResolver.storePendingKey()` instead of `UiKeyRefResolver`
  - `findUiKeyRef()` → `findKeyRef()`, use `ComponentKeyRefMeta`
  - For backward compat: when reading a UIComponent, if `"uiKey"` JSON field is present, map it to the `key` field on Component

## Phase 4: Editor UI

- [ ] **ComponentKeyReferenceEditor.java** (NEW, replaces `UiKeyReferenceEditor.java`) — Main change in `collectAvailableKeys()`:
  - Was: scan all entities for `UIComponent` with matching type and non-empty `uiKey`
  - Now: scan all entities for any `Component` with matching type and non-empty `key`
  - Tooltip updated from "UI Key reference → ..." to "Component key reference → ..."
- [ ] **UiKeyReferenceEditor.java** — Delete.
- [ ] **ComponentKeyField.java** (NEW, replaces `UIKeyField.java`) — The inspector widget that shows the `key` field with color-coded background. Now works on any Component, not just UIComponent. Change field name from `"uiKey"` to `"key"`, label from "UI Key" to "Key".
- [ ] **UIKeyField.java** — Delete.
- [ ] **ReflectionFieldEditor.java** — Update references: `UiKeyRefMeta` → `ComponentKeyRefMeta`, `UiKeyReferenceEditor` → `ComponentKeyReferenceEditor`, `findUiKeyRefForField()` → `findKeyRefForField()`, `meta.uiKeyRefs()` → `meta.keyRefs()`
- [ ] **ComponentReflectionUtils.java** — Update `isUiKeyRefField()` → `isKeyRefField()`, use `meta.keyRefs()` instead of `meta.uiKeyRefs()`

## Phase 5: Update Consumers

- [ ] **BattleUI.java** — Change `@UiKeyReference` → `@ComponentKeyReference`
- [ ] **BattlerDetailsUI.java** — Change `@UiKeyReference` → `@ComponentKeyReference`
- [ ] **PlayerUI.java** — Change `@UiKeyReference` → `@ComponentKeyReference`
- [ ] **PlayerPauseUI.java** — Change `@UiKeyReference` → `@ComponentKeyReference`
- [ ] **Any inspector that calls `UIKeyField.draw()`** — Update to `ComponentKeyField.draw()`

## Phase 6: Scene File Migration

- [ ] **Battle.scene, DemoScene.scene** — Rename `"uiKey"` → `"key"` in JSON. (Or handle via backward compat in deserializer and migrate lazily on next save.)

## Phase 7: Verification

- [ ] Delete old files: `UiKeyReference.java`, `UiKeyRefMeta.java`, `UiKeyRefResolver.java`, `UiKeyReferenceEditor.java`, `UIKeyField.java`
- [ ] Run `mvn compile` — verify no compile errors
- [ ] Run `mvn test` — verify no test failures
- [ ] Manual test: open editor, verify key dropdown works for both UIComponent and non-UI Component fields

## Phase 8: Remove Deprecated Code

Remove all backward-compatibility shims introduced during migration.

- [ ] **UIManager.java** — Delete entirely. All callers should now use `ComponentKeyRegistry` directly.
- [ ] **UIComponent.java** — Remove deprecated `getUiKey()`/`setUiKey()` methods. The `key` field on `Component` is the sole source of truth.
- [ ] **ComponentTypeAdapterFactory.java** — Remove `"uiKey"` backward-compat read path. Only `"key"` is recognized.
- [ ] **Grep for any remaining references** to `UIManager`, `getUiKey`, `setUiKey`, `uiKey` — update or remove.
- [ ] Run `mvn compile` — verify no compile errors
- [ ] Run `mvn test` — verify no test failures
- [ ] Code review

---

## Files Summary

| File | Action | Phase |
|------|--------|-------|
| `components/Component.java` | Add `key` field | 1 |
| `components/ui/UIComponent.java` | Remove `uiKey`, add deprecated delegates | 1 |
| `ui/ComponentKeyRegistry.java` | **NEW** — generic component registry | 1 |
| `ui/UIManager.java` | Deprecate, delegate to ComponentKeyRegistry | 1 |
| `components/ComponentKeyReference.java` | **NEW** — annotation replacing @UiKeyReference | 2 |
| `components/UiKeyReference.java` | Delete | 2 |
| `serialization/ComponentKeyRefMeta.java` | **NEW** — replaces UiKeyRefMeta | 2 |
| `serialization/UiKeyRefMeta.java` | Delete | 2 |
| `serialization/ComponentMeta.java` | Rename field `uiKeyRefs` → `keyRefs` | 2 |
| `serialization/ComponentRegistry.java` | Remove UIComponent type check | 2 |
| `serialization/ComponentKeyRefResolver.java` | **NEW** — replaces UiKeyRefResolver | 3 |
| `serialization/UiKeyRefResolver.java` | Delete | 3 |
| `scenes/Scene.java` | Register any Component with key | 3 |
| `editor/scene/RuntimeSceneLoader.java` | Use ComponentKeyRefResolver | 3 |
| `serialization/custom/ComponentTypeAdapterFactory.java` | Use ComponentKeyRefMeta, backward compat for `"uiKey"` | 3 |
| `editor/ui/fields/ComponentKeyReferenceEditor.java` | **NEW** — replaces UiKeyReferenceEditor | 4 |
| `editor/ui/fields/UiKeyReferenceEditor.java` | Delete | 4 |
| `editor/ui/inspectors/ComponentKeyField.java` | **NEW** — replaces UIKeyField | 4 |
| `editor/ui/inspectors/UIKeyField.java` | Delete | 4 |
| `editor/ui/fields/ReflectionFieldEditor.java` | Update references | 4 |
| `serialization/ComponentReflectionUtils.java` | Update references | 4 |
| `components/pokemon/BattleUI.java` | `@ComponentKeyReference` | 5 |
| `components/pokemon/BattlerDetailsUI.java` | `@ComponentKeyReference` | 5 |
| `components/pokemon/PlayerUI.java` | `@ComponentKeyReference` | 5 |
| `components/pokemon/PlayerPauseUI.java` | `@ComponentKeyReference` | 5 |
| `gameData/scenes/Battle.scene` | `"uiKey"` → `"key"` | 6 |
| `gameData/scenes/DemoScene.scene` | `"uiKey"` → `"key"` | 6 |
| `ui/UIManager.java` | Delete | 8 |
| `components/ui/UIComponent.java` | Remove deprecated `getUiKey`/`setUiKey` | 8 |
| `serialization/custom/ComponentTypeAdapterFactory.java` | Remove `"uiKey"` backward compat | 8 |

## Testing Strategy

1. **Compile check** — `mvn compile` passes
2. **Existing tests** — `mvn test` passes
3. **Editor manual test:**
   - Open Battle.scene in editor
   - Verify UIComponent key fields still display correctly with color coding
   - Verify `@ComponentKeyReference` dropdowns on BattleUI show available keys
   - Add a `key` to an `AlphaGroup` component, verify it appears in the dropdown for `BattlerDetailsUI`
   - Enter play mode, verify references resolve correctly
4. **Backward compat** — Open a scene file with old `"uiKey"` format, verify it loads correctly
