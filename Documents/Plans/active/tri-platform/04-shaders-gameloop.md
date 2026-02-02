# Plan 4: Shaders + Game Loop + Java 17

**Goal:** Fix shader versions for ES 3.0 compatibility, make game loop platform-driveable, fix Java 17 incompatibility.

**Prerequisites:** Plan 1 (ShaderBackend must exist for version preprocessing).

**Status:** Not started

---

## Dependency Graph

```
Plan 1 (ShaderBackend)
   |
   v
Plan 4 (this plan)
   |
   v
Plan 5 (needs all of Plans 1-4)
```

---

## Deliverables

### 1. Shader version preprocessing

**Current problem:** 17 of 20 shaders use `#version 310 es` (ES 3.1). WebGL 2.0 only supports ES 3.0 (`#version 300 es`). The current `#ifdef GL_ES` approach is invalid GLSL.

**Solution:**
- Strip all `#version` lines from shader source files
- `ShaderBackend.createProgram()` prepends the correct version header per platform:
  - Desktop: `#version 330 core`
  - Web: `#version 300 es` + `precision mediump float;`
  - Android (future): `#version 300 es` + `precision mediump float;`
- No `#ifdef GL_ES` in shader sources — version is purely a backend concern
- Shaders written in the common subset of GLSL 3.30 and GLSL ES 3.0

### 2. Shader compatibility audit

- Review all 20 shaders for ES 3.0 incompatible features
- Replace `GL_TEXTURE_SWIZZLE` usage with proper format (ES 3.0 compatible)
- Document any desktop-only GLSL features that need replacement
- Ensure all shaders use `in`/`out` (not `attribute`/`varying`)

### 3. `GameRunner` class

**Current:** `GameApplication` contains a blocking `while (!shouldClose)` loop with all frame logic inline.

**After:** Frame processing extracted into a standalone class that can be driven by any platform loop.

```java
public class GameRunner {
    void init();           // one-time setup
    void processFrame();   // single frame: input → update → render
    void dispose();        // cleanup
    boolean shouldStop();  // termination check
}
```

- Contains no loop — caller drives it
- No GLFW/LWJGL dependencies in `GameRunner` itself
- All platform-specific windowing stays in the loop driver

### 4. `DesktopLoop` class

- Drives the existing blocking `while` loop on desktop
- Calls `GameRunner.processFrame()` each iteration
- Handles GLFW poll events, swap buffers, frame timing
- `GameApplication` becomes thin: creates `DesktopLoop` + `GameRunner`, calls `loop.run()`

### 5. `TimeContext` enhancements

- `pause()` — freeze time progression (for tab backgrounding on web)
- `resume()` — resume time progression
- `clampDeltaTime(float maxSeconds)` — cap delta time to prevent physics explosion after long pause
- Desktop implementation: `clampDeltaTime` set to reasonable default (e.g., 0.1s)
- Web/Android will use these when `visibilitychange` or lifecycle events fire

### 6. Java 22+ syntax fix

- Find and fix unnamed variable lambda (`_`) in `GameApplication` or other game code
- Replace with named parameter (e.g., `unused` or `ignored`)
- Ensure no Java 22+ syntax exists in non-editor game code
- Editor code (Java 25) is exempt

---

## Shader Files (20)

All shader `.glsl` files need `#version` line removal and ES 3.0 compatibility check:

| Shader | Notes |
|--------|-------|
| All vertex/fragment shader pairs | Strip `#version`, verify ES 3.0 compat |
| Post-effect shaders | Check for desktop-only features |
| UI shaders | Usually simple, low risk |

---

## Success Criteria

- [ ] All shaders compile with new preprocessing (version prepended by backend)
- [ ] No `#version` lines in any `.glsl` source file
- [ ] No `#ifdef GL_ES` in any shader source
- [ ] Desktop game loop works via `DesktopLoop` -> `GameRunner.processFrame()`
- [ ] Golden screenshots match (visual regression)
- [ ] `TimeContext.pause()` / `resume()` / `clampDeltaTime()` work
- [ ] No Java 22+ syntax in non-editor game code
- [ ] All existing tests pass

---

## Implementation Order

1. Audit all 20 shaders for ES 3.0 compatibility issues
2. Strip `#version` lines from all shaders
3. Update `ShaderBackend.createProgram()` (from Plan 1) to prepend version headers
4. Fix any ES 3.0 incompatible shader features
5. Verify all shaders compile and render correctly
6. Golden screenshot comparison
7. Extract `GameRunner` from `GameApplication`
8. Create `DesktopLoop` driving `GameRunner`
9. Refactor `GameApplication` to use `DesktopLoop` + `GameRunner`
10. Add `TimeContext` enhancements (`pause`, `resume`, `clampDeltaTime`)
11. Fix Java 22+ unnamed variable syntax
12. Final verification: full game playthrough
