# Hot-Reload for Editor Component Classes

## Problem

Every component property change, new component class, or logic edit requires a full editor restart. This breaks development flow significantly.

## Three Options (Incremental)

Each option builds on the previous one. They are complementary, not mutually exclusive.

| Option | What It Covers | Code Changes | Prerequisite |
|--------|---------------|-------------|--------------|
| **1. DCEVM + HotSwapAgent** | Method body edits, field add/remove (at JVM level) | None — config only | JetBrains Runtime |
| **2. Scene Reload** | New components, annotation changes, field changes reflected in editor | `ComponentRegistry.reinitialize()` + `reloadScene()` command | Option 1 recommended |
| **3. Custom ClassLoader + File Watcher** | Fully automatic: save file → editor updates | File watcher, compiler, classloader, orchestrator | Option 2 required |

## Recommended Path

1. **Start with Option 1** — Zero code changes, immediate benefit for ~70% of edits
2. **Implement Option 2** — Covers the remaining 30% with a manual Ctrl+Shift+R trigger
3. **Evaluate Option 3** — Only if the manual trigger in Option 2 feels too disruptive. Consider the simplified variant (DCEVM + file watcher, no custom classloader) described at the end of Option 3's plan.

## Plan Files

- [Option 1: DCEVM + HotSwapAgent](option1-dcevm-hotswap.md) — Configuration guide
- [Option 2: Scene Reload](option2-scene-reload.md) — `ComponentRegistry.reinitialize()` + scene snapshot/restore
- [Option 3: Custom ClassLoader + File Watcher](option3-classloader-filewatcher.md) — Full automatic pipeline
