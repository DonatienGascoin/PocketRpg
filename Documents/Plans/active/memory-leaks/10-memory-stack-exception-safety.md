# Plan 10: MemoryStack Exception Safety in EditorWindow

## Overview

**Problem:** `EditorWindow` uses `MemoryStack.stackPush()` in try-with-resources blocks, which is correct. However, if an exception occurs within the GLFW calls that use the stack-allocated buffers, the stack cleanup may be incomplete. This is a low-risk issue since MemoryStack is designed to handle this.

**Severity:** LOW-MEDIUM

**Approach:** Audit and confirm that all MemoryStack usages follow the try-with-resources pattern correctly. Fix any that don't.

## Phase 1: Audit MemoryStack Usage

- [ ] Verify all `MemoryStack.stackPush()` calls use try-with-resources
- [ ] Check for any `stackPush()` without corresponding `stackPop()` or auto-close
- [ ] Verify no stack-allocated buffers escape the try block scope

## Phase 2: Fix Any Issues Found

- [ ] Wrap any non-try-with-resources MemoryStack usage in proper blocks
- [ ] Ensure buffers allocated on the stack are only used within the try block

## Files to Modify

| File | Change |
|------|--------|
| `editor/core/EditorWindow.java` | Audit and fix MemoryStack patterns |
| Any other files using MemoryStack | Same audit |

## Testing Strategy

- Editor window creation and resize work correctly
- No stack overflow errors during extended use

## Code Review

- [ ] Verify no stack-allocated buffers are stored in fields or returned from methods
