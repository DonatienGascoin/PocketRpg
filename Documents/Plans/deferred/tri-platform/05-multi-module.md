# Plan 5: Multi-Module Maven + Web Build Setup

**Goal:** Split single module into core (Java 17) + platform-lwjgl (Java 25) + editor (Java 25) + platform-web (TeaVM stubs).

**Prerequisites:** Plans 1, 2, 3, 4 all complete (all abstractions in place, no LWJGL/filesystem/Reflections in core).

**Status:** Not started

---

## Dependency Graph

```
Plans 1, 2, 3, 4 (all abstractions done)
        |
        v
     Plan 5 (this plan)
        |
        v
     Plan 6 (web implementation)
```

---

## Deliverables

### 1. Parent POM with 4 modules

```
pocket-rpg/
├── pom.xml                    (parent POM)
├── core/                      (game logic, Java 17)
│   ├── pom.xml
│   └── src/
├── platform-lwjgl/            (desktop backends, Java 25)
│   ├── pom.xml
│   └── src/
├── editor/                    (scene editor, Java 25)
│   ├── pom.xml
│   └── src/
├── platform-web/              (TeaVM backends, Java 17)
│   ├── pom.xml
│   └── src/
└── pocket-rpg-processor/      (annotation processor, from Plan 3)
    ├── pom.xml
    └── src/
```

### 2. `core` module

- All game logic: ECS, rendering interfaces, scenes, components, physics, audio interfaces
- **Java 17** source/target
- **Zero** LWJGL, STB, ImGui, or Reflections dependencies
- Dependencies: JOML, GSON, annotation processor
- Contains: `rendering/api/`, `AssetProvider`, `SaveProvider`, `GameRunner`, `TimeContext`, all game systems

### 3. `platform-lwjgl` module

- Desktop platform implementations
- Java 25 (can use latest Java features)
- Dependencies: `core`, LWJGL (OpenGL, GLFW, OpenAL, STB)
- Contains: `GL33RenderDevice`, `FileSystemAssetProvider`, `FileSystemSaveProvider`, `StbImageDecoder`, `DesktopLoop`, desktop audio, `DesktopPlatformFactory`
- Entry point: `DesktopMain` (launches game)

### 4. `editor` module

- Scene editor (desktop-only, never cross-platform)
- Java 25
- Dependencies: `platform-lwjgl`, ImGui
- Contains: all `editor/` code, `EditorApplication`
- No constraints on LWJGL/filesystem/reflection usage

### 5. `platform-web` module

- TeaVM web platform
- Java 17 source/target
- Dependencies: `core`, TeaVM plugin + JSO
- Contains: stub backend implementations (compile but don't render yet — Plan 6 fills these in)
- TeaVM Maven plugin configured for compilation
- Entry point: `WebMain` (stub)

### 6. Build-time asset manifest generator

- Maven plugin or build step that generates `asset-manifest.json` per scene
- Lists all assets required by each scene
- Used by web platform for preloading (Plan 6)
- Format:
  ```json
  {
    "scenes": {
      "town": ["textures/town.png", "maps/town.json", "audio/town.ogg"],
      "forest": ["textures/forest.png", "maps/forest.json"]
    },
    "global": ["textures/player.png", "shaders/sprite.glsl", "config/input.json"]
  }
  ```

### 7. Audio format conversion

- Build step converting OGG audio to MP3 for Safari compatibility
- Web build includes both OGG and MP3 variants
- Desktop build unchanged (OGG only)

### 8. `PlatformLogger` interface

```java
public interface PlatformLogger {
    void info(String message);
    void warn(String message);
    void error(String message, Throwable t);
}
```

- Desktop implementation: `System.out` / `System.err`
- Web implementation (Plan 6): `console.log` / `console.warn` / `console.error`
- Replaces direct `System.out.println` in core game code

---

## Module Dependency Graph

```
pocket-rpg-processor (compile-time only)
        |
        v
      core (Java 17, no platform deps)
      / \
     /   \
    v     v
platform-lwjgl    platform-web
(Java 25)         (Java 17 + TeaVM)
    |
    v
  editor
(Java 25 + ImGui)
```

---

## File Movement Plan

| Current Location | New Module | Notes |
|-----------------|------------|-------|
| `src/main/java/com/pocket/rpg/` (game code) | `core` | Bulk of the codebase |
| `src/main/java/com/pocket/rpg/editor/` | `editor` | All editor code |
| GL33 backend implementations | `platform-lwjgl` | Created in Plan 1 |
| `FileSystemAssetProvider` etc. | `platform-lwjgl` | Created in Plan 2 |
| `StbImageDecoder` | `platform-lwjgl` | Created in Plan 1 |
| `DesktopLoop` | `platform-lwjgl` | Created in Plan 4 |
| `GameApplication` (desktop entry) | `platform-lwjgl` | Becomes `DesktopMain` |
| `gameData/` (assets) | `core/src/main/resources/` or separate | TBD |
| Shader `.glsl` files | `core` (resources) | Platform-independent after Plan 4 |

---

## Success Criteria

- [ ] `mvn compile` succeeds for all modules
- [ ] `mvn test` passes for core module
- [ ] Desktop game launches from `platform-lwjgl` module
- [ ] Editor launches from `editor` module
- [ ] `platform-web` compiles with TeaVM (stubs only)
- [ ] `core` has zero LWJGL/STB/ImGui/Reflections imports
- [ ] Asset manifest generates correctly
- [ ] Audio conversion produces MP3 files from OGG sources
- [ ] All existing tests pass

---

## Implementation Order

1. Create parent POM structure with module declarations
2. Create `core` module — move game code, set Java 17
3. Verify `core` compiles with zero platform dependencies
4. Create `platform-lwjgl` module — move backend implementations
5. Verify desktop game launches from `platform-lwjgl`
6. Create `editor` module — move editor code
7. Verify editor launches from `editor` module
8. Create `platform-web` module with stub backends
9. Configure TeaVM Maven plugin
10. Verify `platform-web` compiles with TeaVM
11. Implement asset manifest generator
12. Implement audio format conversion build step
13. Create `PlatformLogger` interface + desktop implementation
14. Final verification: full build, all tests, both entry points
