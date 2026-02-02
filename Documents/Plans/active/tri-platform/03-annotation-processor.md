# Plan 3: Reflection Replacement (Annotation Processor)

**Goal:** Replace runtime classpath scanning with compile-time code generation for all 4 systems.

**Prerequisites:** Plan 0 (go decision). **Parallelizable with Plans 1 and 2.**

**Status:** Not started

---

## Dependency Graph

```
Plan 0 (go/no-go)
   |
   v
Plan 1    Plan 2    Plan 3 (this plan)    ← parallel, different files
   |         |         |
   +----+----+---------+
        |
        v
     Plan 4
```

---

## Context

The current codebase uses `org.reflections` for runtime classpath scanning in 4 systems. TeaVM does not support runtime reflection or classpath scanning. All 4 must be replaced with compile-time code generation.

---

## Deliverables

### 1. `pocket-rpg-processor/` Maven annotation processor module

- Separate Maven module (runs at compile time)
- Processes annotations on game classes
- Generates registry source files that are compiled into the game

### 2. Generated registry: `ComponentRegistry`

**Current:** Scans classpath for `Component` subclasses at runtime.
**After:** Annotation processor finds all `@ComponentRef`-annotated classes and generates a registry with all component types pre-registered.

- Processor scans for classes annotated with `@ComponentRef`
- Generates `GeneratedComponentRegistry` with a static map of name → class
- `ComponentRegistry` delegates to generated code instead of Reflections

### 3. Generated registry: `AssetManager` (loader discovery)

**Current:** Scans classpath for `AssetLoader` implementations at runtime.
**After:** Processor finds all loader classes and generates registration code.

- New annotation: `@AssetLoaderRef` (or reuse existing if one exists)
- Generates `GeneratedLoaderRegistry` listing all loader classes
- `AssetManager` uses generated registry for loader discovery

### 4. Generated registry: `PostEffectRegistry`

**Current:** Scans classpath for `PostEffect` subclasses at runtime.
**After:** Processor finds all post-effect classes and generates registration code.

- New annotation: `@PostEffectRef` (or reuse existing)
- Generates `GeneratedPostEffectRegistry`
- `PostEffectRegistry` delegates to generated code

### 5. Generated registry: `ComponentTypeAdapterFactory` (type map)

**Current:** Uses reflection to build GSON type adapter mappings for Component serialization.
**After:** Processor generates the type mapping at compile time.

- Generates `GeneratedComponentTypeMap` with component name → class mappings
- `ComponentTypeAdapterFactory` uses generated map instead of Reflections

### 6. Transition validation

- During development: run both Reflections-based and generated registries
- Compare outputs — they must match exactly
- Remove Reflections-based code only after validation passes

### 7. `org.reflections` dependency removed

- Remove `org.reflections:reflections` from `pom.xml`
- Zero `org.reflections` imports in game code

---

## Annotation Processor Design

### Processing flow

```
Source code with annotations
        |
        v
  Annotation Processor (compile time)
        |
        v
  Generated Java source files
        |
        v
  Compiled into game alongside regular code
```

### Error handling

- Build-time error if a `Component` subclass is missing `@ComponentRef`
- Build-time error if duplicate registry names detected
- Build-time warning for suspicious patterns (e.g., abstract class with annotation)

### Generated code location

- Generated sources go to `target/generated-sources/annotations/`
- Standard Maven annotation processing — no special build config needed beyond processor dependency

---

## Key Files

| File | Change |
|------|--------|
| `ComponentRegistry` | Delegate to generated registry |
| `AssetManager` | Delegate to generated loader list |
| `PostEffectRegistry` | Delegate to generated registry |
| `ComponentTypeAdapterFactory` | Delegate to generated type map |
| `pom.xml` | Add processor module dependency, remove Reflections |
| New: `pocket-rpg-processor/pom.xml` | Processor module build config |
| New: `pocket-rpg-processor/src/.../ComponentRefProcessor.java` | Main annotation processor |
| New: Generated `GeneratedComponentRegistry.java` | Compile-time output |
| New: Generated `GeneratedLoaderRegistry.java` | Compile-time output |
| New: Generated `GeneratedPostEffectRegistry.java` | Compile-time output |
| New: Generated `GeneratedComponentTypeMap.java` | Compile-time output |

---

## Success Criteria

- [ ] Zero `org.reflections` imports in non-editor game files
- [ ] Build-time error if a Component subclass is missing annotation
- [ ] Parallel validation passes (generated == Reflections output) during development
- [ ] Desktop behavior unchanged
- [ ] All existing tests pass
- [ ] `org.reflections` dependency removed from game `pom.xml`
- [ ] Annotation processor runs as part of standard `mvn compile`

---

## Implementation Order

1. Create `pocket-rpg-processor/` Maven module with processor skeleton
2. Implement `ComponentRegistry` processor + generated output
3. Validate: generated registry matches Reflections-based registry
4. Implement `AssetManager` loader discovery processor
5. Implement `PostEffectRegistry` processor
6. Implement `ComponentTypeAdapterFactory` type map processor
7. Validate all 4 generated registries match Reflections output
8. Switch all 4 systems to use generated code
9. Remove Reflections-based discovery code
10. Remove `org.reflections` dependency
11. Verify build-time errors work (missing annotations)
12. Full game test — behavior unchanged
