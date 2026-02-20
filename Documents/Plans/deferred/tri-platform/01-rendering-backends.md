# Plan 1: Rendering Backend Abstraction

**Goal:** Replace all direct `GL33.*` calls in game code with backend interfaces. Pure desktop refactor.

**Prerequisites:** Plan 0 (go decision).

**Status:** Not started

---

## Dependency Graph

```
Plan 0 (go/no-go)
   |
   v
Plan 1 (this plan)    Plan 2    Plan 3    ← parallel
   |
   v
Plan 4 (needs ShaderBackend from this plan)
```

---

## Deliverables

### 1. Backend interfaces in `rendering/api/`

Five focused interfaces:

**`BufferBackend`** — VAO/VBO lifecycle + draw calls (DrawBackend folded in)
- `int createVAO()`
- `void bindVAO(int vao)`
- `void deleteVAO(int vao)`
- `int createVBO()`
- `void bindVBO(int target, int vbo)`
- `void deleteVBO(int vbo)`
- `void bufferData(int target, float[] data, int usage)`
- `void bufferData(int target, byte[] data, int usage)`
- `void bufferSubData(int target, int offset, float[] data)`
- `void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset)`
- `void enableVertexAttribArray(int index)`
- `void drawArrays(int mode, int first, int count)`
- `void drawElements(int mode, int count, int type, int offset)`

**`TextureBackend`** — Texture operations
- `int createTexture()`
- `void bindTexture(int target, int texture)`
- `void deleteTexture(int texture)`
- `void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, byte[] pixels)`
- `void texSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, byte[] pixels)`
- `void texParameteri(int target, int pname, int param)`
- `void generateMipmap(int target)`

**`ShaderBackend`** — Shader compilation, linking, uniforms
- `int createProgram(String vertexSource, String fragmentSource)` — prepends platform headers
- `void useProgram(int program)`
- `void deleteProgram(int program)`
- `int getUniformLocation(int program, String name)`
- `void uniform1i(int location, int value)`
- `void uniform1f(int location, float value)`
- `void uniform2f(int location, float x, float y)`
- `void uniform3f(int location, float x, float y, float z)`
- `void uniform4f(int location, float x, float y, float z, float w)`
- `void uniformMatrix4fv(int location, boolean transpose, float[] matrix)`

**`FramebufferBackend`** — FBO/renderbuffer operations
- `int createFramebuffer()`
- `void bindFramebuffer(int target, int framebuffer)`
- `void deleteFramebuffer(int framebuffer)`
- `void framebufferTexture2D(int target, int attachment, int texTarget, int texture, int level)`
- `int createRenderbuffer()`
- `void bindRenderbuffer(int renderbuffer)`
- `void deleteRenderbuffer(int renderbuffer)`
- `void renderbufferStorage(int internalFormat, int width, int height)`
- `void framebufferRenderbuffer(int target, int attachment, int renderbuffer)`
- `int checkFramebufferStatus(int target)`

**`StateBackend`** — Blend modes, viewport, clearing, error checking
- `void enable(int cap)`
- `void disable(int cap)`
- `void blendFunc(int sfactor, int dfactor)`
- `void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha)`
- `void viewport(int x, int y, int width, int height)`
- `void scissor(int x, int y, int width, int height)`
- `void clear(int mask)`
- `void clearColor(float r, float g, float b, float a)`
- `void depthMask(boolean flag)`
- `void colorMask(boolean r, boolean g, boolean b, boolean a)`
- `int getError()`

### 2. `GLConstants` class

- Numeric GL constant values (e.g., `GL_TRIANGLES = 0x0004`)
- Used by all rendering code instead of `GL33.GL_TRIANGLES`
- Single source of truth — no platform-specific constant imports

### 3. `RenderDevice` composite holder

- Standard injection point for all rendering code
- Holds all 5 backends:
  ```java
  public class RenderDevice {
      BufferBackend buffers();
      TextureBackend textures();
      ShaderBackend shaders();
      FramebufferBackend framebuffers();
      StateBackend state();
  }
  ```
- Passed through `PlatformFactory` or constructor injection

### 4. `ImageDecoder` interface

- Decouples STB image loading from `Texture`
- `ImageData decode(byte[] rawData)` — returns width, height, channels, pixel byte array
- `StbImageDecoder` desktop implementation wrapping STBImage

### 5. `GL33RenderDevice` implementation

- Wraps all LWJGL `GL33.*` calls
- Lives in platform-specific code (future `platform-lwjgl` module)
- Handles `FloatBuffer`/`ByteBuffer` allocation internally (callers pass `float[]`/`byte[]`)

### 6. Game code refactoring (~25 non-editor files, ~420 call sites)

All game rendering code refactored to use backend interfaces instead of direct GL calls.

### 7. `GPUResourceTracker` interface

- For future context loss recovery (Android/web)
- No-op implementation on desktop
- `void track(int resourceType, int handle)`
- `void untrack(int resourceType, int handle)`
- `void onContextLost()`
- `void onContextRestored()`

### 8. `RecordingRenderDevice` for testing

- Records all backend calls in order
- Can verify call sequences in tests
- Useful for automated visual regression testing

---

## Interface Design Rules (from architecture review)

- `float[]` / `byte[]` parameters, **not** `FloatBuffer` / `ByteBuffer`
- `int` parameters, **not** `long` (TeaVM JSO limitation)
- `int getError()` on `StateBackend`
- `bufferData(int target, float[] data, int usage)` overload for float data
- Pre-allocated reusable float array in `Shader` for uniform uploads (avoid per-frame GC)
- `GL_TEXTURE_SWIZZLE` replaced with ES 3.0 compatible approach (use proper format instead)

---

## Key Files to Refactor (~25)

| File | GL Usage |
|------|----------|
| `SpriteBatch` | VAO/VBO, draw calls, buffer uploads |
| `Shader` | Program creation, uniform uploads, file loading |
| `Texture` | Texture creation, binding, parameters, STB decoding |
| `PostProcessor` | Framebuffers, fullscreen quad rendering |
| `PillarBox` | Viewport, clear, letterboxing |
| `Font` | Texture atlas, quad generation |
| `OverlayRenderer` | Blend state, drawing |
| `UIRenderer` | UI quad rendering |
| `BatchRenderer` | Batch draw calls |
| `FramebufferTarget` | FBO creation/binding |
| `ScreenTarget` | Default framebuffer binding |
| `VertexLayout` | VAO attribute setup |
| `SpritePostEffect` | Base post-effect rendering |
| 13+ `PostEffect` subclasses | Shader uniforms, rendering |
| `PlatformFactory` | Extended to create `RenderDevice` |

---

## Success Criteria

- [ ] Zero `org.lwjgl.opengl` imports in non-editor game files
- [ ] Golden screenshots before/after match (visual regression)
- [ ] All existing tests pass
- [ ] `RecordingRenderDevice` can verify call sequences
- [ ] `SpriteBatch` uses pre-allocated float array for uniform uploads
- [ ] No `FloatBuffer`/`ByteBuffer` in interface signatures

---

## Implementation Order

1. Define all 5 interfaces + `GLConstants` + `RenderDevice`
2. Implement `GL33RenderDevice` (wrapping existing LWJGL calls)
3. Create `ImageDecoder` interface + `StbImageDecoder`
4. Refactor `Shader` (highest dependency — many files use it)
5. Refactor `Texture` (uses `ImageDecoder`)
6. Refactor `SpriteBatch` (largest single file)
7. Refactor `FramebufferTarget` / `ScreenTarget`
8. Refactor `PostProcessor` + all `PostEffect` subclasses
9. Refactor remaining files (`Font`, `PillarBox`, renderers, etc.)
10. Create `GPUResourceTracker` interface + no-op impl
11. Create `RecordingRenderDevice` + tests
12. Final sweep: verify zero LWJGL imports in game code
13. Golden screenshot comparison
