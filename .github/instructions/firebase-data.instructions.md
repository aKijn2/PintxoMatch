---
description: Use when implementing Firebase data logic in Food View X, especially Firestore transactions for XP, streaks, badges, and Realtime Database chat efficiency.
---

# Firebase Data Standards

## Firestore Transaction Safety

- Use runTransaction for any multi-field consistency update:
  - XP increments
  - streak updates
  - challenge progress
  - badge unlock writes
- Read all needed documents inside the transaction and write once per document.
- Ensure idempotency for badges: never add duplicates.

## Gamification Consistency

- Keep all reward updates atomic in one transaction.
- Clamp progress to targetCount to avoid overshoot.
- Track completed and completedAt to avoid repeated unlocks.
- Update lastActionTimestamp with the same timeline used for streak evaluation.

## Data Modeling Guidelines

- Keep user gamification state under Users/{uid}.
- Keep challenge definitions centralized under WeeklyChallenges.
- Keep per-user challenge progress in Users/{uid}/WeeklyChallengeProgress.
- Use explicit field names; avoid implicit map nesting for critical counters.

## Realtime Database Chat Efficiency

- Subscribe to narrow paths only (thread-level, not broad global listeners).
- Use limitToLast for message streams where possible.
- Detach listeners in lifecycle-aware blocks (DisposableEffect or ViewModel clear paths).
- Avoid large fan-out reads in composables.

## Error Handling

- Gamification reward writes should be resilient and not block core UX actions.
- Surface user-safe fallback messages; log detailed technical errors separately.
- Prefer returning structured result objects over boolean-only responses.
