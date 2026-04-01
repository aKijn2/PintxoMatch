---
description: Use when generating, reviewing, or modifying code for the Food View X Android app, especially Jetpack Compose UI, vertical feeds, and Firebase integration.
---

# Food View X - Project Context & Guidelines

**Project Context:**
* **App Name:** Food View X (formerly PintxoMatch)
* **Concept:** A TikTok-style vertical scrolling feed for food enthusiasts to share images and videos of food.
* **Tech Stack:** Android, Kotlin, Jetpack Compose, Firebase (Firestore, Realtime DB, Auth), Coil for media loading.

**UI/UX Guidelines:**
* Always use **Jetpack Compose**. Do not use XML layouts.
* The main feed must use `VerticalPager` for a full-screen, infinite-scroll experience.
* Backgrounds for media screens should be pure `Color.Black` to make content pop.
* Overlay UI elements (Profile, Like, Chat) should float on top of the media, stacked vertically on the bottom-right side (TikTok style).
* Use sleek, physics-based animations (like `spring()`) for interactions, popups, and notifications. 
* Avoid clunky, default Material backgrounds when building immersive media screens.

**Architecture Rules:**
* Use `ViewModel` and `StateFlow` (e.g., `MutableStateFlow`, `asStateFlow()`) for state management.
* Keep composables stateless where possible by hoisting state to the ViewModel.
* Use Kotlin Coroutines (`viewModelScope.launch`) for asynchronous Firebase calls.