---
description: Scaffold a new Food View X feature using the required order Model -> ViewModel -> UI with testing and rollout checks.
---

# New Feature Blueprint

You are implementing a new feature for Food View X.

## Inputs

- Feature goal:
- User flow summary:
- Firebase data impact:
- UI constraints (red/black/white theme, animation expectations):
- Definition of done:

## Required Implementation Order

1. Model and Data Layer
- Define or extend data models.
- Define Firestore/Realtime DB schema changes if needed.
- Identify migration or backward-compat notes.

2. Repository and Domain Rules
- Add repository methods and transaction safety where needed.
- Keep business rules deterministic and testable.

3. ViewModel and StateFlow
- Expose UI state through StateFlow.
- Define loading, success, and error states.
- Keep side effects isolated.

4. UI in Jetpack Compose
- Implement premium visual style aligned with Food View X.
- Keep composables mostly stateless and hoist state.
- Add meaningful motion (spring/tween as needed).

5. Testing
- Add JVM tests for domain/repository logic.
- Add ViewModel StateFlow tests.
- Add Compose UI tests for critical nodes.

6. Documentation and Verification
- Update README or docs when architecture or flows change.
- Provide test/run commands and a short verification checklist.

## Output Format

- Architecture notes
- Data model changes
- Code changes by layer
- Tests added
- Manual QA checklist
