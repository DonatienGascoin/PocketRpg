# CLAUDE.md

Project guidance for Claude Code.

During each session, try to improve this document with any relevant tips, best practices, or reference links you find yourself sharing repeatedly. 
The goal is to build a single source of truth for how to work effectively on PocketRpg, so future contributors can onboard faster and avoid common pitfalls.

Encyclopedia folder is the user source of truth, it also needs to be kept up to date with new features and editor workflows.
If you implement or update a feature that requires an encyclopedia guide, **always ask the user before creating or updating encyclopedia files.** Some users may prefer to write their own guides or may want to delay documentation until the feature is more stable.

## Build & Run Commands

```bash
mvn compile                                    # Build
mvn test                                       # Run tests
mvn test -Dtest=ClassName                      # Single test class
mvn exec:java -Dexec.mainClass="com.pocket.rpg.Main"                    # Run game
mvn exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication" # Run editor
```

## Project Overview

PocketRpg is a 2D game engine with a scene editor, built in Java 25. Uses LWJGL for OpenGL/GLFW, ImGui for editor UI, and custom tile-based collision (Pokémon-style grid movement).

## Documentation Structure

| Location | Content |
|----------|---------|
| `.claude/workflows/` | How to design and implement plans |
| `.claude/reference/` | Architecture, pitfalls, how-to guides |
| `Documents/Plans/active/` | Implementation plans in progress |
| `Documents/Plans/finished/` | Completed implementation plans |
| `Documents/Design/` | Design explorations and architecture docs |
| `Documents/Encyclopedia/` | User guides for editor features |
| `Documents/Reviews/` | Code reviews and analysis |
| `Documents/Roadmap/` | Future features and integration notes |

## Before Writing Code

**Read `.claude/reference/common-pitfalls.md`** - Covers ImGui push/pop rules, component lifecycle, serialization, undo patterns.

## Before Implementing a Plan

**Read `.claude/workflows/plan-implementation.md`** - Worktree workflow, phase reviews, PR process.

## Before Designing a Plan

**Read `.claude/workflows/plan-design.md`** - Plan structure, existing systems to leverage.

## Reference Files

- **Architecture**: `.claude/reference/architecture.md`
- **Field Editors**: `.claude/reference/field-editors.md`
- **Quick Reference**: `.claude/reference/quick-reference.md`

## Keeping Docs Updated

When modifying core systems, update corresponding reference files:

| If you change... | Update... |
|------------------|-----------|
| `animation/`, `AnimationComponent` | `architecture.md` - Animation System |
| `collision/`, `GridMovement` | `architecture.md` - Collision System |
| `resources/`, `AssetLoader` | `architecture.md` - Asset Pipeline |
| `editor/gizmos/`, Component gizmos | `architecture.md` - Gizmos System, `common-pitfalls.md` |
| `editor/ui/fields/` | `field-editors.md` |
| Component lifecycle | `common-pitfalls.md` |

## GitHub PR Comments

When replying to PR review comments via `gh api`, always prefix the message body with `Claude:` so reviewers can distinguish automated replies from human ones.

## Encyclopedia

After completing a feature, check if `Documents/Encyclopedia/` needs a new/updated guide. **Always ask user before creating or updating encyclopedia files.**
