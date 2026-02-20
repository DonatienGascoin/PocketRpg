# Plan: Editor Time and Input Context Wrappers

## Goal

Create `EditorTimeContext` and `EditorInputContext` that wrap ImGui's time/input systems, so the editor initializes all three singletons (`Time`, `Audio`, `Input`) at startup — matching the game's architecture. This makes play mode save/restore symmetric for all three contexts.

## Current State

- Editor uses `ImGui.getIO().getDeltaTime()` directly for delta time
- Editor uses `ImGui.isKeyPressed()`, `ImGui.isMouseClicked()` etc. for input
- `Time` and `Input` singletons are only initialized during play mode
- Components don't run outside play mode, so singletons being uninitialized is safe today

## Proposed

- `EditorTimeContext` wraps `ImGui.getIO().getDeltaTime()` behind the `TimeContext` interface
- `EditorInputContext` wraps ImGui's input behind the `InputContext` interface
- `EditorApplication` initializes all 3 singletons at startup
- Editor code migrates from `ImGui.getIO().getDeltaTime()` to `Time.deltaTime()`
- Play mode saves/restores all 3 contexts symmetrically (not just Audio)

## Motivation

- Unified API: editor and game use the same `Time`/`Input` singletons
- Opens the door for editor features that use `Input.getKey()` (e.g., viewport controls, custom shortcuts)
- Symmetric play mode context management (save/restore all 3)

## Scope

This is a follow-up to the "GameEngine owns all contexts" plan. Implement that first.

## Status

**Not started** — implement after "Make GameEngine Own All Static Contexts" is complete.
