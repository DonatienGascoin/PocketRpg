# Scene View Play Mode Support

How the scene viewport should behave during play mode.

## Documents

- `implementation-plan.md` - Full implementation plan with two options

## Options

| Option | Description | Complexity |
|--------|-------------|------------|
| A | Scene view shows runtime entities, supports selection | High |
| B | Scene view disabled (greyed out) during play mode | Low |

## Recommendation

Option B (Disabled) recommended for initial implementation due to simplicity.

## Dependencies

- Requires `play-mode-inspection` plan for `PlayModeController` and `PlayModeSelectionManager`

## Status

- [ ] Plan reviewed
- [ ] Option selected
- [ ] Implementation started
- [ ] Implementation complete
