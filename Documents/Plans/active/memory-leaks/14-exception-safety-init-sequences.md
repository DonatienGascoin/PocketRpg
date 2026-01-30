# Plan 14: Exception Safety in Critical Init Sequences

## Overview

**Problem:** Several classes have complex initialization sequences (shader compilation, buffer allocation, context creation) where failure partway through leaves partially initialized resources that are never cleaned up. This is a cross-cutting concern that affects `Renderer`, `SpriteBatch`, `ImGuiLayer`, and potentially others.

**Severity:** MEDIUM

**Approach:** Audit all classes with multi-step native resource initialization. Wrap init sequences in try-catch that calls destroy on failure. Ensure destroy handles partial state.

## Phase 1: Audit Init Sequences

- [ ] `Renderer.java` — shader + VAO + VBO + FloatBuffer (covered by Plan 04)
- [ ] `SpriteBatch.java` — VAO + VBO + FloatBuffer (covered by Plan 04)
- [ ] `ImGuiLayer.java` — context + GLFW backend + GL3 backend + fonts (covered by Plan 07)
- [ ] `Font.java` — file read + STB structs + GL texture (covered by Plan 02)
- [ ] `EditorWindow.java` — GLFW init + window + context + callbacks
- [ ] Any `Shader.java` compilation — vertex + fragment + program link
- [ ] Any `Framebuffer` or render target initialization

## Phase 2: Apply Pattern to Remaining Classes

For each class not already covered by Plans 02/04/05/07:

- [ ] `EditorWindow.java` — wrap init in try-catch, call partial cleanup on failure
- [ ] `Shader.java` — if vertex shader compiles but fragment fails, delete vertex shader
- [ ] Any Framebuffer classes — delete FBO and attachments on partial init failure

The pattern is:
```java
public void init() {
    try {
        // allocate resource A
        // allocate resource B (might fail)
        // allocate resource C
    } catch (Exception e) {
        destroy(); // handles partial state via null/zero checks
        throw e;   // or wrap in RuntimeException
    }
}
```

## Files to Modify

| File | Change |
|------|--------|
| `editor/core/EditorWindow.java` | Try-catch init, partial cleanup |
| Shader class(es) | Try-catch compilation, delete partial shaders |
| Framebuffer class(es) (if any) | Try-catch init, delete partial attachments |

## Testing Strategy

- All rendering systems work correctly after changes
- Force failures (bad shader source, invalid GL state) to verify cleanup runs
- No OpenGL errors reported after failed init attempts

## Code Review

- [ ] Verify every init method either fully succeeds or fully cleans up
- [ ] Verify destroy methods are safe with partial state
- [ ] Verify exceptions are properly propagated after cleanup
