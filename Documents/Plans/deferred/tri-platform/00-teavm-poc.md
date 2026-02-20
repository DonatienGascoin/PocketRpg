# Plan 0: TeaVM Feasibility POC

**Goal:** Answer "can TeaVM compile our dependencies and render WebGL?" before investing in refactoring.

**Prerequisites:** None.

**Status:** Not started

---

## Dependency Graph

```
Plan 0 (TeaVM POC)
   |
   v (go/no-go)
   |
   +---------+---------+
   v         v         v
Plan 1    Plan 2    Plan 3
```

This plan gates all subsequent work. Its go/no-go recommendation determines whether the tri-platform port proceeds.

---

## Deliverables

### 1. Throwaway `poc-teavm/` Maven module
- Separate Maven module with TeaVM plugin configured
- Not part of the main build — isolated experiment
- Will be deleted after evaluation

### 2. WebGL 2.0 colored triangle
- Render a colored triangle via TeaVM JSO bindings to WebGL 2.0
- Vertex shader + fragment shader compiled and linked
- Validates: TeaVM can call WebGL APIs, shaders compile in browser

### 3. JOML matrix validation
- Use `JOML Matrix4f` to compute a projection matrix
- Apply it to the triangle rendering
- Validates: JOML compiles and runs correctly under TeaVM

### 4. GSON polymorphic deserialization test
- Deserialize a `SceneData`-shaped polymorphic JSON (nested types, type adapters)
- Validates: GSON works under TeaVM, OR produces a clear failure report
- If GSON fails: document the failure mode and evaluate alternatives (Moshi, manual parsing, TeaVM-compatible JSON lib)

### 5. Concurrency compilation test
- Compile code using `ConcurrentHashMap` and `ReentrantReadWriteLock`
- Validates: TeaVM handles `java.util.concurrent` classes used in our codebase
- Document any unsupported classes or methods

### 6. Evaluation document
- Compare three approaches:
  - **Custom TeaVM port** (what this POC tests)
  - **LibGDX migration** (rewrite rendering on LibGDX abstraction)
  - **CheerpJ** (JVM bytecode interpreter in browser)
- Evaluation criteria: runtime performance, build complexity, dependency compatibility, maintenance burden, community support
- **Go/no-go recommendation** with justification

---

## Success Criteria

- [ ] Triangle renders in Chrome + Firefox
- [ ] JOML matrix operations work in browser
- [ ] GSON deserializes nested polymorphic JSON, OR clear report of failure + alternative
- [ ] `ConcurrentHashMap` / `ReentrantReadWriteLock` compile, OR clear report of blockers
- [ ] Decision document written with go/no-go recommendation

---

## QA Integration

- Document every TeaVM compilation warning/error encountered
- Test in Chrome, Firefox, and Safari (WebGL 2.0 conformance varies)
- Record browser console errors and TeaVM compilation output
- Note any classes that TeaVM silently stubs vs explicitly fails on

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| GSON reflection fails on TeaVM | High | Test early; have alternatives ready (manual JSON, Moshi) |
| WebGL 2.0 not available in Safari | Medium | Safari 15+ supports it; document minimum version |
| TeaVM compilation too slow | Low | Only affects dev iteration; measure build time |
| JOML uses unsupported Java features | Medium | JOML is pure Java math; low risk but must verify |

---

## Estimated Scope

- ~1 day to set up Maven module + TeaVM plugin
- ~1 day for WebGL triangle + JOML
- ~0.5 day for GSON + concurrency tests
- ~0.5 day for evaluation document
- **Total: ~3 days**

---

## Decision Gate

After this plan completes, one of three outcomes:

1. **Go** — TeaVM works. Proceed with Plans 1-6.
2. **Go with caveats** — TeaVM works but GSON/concurrency needs workarounds. Proceed with Plans 1-6, incorporating workarounds.
3. **No-go** — TeaVM is not viable. Evaluate LibGDX or CheerpJ alternatives, or abandon web port.
