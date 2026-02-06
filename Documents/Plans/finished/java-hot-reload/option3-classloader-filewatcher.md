# Option 3: Custom ClassLoader + File Watcher (Long Term)

## Overview

**Problem:** Options 1 and 2 require manual intervention — the developer must trigger recompilation (DCEVM) or press Ctrl+Shift+R (Scene Reload). The ideal workflow is: save a Java file, the editor automatically detects the change, recompiles, reloads the affected classes, and refreshes the scene — all seamlessly.

**Approach:** Build an automatic hot-reload pipeline:
1. **File watcher** monitors `src/main/java/` for `.java` file changes
2. **Incremental compiler** recompiles changed files
3. **Custom ClassLoader** loads the new `.class` files, replacing the old definitions
4. **Scene reload** (from Option 2) rebuilds the active scene with updated classes

**Scope:** Full automatic hot-reload for component classes. Covers all changes: new classes, removed classes, field changes, method changes, annotation changes.

**Effort:** Significant architectural change. Requires careful handling of classloader isolation, class identity, and resource cleanup.

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│ EditorApplication                                                │
│                                                                  │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐ │
│  │ FileWatcher  │───→│ Compiler     │───→│ HotReloadManager    │ │
│  │ (NIO Watch)  │    │ (javax.tools │    │                     │ │
│  │              │    │  or Maven)   │    │ 1. Swap classloader │ │
│  │ Watches:     │    │              │    │ 2. Re-scan registry │ │
│  │ src/**/*.java│    │ Output:      │    │ 3. Reload scene     │ │
│  │              │    │ target/      │    │                     │ │
│  └─────────────┘    │ classes/     │    └─────────────────────┘ │
│                      └──────────────┘                            │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ ComponentClassLoader (child of app classloader)              ││
│  │                                                              ││
│  │ Loads: com.pocket.rpg.components.*                           ││
│  │ Delegates everything else to parent                          ││
│  │                                                              ││
│  │ On reload: create NEW instance, discard old one              ││
│  └──────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: File Watcher Service

### Design

Use Java NIO `WatchService` to monitor the source directory for `.java` file changes. Debounce rapid saves (IDEs often write multiple times) and batch changes.

### Implementation

- [ ] **Create `FileWatcherService` class**

  ```java
  // src/main/java/com/pocket/rpg/editor/hotreload/FileWatcherService.java
  public class FileWatcherService {
      private final Path watchRoot;          // src/main/java/
      private final Consumer<Set<Path>> onChange;
      private final ScheduledExecutorService debouncer;
      private WatchService watchService;
      private Thread watchThread;
      private volatile boolean running;
      private final Set<Path> pendingChanges = ConcurrentHashMap.newKeySet();

      // Debounce: collect changes for 500ms before notifying
      private static final long DEBOUNCE_MS = 500;

      public FileWatcherService(Path watchRoot, Consumer<Set<Path>> onChange);
      public void start();   // Start watching in background thread
      public void stop();    // Stop watching, clean up
  }
  ```

- [ ] **Register directories recursively**
  `WatchService` only watches a single directory per registration. Must walk the tree and register each subdirectory. Also register new directories as they appear (e.g., new package created).

  ```java
  private void registerRecursive(Path root) throws IOException {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
              dir.register(watchService,
                  StandardWatchEventKinds.ENTRY_CREATE,
                  StandardWatchEventKinds.ENTRY_MODIFY,
                  StandardWatchEventKinds.ENTRY_DELETE);
              return FileVisitResult.CONTINUE;
          }
      });
  }
  ```

- [ ] **Filter for .java files only**
  Ignore `.class`, `.md`, `.txt`, and other non-Java files.

- [ ] **Debounce mechanism**
  ```java
  private void onFileEvent(Path changedFile) {
      if (!changedFile.toString().endsWith(".java")) return;
      pendingChanges.add(changedFile);
      debouncer.schedule(this::flushChanges, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
  }

  private void flushChanges() {
      Set<Path> batch = Set.copyOf(pendingChanges);
      pendingChanges.clear();
      if (!batch.isEmpty()) {
          onChange.accept(batch);  // Notify HotReloadManager
      }
  }
  ```

### Files to Create

| File | Purpose |
|------|---------|
| `editor/hotreload/FileWatcherService.java` | **NEW** — NIO-based file watcher with debouncing |

---

## Phase 2: Incremental Compilation

### Design

When the file watcher detects changes, recompile the affected `.java` files. Two approaches:

#### Approach A: `javax.tools.JavaCompiler` (in-process)

The JDK includes a compiler API. Compile changed files directly in the editor process.

```java
public class IncrementalCompiler {
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private final Path sourceRoot;    // src/main/java/
    private final Path outputRoot;    // target/classes/ (or a separate hot-reload dir)
    private final String classpath;   // Full classpath from Maven

    public CompilationResult compile(Set<Path> changedFiles) {
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        Iterable<? extends JavaFileObject> units =
            fileManager.getJavaFileObjectsFromPaths(changedFiles);

        List<String> options = List.of(
            "-classpath", classpath,
            "-d", outputRoot.toString(),
            "--release", "25"
        );

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, fileManager, diagnostics, options, null, units);

        boolean success = task.call();
        return new CompilationResult(success, diagnostics.getDiagnostics());
    }
}
```

**Pros:** No external process, fast for small changes, full diagnostic access.
**Cons:** Requires JDK (not JRE) at runtime. Classpath must be computed. May struggle with annotation processors.

#### Approach B: External Maven/Gradle invocation

Shell out to `mvn compile -pl .` in a background process.

```java
public CompilationResult compileExternal() {
    ProcessBuilder pb = new ProcessBuilder("mvn", "compile", "-q")
        .directory(projectRoot.toFile())
        .redirectErrorStream(true);
    Process proc = pb.start();
    String output = new String(proc.getInputStream().readAllBytes());
    int exitCode = proc.waitFor();
    return new CompilationResult(exitCode == 0, output);
}
```

**Pros:** Uses existing build system, handles all dependencies and processors.
**Cons:** Slower (Maven startup overhead ~2-5s), external process management.

#### Recommendation

Start with **Approach A** (`javax.tools`) for speed. Fall back to external compilation if the project uses annotation processors or has complex build requirements. The compiler approach is faster and provides better integration.

### Implementation

- [ ] **Create `IncrementalCompiler` class**
- [ ] **Compute classpath from Maven model or `target/classes` + `lib/`**
  At editor startup, read the effective classpath. Options:
  - Parse `mvn dependency:build-classpath -Dmdep.outputFile=cp.txt`
  - Use `System.getProperty("java.class.path")` if launched via `mvn exec:java`
  - Hardcode `target/classes` + glob `lib/*.jar`

- [ ] **Create a separate output directory for hot-reloaded classes**
  Write compiled classes to `target/hot-reload/` instead of `target/classes/` to avoid conflicts with the main build. The custom classloader will check this directory first.

- [ ] **Report compilation errors to the editor**
  Show compilation errors in the StatusBar or a dedicated "Compiler Output" panel.
  ```java
  if (!result.success()) {
      StatusBar.showError("Compilation failed: " + result.firstError());
      // Optionally show full diagnostics in a panel
      return;
  }
  ```

### Files to Create

| File | Purpose |
|------|---------|
| `editor/hotreload/IncrementalCompiler.java` | **NEW** — javax.tools-based incremental compiler |
| `editor/hotreload/CompilationResult.java` | **NEW** — Result record with diagnostics |

---

## Phase 3: Custom ClassLoader

### Design

Java class identity is `(className, classLoader)`. To reload a class, you must use a new ClassLoader instance. The strategy:

1. **Parent-first for engine classes** — `com.pocket.rpg.core.*`, `com.pocket.rpg.editor.*`, `com.pocket.rpg.serialization.*`, etc. are loaded by the system classloader and never reloaded.
2. **Child-first for component classes** — `com.pocket.rpg.components.*` (and any user-defined component packages) are loaded by the custom classloader. On reload, create a new classloader instance.

```
System ClassLoader (parent)
  └── loads: engine, editor, serialization, rendering, etc.
      │
      └── ComponentClassLoader (child, replaceable)
            └── loads: com.pocket.rpg.components.*
                       (overrides from target/hot-reload/)
```

### Implementation

- [ ] **Create `ComponentClassLoader`**
  ```java
  public class ComponentClassLoader extends URLClassLoader {
      private final Set<String> reloadablePackages;

      public ComponentClassLoader(URL[] classpath, ClassLoader parent,
                                   Set<String> reloadablePackages) {
          super(classpath, parent);
          this.reloadablePackages = reloadablePackages;
      }

      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
          // Child-first for reloadable packages
          if (isReloadable(name)) {
              // Check if already loaded by THIS loader
              Class<?> loaded = findLoadedClass(name);
              if (loaded != null) return loaded;

              // Try to find in our classpath first
              try {
                  Class<?> c = findClass(name);
                  if (resolve) resolveClass(c);
                  return c;
              } catch (ClassNotFoundException e) {
                  // Fall through to parent
              }
          }

          // Parent-first for everything else
          return super.loadClass(name, resolve);
      }

      private boolean isReloadable(String name) {
          return reloadablePackages.stream().anyMatch(name::startsWith);
      }
  }
  ```

- [ ] **Configure reloadable packages**
  Default: `com.pocket.rpg.components`. Allow configuration for user-defined packages.
  ```java
  Set<String> reloadablePackages = Set.of(
      "com.pocket.rpg.components"
  );
  ```

- [ ] **ClassLoader swap on reload**
  ```java
  public void reloadClasses() {
      // Create new classloader with updated classpath
      URL[] urls = new URL[] {
          hotReloadDir.toUri().toURL(),   // Hot-reloaded classes (priority)
          targetClassesDir.toUri().toURL() // Main compiled classes (fallback)
      };

      ComponentClassLoader newLoader = new ComponentClassLoader(
          urls, getClass().getClassLoader(), reloadablePackages);

      // Old loader becomes eligible for GC once no references remain
      ComponentClassLoader oldLoader = currentLoader;
      currentLoader = newLoader;

      // Close old loader to release file handles
      oldLoader.close();
  }
  ```

### Critical Challenge: Class Identity

When you reload a class, `OldLoader.MyComponent != NewLoader.MyComponent`. This means:
- `instanceof` checks fail across loader boundaries
- `Class.cast()` throws `ClassCastException`
- Static fields are separate per loader

**Solution:** The `Component` base class must be loaded by the **parent** classloader (it's in `com.pocket.rpg.components` package but is a core engine class). All engine code that references components must use the base `Component` type or reflection.

- [ ] **Move `Component.java` to `com.pocket.rpg.core` if not already there**
  Or exclude it from reloadable packages:
  ```java
  private boolean isReloadable(String name) {
      if (name.equals("com.pocket.rpg.components.Component")) return false;
      return reloadablePackages.stream().anyMatch(name::startsWith);
  }
  ```

- [ ] **Ensure ComponentRegistry uses the custom classloader for instantiation**
  ```java
  // In ComponentRegistry
  public static Component instantiate(String name) {
      ComponentMeta meta = bySimpleName.get(name);
      // Use the hot-reload classloader, not Class.forName()
      Class<?> clazz = HotReloadManager.getClassLoader().loadClass(meta.fullName());
      return (Component) clazz.getDeclaredConstructor().newInstance();
  }
  ```

- [ ] **Ensure Reflections library uses the custom classloader for scanning**
  ```java
  Reflections reflections = new Reflections(
      new ConfigurationBuilder()
          .forPackage("com.pocket.rpg", currentLoader)
          .addClassLoaders(currentLoader)
          .addScanners(Scanners.SubTypes)
  );
  ```

### Files to Create/Modify

| File | Change |
|------|--------|
| `editor/hotreload/ComponentClassLoader.java` | **NEW** — Child-first classloader for components |
| `serialization/ComponentRegistry.java` | Modify to use custom classloader for instantiation and scanning |

---

## Phase 4: HotReloadManager (Orchestrator)

### Design

Central coordinator that wires file watcher → compiler → classloader → scene reload.

### Implementation

- [ ] **Create `HotReloadManager`**
  ```java
  public class HotReloadManager {
      private final FileWatcherService fileWatcher;
      private final IncrementalCompiler compiler;
      private volatile ComponentClassLoader currentLoader;
      private final EditorSceneController sceneController;
      private final EditorContext context;
      private boolean enabled = true;

      // Singleton for ClassLoader access from ComponentRegistry
      private static HotReloadManager instance;

      public HotReloadManager(Path projectRoot, EditorSceneController sceneController,
                               EditorContext context) {
          this.compiler = new IncrementalCompiler(
              projectRoot.resolve("src/main/java"),
              projectRoot.resolve("target/hot-reload"),
              computeClasspath()
          );

          this.fileWatcher = new FileWatcherService(
              projectRoot.resolve("src/main/java"),
              this::onSourceChanged
          );

          this.currentLoader = createClassLoader();
          instance = this;
      }

      public void start() {
          fileWatcher.start();
          LOG.info("Hot-reload active — watching src/main/java/");
      }

      public void stop() {
          fileWatcher.stop();
      }

      private void onSourceChanged(Set<Path> changedFiles) {
          if (!enabled) return;

          // Must run on main thread (GL context, ImGui)
          context.runOnMainThread(() -> {
              LOG.info("Source change detected: {}", changedFiles);

              // 1. Compile
              CompilationResult result = compiler.compile(changedFiles);
              if (!result.success()) {
                  StatusBar.showError("Compilation error: " + result.firstError());
                  return;
              }

              // 2. Swap classloader
              ComponentClassLoader oldLoader = currentLoader;
              currentLoader = createClassLoader();
              oldLoader.close();

              // 3. Re-scan registry
              ComponentRegistry.reinitialize();

              // 4. Reload scene (from Option 2)
              sceneController.reloadScene();

              StatusBar.showInfo("Hot-reload complete (" + changedFiles.size() + " files)");
          });
      }

      public static ClassLoader getClassLoader() {
          return instance != null ? instance.currentLoader :
                 Thread.currentThread().getContextClassLoader();
      }
  }
  ```

- [ ] **Add `runOnMainThread()` to EditorContext**
  The file watcher runs on a background thread, but all GL/ImGui/scene operations must happen on the main thread.
  ```java
  // EditorContext.java
  private final Queue<Runnable> mainThreadQueue = new ConcurrentLinkedQueue<>();

  public void runOnMainThread(Runnable action) {
      mainThreadQueue.add(action);
  }

  public void processMainThreadQueue() {
      Runnable action;
      while ((action = mainThreadQueue.poll()) != null) {
          action.run();
      }
  }
  ```
  Call `processMainThreadQueue()` in the main loop, before rendering.

### Files to Create/Modify

| File | Change |
|------|--------|
| `editor/hotreload/HotReloadManager.java` | **NEW** — Orchestrator |
| `editor/EditorContext.java` | Add `runOnMainThread()` queue |
| `editor/EditorApplication.java` | Initialize HotReloadManager, add `processMainThreadQueue()` to loop |

---

## Phase 5: Integration with Editor

### Editor Startup Changes

- [ ] **Initialize HotReloadManager after ComponentRegistry**
  ```java
  // In EditorApplication.init()
  ComponentRegistry.initialize();

  // After controllers are created:
  if (editorConfig.isHotReloadEnabled()) {
      hotReloadManager = new HotReloadManager(projectRoot, sceneController, context);
      hotReloadManager.start();
  }
  ```

- [ ] **Add hot-reload toggle to EditorConfig**
  ```java
  // EditorConfig
  private boolean hotReloadEnabled = true;
  ```

- [ ] **Add main thread queue processing to game loop**
  ```java
  // In main loop, before rendering
  context.processMainThreadQueue();
  ```

- [ ] **Shutdown on editor exit**
  ```java
  // In EditorApplication.destroy()
  if (hotReloadManager != null) {
      hotReloadManager.stop();
  }
  ```

### StatusBar / Notification Integration

- [ ] **Show file-watching status**
  Indicate in the status bar that hot-reload is active:
  ```
  [Hot-Reload: Active] | DemoScene.scene | 3 entities selected
  ```

- [ ] **Show compilation progress**
  ```
  [Compiling...] → [Reloading...] → [Hot-Reload: Active]
  ```

- [ ] **Show errors inline**
  If compilation fails, show the first error in the status bar with a clickable link to see full diagnostics.

### Files to Modify

| File | Change |
|------|--------|
| `editor/EditorApplication.java` | Initialize/shutdown HotReloadManager, process main thread queue |
| `editor/EditorConfig.java` | Add `hotReloadEnabled` setting |
| `editor/ui/StatusBar.java` | Show hot-reload status |

---

## Phase 6: Resource Cleanup and Safety

### Prevent Resource Leaks

When a scene is rebuilt, old component instances are discarded. Components that hold native resources (GL textures, audio buffers, NIO channels) must release them.

- [ ] **Ensure `onDestroy()` is called on all components before scene teardown**
  The existing `Scene.destroy()` should handle this via `GameObject.destroy()` → `Component.destroy()`. Verify this path is triggered during reload.

- [ ] **Audit components for native resource usage**
  Check each component subclass for:
  - OpenGL resources (textures, framebuffers, shaders, VAOs/VBOs)
  - OpenAL resources (buffers, sources)
  - File handles
  - Thread references

- [ ] **Add ClassLoader leak prevention**
  Old classloaders can leak if any reference to their classes persists. Common leak sources:
  - ThreadLocal variables
  - Static registries
  - Event listeners
  - Cached reflection data

  Mitigate:
  ```java
  // Before discarding old loader
  ComponentRegistry.clearCaches();  // Clear all static maps
  // Then reinitialize with new loader
  ComponentRegistry.reinitialize();
  ```

### Thread Safety

- [ ] **All reload operations on main thread**
  Already handled by `runOnMainThread()`, but verify no background threads access component instances during reload.

- [ ] **Disable reload during play mode**
  ```java
  private void onSourceChanged(Set<Path> changedFiles) {
      if (!enabled) return;
      if (playModeController.isActive()) {
          LOG.info("Hot-reload deferred — play mode active");
          pendingReload = changedFiles;  // Apply after play mode exits
          return;
      }
      // ... proceed with reload
  }
  ```

- [ ] **Handle reload during user operation**
  If the user is in the middle of dragging an entity, painting tiles, or editing an inspector field, defer the reload until the operation completes.
  ```java
  if (toolManager.isOperationInProgress()) {
      LOG.info("Hot-reload deferred — tool operation in progress");
      pendingReload = changedFiles;
      return;
  }
  ```

---

## Phase 7: Testing Strategy

### Unit Tests

- [ ] `FileWatcherService` — Create/modify/delete files and verify callback fires with correct paths
- [ ] `IncrementalCompiler` — Compile a valid Java file, verify `.class` output. Compile invalid file, verify error diagnostics.
- [ ] `ComponentClassLoader` — Load a class, verify it's the child-first version. Load a non-reloadable class, verify parent delegation.
- [ ] `HotReloadManager` — Mock compiler and scene controller, verify orchestration flow.

### Integration Tests

- [ ] Full cycle: Write `.java` file → detect change → compile → reload → verify new class is used
- [ ] Compile error: Write invalid `.java` → verify error shown, scene not reloaded
- [ ] ClassLoader swap: Verify old classes are garbage collected (use WeakReference)
- [ ] Concurrent safety: Trigger reload during scene editing, verify no corruption

### Manual Tests

- [ ] Save a component file in IntelliJ → editor automatically reloads within ~2 seconds
- [ ] Add a new component class → it appears in "Add Component" menu
- [ ] Introduce a compilation error → error shown in status bar → fix error → auto-reload succeeds
- [ ] Hot-reload during play mode → deferred until play mode exits
- [ ] Hot-reload with unsaved scene changes → changes preserved
- [ ] Monitor memory usage over many reloads (check for classloader leaks)

---

## Phase 8: Code Review

- [ ] Review ClassLoader hierarchy for class identity issues
- [ ] Review thread safety of all shared state
- [ ] Review resource cleanup paths (GL, AL, NIO)
- [ ] Review error handling and recovery (what happens if reload fails midway?)
- [ ] Performance review: measure reload latency end-to-end
- [ ] Security review: ensure file watcher can't be tricked into compiling arbitrary code

---

## Complete File Summary

| File | Change |
|------|--------|
| `editor/hotreload/FileWatcherService.java` | **NEW** — File watcher with debouncing |
| `editor/hotreload/IncrementalCompiler.java` | **NEW** — javax.tools compiler wrapper |
| `editor/hotreload/CompilationResult.java` | **NEW** — Compilation result record |
| `editor/hotreload/ComponentClassLoader.java` | **NEW** — Child-first classloader |
| `editor/hotreload/HotReloadManager.java` | **NEW** — Orchestrator |
| `serialization/ComponentRegistry.java` | Add `reinitialize()`, use custom classloader |
| `editor/EditorContext.java` | Add `runOnMainThread()` queue |
| `editor/EditorApplication.java` | Initialize HotReloadManager, process queue, shutdown |
| `editor/EditorConfig.java` | Add `hotReloadEnabled` setting |
| `editor/ui/StatusBar.java` | Show hot-reload status |
| `editor/scene/EditorSceneController.java` | `reloadScene()` (from Option 2, prerequisite) |

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| ClassLoader memory leaks | Slow memory growth over dev session | WeakReference auditing, periodic full-restart reminder |
| Class identity issues (`instanceof` fails) | Runtime exceptions | Careful package boundary for reloadable vs non-reloadable |
| Compilation classpath stale | Compile errors after adding dependencies | Re-read classpath from Maven model or `java.class.path` |
| File watcher misses events | Changes not detected | Manual "Reload" shortcut as fallback (Option 2) |
| Native resource leaks | GL context corruption | Thorough `onDestroy()` audit, defensive cleanup |
| Thread safety | Crash during reload | All operations on main thread, defer during active operations |

---

## Dependencies

- **Requires Option 2 (Scene Reload)** — The `reloadScene()` mechanism is used as the final step
- **Benefits from Option 1 (DCEVM)** — DCEVM allows structural class changes without custom classloader for simple cases; the classloader approach handles the more complex scenarios and automation

---

## Alternative: Skip Custom ClassLoader, Use DCEVM + File Watcher

A simpler variant of Option 3 that avoids the ClassLoader complexity:

1. File watcher detects `.java` changes
2. Run `mvn compile` (external process)
3. DCEVM automatically picks up new `.class` files (if running in debug mode with JBR)
4. Trigger `ComponentRegistry.reinitialize()` + `reloadScene()`

This avoids all ClassLoader identity issues but requires DCEVM/JBR. If you're already using JBR from Option 1, this is significantly simpler. The custom classloader is only needed if you want hot-reload on a standard JVM without DCEVM.
