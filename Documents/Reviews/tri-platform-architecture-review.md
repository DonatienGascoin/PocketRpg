# Tri-Platform Architecture — Consolidated Review

Multiple specialist reviewers examined the design in `Documents/Design/tri-platform-architecture.md`.
This document synthesizes their findings into actionable feedback.

**Review rounds:**
- Round 1: Engine Architect, Product Manager, Android Developer, Web/TeaVM Specialist, QA/DevOps
- Round 2: Senior Software Engineer (deep GL/TeaVM/NDK), QA Engineer (cross-platform testing), Project Owner

---

## Verdict

The architecture is **fundamentally sound**. The module structure, phasing strategy, six-backend
rendering split, and use of existing abstractions (PlatformFactory, InputBackend, AudioBackend)
are well-designed. However, the plan has **critical gaps** that would cause blockers if not
addressed before implementation begins.

The project owner review flags a more fundamental concern: **the scope is very large for a solo
or small-team project** (~50+ new classes, ~30 files refactored, 5 phases). A feasibility spike
and tighter MVP definition are strongly recommended before committing.

---

## Critical Blockers (Must Fix Before Starting)

### 1. `java.nio.file` is pervasive — not just in AssetLoaders

TeaVM does not support `java.nio.file` at all. The plan only addresses `AssetLoader`
implementations, but **14+ game files** use `Files`, `Paths`, `Path` directly:
`ConfigLoader`, `SaveManager`, `Shader`, `AudioClipLoader`, `MusicConfig`, `AudioConfig`,
`SceneDataLoader`, `FontLoader`, `AnimationLoader`, `AnimatorControllerLoader`,
`JsonPrefabLoader`, `AssetMetadata`, `FileUtils`, plus `AssetManager` itself.

**Action:** The `AssetProvider` refactor scope must include every file that touches the
filesystem, not just loaders. This is the single largest mechanical refactoring.

### 2. Reflection affects 4 systems, not just ComponentRegistry

The annotation processor plan only covers `ComponentRegistry`. Three other systems use
the same `Reflections` classpath scanning pattern:
- `AssetManager.registerDefaultLoaders()` — discovers `AssetLoader` subtypes
- `PostEffectRegistry.initialize()` — discovers `PostEffect` subtypes
- `ComponentTypeAdapterFactory` — uses `Class.forName()` for deserialization

**Action:** The annotation processor must generate registries for all four systems.

### 3. GSON may not work on TeaVM — needs early validation

GSON uses heavy internal reflection (`Field.setAccessible(true)`, `Field.set()`,
`ParameterizedType` introspection). TeaVM's reflection support is limited. If GSON fails,
the entire serialization layer must be rewritten — this changes the plan fundamentally.

**Action:** Run a spike test: compile a minimal GSON deserialization of a scene JSON on
TeaVM before committing to the architecture. Do this before Phase 1.

### 4. Shader versions are wrong for WebGL 2.0

17 of 20 shader files use `#version 310 es` (ES 3.1). WebGL 2.0 only supports
**GLSL ES 300** (ES 3.0). These shaders will fail at runtime on web.

The `#ifdef GL_ES` approach shown in the plan **produces invalid GLSL** — the `#version`
directive must be the very first non-comment, non-whitespace line. `GL_ES` is only defined
*inside* GLSL ES shaders, never before `#version` is processed. The preprocessing must happen
in Java-side string manipulation, not in the shader source.

Additionally, the existing shaders use a custom `#type vertex` / `#type fragment` format
(split in `Shader.java`). The document doesn't address how `#type` splitting interacts
with per-platform header injection. The `ShaderBackend.createProgram()` takes already-split
vertex/fragment sources, but the example shows a single combined file without `#type` markers.

**Action:** Downgrade all shaders to `#version 300 es`. Implement a cleaner approach: keep
`#type` splitting in `Shader.java`, then have `ShaderBackend.createProgram()` prepend the
appropriate `#version` + precision header based on platform, stripping any existing `#version`
line. No `#ifdef` needed at all.

### 5. `FloatBuffer` in backend interfaces is not platform-neutral

All technical reviewers flagged this. `BufferBackend.bufferSubData(int target, long offset,
FloatBuffer data)` uses `java.nio.FloatBuffer`, which:
- On desktop: is LWJGL off-heap (`MemoryUtil.memAllocFloat()`)
- On Android: must be `ByteBuffer.allocateDirect().asFloatBuffer()` (non-direct buffers crash)
- On TeaVM: does not exist — TeaVM uses `Float32Array` via JSO interop

Same issue with `ByteBuffer` in `TextureBackend.texImage2D()` and `FloatBuffer` in all
`ShaderBackend.uniform*` methods.

**Action:** Replace `FloatBuffer`/`ByteBuffer` with `float[]`/`byte[]` in all backend
interface signatures. Each platform wraps internally. JOML's `Matrix4f.get(float[])` already
supports this. SpriteBatch would stage vertices into a `float[]` instead of a `FloatBuffer`.
Slightly less performant on desktop (one extra copy) but correct on all platforms.

### 6. GL context loss must be designed into interfaces now, not Phase 5

When Android's GL context is lost (pause, surface destroyed, config change), **every** GL
handle becomes invalid: all texture IDs, VBO/VAO IDs, FBO IDs, shader program IDs.

The current `Texture` class calls `stbi_image_free(imageData)` after GPU upload — the pixel
data is gone and the texture cannot be recreated without re-loading from disk.

This also applies to **WebGL** — `webglcontextlost` events can fire any time (GPU driver
crash, resource pressure, tab backgrounding on mobile browsers). The plan doesn't mention
WebGL context loss at all.

**Action:** Design a `GPUResource` interface with `invalidate()` and `recreate()` methods in
Phase 1. Have `Texture`, `Shader`, `SpriteBatch`, `PostProcessor`, and all `PostEffect`
implementations register with a `GPUResourceTracker`. This is a no-op on desktop but prevents
a painful retrofit when Android/web arrive. Textures must retain their source path (or raw
bytes) for re-upload.

### 7. `MemoryUtil.memAllocFloat` / `memFree` in core code

`SpriteBatch.java` uses LWJGL's `MemoryUtil` for off-heap allocation. The vertex buffer
allocation and vertex writing (`putVertex` using `FloatBuffer.put()`) happens entirely
within SpriteBatch, not through any backend interface. `BufferBackend` only handles GPU-side
operations. The CPU-side buffer management is LWJGL-specific and unaddressed.

**Action:** If backend interfaces use `float[]` (fix #5), SpriteBatch stages into a
`float[]` and this resolves naturally. Otherwise, add a `BufferFactory` abstraction for
CPU-side buffer allocation.

---

## Architectural Improvements

### 8. Add a `RenderDevice` composite over the sub-interfaces

The six-interface split is correct in granularity, but `SpriteBatch.flush()` needs
coordinated calls across 4 backends (Buffer, Texture, Draw, State). Passing 3-4 backend
references to every rendering class adds noise.

**Recommendation:** Keep the sub-interfaces for typing, but introduce a `RenderDevice`
composite that holds all of them and is the standard injection point. Most rendering classes
receive `RenderDevice`; specialized code can access individual backends through it.

### 9. Fold `DrawBackend` into `BufferBackend`

`DrawBackend` has only two methods. A separate interface with its own implementation class,
factory method, and test suite is unjustified overhead. Fold into `BufferBackend` or the
`RenderDevice` composite.

### 10. Add an `ImageDecoder` interface

`Texture` tightly couples STB image decoding with GL texture upload. STB is LWJGL-native —
doesn't exist on Android (`BitmapFactory`) or web (`HTMLImageElement`).

```java
public interface ImageDecoder {
    DecodedImage decode(byte[] data, boolean flipVertically);
}
record DecodedImage(byte[] pixels, int width, int height, int channels) {}
```

### 11. Font loading strategy for web

`Font.java` uses STBTruetype native bindings for glyph rasterization. Doesn't exist on web.
Options: pre-rasterized font atlas textures (simplest), HTML5 Canvas 2D `fillText()`, or a
Wasm port of stb_truetype.

### 12. `GL_TEXTURE_SWIZZLE_*` is not available on ES 3.0

`Texture.java` uses `GL_TEXTURE_SWIZZLE_G` and `GL_TEXTURE_SWIZZLE_B` for single-channel
textures. This is a GL 3.3 feature not in ES 3.0 / WebGL 2.0. The `GLConstants` approach
masks this — the constant compiles but the call fails at runtime.

### 13. `long` parameters in interfaces won't work on TeaVM

`DrawBackend.drawElements` takes `long offset`. `BufferBackend.bufferData` takes
`long sizeBytes`, `bufferSubData` takes `long offset`. WebGL 2.0 uses `int` for all of
these. TeaVM's JSO interop cannot reliably pass Java `long` to JavaScript. A 2D game engine
will never exceed 2GB buffers — use `int` throughout.

### 14. `AbstractESBackend` sharing has limited value

The doc suggests shared base classes for Web and Android ES backends. In practice, WebGL
calls go through JSO annotations (`@JSBody`) while Android GLES calls standard Java methods
(`GLES30.glBindTexture()`). The calling conventions are completely different. Each backend
method is 1-3 lines — the duplication cost is negligible vs. forced inheritance complexity.

### 15. Add `glGetError` / debug support

None of the six interfaces include error checking. Add `int getError()` to `StateBackend`
or provide a debug decorator that wraps any backend and calls `glGetError()` after every
operation. Essential during development.

### 16. Add `bufferData` overload with data

`PostProcessor.java` calls `glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW)`
with a `float[]` directly (LWJGL overload for static geometry). The interface only has the
size-based allocation variant. Add: `void bufferData(int target, float[] data, int usage);`

### 17. 13+ PostEffect subclasses also need refactoring

`PostEffect.applyPass()` takes raw FBO/VAO handles, meaning every PostEffect implementation
makes its own GL calls. The document lists `SpritePostEffect` but there are 13+ effects
(bloom, blur, chromatic aberration, color grading, desaturation, displacement, edge detection,
film grain, motion blur, pixelation, radial blur, scanlines, vignette). Each is a refactoring
target — the scope is larger than the document suggests.

---

## Android-Specific Findings

### 18. Threading: `pollEvents()` is NOT a no-op on Android

Input events arrive on the UI thread; rendering runs on the GL thread.
`AndroidInputBackend` must implement a thread-safe event queue. `pollEvents()` on the GL
thread drains the queue and dispatches callbacks. Without this, input state has data races.

### 19. GLSurfaceView configuration matters

Critical settings not mentioned: `setEGLContextClientVersion(3)`,
`setPreserveEGLContextOnPause(true)`, `setRenderMode(RENDERMODE_CONTINUOUSLY)`, and a
custom `EGLConfigChooser` requesting RGBA8888 (default may select 16-bit color).

### 20. Lock screen orientation

For a pixel-art RPG with fixed internal resolution, lock to landscape via
`android:screenOrientation="sensorLandscape"`.

### 21. Audio: SoundPool + MediaPlayer, skip Oboe

For a 2D RPG: SFX via `SoundPool` (pre-loaded, low latency), music via `MediaPlayer`
(streaming). NDK/C++ complexity of Oboe is unjustified. The `AudioBackend` interface may
need a streaming source concept for music.

### 22. Cap `deltaTime` after resume

After returning from background, `System.nanoTime()` produces a multi-second delta. Clamp
`deltaTime` (e.g., max 500ms) to prevent physics/tween explosions. Also applies to web
when tab is hidden (see #25).

### 23. Post-processing budget on mobile

15+ post-effects is aggressive for mobile GPUs. Tile-based mobile renderers suffer from
excessive FBO switching. Add a tiered quality system (LOW/MEDIUM/HIGH) to `RenderingConfig`.

---

## Web-Specific Findings

### 24. `javax.sound.sampled` is a blocker

`AudioClipLoader.java` uses `javax.sound.sampled.AudioSystem` for WAV/MP3 decoding. TeaVM
doesn't support this. OGG loading uses `STBVorbis` (native). On web, use
`AudioContext.decodeAudioData()` during the preload phase.

### 25. Browser tab backgrounding / `visibilitychange`

When a tab is hidden, `requestAnimationFrame` stops firing. When the tab returns, the time
system produces a massive delta (same as Android resume, #22). The plan's `WebLoop` doesn't
handle this. Must listen for `visibilitychange` to pause the game loop and freeze
`TimeContext`. On return, clamp `deltaTime`.

### 26. OGG audio doesn't work in Safari

Safari/iOS Safari don't support OGG Vorbis. Ship audio as MP3 (universally supported) or
provide dual OGG+MP3 with runtime format detection.

### 27. `SaveManager` has no web strategy

`SaveManager` uses filesystem I/O. On web, saves must use `localStorage` (sync, 5-10 MB
limit) or IndexedDB (async, larger). Needs a `SaveProvider` interface parallel to
`AssetProvider`. Also relevant for Android (internal storage).

### 28. Preload strategy is harder than described

Discovering transitive asset dependencies (scene → prefab → spritesheet → texture;
scene → animator → animation → texture) requires partially re-implementing the
deserialization pipeline.

**Better approach:** Generate an `asset-manifest.json` at build time that lists all asset
paths per scene. The web preloader fetches everything in the manifest. Simpler, more reliable.

### 29. Web asset fetch failures — no error handling

The `WebMain.java` example has no error callbacks on any async operation. Every `fetch` can
fail (network error, 404, CORS). Define what the user sees on failure. Add timeout (e.g.,
10s per asset), retry logic (2 retries with exponential backoff), and an error screen with
a "retry" button.

### 30. `java.util.concurrent` partially unsupported

The game code uses `ConcurrentHashMap`, `CopyOnWriteArrayList`, `ReentrantReadWriteLock`.
On TeaVM (single-threaded JS), `ConcurrentHashMap` compiles as a regular `HashMap`, but
`ReentrantReadWriteLock` is likely unsupported. Audit and replace with non-concurrent
equivalents where needed.

### 31. WebGL context loss not mentioned

WebGL contexts can be lost at any time (GPU reset, resource pressure, mobile browser
backgrounding). The `webglcontextlost` / `webglcontextrestored` events must be handled.
All GL calls silently no-op during the lost period. Test with the `WEBGL_LOSE_CONTEXT`
extension.

### 32. Canvas/viewport sizing edge cases

Browser window resize, CSS pixel ratio changes (moving between displays), mobile browser
chrome appear/disappear, fullscreen enter/exit, `devicePixelRatio > 1` — the plan mentions
"canvas sizing" with no detail. `AbstractWindow.getScreenWidth/Height()` must distinguish
physical pixels (for `glViewport`) from logical pixels (for input coordinates).

---

## Testing & Process Gaps

### 33. No testing strategy at all

The plan contains zero mentions of testing.

**Phase 1 visual regression:** Capture golden screenshots before the refactor, pixel-diff
after. The refactor touches ~27 files and ~420 GL call sites. Without automated verification,
"desktop still works" is a hope, not a plan.

**Mock/recording backends:** Build `Recording*Backend` implementations that log all calls
and parameters. Use these to verify exact call sequences for known operations (e.g.,
"rendering one sprite issues: bindVAO, bufferSubData, bindTexture, useProgram, drawArrays").

**Contract tests:** Each interface needs a shared abstract test suite that runs against every
implementation, catching cases where `GL33BufferBackend` and `WebGLBufferBackend` interpret
the same method differently.

**Annotation processor validation:** Run generated registry alongside `Reflections`-based
registry during transition to verify identical output. Add build-time check that compares
generated registry against all `Component` subclasses — if a component is missed, it silently
doesn't exist at runtime.

**Platform CI:** Chrome headless + SwiftShader for web. x86_64 emulator (API 30+) for
Android. Neither is mentioned.

### 34. No performance benchmarks or targets

No baseline measurements, no target FPS per platform, no overhead budget for the abstraction
layer. Without defined targets, there is no objective way to determine if the implementation
is acceptable. Mobile thermal throttling (sustained vs. burst performance) is not considered.

### 35. No error reporting or crash analytics

On desktop, errors appear in the console. On web, they vanish into the browser console. On
Android, GL thread crashes are silent. Need at minimum `window.onerror` on web,
`Thread.setDefaultUncaughtExceptionHandler` on Android. Consider Sentry or similar.

### 36. No logging strategy

`System.out.println` doesn't work cross-platform (TeaVM needs `console.log`, Android needs
`android.util.Log`). Core code needs a platform-abstracted logging interface.

### 37. No debug tools mentioned

Web: recommend Spector.js or WebGL Inspector. Android: Android GPU Inspector. All platforms:
consider an in-game debug overlay (FPS, draw calls, memory) for mobile profiling where
external tools are cumbersome.

### 38. Shader.java allocates per-frame

`Shader.java` calls `BufferUtils.createFloatBuffer(16)` on every `uploadMat4f()` call —
allocating a new buffer per uniform upload per frame. On Android this causes significant GC
pressure (hundreds of allocations per frame). Pre-allocate a reusable buffer. Fixes desktop
performance too.

---

## Scope & Strategy Concerns

### 39. Scope is very large for a solo/small team

~50+ new classes, ~30 files refactored, 5 phases, touching rendering, asset loading, game
loop, input, audio, windowing, reflection, build system, and shaders. The multi-module Maven
restructure alone is a significant project. The annotation processor is a standalone project.

### 40. Run a TeaVM feasibility spike first

Before investing months in abstraction, create a throwaway TeaVM project that renders a
colored triangle using WebGL 2.0. Verify TeaVM can compile JOML. Verify GSON works or
identify a replacement. This answers "is this even possible" before Phase 1 begins.

### 41. Phase 2 bundles too many unrelated high-risk items

Phase 2 combines annotation processor, shader preprocessing, game loop refactor, multi-module
Maven restructure, and Java 17 compatibility fix. Any one could block for weeks. These should
be separate phases or at least have explicit ordering with go/no-go gates between them.

### 42. MVP: target web-only, defer Android

Web has lower friction (no app store, instant play, shareable URL), lower development risk
(no GL context loss recovery required for basic functionality, no device fragmentation), and
validates the abstraction. Ship web first. Android adds lifecycle management, threading,
context loss, touch input, mixed build systems, and device fragmentation — a much larger
surface area.

### 43. Consider starting with a monolithic `RenderBackend`

The 6-interface split is an educated guess based on call clustering, but it's untested. A
single monolithic interface is faster to iterate on during Phase 1. Split into sub-interfaces
later when you understand real usage patterns from having two working implementations.

### 44. Evaluate alternatives

The plan doesn't mention alternatives:
- **LibGDX** — already cross-platform (Desktop/Web/Android/iOS), mature abstraction layer.
  PocketRpg would need to rewrite its rendering pipeline, but this plan also rewrites the
  rendering pipeline. LibGDX's abstraction is battle-tested across thousands of games.
- **CheerpJ** — runs full JVM bytecode in browser via WebAssembly. JOML, GSON, and most
  libraries work without modification. Tradeoff is performance/bundle size, but for a 2D
  tile-based RPG, unlikely to be a bottleneck.

**If the goal is "ship a game on web," LibGDX is the pragmatic choice. If the goal is "learn
cross-platform engine development," the custom approach is educational but higher risk.**

### 45. Maintenance burden

After the port, every rendering change must work on 3 platforms. Every new post-effect needs
multiple backend implementations. The ongoing tax is real. Design module boundaries so any
platform module can be deleted cleanly — this should be an explicit goal with a test.

### 46. Missing deployment logistics

**Web:** Where is it hosted? What's the download size? Mobile browser experience? PWA/offline
support? Browser back button handling?

**Android:** Minimum API level? APK size (Play Store 150MB limit)? Permissions? Target SDK
(required for Play Store)? Content rating?

### 47. Missing success criteria

The plan has no definition of "done" for any phase. Suggested acceptance criteria:

- **Phase 1 done:** Desktop game runs identically to pre-refactor. Zero direct
  `org.lwjgl.opengl` imports in core module. Automated visual regression tests pass.
- **Web MVP done:** Game loads in Chrome and Firefox. First scene renders correctly. Player
  can navigate with keyboard. Scene transitions work. Audio plays after click-to-start.
  30fps+ on mid-range laptop.
- **Android MVP done:** Installs on Android 8+. Renders correctly. Survives backgrounding
  without visual artifacts. Touch d-pad allows grid movement. Audio respects silent mode.

---

## Build System

### 48. Mixed Maven + Gradle is high-risk

Maven for core/desktop, Gradle for Android. The plan never fully addresses how the core
module is shared. Options: `mavenLocal()` publishing (fragile), Gradle composite builds, or
full Gradle migration. Given the project's straightforward `pom.xml`, full Gradle migration
is worth evaluating.

---

## Summary: Priority Actions

### Before Phase 1 (spikes/validation)
1. **TeaVM feasibility spike** — render a triangle, test JOML, test GSON
2. **Fix shader versions** — downgrade `310 es` → `300 es` (can do now on desktop)
3. **Audit `java.nio.file` usage** — map full scope of AssetProvider refactor
4. **Evaluate alternatives** — at least consider LibGDX and CheerpJ before committing

### Phase 1 additions
5. Replace `FloatBuffer`/`ByteBuffer`/`long` with `float[]`/`byte[]`/`int` in interfaces
6. Introduce `RenderDevice` composite (fold `DrawBackend` into `BufferBackend`)
7. Design `GPUResource` / `GPUResourceTracker` for context loss (no-op on desktop)
8. Add `ImageDecoder` interface (decouple Texture from STB)
9. Introduce `AssetProvider` across ALL file-reading code (14+ files, not just loaders)
10. Pre-allocate reusable buffer in `Shader.java` uniform uploads
11. Abstract `MemoryUtil` allocation (or resolve via `float[]` approach)
12. Capture golden screenshots for visual regression testing
13. Build recording/mock backends for automated call-sequence verification
14. Add `bufferData(target, float[], usage)` overload

### Phase 2 additions
15. Annotation processor generates registries for **4** systems (Components, AssetLoaders, PostEffects, type adapter map)
16. Add `SaveProvider` interface
17. Design touch input model and any `InputBackend` contract changes
18. Add tiered quality system to `RenderingConfig` (LOW/MEDIUM/HIGH)
19. Build audio format conversion into the build pipeline
20. Generate `asset-manifest.json` at build time for web preloading
21. Add font loading strategy for web (pre-rasterized atlases recommended)
22. Evaluate Gradle migration
23. Add platform-agnostic logging interface
24. Define success criteria for each phase
25. **Split Phase 2 into smaller independent phases with go/no-go gates**

### Phase 3 (Web) additions
26. Shader preprocessing in Java (prepend headers), not GLSL `#ifdef`
27. Handle `visibilitychange` — pause loop, freeze TimeContext, clamp deltaTime
28. Handle Safari audio format fallback (OGG → MP3)
29. Handle keyboard focus loss (release all keys on blur)
30. `SaveProvider` → `localStorage` / IndexedDB
31. WebGL context loss handling (`webglcontextlost`/`webglcontextrestored`)
32. Asset fetch error handling (timeout, retry, error screen)
33. Canvas/viewport sizing (devicePixelRatio, resize, fullscreen)
34. Error reporting (at minimum `window.onerror`)

### Phase 4 (Android) additions
35. Design input threading model (UI thread → GL thread queue)
36. Configure GLSurfaceView properly (ES 3, RGBA8888, continuous render, preserve context)
37. Lock orientation, handle audio focus, cap deltaTime
38. GL context loss recovery — core requirement, not polish
39. Handle `onTrimMemory` — release cached textures under memory pressure

### Cross-cutting
40. Define performance baselines and per-platform targets
41. Set up platform CI (headless Chrome + SwiftShader, Android emulator)
42. Plan deployment logistics (hosting, APK size, min API level)
