# Plan 1: Deep Copy Robustness

## Overview

**Problem**: `ComponentReflectionUtils.deepCopyValue()` only handles `Vector2f/3f/4f`. Lists, Maps, and arrays are shared between clone and original. Editing a list field on a cloned component mutates the original.

**Approach**: Expand `deepCopyValue()` to recursively deep-copy all mutable field types: `List`, `Map`, arrays, and nested mutables (e.g., a `List<Vector2f>`).

**Addresses**: Review finding #6 (shallow clone of mutable sub-objects).

---

## Phase 1: Expand deepCopyValue()

### Files to Modify

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/serialization/ComponentReflectionUtils.java` | Expand `deepCopyValue()` to handle `List`, `Map`, arrays, nested mutables |

### Tasks

- [ ] Add `List` handling: create a new `ArrayList`, recursively `deepCopyValue()` each element
- [ ] Add `Map` handling: create a new `LinkedHashMap`, recursively `deepCopyValue()` each key and value
- [ ] Add array handling: use `Array.newInstance()` + `System.arraycopy` for primitives, recursive copy for object arrays
- [ ] Preserve existing immutable-type passthrough (String, Number, Enum, Sprite, Texture, Font)

### Implementation Detail

Current method (lines 209-228 of `ComponentReflectionUtils.java`):

```java
private static Object deepCopyValue(Object value) {
    if (value == null) return null;
    if (value instanceof Vector2f v) return new Vector2f(v);
    if (value instanceof Vector3f v) return new Vector3f(v);
    if (value instanceof Vector4f v) return new Vector4f(v);
    return value;
}
```

New method should handle:

```java
private static Object deepCopyValue(Object value) {
    if (value == null) return null;

    // Vector types
    if (value instanceof Vector2f v) return new Vector2f(v);
    if (value instanceof Vector3f v) return new Vector3f(v);
    if (value instanceof Vector4f v) return new Vector4f(v);

    // List - deep copy elements
    if (value instanceof List<?> list) {
        List<Object> copy = new ArrayList<>(list.size());
        for (Object element : list) {
            copy.add(deepCopyValue(element));
        }
        return copy;
    }

    // Map - deep copy keys and values
    if (value instanceof Map<?, ?> map) {
        Map<Object, Object> copy = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(deepCopyValue(entry.getKey()), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    // Arrays
    if (value.getClass().isArray()) {
        int length = Array.getLength(value);
        Class<?> componentType = value.getClass().getComponentType();
        Object copy = Array.newInstance(componentType, length);
        if (componentType.isPrimitive()) {
            System.arraycopy(value, 0, copy, 0, length);
        } else {
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, deepCopyValue(Array.get(value, i)));
            }
        }
        return copy;
    }

    // Immutable types - return as-is
    return value;
}
```

Required imports to add: `java.lang.reflect.Array`, `java.util.ArrayList`, `java.util.LinkedHashMap`, `java.util.List`, `java.util.Map`.

### Visibility Change

Change `deepCopyValue()` from `private` to `public static` so it can be used by other systems that need to deep-copy field values (e.g., the future PrefabEditController for cloning component lists).

### Known Mutable Fields in Codebase

Current components with mutable collection fields:
- `SpritePostEffect.effects` — `List<PostEffect>` (most common case)
- `TilemapRenderer.TileChunk.tiles` — `Tile[][]` (transient/internal, but array handling covers it)

Most components use primitives, Strings, enums, or immutable asset references (Sprite, Texture, Font, Animation, AnimatorController). The deep copy is defensive — covering types that could appear in future components.

### Edge Case: Set

If any future component uses `Set`, the current implementation treats it as an immutable passthrough. Consider adding `Set` handling:

```java
if (value instanceof Set<?> set) {
    Set<Object> copy = new LinkedHashSet<>(set.size());
    for (Object element : set) {
        copy.add(deepCopyValue(element));
    }
    return copy;
}
```

This is optional for the initial implementation since no current component uses `Set`.

---

## Phase 2: Tests

### Files to Create

| File | Change |
|------|--------|
| `src/test/java/com/pocket/rpg/serialization/ComponentReflectionUtilsTest.java` | **NEW** -- tests for deep copy isolation |

### Test Cases

- [ ] Clone a component with a `List<String>` field: modify the clone's list, verify original is unchanged
- [ ] Clone a component with a `List<Vector2f>` field: modify a vector in the clone's list, verify original vector unchanged
- [ ] Clone a component with a `Map<String, Object>` field: add/remove entries on clone, verify original unchanged
- [ ] Clone a component with an `int[]` field: modify clone's array, verify original unchanged
- [ ] Clone a component with a `Vector2f[]` field: modify a vector in clone's array, verify original vector unchanged
- [ ] Clone a component with nested structures (`List<List<String>>`): modify inner list on clone, verify isolation
- [ ] Regression: clone with Vector2f/3f/4f still works correctly
- [ ] Regression: clone with immutable types (String, int, enum) passes through correctly
- [ ] Null values in collections are handled gracefully

### Test Commands

```bash
mvn test -Dtest=ComponentReflectionUtilsTest
```

Also run existing tests for regression:

```bash
mvn test -Dtest=ComponentCommandsTest
```

---

## Size

Small. One method expansion + one new test class.

---

## Code Review

- [ ] Verify `deepCopyValue()` handles all mutable types used by existing components
- [ ] Verify no existing callers depend on the shallow-copy behavior (they shouldn't -- it was a bug)
- [ ] Verify `cloneComponent()` still works correctly with the expanded deep copy
- [ ] Run full test suite: `mvn test`
