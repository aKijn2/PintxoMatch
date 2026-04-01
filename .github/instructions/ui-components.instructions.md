---
description: Use when creating or refactoring Jetpack Compose UI for Food View X, especially premium animations, overlays, and red brand styling.
---

# UI Components Standards

## Visual Language

- Keep the corporate palette consistent: red surfaces, black focus layers, white text.
- Avoid random neutral blocks that break the red-first visual identity.
- Use strong contrast in critical overlays with black alpha between 0.75 and 0.85.

## Typography and Readability

- Primary text on red or black surfaces must be Color.White.
- Use bold titles for achievement moments and key profile metrics.
- For animated overlays, apply subtle shadow on white text when background content may interfere.

## Motion Rules

- For interactive UI motion, prefer spring-based animations.
- For choreographed sequences (achievement moments), use tween with explicit phases:
  1) Overlay darken
  2) Hero element entrance
  3) Supporting text entrance
  4) Unified fade out
- Avoid abrupt state jumps unless the interaction requires instant feedback.

## Overlay and Focus Patterns

- For fullscreen achievement overlays, use a Dialog with usePlatformDefaultWidth = false.
- Overlay must fill the entire viewport before hero content enters.
- Hero content should remain visible for at least ~2 seconds to allow user recognition.

## Compose Implementation Notes

- Keep composables stateless when possible and hoist business state to ViewModel.
- Use stable testTag values on critical animated nodes for androidTest validation.
- Any debug replay trigger should be temporary and gated for debug usage only.
