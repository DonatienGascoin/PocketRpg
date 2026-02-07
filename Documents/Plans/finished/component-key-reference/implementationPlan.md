# Component Reference — Unify @ComponentRef and @UiKeyReference

## Overview

**Problem:** `@UiKeyReference` only works with `UIComponent` subclasses because the key system (`uiKey` field) lives exclusively on `UIComponent`. Non-UI components like `AlphaGroup` or `BattlerDetailsUI` cannot be referenced by key, making it impossible for `BattleUI` to reference specific instances of these components in nested children.

**Solution:** Merge `@ComponentRef` and `@UiKeyReference` into a single `@ComponentReference` annotation with a mandatory `source` parameter. Add a `componentKey` field to base `Component`. Create `ComponentKeyRegistry` to replace `UIManager`. No backward compatibility shims — atomic migration.

### Unified Annotation Design

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ComponentReference {
    Source source();          // mandatory — no default, intent must be explicit
    boolean required() default true;

    enum Source {
        SELF,                 // hierarchy: same GameObject (not serialized)
        PARENT,               // hierarchy: parent GameObject (not serialized)
        CHILDREN,             // hierarchy: direct children (not serialized)
        CHILDREN_RECURSIVE,   // hierarchy: all descendants (not serialized)
        KEY                   // registry: lookup by componentKey (serialized as string/string[])
    }
}
```

Usage:

```java
// Hierarchy-based (not serialized, auto-resolved by type)
@ComponentReference(source = Source.SELF)
private Transform transform;

@ComponentReference(source = Source.PARENT)
private Inventory parentInventory;

@ComponentReference(source = Source.CHILDREN)
private List<Collider> childColliders;

// Key-based (serialized as string, resolved from ComponentKeyRegistry)
@ComponentReference(source = Source.KEY)
private BattlerDetailsUI opponentDetails;

@ComponentReference(source = Source.KEY)
private AlphaGroup healthAlpha;

// Key-based list (serialized as string[], editor shows list of dropdowns)
@ComponentReference(source = Source.KEY)
private List<BattlerDetailsUI> allDetails;
```

### Naming Changes

| Old Name | New Name |
|----------|----------|
| `@ComponentRef` | `@ComponentReference(source = Source.SELF/PARENT/CHILDREN/...)` |
| `@UiKeyReference` | `@ComponentReference(source = Source.KEY)` |
| `UIComponent.uiKey` | `Component.componentKey` |
| `UIManager` | `ComponentKeyRegistry` |
| `ComponentRefMeta` + `UiKeyRefMeta` | `ComponentReferenceMeta` |
| `ComponentRefResolver` + `UiKeyRefResolver` | `ComponentReferenceResolver` |
| `UiKeyReferenceEditor` | `ComponentKeyReferenceEditor` |
| `UIKeyField` | `ComponentKeyField` |

### Resolution Order

The unified `ComponentReferenceResolver.resolveAll(go)` runs two internal passes in a single call:

1. **Pass 1 — Hierarchy refs** (SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE): Resolved from the GameObject tree. Only requires parent-child links to be established.
2. **Pass 2 — Key refs** (KEY): Resolved from `ComponentKeyRegistry`. Requires registry to be populated.

Both passes run **after** `scene.addGameObject()` (which populates the registry). The hierarchy resolver only needs the parent-child tree — it has no dependency on running before scene registration. `Scene.initialize()` already runs both passes sequentially after all GameObjects are added, confirming this.

**RuntimeSceneLoader lifecycle after migration:**

```
Phase 1: Create all GameObjects (deserialize from JSON)
Phase 2: Set up parent-child relationships
Phase 3: scene.addGameObject(go)                        ← populates ComponentKeyRegistry
Phase 4: ComponentReferenceResolver.resolveAll(go)      ← hierarchy + key, single call
Phase 5: go.start()
```

### Key-based List Support

`@ComponentReference(source = Source.KEY)` on a `List<T>` field:

- **Serialization:** JSON array of strings: `["key1", "key2"]`
- **Editor:** List of dropdown combos with add/remove buttons
- **Resolution:** Iterate keys, look up each in `ComponentKeyRegistry`, collect into list

---

## Phase 1: Add New Infrastructure

New files only. Old files still exist, code compiles.

- [ ] **Component.java** — Add `@Getter @Setter protected String componentKey;` field
- [ ] **ComponentReference.java** (NEW) — Unified annotation with mandatory `source`, `Source` enum
- [ ] **ComponentReferenceMeta.java** (NEW) — Unified metadata record:
  ```java
  public record ComponentReferenceMeta(
      Field field,
      String fieldName,
      Class<? extends Component> componentType,
      ComponentReference.Source source,
      boolean required,
      boolean isList
  )
  ```
- [ ] **ComponentReferenceResolver.java** (NEW) — Unified resolver with two passes (hierarchy then key)
- [ ] **ComponentKeyRegistry.java** (NEW) — Generic component registry (`Map<String, Component>`)
- [ ] **ComponentKeyReferenceEditor.java** (NEW) — Editor dropdown for KEY mode fields, scans all entities for matching type with non-empty `componentKey`
- [ ] **ComponentKeyField.java** (NEW) — Inspector widget showing `componentKey` with color-coded background and duplicate detection. Drawn by `ReflectionFieldEditor` at the top of every component's inspector (no opt-in needed — `componentKey` is on base `Component`, which `collectFields` excludes, so it won't appear in the regular field loop)

## Phase 2: Migrate All Consumers + Scene Files

All references updated atomically. Old classes become unreferenced. Scene files updated in the same phase to stay in sync with the serializer.

### Annotation consumers — @UiKeyReference → @ComponentReference(source = KEY)

- [ ] **BattleUI.java** — `@UiKeyReference` → `@ComponentReference(source = Source.KEY)`
- [ ] **BattlerDetailsUI.java** — `@UiKeyReference` → `@ComponentReference(source = Source.KEY)`
- [ ] **PlayerUI.java** — `@UiKeyReference` → `@ComponentReference(source = Source.KEY)`
- [ ] **PlayerPauseUI.java** — `@UiKeyReference` → `@ComponentReference(source = Source.KEY)`

### Annotation consumers — @ComponentRef → @ComponentReference(source = ...)

- [ ] **PlayerUI.java** — `@ComponentRef` → `@ComponentReference(source = Source.SELF)`
- [ ] **PlayerMovement.java** — `@ComponentRef` → `@ComponentReference(source = Source.SELF)`
- [ ] **AnimationComponent.java** — `@ComponentRef` → `@ComponentReference(source = Source.SELF)`
- [ ] **AnimatorComponent.java** — `@ComponentRef` → `@ComponentReference(source = Source.SELF)`
- [ ] **GridMovementAnimator.java** — `@ComponentRef` (x2) → `@ComponentReference(source = Source.SELF)`
- [ ] **InteractableComponent.java** — `@ComponentRef` → `@ComponentReference(source = Source.SELF)`
- [ ] **WarpZone.java** — `@ComponentRef` → `@ComponentReference(source = Source.SELF)`

### Documentation references (comments only, no code change needed)

- `ISaveable.java` line 18 — mentions `@ComponentRef` in a javadoc comment

### Inspector consumers

- [ ] **UITextInspector.java** — `UIKeyField.draw()` → `ComponentKeyField.draw()`
- [ ] **UIPanelInspector.java** — `UIKeyField.draw()` → `ComponentKeyField.draw()`
- [ ] **UIImageInspector.java** — `UIKeyField.draw()` → `ComponentKeyField.draw()`
- [ ] **UIButtonInspector.java** — `UIKeyField.draw()` → `ComponentKeyField.draw()`
- [ ] **UICanvasInspector.java** — `UIKeyField.draw()` → `ComponentKeyField.draw()`

### Core systems

- [ ] **UIComponent.java** — Remove `uiKey` field entirely (replaced by inherited `componentKey`)
- [ ] **ComponentMeta.java** — Replace `references()` + `uiKeyRefs()` with single `references()` list of `ComponentReferenceMeta`
- [ ] **ComponentRegistry.java** — Unified field discovery: scan for `@ComponentReference`, create `ComponentReferenceMeta` based on `source`. For KEY mode, add to fields list with String.class override. For hierarchy mode, don't add to fields list.
- [ ] **Scene.java** — Registration/unregistration: change `component instanceof UIComponent` + `getUiKey()` to `component.getComponentKey()` on any Component. Call `ComponentKeyRegistry.register()`. Update `initialize()` (line 233) to use `ComponentReferenceResolver`.
- [ ] **SceneManager.java** — Replace `UIManager.clear()` with `ComponentKeyRegistry.clear()` at lines 133 and 508.
- [ ] **RuntimeSceneLoader.java** — Remove Phase 3 (hierarchy resolve before addGameObject). Move resolution to after Phase 4 (addGameObject). Replace `ComponentRefResolver` + `UiKeyRefResolver` with single `ComponentReferenceResolver.resolveAll()` call after `scene.addGameObject()`.
- [ ] **ComponentTypeAdapterFactory.java** — Use `ComponentReferenceMeta`. For KEY mode fields: read/write pending keys via `ComponentReferenceResolver`. For list KEY mode: read/write as JSON string array. No `"uiKey"` fallback — scene files are migrated atomically.
- [ ] **ReflectionFieldEditor.java** — Update to use `ComponentReferenceMeta`, `ComponentKeyReferenceEditor`. Draw `ComponentKeyField` at the top of every component inspector automatically. (`componentKey` is on base `Component` which `collectFields` skips at line 428, so no duplicate field will appear.)
- [ ] **ComponentReflectionUtils.java** — Update `isKeyRefField()` to check `meta.references()` for KEY source entries

### Scene files (atomic with code changes)

- [ ] **Battle.scene** — `"uiKey"` → `"componentKey"` in all component properties
- [ ] **DemoScene.scene** — `"uiKey"` → `"componentKey"` in all component properties

## Phase 3: Delete Old Files

Old files are now unreferenced. Code compiles after deletion.

- [ ] Delete `components/UiKeyReference.java`
- [ ] Delete `components/ComponentRef.java`
- [ ] Delete `serialization/UiKeyRefMeta.java`
- [ ] Delete `serialization/ComponentRefMeta.java`
- [ ] Delete `serialization/UiKeyRefResolver.java`
- [ ] Delete `serialization/ComponentRefResolver.java`
- [ ] Delete `editor/ui/fields/UiKeyReferenceEditor.java`
- [ ] Delete `editor/ui/inspectors/UIKeyField.java`
- [ ] Delete `ui/UIManager.java`

## Phase 4: Tests

Existing test `ComponentReflectionUtilsTest` only tests `deepCopyValue` — no annotation/resolver tests exist. No existing tests reference `@ComponentRef` or `@UiKeyReference`.

- [ ] **Write new tests for `ComponentReferenceResolver`:**
  - Hierarchy resolution: SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE
  - KEY resolution: single field
  - KEY resolution: list field (if list support is implemented)
  - Error cases: missing key with required=true logs warning, wrong type returns null
  - Verify null field when key not found and required=false
- [ ] **Write new tests for `ComponentKeyRegistry`:**
  - Register/unregister/clear
  - Type-safe get with wrong type returns null
  - Duplicate key warning
- [ ] `mvn compile` — no compile errors
- [ ] `mvn test` — all tests pass

## Phase 5: Manual Verification

- [ ] Open Battle.scene in editor
  - Verify UIComponent `componentKey` fields display with color coding
  - Verify `@ComponentReference(source = KEY)` dropdowns show available keys
  - Set a `componentKey` on an `AlphaGroup` component, verify it appears in BattlerDetailsUI's dropdown
  - Enter play mode, verify key references resolve correctly
  - Verify hierarchy references (SELF, PARENT, CHILDREN) still resolve correctly
- [ ] Verify keys on nested children (3+ levels deep) appear in dropdown
- [ ] Code review

---

## Files Summary

| File | Action | Phase |
|------|--------|-------|
| `components/Component.java` | Add `componentKey` field | 1 |
| `components/ComponentReference.java` | **NEW** — unified annotation | 1 |
| `serialization/ComponentReferenceMeta.java` | **NEW** — unified metadata | 1 |
| `serialization/ComponentReferenceResolver.java` | **NEW** — unified resolver (2 passes) | 1 |
| `ui/ComponentKeyRegistry.java` | **NEW** — generic component registry | 1 |
| `editor/ui/fields/ComponentKeyReferenceEditor.java` | **NEW** — editor dropdown for KEY mode | 1 |
| `editor/ui/inspectors/ComponentKeyField.java` | **NEW** — inspector key widget | 1 |
| `components/ui/UIComponent.java` | Remove `uiKey` field | 2 |
| `serialization/ComponentMeta.java` | Merge `references()` + `uiKeyRefs()` → single `references()` | 2 |
| `serialization/ComponentRegistry.java` | Unified `@ComponentReference` discovery | 2 |
| `scenes/Scene.java` | Use `componentKey` + `ComponentKeyRegistry`, update `initialize()` | 2 |
| `scenes/SceneManager.java` | `ComponentKeyRegistry.clear()` | 2 |
| `editor/scene/RuntimeSceneLoader.java` | Use `ComponentReferenceResolver` | 2 |
| `serialization/custom/ComponentTypeAdapterFactory.java` | Use `ComponentReferenceMeta`, KEY list support | 2 |
| `editor/ui/fields/ReflectionFieldEditor.java` | Update references | 2 |
| `serialization/ComponentReflectionUtils.java` | Update references | 2 |
| `components/pokemon/BattleUI.java` | `@ComponentReference(source = KEY)` | 2 |
| `components/pokemon/BattlerDetailsUI.java` | `@ComponentReference(source = KEY)` | 2 |
| `components/pokemon/PlayerUI.java` | `@ComponentReference(source = SELF/KEY)` | 2 |
| `components/pokemon/PlayerPauseUI.java` | `@ComponentReference(source = KEY)` | 2 |
| `components/pokemon/PlayerMovement.java` | `@ComponentReference(source = SELF)` | 2 |
| `components/pokemon/GridMovementAnimator.java` | `@ComponentReference(source = SELF)` (x2) | 2 |
| `components/animations/AnimationComponent.java` | `@ComponentReference(source = SELF)` | 2 |
| `components/animations/AnimatorComponent.java` | `@ComponentReference(source = SELF)` | 2 |
| `components/interaction/InteractableComponent.java` | `@ComponentReference(source = SELF)` | 2 |
| `components/interaction/WarpZone.java` | `@ComponentReference(source = SELF)` | 2 |
| `editor/ui/inspectors/UITextInspector.java` | `ComponentKeyField.draw()` | 2 |
| `editor/ui/inspectors/UIPanelInspector.java` | `ComponentKeyField.draw()` | 2 |
| `editor/ui/inspectors/UIImageInspector.java` | `ComponentKeyField.draw()` | 2 |
| `editor/ui/inspectors/UIButtonInspector.java` | `ComponentKeyField.draw()` | 2 |
| `editor/ui/inspectors/UICanvasInspector.java` | `ComponentKeyField.draw()` | 2 |
| `gameData/scenes/Battle.scene` | `"uiKey"` → `"componentKey"` | 2 |
| `gameData/scenes/DemoScene.scene` | `"uiKey"` → `"componentKey"` | 2 |
| `components/UiKeyReference.java` | Delete | 3 |
| `components/ComponentRef.java` | Delete | 3 |
| `serialization/UiKeyRefMeta.java` | Delete | 3 |
| `serialization/ComponentRefMeta.java` | Delete | 3 |
| `serialization/UiKeyRefResolver.java` | Delete | 3 |
| `serialization/ComponentRefResolver.java` | Delete | 3 |
| `editor/ui/fields/UiKeyReferenceEditor.java` | Delete | 3 |
| `editor/ui/inspectors/UIKeyField.java` | Delete | 3 |
| `ui/UIManager.java` | Delete | 3 |

## Testing Strategy

1. **Compile check** — `mvn compile` passes
2. **Existing tests** — `mvn test` passes
3. **Editor manual test:**
   - Verify `componentKey` field displays on any component in inspector
   - Verify KEY dropdowns show available keys from any component type
   - Verify keys on nested children appear in dropdown
   - Verify hierarchy sources (SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE) resolve
   - Enter play mode, verify all references resolve correctly
4. **Scene loading** — Open Battle.scene and DemoScene.scene, verify all keys load correctly

## Design Notes

### EditorScene.getEntities() is flat and complete

`EditorScene.entities` is a flat list containing ALL entities (roots + children). `getRootEntities()` filters this list for entities without a parent. No recursive helper method is needed — `collectAvailableKeys()` already scans all entities including nested children.

### Null componentKey won't bloat scene files

`ComponentTypeAdapterFactory` (line 185-187) skips null field values during serialization. Since `componentKey` defaults to null, only components with an explicitly set key will have `"componentKey"` in their JSON.

### Resolver single call works because hierarchy doesn't depend on scene registration

The current RuntimeSceneLoader runs hierarchy resolution (Phase 3) BEFORE `addGameObject` and key resolution (Phase 5) AFTER. But the hierarchy resolver only reads the parent-child tree — it never accesses the registry. So both passes can safely run after `addGameObject` in a single `resolveAll()` call. `Scene.initialize()` already does this (both passes after all GameObjects are added).

### componentKey field is invisible to collectFields

`ComponentRegistry.collectFields()` (line 428) stops recursing at `Component.class`. Since `componentKey` is declared on `Component`, it never enters the field list. This means `ReflectionFieldEditor` can safely draw `ComponentKeyField` at the top of every inspector without worrying about duplication. Custom UI inspectors that already call `UIKeyField.draw()` will switch to `ComponentKeyField.draw()` — they still draw it explicitly because they control their own layout.
