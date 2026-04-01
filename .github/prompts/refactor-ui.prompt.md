---
description: Refactor legacy Food View X Compose screens into premium red/black/animated UI while preserving behavior.
---

# UI Refactor Premium Mode

Refactor existing Food View X UI code to the premium design language without breaking behavior.

## Refactor Goals

- Preserve functional behavior and navigation.
- Upgrade visuals to red-first corporate style.
- Improve readability with strong contrast and white typography.
- Add smooth, intentional motion (spring for interactions, tween for sequences).

## Refactor Process

1. Analyze current UI
- Identify visual inconsistencies and readability issues.
- Identify state leaks and non-hoisted state that should move to ViewModel.

2. Apply visual system
- Red containers and accents as primary visual base.
- Black overlays for focus moments.
- White text for primary content over red or dark surfaces.

3. Improve interaction design
- Replace abrupt transitions with spring/tween.
- Add clear loading and empty states.
- Keep overlays fully immersive when needed.

4. Keep code maintainable
- Extract reusable composables.
- Remove duplicated modifiers and style fragments.
- Add minimal comments only where logic is non-obvious.

5. Validate
- Ensure screen parity with existing behavior.
- Run compile and relevant tests.
- Add or update Compose testTags for critical nodes.

## Output Format

- Before/after summary
- Updated composables and style decisions
- Behavior parity checks
- Tests or checks executed
