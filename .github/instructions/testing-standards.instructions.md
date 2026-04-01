---
description: Use when writing or reviewing tests in Food View X, especially ViewModel StateFlow tests and Compose UI tests.
---

# Testing Standards

## Test Scope by Folder

- Use app/src/test for JVM unit tests:
  - domain rules
  - repository pure logic
  - ViewModel StateFlow behavior
- Use app/src/androidTest for instrumentation tests:
  - Compose rendering
  - semantic assertions
  - visual behavior wiring

## ViewModel + StateFlow

- Use kotlinx-coroutines-test with deterministic dispatcher control.
- Assert state transitions, not only final values.
- Cover success, error, and reload/update paths.
- Do not rely on real Firebase in ViewModel unit tests; use fakes.

## Compose UI Tests

- Add stable testTag values for important nodes.
- Validate critical text and progress indicators.
- Keep UI tests behavior-focused; avoid brittle pixel assumptions.

## Naming and Structure

- Test names should describe behavior clearly:
  given_when_then style or equivalent readable style.
- Follow Arrange / Act / Assert sections.
- Keep each test focused on one behavior.

## Quality Gate Expectations

- New feature work should include at least:
  - one unit test for business logic
  - one ViewModel state test when state changes are introduced
  - one Compose test for new critical UI component when feasible
