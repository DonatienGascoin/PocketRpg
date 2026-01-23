# Plan Design Guidelines

How to create and structure implementation plans.

## Plan Location

- `Documents/Plans/` - All implementation plans
- Use subfolders for multi-file plans: `Documents/Plans/<feature-name>/`
- Single file OK for simple fixes: `Documents/Plans/small-fix.md`

## Folder Organization

```
Documents/Plans/
├── editor-modes-refactor/        # Subfolder for related files
│   ├── analysis.md
│   ├── implementation-plan.md
│   └── review.md
├── save-system/
│   ├── architecture.md
│   └── api-design.md
└── small-fix.md                  # Single file for simple fixes
```

**Rules:**
1. If a plan grows beyond one file, move it to a subfolder
2. Name subfolders with kebab-case matching the feature name
3. Keep related analysis, implementation, and review docs together

## Plan Structure

A good plan should include:

### 1. Overview
- What problem does this solve?
- High-level approach

### 2. Phases
Break work into reviewable chunks:
```markdown
## Phase 1: Core Infrastructure
- [ ] Task 1
- [ ] Task 2

## Phase 2: Integration
- [ ] Task 3
- [ ] Task 4
```

### 3. Files to Change
List files that will be modified/created:
```markdown
## Files to Modify
| File | Change |
|------|--------|
| `Foo.java` | Add new method |
| `Bar.java` | **NEW** - New class |
```

### 4. Testing Strategy
How to verify the implementation works.

### 5. Code Review Step
Every plan must end with a code review task.

## Integrate with Existing Systems

Before creating new infrastructure, check:

| System | Location | Use For |
|--------|----------|---------|
| **Undo/Redo** | `editor/undo/` | Editor state changes |
| **Assets** | `resources/Assets.java` | Loading, caching assets |
| **AssetLoader** | `resources/AssetLoader.java` | New asset types |
| **Shortcuts** | `editor/shortcut/` | Keyboard shortcuts |
| **ComponentRef** | `serialization/ComponentRef.java` | Component dependencies |
| **Inspector** | `editor/panels/InspectorPanel.java` | Property editing |
| **ThumbnailCache** | `editor/assets/ThumbnailCache.java` | Asset previews |
| **StatusBar** | `editor/ui/StatusBar.java` | User feedback |

**Ask yourself:**
1. Does a similar system already exist?
2. Can an existing system be extended?
3. Does this follow patterns used elsewhere?

## Exiting Plan Mode

When exiting plan mode, provide a summary:

```
Plan updated in "Documents/Plans/feature-name/implementation-plan.md"

**Things to change:**

1. **Foo.java**
   Add new method for X

2. **Bar.java** (NEW)
   New class to handle Y
```

## During Implementation

**Update the plan as you go:**
- Mark tasks completed with `[x]` or ~~strikethrough~~
- Note any deviations or issues discovered
- Keep the plan as a living document

**After implementation:**
1. Mark the plan as complete
2. Check if CLAUDE.md sections need updating
3. Document new systems/patterns introduced
