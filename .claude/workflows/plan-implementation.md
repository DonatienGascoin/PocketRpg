# Plan Implementation Workflow

How to implement plans, with optional worktree isolation and phase-by-phase review.

## Before Starting

**Ask the user:**
```
Before starting implementation:
- Use worktree workflow? (isolated branch, PR review per phase)
- Or implement directly on main?
```

Worktrees are recommended for large plans (multiple phases, many files) but not mandatory.

---

## Option A: Worktree Workflow (Recommended for Large Plans)

### 1. Create Worktree

```bash
mkdir -p .worktrees
git worktree add .worktrees/<feature-name> -b <feature-branch>
cd .worktrees/<feature-name> && mvn compile
```

### 2. Implement in Worktree

- Use paths: `.worktrees/<feature-name>/src/...`
- Set `path` parameter on Glob/Grep/Read to `.worktrees/<feature-name>`
- Run tests: `cd .worktrees/<feature-name> && mvn test`

### 3. Phase-by-Phase Review

After completing each phase, commit and push:

```bash
cd .worktrees/<feature-name> && git add -A && git commit -m "Phase N: <description>"
cd .worktrees/<feature-name> && git push -u origin <feature-branch>
```

**After Phase 1**, create the PR:
```bash
gh pr create --title "<feature-name>" --body "Phase 1: <description>"
```

**After subsequent phases**, just push (PR updates automatically).

**After each phase, tell the user:**
```
Phase N complete.

PR: <github PR URL>
Review this phase: <github PR URL>/commits (see latest commit)

# Test commands
cd .worktrees/<feature-name> && mvn test
cd .worktrees/<feature-name> && mvn compile exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication"
cd .worktrees/<feature-name> && mvn compile exec:java -Dexec.mainClass="com.pocket.rpg.Main"
```

**Wait for user approval before starting next phase.**

### 4. Merge and Cleanup

After all phases approved:
```bash
# Rebase onto latest main and merge PR
cd .worktrees/<feature-name> && git fetch origin main && git rebase origin/main
cd .worktrees/<feature-name> && git push --force-with-lease
gh pr merge <pr-number> --merge

# Pull merged changes to main
git checkout main && git pull origin main

# Full cleanup: worktree, local branch, remote branch, PR
# IMPORTANT: Remind user to close any running editor/game instance
# from the worktree before removing it, as open file handles will
# cause the removal to fail.
git worktree remove .worktrees/<feature-name>
git branch -d <feature-branch>
git push origin --delete <feature-branch>
```

> **Note:** `gh pr merge` automatically closes the PR. If abandoning a PR
> without merging, close it explicitly with `gh pr close <pr-number>`.

### Rollback (If Needed)

```bash
git worktree remove .worktrees/<feature-name> --force
git branch -D <feature-branch>
git push origin --delete <feature-branch>
gh pr close <pr-number>
```

---

## Option B: Direct Implementation (Simple Changes)

For small fixes (1-2 files), implement directly on main without worktree.

---

## When to Use Worktrees

| Scenario | Use Worktree? |
|----------|---------------|
| Implementing a full plan | Yes |
| Multi-file refactoring | Yes |
| Risky/experimental changes | Yes |
| Small bug fix (1-2 files) | No |
| Documentation-only changes | No |

## Naming Convention

- Worktree: `.worktrees/<feature-name>` (gitignored)
- Branch: `<feature-name>` (kebab-case, matching plan folder)

Example:
- Worktree: `.worktrees/audio-system`
- Branch: `audio-system`
