# Tri-Platform Architecture — Consolidated Review

Five specialist reviewers examined the design in `Documents/Design/tri-platform-architecture.md`.
This document synthesizes their findings into actionable feedback.

**Reviewers:**
1. Senior Game Engine Architect (15 years, cross-platform engine experience)
2. Product Manager (10 years, multi-platform game shipping)
3. Senior Android/Mobile Developer (12 years, GL ES porting)
4. Senior Web/TeaVM Specialist (deep TeaVM, WebGL, browser porting)
5. QA & DevOps Engineer (10 years, multi-platform CI/CD)

---

## Verdict

The architecture is **fundamentally sound**. The module structure, phasing strategy, six-backend
rendering split, and use of existing abstractions (PlatformFactory, InputBackend, AudioBackend)
are well-designed. However, the plan has **critical gaps** that would cause blockers if not
addressed before implementation begins.

---

## Critical Blockers (Must Fix Before Starting)

### 1. `java.nio.file` is pervasive — not just in AssetLoaders

**[Web Specialist]** TeaVM does not support `java.nio.file` at all. The plan only addresses
`AssetLoader` implementations, but **14+ game files** use `Files`, `Paths`, `Path` directly:
`ConfigLoader`, `SaveManager`, `Shader`, `AudioClipLoader`, `MusicConfig`, `AudioConfig`,
`SceneDataLoader`, `FontLoader`, `AnimationLoader`, `AnimatorControllerLoader`,
`JsonPrefabLoader`, `AssetMetadata`, `FileUtils`, plus `AssetManager` itself.

**Action:** The `AssetProvider` refactor scope must include every file that touches the
filesystem, not just loaders. This is the single largest mechanical refactoring.

### 2. Reflection affects 4 systems, not just ComponentRegistry

**[Web Specialist]** The annotation processor plan only covers `ComponentRegistry`. Three
other systems use the same `Reflections` classpath scanning pattern:
- `AssetManager.registerDefaultLoaders()` — discovers `AssetLoader` subtypes
- `PostEffectRegistry.initialize()` — discovers `PostEffect` subtypes
- `ComponentTypeAdapterFactory` — uses `Class.forName()` for deserialization

**Action:** The annotation processor must generate registries for all four systems.

### 3. GSON may not work on TeaVM — needs early validation

**[Web Specialist]** GSON uses heavy internal reflection (`Field.setAccessible(true)`,
`Field.set()`, `ParameterizedType` introspection). TeaVM's reflection support is limited.
If GSON fails, the entire serialization layer must be rewritten — this changes the plan
fundamentally.

**Action:** Run a spike test: compile a minimal GSON deserialization of a scene JSON on
TeaVM before committing to the architecture. Do this before Phase 1.

### 4. Shader versions are wrong for WebGL 2.0

**[Web Specialist]** 17 of 20 shader files use `#version 310 es` (ES 3.1). WebGL 2.0 only
supports **GLSL ES 300** (ES 3.0). These shaders will fail at runtime on web.

Additionally, the `#ifdef GL_ES` approach shown in the plan **will not work in GLSL** —
the `#version` directive must be the very first line, before any preprocessor logic. The
preprocessing must happen in Java-side string manipulation, not in the shader source.

**Action:** Downgrade all shaders to `#version 300 es`. Implement shader preprocessing in
Java (in `ShaderBackend.createProgram()` or a `ShaderPreprocessor` utility), not via GLSL
`#ifdef` directives.

### 5. `FloatBuffer` in backend interfaces is not platform-neutral

**[Engine Architect, Web Specialist, Mobile Dev]** All three technical reviewers flagged this.
The `BufferBackend.bufferSubData(int target, long offset, FloatBuffer data)` signature uses
`java.nio.FloatBuffer`, which:
- On desktop: is LWJGL off-heap (`MemoryUtil.memAllocFloat()`)
- On Android: must be `ByteBuffer.allocateDirect().asFloatBuffer()` (non-direct buffers crash GLES)
- On TeaVM: is emulated and requires copying to `Float32Array` via JSO bridge

**Action:** Replace `FloatBuffer` with `float[]` in all backend interface signatures. Each
platform wraps the array in its native buffer type internally. Alternatively, introduce a
`NativeBuffer` abstraction.

### 6. GL context loss must be Phase 4, not Phase 5

**[Engine Architect, Mobile Dev]** Both reviewers independently flagged this as the plan's
biggest gap. When Android's GL context is lost (pause, surface destroyed, config change),
every GL handle becomes invalid: all texture IDs, VBO/VAO IDs, FBO IDs, shader program IDs.

The current `Texture` class calls `stbi_image_free(imageData)` immediately after GPU upload —
the pixel data is gone and the texture cannot be recreated without re-loading from disk.

**Action:** Design a `GPUResource` interface with `invalidate()` and `recreate()` methods in
Phase 1. Have `Texture`, `Shader`, `SpriteBatch`, `PostProcessor`, and all `PostEffect`
implementations register with a `GPUResourceTracker`. This is a no-op on desktop but prevents
a painful retrofit when Android arrives. Textures must retain their source path (or raw bytes)
for re-upload.

---

## Architectural Improvements

### 7. Add a `RenderDevice` composite over the 6 sub-interfaces

**[Engine Architect]** The six-interface split is correct in granularity, but `SpriteBatch.flush()`
needs coordinated calls across 4 backends (Buffer, Texture, Draw, State). Passing 3-4 backend
references to every rendering class adds noise.

**Recommendation:** Keep the six interfaces for resource lifecycle, but introduce a `RenderDevice`
composite that holds all six and is the standard injection point. Most rendering classes receive
`RenderDevice`; specialized code (e.g., PostProcessor) can access individual backends through it.

### 8. Fold `DrawBackend` into `BufferBackend`

**[Engine Architect]** `DrawBackend` has only two methods (`drawArrays`, `drawElements`). A
separate interface with its own implementation class, factory method, and test suite is
unjustified overhead. Fold it into `BufferBackend` or the proposed `RenderDevice`.

### 9. Add an `ImageDecoder` interface

**[Engine Architect]** `Texture` tightly couples STB image decoding with GL texture upload.
STB is LWJGL-native — it doesn't exist on Android (`BitmapFactory`) or web (`HTMLImageElement`).

```java
public interface ImageDecoder {
    DecodedImage decode(byte[] data, boolean flipVertically);
}
record DecodedImage(ByteBuffer pixels, int width, int height, int channels) {}
```

### 10. Add a font loading strategy for web

**[Web Specialist]** `Font.java` uses STBTruetype native bindings for glyph rasterization.
This doesn't exist on web. Options:
- Ship pre-rasterized font atlas textures (simplest)
- Use HTML5 Canvas 2D `fillText()` to rasterize glyphs
- Use a Wasm port of stb_truetype

### 11. `GL_TEXTURE_SWIZZLE_*` is not available on ES 3.0

**[Engine Architect]** `Texture.java` uses `GL_TEXTURE_SWIZZLE_G` and `GL_TEXTURE_SWIZZLE_B`
for single-channel textures. This is a GL 3.3 feature that does not exist in ES 3.0 / WebGL 2.0.
The `GLConstants` approach masks this — the constant compiles but the call fails at runtime.

**Action:** Add a capability detection or validation pass at startup. At minimum, remove
swizzle calls on ES platforms and handle single-channel textures differently.

---

## Android-Specific Findings

### 12. Threading: `pollEvents()` is NOT a no-op on Android

**[Mobile Dev]** The plan says `pollEvents()` is a no-op on Android. This is wrong. Input
events arrive on the UI thread; rendering runs on the GL thread. `AndroidInputBackend` must
implement a thread-safe event queue. `pollEvents()` on the GL thread drains the queue and
dispatches callbacks. Without this, input state has data races.

**Action:** Design the input threading model explicitly. Use `ConcurrentLinkedQueue` or a
ring buffer. Document that all backend interface methods must be called from the GL thread.

### 13. GLSurfaceView configuration matters

**[Mobile Dev]** The plan doesn't specify critical settings:
- `setEGLContextClientVersion(3)` — required for ES 3.0
- `setPreserveEGLContextOnPause(true)` — reduces (but doesn't eliminate) context loss
- `setRenderMode(RENDERMODE_CONTINUOUSLY)` — required for a game with animations
- Custom `EGLConfigChooser` requesting RGBA8888 — default may select 16-bit color

### 14. Lock screen orientation

**[Mobile Dev]** The plan doesn't mention orientation. For a pixel-art RPG with fixed internal
resolution, lock to landscape via `android:screenOrientation="sensorLandscape"` in the manifest.

### 15. Audio on Android: SoundPool + MediaPlayer hybrid

**[Mobile Dev]** For a 2D RPG:
- **SFX:** `SoundPool` (pre-loaded, low latency ~20ms)
- **Music:** `MediaPlayer` (streaming, handles OGG/MP3)
- **Skip Oboe** — NDK/C++ complexity is unjustified for a Pokémon-style RPG

The `AudioBackend` interface may need a streaming source concept for music, since
`createBuffer(short[] data, ...)` implies pre-decoded audio, which is wrong for music streams.

### 16. Cap `deltaTime` after resume

**[Mobile Dev]** After returning from background, `System.nanoTime()` produces a multi-second
delta. The game loop must clamp `deltaTime` (e.g., max 500ms) to prevent physics and tween
explosions.

### 17. Post-processing budget on mobile

**[Engine Architect, Mobile Dev]** 15+ post-effects is aggressive for mobile GPUs. Tile-based
mobile renderers (Mali, Adreno, PowerVR) suffer from excessive FBO switching.

**Action:** Add a tiered quality system to `RenderingConfig` (LOW/MEDIUM/HIGH). LOW: 2-3
effects. MEDIUM: half-resolution passes. HIGH: full pipeline. Select tier per platform.

---

## Web-Specific Findings

### 18. `javax.sound.sampled` is a blocker

**[Web Specialist]** `AudioClipLoader.java` uses `javax.sound.sampled.AudioSystem` for
WAV/MP3 decoding. TeaVM doesn't support this. OGG loading uses `STBVorbis` (native).
On web, use `AudioContext.decodeAudioData()` during the preload phase.

### 19. OGG audio doesn't work in Safari

**[Web Specialist]** Safari/iOS Safari don't support OGG Vorbis. Ship audio as MP3
(universally supported) or provide dual OGG+MP3 with runtime format detection.

### 20. `SaveManager` has no web strategy

**[Web Specialist, Product Manager]** `SaveManager` uses filesystem I/O. On web, saves
must use `localStorage` (sync, 5-10 MB limit) or IndexedDB (async, larger). The plan needs
a `SaveProvider` interface parallel to `AssetProvider`.

### 21. Preload strategy is harder than described

**[Web Specialist]** Discovering transitive asset dependencies (scene → prefab → spritesheet →
texture; scene → animator → animation → texture) requires partially re-implementing the
deserialization pipeline.

**Better approach:** Generate an `asset-manifest.json` at build time that lists all asset
paths. The web preloader fetches everything in the manifest. Simpler, more reliable.

### 22. Dynamic scene loading on web

**[Web Specialist]** Scene transitions during gameplay require new asset fetches. Options:
- Preload ALL scenes upfront (simplest for small games)
- Loading screen during transitions
- Predictive preloading of adjacent scenes

### 23. `java.util.concurrent` partially unsupported

**[Web Specialist]** The game code uses `ConcurrentHashMap`, `CopyOnWriteArrayList`,
`ReentrantReadWriteLock`. On TeaVM (single-threaded JS), `ConcurrentHashMap` compiles as
a regular `HashMap`, but `ReentrantReadWriteLock` is likely unsupported. Audit and replace
with non-concurrent equivalents where needed.

---

## Build, Test & Process Gaps

### 24. Migrate to Gradle before Phase 1

**[QA/DevOps]** Mixed Maven + Gradle (Maven for core/desktop, Gradle for Android) is the
single largest operational risk. The plan never addresses how the core module is shared.
Options: `mavenLocal()` publishing (fragile), Gradle composite builds, or full Gradle
migration. Given the straightforward `pom.xml`, migrating everything to Gradle is recommended.

### 25. No testing strategy at all

**[QA/DevOps]** The plan contains zero mentions of testing.
- **Phase 1 visual regression:** Capture golden screenshots before the refactor, pixel-diff
  after. The refactor touches ~27 files and ~420 GL call sites.
- **Mock backends:** Build `MockBufferBackend`, `MockTextureBackend`, etc. to verify call
  ordering without a GPU.
- **Annotation processor:** Run generated registry alongside `Reflections`-based registry
  during transition to verify identical output.
- **Web:** Chrome headless with SwiftShader for CI.
- **Android:** x86_64 emulator with SwiftShader (API 30+ stable).

### 26. Pre-frame `FloatBuffer` allocation in `Shader.java`

**[Engine Architect, Mobile Dev]** `Shader.java` calls `BufferUtils.createFloatBuffer(16)` on
every `uploadMat4f()` call — allocating a new buffer per uniform upload per frame. On desktop
this uses off-heap memory; on Android it causes GC pressure (hundreds of allocations per frame).

**Action:** Pre-allocate a reusable `FloatBuffer(16)` in the `Shader` class. Fixes desktop
performance too.

### 27. `MemoryUtil.memAllocFloat` / `memFree` portability

**[Engine Architect]** `SpriteBatch` uses LWJGL's `MemoryUtil` for off-heap allocation.
Neither `memAllocFloat()` nor `memFree()` exist on Android or TeaVM. The vertex buffer
allocation strategy must be abstracted or use `java.nio` exclusively.

---

## Product & Strategy Findings

### 28. Platform priority: Web before Android

**[Product Manager]** Web has lower friction (no app store, instant play, shareable URL),
lower development risk (no context loss, no device fragmentation), and validates the core
abstraction. Ship web first to prove the architecture, then tackle Android.

### 29. Audio assets need a build-time conversion pipeline

**[Product Manager]** Current assets are WAV files. WAV is unacceptable for web delivery
(file size) and suboptimal for mobile. Build step needed: WAV → OGG (desktop), WAV → MP3
(web/Safari), WAV → OGG (Android).

### 30. Missing `SaveProvider` interface

**[Product Manager, Web Specialist]** The plan has `AssetProvider` for reading game data but
nothing for save data. Each platform needs different save storage:
- Desktop: filesystem
- Web: `localStorage` / IndexedDB
- Android: internal storage / `SharedPreferences`

### 31. Touch input design cannot wait until Phase 5

**[Product Manager]** Touch input may require changes to the `InputBackend` contract (touch
coordinates, gesture events, virtual d-pad state). Design it during Phase 2 so the interface
is stable before platform implementations begin.

**[Mobile Dev]** Recommends a floating virtual d-pad (appears at first touch point, 4/8
directional zones for grid movement). Touch targets must be at least 48dp.

---

## Summary: Priority Actions

### Before Phase 1 (spikes/validation)
1. **GSON on TeaVM spike** — validate it works or plan alternative serialization
2. **Fix shader versions** — downgrade `310 es` → `300 es` (can do now on desktop)
3. **Audit `java.nio.file` usage** — map the full scope of the AssetProvider refactor

### Phase 1 additions
4. Replace `FloatBuffer` with `float[]` in backend interface signatures
5. Introduce `RenderDevice` composite over the 6 sub-interfaces (fold `DrawBackend` in)
6. Design `GPUResource` / `GPUResourceTracker` for context loss (no-op on desktop)
7. Add `ImageDecoder` interface (STB stays on desktop, but Texture decoupled from it)
8. Introduce `AssetProvider` across ALL file-reading code (14+ files, not just loaders)
9. Pre-allocate reusable `FloatBuffer` in `Shader.java` uniform uploads
10. Abstract `MemoryUtil` allocation behind a platform-neutral buffer factory
11. Capture golden screenshots for visual regression testing

### Phase 2 additions
12. Annotation processor generates registries for 4 systems (Components, AssetLoaders, PostEffects, deserialization type map)
13. Add `SaveProvider` interface
14. Design touch input model and any `InputBackend` contract changes
15. Add tiered quality system to `RenderingConfig` (LOW/MEDIUM/HIGH)
16. Build audio format conversion into the build pipeline
17. Generate `asset-manifest.json` at build time for web preloading
18. Add font loading strategy for web (pre-rasterized atlases or Canvas 2D)
19. Evaluate Gradle migration

### Phase 3/4 additions
20. Shader preprocessing in Java, not GLSL `#ifdef`
21. Android: design input threading model (UI thread → GL thread queue)
22. Android: configure GLSurfaceView properly (ES 3, RGBA8888, continuous render)
23. Android: lock orientation, handle audio focus, cap deltaTime
24. Web: handle Safari audio format fallback (OGG → MP3)
25. Web: handle keyboard focus loss (release all keys on blur)
26. Web: `SaveProvider` → `localStorage`
