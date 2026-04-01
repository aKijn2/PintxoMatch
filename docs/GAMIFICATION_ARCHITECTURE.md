# Food View X - Gamification Architecture

## Objective
Provide a retention layer that increases weekly return sessions through:

- XP progression and levels.
- Daily streak tracking.
- Weekly challenges.
- Badge unlock rewards.

## Layered Design

### Data Layer
Files:
- app/src/main/java/com/example/pintxomatch/data/model/UserGamification.kt
- app/src/main/java/com/example/pintxomatch/data/model/WeeklyChallenge.kt
- app/src/main/java/com/example/pintxomatch/data/repository/GamificationRepository.kt

Responsibilities:
- Read and write gamification fields under Users.
- Keep weekly challenge definitions.
- Update XP, streak and challenge progress in a Firestore transaction.

### Domain Layer
File:
- app/src/main/java/com/example/pintxomatch/domain/gamification/GamificationRules.kt

Responsibilities:
- Calculate level and progress from XP.
- Calculate streak transitions based on last action timestamp.

### UI Layer
Files:
- app/src/main/java/com/example/pintxomatch/ui/viewmodel/GamificationViewModel.kt
- app/src/main/java/com/example/pintxomatch/ui/components/GamificationComponents.kt
- app/src/main/java/com/example/pintxomatch/ui/components/BadgeUnlockedPopup.kt

Responsibilities:
- Expose StateFlow state for profile gamification.
- Render profile gamification section and weekly challenge cards.
- Render premium popup when a new badge is unlocked.

## Firestore Data Model

### Users/{uid}
Fields:
- xp: Int
- currentStreak: Int
- lastActionTimestamp: Long
- badges: List<String>

### WeeklyChallenges/{challengeId}
Fields:
- weekId: String
- title: String
- description: String
- actionType: String (RATE_PINTXO | UPLOAD_PINTXO)
- targetCount: Int
- badgeId: String
- startsAt: Long
- endsAt: Long
- isActive: Boolean

### Users/{uid}/WeeklyChallengeProgress/{challengeId}
Fields:
- challengeId: String
- title: String
- description: String
- badgeId: String
- targetCount: Int
- progressCount: Int
- completed: Boolean
- weekId: String
- lastUpdatedAt: Long
- completedAt: Long (optional)

## Transaction Flow

When user performs a tracked action:

1. Identify action type and XP reward.
2. Read user gamification state in transaction.
3. Read matching active weekly challenges.
4. Read progress docs for matching challenges.
5. Compute updated XP, streak, progress and new badges.
6. Write challenge progress docs.
7. Write user doc with new XP/streak/badges.

This makes XP and badge unlocks atomic and race-safe.

## Current Rewards

- RATE_PINTXO: +10 XP
- UPLOAD_PINTXO: +50 XP
- Level formula: level = floor(xp / 100) + 1

## Trigger Points in UI

- HomeReviewScreen rating success -> award RATE_PINTXO.
- MainSwipeScreen rating success -> award RATE_PINTXO.
- UploadPintxoScreen publish success -> award UPLOAD_PINTXO.

If unlockedBadges is not empty, premium popup is shown.

## Testing Coverage

### Unit
- GamificationRulesTest validates level and streak edge cases.

### Integration-style (Repository)
- GamificationRepositoryComputationTest validates transaction computation logic:
  - challenge completion unlocks badge
  - no duplicate badge when already completed

### ViewModel
- GamificationViewModelTest validates StateFlow loading, error handling and XP refresh after action.

### UI Compose
- GamificationComponentsTest validates render of profile section and challenge progress card.
