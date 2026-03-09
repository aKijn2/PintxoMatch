# Food View X (PintxoMatch)

Food View X is an Android application built with Kotlin and Jetpack Compose to discover pintxos, rate them, write community reviews, and contact support in real time.

## Overview

The app combines food discovery, community feedback, and support operations in a single mobile product. Users can browse pintxos with swipe gestures, publish new entries, rate pintxos with stars, write one editable review per pintxo, compare rankings, and explore nearby places on an integrated map.

## Product Scope

The project is organized around three main product areas:

- Pintxo discovery through a swipe-based browsing experience.
- Community interaction through ratings and editable reviews.
- Real-time support tickets between users and admin.
- Nearby venue exploration with route handoff to Google Maps.

## Core Features

- Email and password authentication with Firebase Authentication.
- Swipe-based pintxo discovery feed backed by Cloud Firestore.
- Per-user star ratings with shared average score and review counts.
- Community reviews linked to previously rated pintxos.
- One review per user per pintxo with edit/update behavior.
- Pintxo publishing flow with image selection from camera or gallery.
- Image upload to Cloudinary with persisted external delivery URLs.
- Real-time support chat per user ticket using Firebase Realtime Database.
- Ticket state management: open/resolved/reopen.
- Support message deletion and full ticket deletion (with confirmations).
- Admin support inbox with all active threads.
- User profile with editable information and contribution stats.
- Ranking view for top contributors and best-rated pintxos.
- Nearby places screen with current location, map view, filters, and Google Maps routing.

## Interface Preview (OLD)

<p align="center">
  <img src="img/Screenshot_20260306_132808.png" alt="PintxoMatch screen 1" width="180" />
  <img src="img/Screenshot_20260306_132855.png" alt="PintxoMatch screen 2" width="180" />
  <img src="img/Screenshot_20260306_132903.png" alt="PintxoMatch screen 3" width="180" />
</p>

<p align="center">
  <img src="img/Screenshot_20260306_132926.png" alt="PintxoMatch screen 4" width="180" />
  <img src="img/Screenshot_20260306_133117.png" alt="PintxoMatch screen 5" width="180" />
  <img src="img/Screenshot_20260306_133124.png" alt="PintxoMatch screen 6" width="180" />
</p>

Additional screenshots remain available in the `img/` directory for extended reference.

## Technology Stack

- Android UI: Kotlin, Jetpack Compose, Navigation Compose, Material 3.
- Backend: Firebase Authentication, Cloud Firestore, Firebase Realtime Database.
- Media: Coil for image loading and Cloudinary for image hosting.
- Mapping: osmdroid with OpenStreetMap tiles.
- Build configuration: Gradle Kotlin DSL.

## Project Structure

- `app/src/main/java/com/example/pintxomatch/`: application source code.
- `app/src/main/res/`: Android resources.
- `app/google-services.json`: Firebase Android configuration.
- `img/`: screenshots used in the project documentation.
- `gradle/` and root Gradle files: dependency and build configuration.

## Data Model

### Cloud Firestore

Collection `Pintxos`:

- `nombre: String`
- `bar: String`
- `ubicacion: String`
- `precio: Double`
- `imageUrl: String`
- `timestamp: Long`
- `uploaderUid: String`
- `uploaderEmail: String`
- `ratings: Map<String, Int>`
- `ratingCount: Long`
- `ratingTotal: Double`
- `averageRating: Double`

Collection `Reviews`:

- `pintxoId: String`
- `pintxoName: String`
- `userUid: String`
- `userName: String`
- `stars: Int`
- `text: String`
- `createdAt: Long`

Review document key strategy:

- `Reviews/{uid}_{pintxoId}` to prevent duplicate reviews and support editing.

### Firebase Realtime Database

- `support_chats/{threadId}` where `threadId` is usually the user uid.
  - `meta`
    - `userUid`
    - `userEmail`
    - `userName`
    - `lastMessage`
    - `updatedAt`
    - `status` (`open` | `resolved`)
    - `resolvedBy`
    - `resolvedAt`
  - `messages/{messageId}`
    - `senderId`
    - `senderName`
    - `text`
    - `timestamp`

- `admins/{uid}: true`
  - Admin role map used by security rules.

## Support Model

- Each user has a dedicated support thread (`support_chats/{uid}`).
- Admin can access all support threads from the inbox.
- Both user and admin can mark a ticket as resolved or reopen it.
- Both user and admin can delete an entire ticket.
- User can delete own support messages; admin can delete any support message.

## Local Setup

### Requirements

- Android Studio.
- JDK 11 or newer.
- A configured Firebase project.
- Location permission enabled on the device or emulator to use the nearby places feature.

### Clone the Repository

```bash
git clone <YOUR_REPOSITORY_URL>
cd PintxoMatch
```

### Firebase Configuration

1. Create a Firebase project.
2. Register an Android application with package name `com.example.pintxomatch`.
3. Download `google-services.json`.
4. Place the file in `app/google-services.json`.

`google-services.json` should remain excluded from version control.

### Cloudinary Configuration

The project uploads pintxo images to Cloudinary and stores the resulting external URL in Firestore as `imageUrl`.

Current values used by the app:

- `cloudName`: `dm99kc8ky`
- `uploadPreset`: `pintxomatch`
- upload folder: `pintxomatch`

If you move to a different Cloudinary account, update these values in the upload flow before building the app.

### Required Firebase Services

- Authentication with Email/Password.
- Cloud Firestore.
- Firebase Realtime Database.

## Image Handling

- Pintxo images can be selected from the gallery or captured with the device camera.
- The image file is uploaded directly to Cloudinary from the app.
- Firestore stores only the resulting public URL in the `imageUrl` field.
- The app does not rely on Firebase Storage for pintxo media.
- Profile images also use the same Cloudinary-based upload approach.

## Build and Run

```bash
./gradlew :app:assembleDebug
```

You can also run the project directly from Android Studio.

## Recommended Realtime Database Rules

```json
{
  "rules": {
    "admins": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === '3rR1Cwqv2Ccvyw9OU6s8Oxu1AJV2' && $uid === '3rR1Cwqv2Ccvyw9OU6s8Oxu1AJV2'",
        ".validate": "newData.val() === true && $uid === '3rR1Cwqv2Ccvyw9OU6s8Oxu1AJV2'"
      }
    },
    "waitingByPintxo": {
      "$pintxoId": {
        "$uid": {
          ".read": "auth != null && auth.uid === $uid",
          ".write": "auth != null && auth.uid === $uid"
        }
      }
    },
    "support_chats": {
      ".read": "auth != null && root.child('admins').child(auth.uid).val() === true",
      "$threadId": {
        ".read": "auth != null && (auth.uid === $threadId || root.child('admins').child(auth.uid).val() === true)",
        ".write": "auth != null && (auth.uid === $threadId || root.child('admins').child(auth.uid).val() === true)",
        "messages": {
          "$messageId": {
            ".read": "auth != null && (auth.uid === $threadId || root.child('admins').child(auth.uid).val() === true)",
            ".write": "auth != null && (auth.uid === $threadId || root.child('admins').child(auth.uid).val() === true) && (newData.val() === null || (newData.hasChildren(['senderId','senderName','text','timestamp']) && newData.child('senderId').val() === auth.uid))",
            ".validate": "newData.val() === null || newData.hasChildren(['senderId','senderName','text','timestamp'])"
          }
        }
      }
    }
  }
}
```

Use `database.rules.json` in this repository as the source of truth for RTDB rules.

## Notes

- Older `Pintxos` documents without `uploaderUid` do not count toward user contribution statistics.
- The app currently forces a light theme for visual consistency across emulator and physical devices.
- The image section is intentionally left as-is and can be updated separately.
