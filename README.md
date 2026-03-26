# Food View X (PintxoMatch)

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Latest-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-Full_Stack-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Material 3](https://img.shields.io/badge/Material_3-Modern-757575?style=for-the-badge&logo=materialdesign&logoColor=white)](https://m3.material.io/)

**Food View X** is a Android application designed for food enthusiasts. Discover, rate, and share the best culinary treasures from the planet through a modern, swipe-based interface and a gamified community experience.

---

## Key Features

### Swipe-to-Match Discovery
Browse a curated feed of pintxos with smooth, fluid animations. Swipe right to "Match" (Save/Favorite) or left to "Pass". Discover your next meal in a fun, intuitive way.

### Gamified Community Profile
Your profile is more than just settings. Inspired by premium gaming platforms, it features:
- **Pintxo XP & Levels:** Earn experience points for every contribution.
- **Achievement Showcase:** Unlock unique badges like "Crítico", "Estrella", or "Leyenda" as you explore.
- **Dynamic Headers:** Beautiful, blurred banners that reflect your personality.

### Nearby Exploration
Integrated map experience powered by **OSM** and **Google Maps Routing**.
- Find bars and pintxos around your current location.
- Category filters to find exactly what you're craving.
- One-tap route handoff to get you there fast.

### Community & Support
- **Reviews:** Share your thoughts and photos of every bite. Editable, verified reviews ensure quality.
- **Real-time Support:** Dedicated chat thread with admins for seamless assistance and feedback.
- **Leaderboards:** See who the top contributors are in the community.

---

## Technology Stack

| Category | Technology |
| :--- | :--- |
| **Android UI** | Jetpack Compose, Material 3, Navigation Compose |
| **Backend** | Firebase Auth, Cloud Firestore, Realtime Database |
| **Media & Images** | Local Docker server (dev) / Cloudinary (prod), Coil (loading) |
| **Navigation & Maps** | osmdroid (OpenStreetMap), Google Maps Intent Routing |
| **Architectures** | MVVM, Coroutines, Flow |

---

## Interface Preview

<p align="center">
  <img src="img/home.jpeg" alt="PintxoMatch screen 1" width="220" />
  <img src="img/profile.jpeg" alt="PintxoMatch screen 2" width="220" />
  <img src="img/settings.jpeg" alt="PintxoMatch screen 3" width="220" />
</p>

---

## Local Setup

### Prerequisites
- **Android Studio** (Hedgehog or newer recommended)
- **Firebase Project:** Set up at [Firebase Console](https://console.firebase.google.com/).
- **Docker Desktop:** Required for the local image server (development mode).

### Quick Start
1. **Clone & Open:**
   ```bash
   git clone https://github.com/mrasa/PintxoMatch.git
   ```
2. **Firebase Setup:**
   - Download `google-services.json` from your Firebase project.
   - Place it in the `app/` directory.
3. **Permissions:**
   - Ensure Location and Camera permissions are granted on your device/emulator.

### Image Storage: Local Docker Server (Development)

The app currently uses a local Docker container for image hosting during development.

**Every dev session, run these two commands:**

```bash
# 1. Start the image server
docker compose -f docker-compose.image-server.yml up -d

# 2. Tunnel the emulator to your host (run while emulator is open)
adb reverse tcp:8080 tcp:8080
```

Verify the server is up:
```bash
# PowerShell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/health"
```

`app/build.gradle.kts` is already configured with `IMAGE_PROVIDER = "local"`. Just rebuild and run.

Uploaded files are persisted in `docker/image-server/data/uploads/`.

> See [docs/CHANGES.md](docs/CHANGES.md) for a full explanation of the image system architecture and all code changes.

### Switching to Cloudinary (Production)

In `app/build.gradle.kts`, change:
```kotlin
buildConfigField("String", "IMAGE_PROVIDER", "\"cloudinary\"")
```

---

## Data Model (Simplified)

### Firestore `Pintxos`
```json
{
  "nombre": "Gilda Premium",
  "bar": "Bar Txepetxa",
  "precio": 3.50,
  "averageRating": 4.8,
  "uploaderUid": "user_123",
  "imageUrl": "http://localhost:8080/uploads/img_xxx.jpg",
  "imagePublicId": "img_xxx",
  "imageDeleteToken": "img_xxx",
  "imageDeleteTokenCreatedAt": 1710000000000
}
```

### Realtime Database `support_chats`
```json
{
  "threadId": {
    "meta": { "status": "open", "lastMessage": "..." },
    "messages": { ... }
  }
}
```

---

## License & Notes
- This project is part of a culinary exploration initiative.
- **Light Theme Only:** The UI is currently optimized for Light Theme to ensure visual consistency in high-contrast environments.

---

*Developed with ❤️ for the food community. Bon Profit!*
