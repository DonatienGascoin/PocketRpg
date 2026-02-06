# Option 1: DCEVM + HotSwapAgent (Short Term)

## Overview

**Problem:** Every component property change or method edit requires a full editor restart, breaking development flow.

**Approach:** Use JetBrains Runtime (JBR) with built-in DCEVM patches and the HotSwapAgent framework to enable structural class redefinition at runtime. This is a zero-code-change option that leverages existing JVM tooling.

**Scope:** Method body changes, field additions/removals, new inner classes. Does NOT cover adding entirely new top-level component classes (ComponentRegistry won't discover them).

**Effort:** Configuration only — no Java code changes needed in PocketRpg itself.

---

## What DCEVM + HotSwapAgent Provides

### Standard JVM HotSwap (baseline)
- Replace method bodies only
- Cannot add/remove fields, methods, or classes
- Built into every JDK via `-agentlib:jdwp`

### DCEVM (Dynamic Code Evolution VM)
- Patched JVM that extends HotSwap to support:
  - Adding/removing fields
  - Adding/removing methods
  - Adding/removing constructors
  - Changing class hierarchy (add/remove interfaces, change superclass)
  - Adding new anonymous/inner classes
- Bundled in JetBrains Runtime (JBR) since 2020

### HotSwapAgent (optional enhancement)
- Open-source Java agent that hooks into DCEVM
- Provides framework-specific plugins to reinitialize caches after class changes
- Relevant plugins: Reflection plugin (clears reflection caches), Generic plugin (field reinitialization)
- Custom plugin API for project-specific reload logic

---

## Prerequisites

### 1. JetBrains Runtime (JBR)

JBR is a patched OpenJDK distributed by JetBrains. It includes DCEVM patches.

**Installation options:**
- **IntelliJ IDEA:** Ships with JBR. In `Settings → Build → Java Compiler`, the bundled JDK is JBR.
- **Standalone download:** https://github.com/JetBrains/JetBrainsRuntime/releases
  - Download the build matching your JDK version (e.g., JBR 21 for Java 21)
  - The `jbr_dcevm` variant includes DCEVM; `jbr_jcef` includes browser engine

**Compatibility note:** PocketRpg targets Java 25. As of mid-2025, JBR tracks OpenJDK closely but may lag by a version. Check JBR releases for Java 25 support. Java 21 LTS is the safest bet if you can target it.

**Verification:**
```bash
# Check if DCEVM is available
java -XX:+AllowEnhancedClassRedefinition -version
# Should print version without error
```

### 2. HotSwapAgent (optional)

**Download:** https://github.com/HotswapProjects/HotswapAgent/releases
- Download `hotswap-agent.jar`
- Place in project root or a `tools/` directory

**Activation:**
```bash
java -XX:+AllowEnhancedClassRedefinition \
     -XX:HotswapAgent=fatjar \
     -javaagent:hotswap-agent.jar \
     -Dexec.mainClass=com.pocket.rpg.editor.EditorApplication
```

Or with Maven:
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <configuration>
        <executable>java</executable>
        <arguments>
            <argument>-XX:+AllowEnhancedClassRedefinition</argument>
            <argument>-XX:HotswapAgent=fatjar</argument>
            <argument>-javaagent:${project.basedir}/tools/hotswap-agent.jar</argument>
            <argument>-classpath</argument>
            <classpath/>
            <argument>com.pocket.rpg.editor.EditorApplication</argument>
        </arguments>
    </configuration>
</plugin>
```

---

## Setup Steps

### Phase 1: Configure JBR as Project JDK

- [ ] Download JBR with DCEVM for your platform
- [ ] Configure IntelliJ to use JBR as the project SDK (`File → Project Structure → SDKs`)
- [ ] Verify DCEVM works: `java -XX:+AllowEnhancedClassRedefinition -version`
- [ ] Update Maven toolchain or `JAVA_HOME` if building from command line

### Phase 2: Configure Debug Run Configuration

- [ ] In IntelliJ, edit the EditorApplication run configuration
- [ ] Add VM options: `-XX:+AllowEnhancedClassRedefinition`
- [ ] Run in Debug mode (required for class redefinition)
- [ ] Test: Change a method body in any component → `Build → Recompile` → verify change takes effect without restart

### Phase 3: (Optional) Add HotSwapAgent

- [ ] Download `hotswap-agent.jar` to `tools/` directory
- [ ] Add to `.gitignore`: `tools/hotswap-agent.jar`
- [ ] Add VM options: `-XX:HotswapAgent=fatjar -javaagent:tools/hotswap-agent.jar`
- [ ] Create `hotswap-agent.properties` in `src/main/resources/`:
  ```properties
  # Enable reflection plugin to clear cached reflection data
  pluginPackages=org.hotswap.agent.plugin.reflection

  # Watch compiled class output directory
  extraClasspath=${project.basedir}/target/classes

  # Log reload events
  LOGGER.org.hotswap.agent=INFO
  ```

### Phase 4: Validate Workflow

- [ ] Start editor in debug mode
- [ ] Open a scene with components
- [ ] Test scenarios:
  - Change a method body in a component (e.g., `update()`) → Recompile → verify behavior changes immediately
  - Add a new field to an existing component → Recompile → verify no crash (field will be default value until scene reload)
  - Add a new method → Recompile → verify no crash
  - Remove a field → Recompile → verify no crash
- [ ] Document which change types work and which require editor restart

---

## Limitations

| Change Type | Works? | Notes |
|---|---|---|
| Method body edit | Yes | Core HotSwap, works on any JDK |
| Add/remove field | Yes (DCEVM) | Existing instances get default values for new fields |
| Add/remove method | Yes (DCEVM) | |
| Change field type | Yes (DCEVM) | Existing instances may have stale data |
| New top-level class | No | ComponentRegistry won't discover it |
| Change class hierarchy | Partial | DCEVM supports it, but ComponentRegistry cache is stale |
| Annotation changes | No effect | ComponentRegistry caches annotations at startup |
| New component category | No | Requires ComponentRegistry re-scan |

**Key limitation:** Adding a brand-new component class or changing annotations requires a restart because `ComponentRegistry` is initialized once at startup and caches all metadata statically. Options 2 and 3 address this.

---

## What This Enables in Practice

**Typical workflow:**
1. Run editor in debug mode with JBR
2. Place components, set up scene
3. Notice a bug in component behavior or want to tweak logic
4. Edit the Java source in IntelliJ
5. Press `Ctrl+Shift+F9` (Recompile current file) or `Ctrl+F9` (Build project)
6. IntelliJ pushes the new class definition to the running JVM
7. Next frame, the component runs the updated code — no restart needed

**This covers ~70% of iterative development** (tweaking behavior, fixing bugs, adjusting values in code). The remaining 30% (new components, field changes that affect serialization) requires the scene reload approach in Option 2.

---

## Cost/Benefit

| | |
|---|---|
| **Code changes required** | None |
| **Setup effort** | Download JBR, configure run config |
| **Maintenance** | Keep JBR updated with JDK releases |
| **Risk** | Low — fallback is normal restart |
| **Coverage** | Method body changes (most common during development) |
| **Combines with** | Options 2 and 3 (complementary, not exclusive) |
