# Proposed CLAUDE.md Addition: Worktree Workflow for Plan Implementation

> This would be added under "Planning Mode Instructions" section

---

## Worktree Workflow for Plan Implementation

When starting to implement a plan, **ask the user** if they want to use the worktree workflow:

```
Before starting implementation:
- Use worktree workflow? (isolated branch, PR review per phase)
- Or implement directly on main?
```

Worktrees are recommended for large plans (multiple phases, many files) but not mandatory.

### Setup (One-time)

Add `.worktrees/` to `.gitignore`:
```
.worktrees/
```

### Workflow Steps

**1. Create Worktree**
```bash
# Create .worktrees directory if needed
mkdir -p .worktrees

# Create worktree with feature branch
git worktree add .worktrees/<feature-name> -b <feature-branch>

# Initialize build in worktree
cd .worktrees/<feature-name> && mvn compile
```

**2. Implement in Worktree**
- Use paths relative to worktree: `.worktrees/<feature-name>/src/...`
- Set `path` parameter on Glob/Grep/Read to `.worktrees/<feature-name>`
- Run tests in worktree: `cd .worktrees/<feature-name> && mvn test`

**3. Phase-by-Phase Review**

Plans are divided into phases. After completing each phase:

```bash
cd .worktrees/<feature-name> && git add -A && git commit -m "Phase N: <description>"
cd .worktrees/<feature-name> && git push -u origin <feature-branch>
```

**After Phase 1**, create the PR:
```bash
gh pr create --title "<feature-name>" --body "Phase 1: <description>"
```

**After subsequent phases**, just push (PR updates automatically):
```bash
cd .worktrees/<feature-name> && git add -A && git commit -m "Phase N: <description>"
cd .worktrees/<feature-name> && git push
```

After each phase, provide the user with:

```
Phase N complete.

PR: <github PR URL>
Review this phase: <github PR URL>/commits  (see latest commit)

# Test commands
cd .worktrees/<feature-name> && mvn test
cd .worktrees/<feature-name> && mvn compile exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication"
```

Wait for user approval before starting next phase.

**4. Merge and Cleanup**

After all phases approved:
```bash
# Close PR (was just for review)
gh pr close <pr-number>

# Merge locally
git checkout main
git merge <feature-branch>
git push

# Cleanup
git worktree remove .worktrees/<feature-name>
git branch -d <feature-branch>
```

### When to Use Worktrees

| Scenario | Use Worktree? |
|----------|---------------|
| Implementing a full plan | Yes |
| Multi-file refactoring | Yes |
| Risky/experimental changes | Yes |
| Small bug fix (1-2 files) | No |
| Documentation-only changes | No |

### Naming Convention

- Worktree directory: `.worktrees/<feature-name>` (inside repo, gitignored)
- Branch name: `<feature-name>` (kebab-case, matching plan folder name)

Example for audio system:
- Worktree: `.worktrees/audio-system`
- Branch: `audio-system`

### Rollback

If implementation fails validation:
```bash
# Discard worktree without merging
git worktree remove .worktrees/<feature-name> --force
git branch -D <feature-branch>
```

### Important Notes

- Always confirm with user before merging to main
- Run full test suite before merge: `mvn test`
- Worktrees are inside repo but gitignored (same permissions, visible in IDE)
- User can test directly in worktree folder
