# Tri-Platform Architecture: Desktop + Web + Android

Design document for making PocketRpg's game runtime target three platforms:
- **Desktop** — LWJGL (OpenGL 3.3 Core, GLFW, OpenAL) — current
- **Web** — TeaVM (WebGL 2.0, Canvas, Web Audio API)
- **Android** — Android SDK (OpenGL ES 3.0, GLSurfaceView, AudioTrack/Oboe)

Only the game is ported. The editor remains desktop-only.

---

## Table of Contents

1. [Platform Constraints](#1-platform-constraints)
2. [Module Structure](#2-module-structure)
3. [Rendering Backend Interfaces](#3-rendering-backend-interfaces)
4. [Shader Strategy](#4-shader-strategy)
5. [Game Loop Refactor](#5-game-loop-refactor)
6. [Asset Loading](#6-asset-loading)
7. [Input, Audio, Windowing](#7-input-audio-windowing)
8. [Reflection Replacement](#8-reflection-replacement)
9. [Java 17 Compatibility](#9-java-17-compatibility)
10. [What Changes, What Doesn't](#10-what-changes-what-doesnt)
11. [Implementation Order](#11-implementation-order)

---

## 1. Platform Constraints

| Constraint          | Desktop (LWJGL)          | Web (TeaVM)                        | Android                            |
|---------------------|--------------------------|------------------------------------|------------------------------------|
| GL variant          | OpenGL 3.3 Core          | WebGL 2.0 (≈ ES 3.0)              | OpenGL ES 3.0                      |
| Shader language     | GLSL 330 core            | GLSL ES 300                        | GLSL ES 300                        |
| Game loop model     | Blocking `while` loop    | `requestAnimationFrame` callback   | `GLSurfaceView.onDrawFrame`        |
| Asset I/O           | Filesystem (sync)        | Fetch API (async)                  | Android AssetManager (sync)        |
| Audio               | OpenAL                   | Web Audio API                      | AudioTrack / Oboe                  |
| Input               | GLFW callbacks           | DOM events                         | MotionEvent / KeyEvent             |
| Reflection          | Full (Reflections lib)   | Limited (no classpath scanning)    | Limited (slow, no scanning)        |
| Java level          | Java 25                  | TeaVM subset (~Java 17)            | Java 17 (desugared via AGP)        |
| Buffer types        | LWJGL `FloatBuffer`      | TeaVM typed arrays / JSO           | `java.nio.FloatBuffer`             |

Key observation: Web and Android both use GLSL ES 300 and ES-level GL. Desktop is the outlier.
The abstraction is really "GL 3.3 vs ES 3.0", not three separate targets.

---

## 2. Module Structure

```
pocketrpg/
├── core/                          ← Platform-independent game code (existing)
│   ├── components/
│   ├── animation/
│   ├── collision/
│   ├── scenes/
│   ├── input/                     ← InputBackend interface (exists)
│   ├── audio/                     ← AudioBackend interface (exists)
│   ├── resources/                 ← AssetContext interface (exists)
│   ├── serialization/
│   ├── rendering/
│   │   ├── api/                   ← NEW: Backend sub-interfaces
│   │   ├── batch/                 ← SpriteBatch refactored to use backends
│   │   ├── pipeline/              ← RenderPipeline (delegates down, mostly unchanged)
│   │   └── postfx/               ← PostEffect logic (uses backends)
│   └── platform/                  ← PlatformFactory interface (exists)
│
├── platform-lwjgl/                ← Desktop backend
│   ├── GL33BufferBackend
│   ├── GL33TextureBackend
│   ├── GL33ShaderBackend
│   ├── GL33FramebufferBackend
│   ├── GL33StateBackend
│   ├── GL33DrawBackend
│   ├── GLFWWindow
│   ├── GLFWInputBackend
│   ├── GLFWPlatformFactory
│   ├── OpenALAudioBackend
│   └── STBTextureLoader
│
├── platform-web/                  ← TeaVM backend
│   ├── WebGLBufferBackend
│   ├── WebGLTextureBackend
│   ├── WebGLShaderBackend
│   ├── WebGLFramebufferBackend
│   ├── WebGLStateBackend
│   ├── WebGLDrawBackend
│   ├── CanvasWindow
│   ├── WebInputBackend
│   ├── WebPlatformFactory
│   ├── WebAudioBackend
│   └── FetchAssetProvider
│
├── platform-android/              ← Android backend
│   ├── GLESBufferBackend
│   ├── GLESTextureBackend
│   ├── GLESShaderBackend
│   ├── GLESFramebufferBackend
│   ├── GLESStateBackend
│   ├── GLESDrawBackend
│   ├── SurfaceViewWindow
│   ├── AndroidInputBackend
│   ├── AndroidPlatformFactory
│   ├── AndroidAudioBackend
│   └── AndroidAssetProvider
│
└── editor/                        ← Desktop-only (unchanged)
```

The Web and Android GL backends will share most of their logic since both target ES 3.0.
A shared `AbstractESBackend` base class for each sub-interface could eliminate duplication.

---

## 3. Rendering Backend Interfaces

The current rendering code makes ~420 direct GL calls across ~27 files, all within
the `rendering/` package (plus `Font`, `SpritePostEffect`, and editor code).

Rather than one monolithic `RenderBackend` interface, the GL API splits naturally into
six focused sub-interfaces matching how calls cluster in the codebase:

### 3.1 BufferBackend — Vertex arrays and buffer objects

Handles VAO/VBO lifecycle and vertex attribute setup. Used by SpriteBatch, UIRenderer,
OverlayRenderer, PostProcessor. ~90 call sites.

```java
public interface BufferBackend {
    int createVertexArray();
    void bindVertexArray(int vao);
    void deleteVertexArray(int vao);
    void unbindVertexArray();

    int createBuffer();
    void bindBuffer(int target, int buffer);
    void bufferData(int target, long sizeBytes, int usage);
    void bufferSubData(int target, long offset, FloatBuffer data);
    void deleteBuffer(int buffer);

    void vertexAttribPointer(int index, int size, int type,
                             boolean normalized, int stride, long offset);
    void enableVertexAttribArray(int index);
}
```

### 3.2 TextureBackend — Texture creation, binding, parameters

Handles texture lifecycle and sampling configuration. Used by Texture, Font,
PostProcessor, all post-effects. ~60 call sites.

```java
public interface TextureBackend {
    int createTexture();
    void bindTexture(int target, int texture);
    void unbindTexture(int target);
    void activeTexture(int unit);
    void texImage2D(int target, int level, int internalFormat,
                    int width, int height, int format, int type, ByteBuffer data);
    void texParameteri(int target, int pname, int param);
    void deleteTexture(int texture);
}
```

### 3.3 ShaderBackend — Shader compilation, linking, uniforms

Handles the full shader program lifecycle and uniform uploads. Concentrated in
Shader.java with its uniform location cache. ~120 call sites.

```java
public interface ShaderBackend {
    int createProgram(String vertexSource, String fragmentSource);
    void useProgram(int program);
    void deleteProgram(int program);

    int getUniformLocation(int program, String name);
    void uniformMatrix4fv(int location, boolean transpose, FloatBuffer value);
    void uniformMatrix3fv(int location, boolean transpose, FloatBuffer value);
    void uniform1i(int location, int value);
    void uniform1f(int location, float value);
    void uniform2f(int location, float x, float y);
    void uniform3f(int location, float x, float y, float z);
    void uniform4f(int location, float x, float y, float z, float w);
    void uniform1iv(int location, int[] values);
}
```

Note: `createProgram` encapsulates create/compile/attach/link/delete-intermediates
in one call. The current Shader.java already does this as a single operation — the
sub-steps don't need to be exposed to callers.

### 3.4 FramebufferBackend — FBOs and renderbuffers

Handles offscreen render targets for post-processing. Used by PostProcessor,
FramebufferTarget, SpritePostEffect. ~80 call sites.

```java
public interface FramebufferBackend {
    int createFramebuffer();
    void bindFramebuffer(int target, int fbo);
    void framebufferTexture2D(int target, int attachment,
                               int texTarget, int texture, int level);
    int checkFramebufferStatus(int target);
    void deleteFramebuffer(int fbo);

    int createRenderbuffer();
    void bindRenderbuffer(int rbo);
    void renderbufferStorage(int internalFormat, int width, int height);
    void framebufferRenderbuffer(int target, int attachment, int rbo);
    void deleteRenderbuffer(int rbo);
}
```

### 3.5 StateBackend — GL state management

Handles blend modes, viewport, clearing. Used everywhere as setup before draw calls.
~40 call sites.

```java
public interface StateBackend {
    void enable(int capability);
    void disable(int capability);
    void blendFunc(int sfactor, int dfactor);
    void viewport(int x, int y, int width, int height);
    void clearColor(float r, float g, float b, float a);
    void clear(int mask);
    void scissor(int x, int y, int width, int height);
}
```

### 3.6 DrawBackend — Issuing draw calls

Simple interface — only two operations used across the entire codebase. ~30 call sites.

```java
public interface DrawBackend {
    void drawArrays(int mode, int first, int count);
    void drawElements(int mode, int count, int type, long offset);
}
```

### 3.7 GL Constants

GL constant values (GL_TRIANGLES, GL_TEXTURE_2D, GL_RGBA, etc.) differ between
GL 3.3, WebGL 2.0, and GLES 3.0 APIs even though their numeric values are identical.
Each platform's import path is different.

Approach: define an `GLConstants` class in core with `public static final int` values
matching the standard OpenGL numeric constants (e.g., `GL_TRIANGLES = 0x0004`).
These are identical across all three GL variants. Core code references `GLConstants`,
platform backends map to their native API.

```java
public final class GLConstants {
    public static final int TRIANGLES           = 0x0004;
    public static final int TEXTURE_2D          = 0x0DE1;
    public static final int RGBA                = 0x1908;
    public static final int UNSIGNED_BYTE       = 0x1401;
    public static final int FLOAT               = 0x1406;
    public static final int ARRAY_BUFFER        = 0x8892;
    public static final int STATIC_DRAW         = 0x88E4;
    public static final int DYNAMIC_DRAW        = 0x88E8;
    public static final int FRAMEBUFFER         = 0x8D40;
    public static final int COLOR_ATTACHMENT0    = 0x8CE0;
    public static final int DEPTH_ATTACHMENT     = 0x8D00;
    public static final int COLOR_BUFFER_BIT     = 0x00004000;
    public static final int DEPTH_BUFFER_BIT     = 0x00000100;
    public static final int BLEND               = 0x0BE2;
    public static final int DEPTH_TEST          = 0x0B71;
    public static final int SRC_ALPHA           = 0x0302;
    public static final int ONE_MINUS_SRC_ALPHA = 0x0303;
    public static final int CLAMP_TO_EDGE       = 0x812F;
    public static final int NEAREST             = 0x2600;
    public static final int LINEAR              = 0x2601;
    public static final int TEXTURE_MIN_FILTER  = 0x2801;
    public static final int TEXTURE_MAG_FILTER  = 0x2800;
    public static final int TEXTURE_WRAP_S      = 0x2802;
    public static final int TEXTURE_WRAP_T      = 0x2803;
    public static final int VERTEX_SHADER       = 0x8B31;
    public static final int FRAGMENT_SHADER     = 0x8B30;
    // ... extend as needed
    private GLConstants() {}
}
```

### 3.8 Composite Access

`PlatformFactory` creates all six backends. Game code receives them through a
composite holder or individually as needed:

```java
public interface PlatformFactory {
    // Existing
    AbstractWindow createWindow(GameConfig config, InputBackend inputBackend, InputEventBus callbacks);
    InputBackend createInputBackend();
    AudioBackend createAudioBackend();
    String getPlatformName();

    // New — replace createPostProcessor
    BufferBackend createBufferBackend();
    TextureBackend createTextureBackend();
    ShaderBackend createShaderBackend();
    FramebufferBackend createFramebufferBackend();
    StateBackend createStateBackend();
    DrawBackend createDrawBackend();
}
```

`PostProcessor` no longer needs to be platform-specific — it uses the backends.
`createPostProcessor` can be removed from PlatformFactory.

---

## 4. Shader Strategy

### Dual-format with compile-time preprocessing

Web and Android both use GLSL ES 300. Desktop uses GLSL 330 core.
Rather than maintaining duplicate shader files, use a single source with guards:

```glsl
// batch_sprite.glsl
#ifdef GL_ES
#version 300 es
precision mediump float;
#else
#version 330 core
#endif

in vec2 aPosition;
in vec2 aTexCoord;
in vec4 aColor;

out vec2 vTexCoord;
out vec4 vColor;

uniform mat4 uProjection;

void main() {
    vTexCoord = aTexCoord;
    vColor = aColor;
    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
}
```

`ShaderBackend.createProgram()` preprocesses the source before compilation:
- Desktop: strips the `#ifdef GL_ES` block, keeps `#version 330 core`
- Web/Android: strips the `#else` block, keeps `#version 300 es` + precision

Note: `#version` must be the first line in GLSL. The preprocessor must output it
first, before any other content.

### Differences to watch

| GLSL 330 core | GLSL ES 300 | Notes |
|---|---|---|
| No precision qualifiers | `precision mediump float;` required | Add in ES block |
| `texture()` | `texture()` | Same in both (WebGL 2 / ES 3.0) |
| `in`/`out` | `in`/`out` | Same in both |
| `layout(location = N)` | `layout(location = N)` | Same in both |
| Implicit int precision | Must declare `precision highp int;` | Add in ES block |

Most of the existing 20 shaders should need only the version/precision header change.

---

## 5. Game Loop Refactor

### Current design

```java
// GameApplication.java
public void loop() {
    while (!window.shouldClose()) {
        processFrame();
    }
}

private void processFrame() {
    window.pollEvents();
    updateUIInput();
    engine.update();
    engine.render(screenTarget);
    window.swapBuffers();
    engine.endFrame();
}
```

### Problem

- Desktop: blocking loop is correct
- Web: must yield to browser via `requestAnimationFrame`
- Android: `GLSurfaceView` calls `onDrawFrame` — the GL thread drives the loop

### Solution

Extract `processFrame()` as the public contract. The platform drives the loop.

```java
// Core — platform-independent
public class GameRunner {
    public void processFrame() {
        window.pollEvents();
        engine.update();
        engine.render(screenTarget);
        window.swapBuffers();
        engine.endFrame();
    }
}

// Desktop — blocking loop (existing behavior)
public class DesktopLoop {
    void run(AbstractWindow window, GameRunner runner) {
        while (!window.shouldClose()) {
            runner.processFrame();
        }
    }
}

// Web — requestAnimationFrame (TeaVM)
public class WebLoop {
    void run(GameRunner runner) {
        Window.requestAnimationFrame(timestamp -> {
            runner.processFrame();
            run(runner);
        });
    }
}

// Android — GLSurfaceView.Renderer
public class AndroidLoop implements GLSurfaceView.Renderer {
    @Override
    public void onDrawFrame(GL10 gl) {
        runner.processFrame();
    }
}
```

`GameApplication` refactors to build a `GameRunner` and hand it to the platform loop.

### pollEvents / swapBuffers

These are no-ops on web and Android (the platform handles event dispatch and buffer
swapping). `AbstractWindow` already defines them as abstract — web/Android
implementations simply return immediately.

---

## 6. Asset Loading

### Current state

- Assets loaded from `gameData/assets/` via `Files.readAllBytes()`, `FileInputStream`
- `AssetContext` interface is well-abstracted — `AssetManager` implements it
- Individual `AssetLoader<T>` implementations per type (Texture, Shader, Sprite, etc.)

### Platform differences

| Platform | Read mechanism | Sync? |
|---|---|---|
| Desktop | `Files.readAllBytes(path)` | Yes |
| Android | `context.getAssets().open(path)` | Yes |
| Web | `fetch(url)` → `ArrayBuffer` | **No** — async only |

### Strategy: Preload-then-run

Desktop and Android can load synchronously — no change needed.

For web, introduce a preload phase before the game loop starts:

1. Parse the initial scene JSON to discover all referenced assets
2. Fetch all assets via Fetch API in parallel
3. Store raw bytes in an in-memory `Map<String, byte[]>` cache
4. Once all fetches complete, start the game loop
5. `AssetLoader.load()` reads from the cache — synchronous from the game's perspective

This keeps the `AssetLoader<T>` interface unchanged. Only the web platform's startup
sequence differs: it inserts a preload step (with a loading screen/progress bar)
before calling `GameRunner.init()`.

### AssetProvider interface

Abstract the raw byte-reading layer:

```java
public interface AssetProvider {
    /** Read raw bytes for an asset path. Sync on desktop/Android. */
    byte[] readBytes(String path);
    /** Read text content for an asset path. */
    String readString(String path);
    /** Check if an asset exists. */
    boolean exists(String path);
}
```

Implementations:
- `FileSystemAssetProvider` — current `Files.readAllBytes()` calls
- `AndroidAssetProvider` — wraps `AssetManager.open()`
- `WebAssetProvider` — reads from the preloaded cache

`AssetLoader<T>` implementations receive an `AssetProvider` instead of constructing
`File` objects directly.

---

## 7. Input, Audio, Windowing

These systems already have good interface abstractions. Each needs a new implementation
per platform, but no architectural changes.

### 7.1 Input

**Existing interface:** `InputBackend` — maps platform key codes to abstract `KeyCode` enum.

| Platform | Implementation | Notes |
|---|---|---|
| Desktop | `GLFWInputBackend` (exists) | GLFW key/mouse/gamepad callbacks |
| Web | `WebInputBackend` | DOM keydown/keyup, mousemove/mousedown, Web Gamepad API |
| Android | `AndroidInputBackend` | `MotionEvent` for touch, `KeyEvent` for keyboard/gamepad |

**Touch input** (web + Android): Map touch events to mouse-equivalent coordinates.
For the grid-based movement system, a virtual d-pad overlay or swipe gesture
detection will be needed as a game-level feature (not a backend concern).

### 7.2 Audio

**Existing interface:** `AudioBackend` — source/buffer lifecycle, playback, 3D positioning.

| Platform | Implementation | Notes |
|---|---|---|
| Desktop | `OpenALAudioBackend` (exists) | LWJGL OpenAL bindings |
| Web | `WebAudioBackend` | Web Audio API via TeaVM JSO. Requires user gesture to start. |
| Android | `AndroidAudioBackend` | AudioTrack for low-level PCM, or Oboe (NDK) for low latency |

**Platform-specific concerns:**
- Web: Browsers require a user gesture before playing audio. Add a "click to start"
  gate at game launch.
- Android: Must handle audio focus (pause on incoming calls, respect silent mode).

### 7.3 Windowing

**Existing abstraction:** `AbstractWindow` — init, pollEvents, swapBuffers, destroy,
screen dimensions, shouldClose, focus state.

| Platform | Implementation | Notes |
|---|---|---|
| Desktop | `GLFWWindow` (exists) | GLFW window creation, event callbacks |
| Web | `CanvasWindow` | HTML5 `<canvas>`, CSS resize, RAF drives the loop |
| Android | `SurfaceViewWindow` | `GLSurfaceView` provides the GL context |

**Lifecycle differences:**
- Desktop: Window owns its lifecycle (create → loop → destroy)
- Web: Canvas exists in DOM; browser manages lifecycle
- Android: Activity lifecycle (pause/resume/destroy). Must handle GL context loss
  on `onPause()` — textures and FBOs may need re-creation on `onResume()`.

The `AbstractWindow` contract covers this. `pollEvents()` and `swapBuffers()` are
no-ops on web and Android (the platform handles these).

---

## 8. Reflection Replacement

### Problem

`ComponentRegistry` uses the `Reflections` library to scan the classpath at startup:

```java
new Reflections("com.pocket.rpg");
reflections.get(SubTypes.of(Component.class).asClass());
```

This does not work on TeaVM (no classpath scanning) and is unreliable on Android.

`ComponentReflectionUtils` uses `Field.setAccessible(true)` for serialization —
TeaVM has limited support for this.

### Solution: Compile-time annotation processor

Write a Maven annotation processor that runs at compile time and generates a static
registry class. No manual registration.

**How it works:**

1. Annotate components with existing `@Component` (or introduce a marker if needed)
2. The annotation processor scans all `Component` subclasses at compile time
3. It generates a `GeneratedComponentRegistry.java` file containing:

```java
// AUTO-GENERATED — do not edit
public final class GeneratedComponentRegistry {
    public static void registerAll(ComponentRegistry registry) {
        registry.register(SpriteRendererComponent.class,
            SpriteRendererComponent::new,
            List.of(
                new FieldMeta("sprite", Sprite.class, ...),
                new FieldMeta("color", Vector4f.class, ...)
            ));
        registry.register(AnimatorComponent.class,
            AnimatorComponent::new,
            List.of(...));
        // ... all components
    }
}
```

4. At startup, `ComponentRegistry.initialize()` calls `GeneratedComponentRegistry.registerAll()`
   instead of using the Reflections library.

**Benefits:**
- Works on all three platforms
- Faster startup (no classpath scanning)
- Compile-time validation (missing components caught early)
- No runtime reflection needed for component instantiation

**For field access** (`ComponentReflectionUtils`): The annotation processor can also
generate type-safe field accessors, avoiding `Field.setAccessible()` at runtime.
Alternatively, if TeaVM supports basic `Field` reflection (it supports some), keep
the current approach and test. The processor is the fallback.

### Build integration

Add the annotation processor to the Maven build. It runs automatically during
`mvn compile` and outputs generated sources to `target/generated-sources/`.

---

## 9. Java 17 Compatibility

### Audit result

The game code (excluding editor) is **already Java 17 compatible** with one exception:

**Single fix required:**

`GameApplication.java` line 128 — unnamed variable syntax (Java 22+):
```java
// Current (Java 22+)
inputEventBus.addResizeListener((_, _) -> engine.getPipeline().resize());

// Java 17 compatible
inputEventBus.addResizeListener((w, h) -> engine.getPipeline().resize());
```

### Java 17-compatible features already in use (no changes needed)

- **Records** — `AnimationFrame`, `MoveResult`, `TileCoord`, `Tile`, `RenderBounds`, etc.
- **Sealed classes** — `TriggerData` sealed interface with `permits StairsData`
- **Pattern matching for instanceof** — `c instanceof BlockingComponent bc`
- **Switch expressions** — arrow-case syntax throughout
- **Text blocks** — not used, but would be fine

### Build configuration change

The shared game module targets Java 17:
```xml
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```

The editor and platform-lwjgl modules can remain on Java 25.

---

## 10. What Changes, What Doesn't

### Unchanged (~300 files)

All game logic, the ECS, animation, collision, scene management, configuration,
input/audio logic layers, JOML math, GSON serialization, time system:

- `components/` — all game components
- `animation/` — tweens, animator, animation data
- `collision/` — grid-based collision, tile behaviors
- `scenes/` — scene manager, transitions
- `input/` — action mapping, axis interpolation, InputContext
- `audio/` — mixer, music manager, buses, AudioContext
- `config/` — GameConfig, RenderingConfig, InputConfig
- `time/` — TimeContext, delta time, time scaling
- `serialization/` — ComponentRegistry API (implementation changes, interface stays)
- `save/` — SaveManager
- `ui/` — UICanvas, UITransform (game UI, not ImGui)

### Modified (moderate changes)

| File/Area | Change |
|---|---|
| `SpriteBatch` | Replace ~20 direct GL calls with backend interface calls |
| `Shader` | Replace GL compile/link/uniform calls with `ShaderBackend` |
| `Texture` | Split image decoding from GL upload, use `TextureBackend` |
| `PostProcessor` | Replace FBO/texture GL calls with `FramebufferBackend` + `TextureBackend` |
| `OverlayRenderer` | Replace GL calls with backend calls |
| `UIRenderer` | Replace GL calls with backend calls |
| `BatchRenderer` | Replace GL state calls with `StateBackend` |
| `FramebufferTarget` / `ScreenTarget` | Use `FramebufferBackend` + `StateBackend` |
| `Font` | Use `TextureBackend` for glyph atlas |
| `VertexLayout` | Use `BufferBackend` for attribute setup |
| `PillarBox` | Use backends for letterbox rendering |
| `SpritePostEffect` | Use backends for per-component post effects |
| `GameApplication` | Extract `processFrame()`, platform drives loop |
| `PlatformFactory` | Add backend factory methods, remove `createPostProcessor` |
| `ComponentRegistry` | Switch from Reflections to generated registry |
| `AssetLoader` implementations | Accept `AssetProvider` instead of direct file I/O |
| Shader files (20) | Add `#ifdef GL_ES` / `#else` preprocessing |

### New code

| Area | Files |
|---|---|
| `rendering/api/` | 6 backend interfaces + `GLConstants` |
| `platform-lwjgl/` | 6 GL33 backend implementations (wrapping existing calls) |
| `platform-web/` | WebGL backends, CanvasWindow, WebInputBackend, WebAudioBackend, FetchAssetProvider |
| `platform-android/` | GLES backends, SurfaceViewWindow, AndroidInputBackend, AndroidAudioBackend, AndroidAssetProvider |
| Annotation processor | Compile-time component registry generator |
| `AssetProvider` interface | Raw byte reading abstraction |

---

## 11. Implementation Order

### Phase 1 — Abstraction prep (in existing codebase, zero behavioral change)

1. **Introduce the 6 backend interfaces** in `rendering/api/`
2. **Implement `GL33*Backend` classes** wrapping existing LWJGL calls
3. **Refactor rendering code** to use backends instead of direct GL imports
4. **Add `GLConstants`** and replace `GL33.GL_*` references in core code
5. **Add `AssetProvider` interface** and `FileSystemAssetProvider`
6. **Refactor `AssetLoader` implementations** to use `AssetProvider`
7. **Verify desktop still works identically** — this is a pure refactor

This phase de-risks everything. If it breaks, it breaks on desktop where debugging
is easy. No new platforms involved yet.

### Phase 2 — Cross-cutting concerns

1. **Build the annotation processor** for component registry generation
2. **Replace Reflections usage** with generated registry
3. **Add shader preprocessing** — `#ifdef GL_ES` guards in all 20 shader files
4. **Refactor game loop** — extract `processFrame()`, make platform-driven
5. **Fix Java 17 incompatibility** — one lambda in `GameApplication.java`
6. **Set up multi-module Maven build** — core (Java 17), platform modules, editor (Java 25)

### Phase 3 — Web platform (TeaVM)

1. Set up TeaVM Maven profile and web project structure
2. Implement `WebGL*Backend` classes (6 backends, based on ES 3.0)
3. Implement `CanvasWindow`, `WebInputBackend`, `WebAudioBackend`
4. Implement `FetchAssetProvider` with preload phase
5. Implement `WebLoop` using `requestAnimationFrame`
6. Handle web-specific: user gesture for audio, canvas sizing, loading screen
7. Test in browsers

### Phase 4 — Android platform

1. Set up Android Gradle module importing core
2. Implement `GLES*Backend` classes (6 backends — shares most logic with web ES backends)
3. Implement `SurfaceViewWindow`, `AndroidInputBackend`, `AndroidAudioBackend`
4. Implement `AndroidAssetProvider` wrapping Android's `AssetManager`
5. Implement `AndroidLoop` via `GLSurfaceView.Renderer`
6. Handle Android-specific: lifecycle (pause/resume), audio focus, screen density, permissions
7. Test on devices

### Phase 5 — Polish

1. Touch input controls (virtual d-pad or swipe detection) for web + Android
2. GL context loss recovery on Android (texture/FBO re-creation)
3. Screen density and aspect ratio handling
4. Performance profiling on mobile GPUs
5. Asset packaging (APK assets/, web server bundle)

Phases 3 and 4 are independent and can be worked on in parallel.
