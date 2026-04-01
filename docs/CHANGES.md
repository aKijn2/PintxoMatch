# PintxoMatch — Image System: Architecture & Change Log

---

## 2026-04-01 — Retención y Gamificación básica (completado)

Se ha implementado un primer sistema de retención para aumentar recurrencia semanal:

- XP por acción (valorar y subir pintxos) con transacción segura en Firestore.
- Racha diaria (`currentStreak`) calculada por última acción (`lastActionTimestamp`).
- Retos semanales activos con progreso por usuario.
- Desbloqueo de badges al completar retos.
- Nueva sección de perfil gamificada (nivel, XP, racha y badges).
- Tarjetas de retos semanales con progreso animado.
- Popup premium animado al desbloquear badge (escala + glow sobre fondo oscuro).
- Suite de tests añadida: reglas de dominio, integración lógica de repository, StateFlow en ViewModel y render Compose.

Documentación y DX:

- README renovado con arquitectura, setup y capa de gamificación.
- Nueva documentación técnica de arquitectura para modelo Firebase de gamificación.

This document explains the full history of changes made to solve the image management problem in PintxoMatch (old images not being deleted when a pintxo is updated) and the resulting image storage architecture.

---

## The Original Problem

When a user edited a pintxo and selected a new photo, the app would upload the new image to Cloudinary and update Firestore with the new URL — but the **old Cloudinary image was never deleted**. Over time this creates orphaned assets and wastes storage quota.

---

## Why Cloudinary Didn't Auto-Delete

The Cloudinary preset used (`pintxomatch`) is an **Unsigned upload preset**. Cloudinary enforces a security rule: unsigned presets cannot have `overwrite: true`. This means:

- Every upload creates a **new asset** with a new unique public ID.
- There is no way to replace/overwrite from the client side without a signed upload.
- Signed uploads require an `api_secret`, which **must never be in an Android APK** (it would be trivially extractable).

### Approaches Considered and Discarded

| Approach | Why rejected |
|---|---|
| Enable `overwrite: true` on unsigned preset | Cloudinary blocks this — it is a hard constraint |
| Use Cloudinary's `delete_by_token` API | Works within 10 minutes of upload only; unreliable for edits done later |
| Firebase Cloud Functions with `api_secret` | Requires Firebase Blaze (pay-as-you-go) plan; project is on Spark (free) |

### Decision

Use a **local Docker image server** during development. It gives full control over uploads and deletes, costs nothing, and can be swapped for Cloudinary (or any other provider) in production by changing a single `buildConfigField`.

---

## Architecture Overview

```
Android App
    │
    ▼
ImageRepository
    │
    ├── IMAGE_PROVIDER == "local"  ──▶  Docker server (localhost:8080 via adb reverse)
    │                                      POST /api/images     → upload
    │                                      DELETE /api/images/:id → delete
    │                                      GET /uploads/*        → serve image
    │
    └── IMAGE_PROVIDER == "cloudinary" ──▶ Cloudinary HTTP API (existing flow)
```

---

## Files Created

### `docker/image-server/server.js`
Node.js + Express HTTP server that handles image storage.

**Endpoints:**

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/health` | None | Returns `{ ok: true }` |
| `POST` | `/api/images` | `x-api-key` | Accepts `multipart/form-data` with a `file` field. Returns `{ imageId, fileName, url, contentType }` |
| `DELETE` | `/api/images/:imageId` | `x-api-key` | Deletes the file matching the given `imageId` prefix |
| `GET` | `/uploads/*` | None | Serves static uploaded files |

**Storage:** Files are written to `/data/uploads/` inside the container (mapped to `docker/image-server/data/uploads/` on host).

**Image ID format:** `img_{timestamp}_{uuid}` — guarantees uniqueness.

**API key auth:** The server checks the `x-api-key` request header against the `IMAGE_SERVER_API_KEY` environment variable. If the env var is empty, auth is skipped (permissive fallback).

---

### `docker/image-server/Dockerfile`
Builds a Node.js 20 Alpine image. Runs `npm ci --omit=dev` so only production dependencies are installed.

---

### `docker/image-server/package.json`
Dependencies: `express`, `cors`, `multer`, `uuid`.

---

### `docker-compose.image-server.yml`
Defines the `pintxomatch-image-server` service.

Key configuration:
```yaml
environment:
  PORT: 8080
  STORAGE_ROOT: /data
  PUBLIC_BASE_URL: http://localhost:8080   # Must match what the emulator resolves
  IMAGE_SERVER_API_KEY: pintxomatch-local-dev-key
ports:
  - "8080:8080"
volumes:
  - ./docker/image-server/data:/data       # Persists uploaded files on host
```

---

### `app/src/main/res/xml/network_security_config.xml`
Android blocks plaintext HTTP by default (since API 28). This file explicitly permits cleartext traffic to the local dev server only:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain>10.0.2.2</domain>
        <domain>localhost</domain>
    </domain-config>
</network-security-config>
```

---

## Files Modified

### `app/build.gradle.kts`

Added inside `defaultConfig`:
```kotlin
buildConfigField("String", "IMAGE_PROVIDER", "\"local\"")
buildConfigField("String", "LOCAL_IMAGE_BASE_URL", "\"http://localhost:8080\"")
buildConfigField("String", "LOCAL_IMAGE_API_KEY", "\"pintxomatch-local-dev-key\"")
```

Added inside `buildFeatures`:
```kotlin
buildConfig = true
```
(Required for `BuildConfig.*` fields to be generated.)

---

### `app/src/main/AndroidManifest.xml`

Added to `<application>` tag:
```xml
android:networkSecurityConfig="@xml/network_security_config"
```

---

### `app/src/main/java/.../data/repository/ImageRepository.kt`

**New methods added:**

- `isUsingLocalProvider()` — returns `true` when `BuildConfig.IMAGE_PROVIDER == "local"`.
- `uploadToLocalServer(payload)` — posts a multipart request to `/api/images`, parses the JSON response, and returns an `ImageUploadResult` where `deleteToken = imageId`.
- `deleteLocalImageById(imageId)` — sends `DELETE /api/images/{imageId}` to remove the file.
- `buildLocalUploadErrorMessage(...)` — extracts human-readable error from server JSON response.

**Modified methods:**

- `uploadImageAttempt()` — now checks `isUsingLocalProvider()` first. If `true`, delegates to `uploadToLocalServer()` wrapped in `withContext(Dispatchers.IO)` (network must not run on the main thread). Otherwise falls through to the existing Cloudinary path.
- `uploadBytesInternal()` / `uploadBytes()` — received new parameters `preferredPublicId` and `requireOverwriteWhenPreferredPublicId` for optional Cloudinary overwrite logic (currently `false`).
- `extractPublicIdFromUrl()` — now handles both Cloudinary URLs (contains `/image/upload/`) and local server URLs (falls back to extracting the filename stem from the last path segment).
- `canDeleteByToken()` — removed the 10-minute freshness check; now returns `true` as long as `deleteToken` is not blank (local server tokens don't expire).

---

### `app/src/main/java/.../data/repository/PintxoRepository.kt`

- Passes `preferredPublicId = previousPublicId` to `uploadImageAttempt()` so the new upload can optionally overwrite the same slot.
- Detects `imageReplacedInPlace` — true when the new upload's `publicId` matches the previous one (relevant for Cloudinary overwrite; always false in local mode).
- Only enqueues `CloudinaryDeletionQueue` when the image was **not** replaced in-place and provider is Cloudinary.
- Better success/error messages surfaced to the UI.

---

## Networking: Why `adb reverse` Is Needed

The Android Emulator's `10.0.2.2` alias for the host machine works in the classic QEMU-based emulator. However, newer emulators running under **Hyper-V** (Google's `emulator -engine` Hyper-V backend) can have routing issues with that alias.

`adb reverse tcp:8080 tcp:8080` creates a **reverse tunnel**:
- The emulator treats its own `localhost:8080` as a tunnel endpoint.
- Traffic is forwarded through ADB to `localhost:8080` on the host machine.
- This bypasses all network routing and firewall issues.

**This tunnel must be re-run each time the emulator restarts:**
```bash
adb reverse tcp:8080 tcp:8080
```

A Windows Firewall inbound rule was also added to allow TCP on port 8080 (required even with the tunnel, because Docker's port binding goes through the Windows network stack):
```powershell
New-NetFirewallRule -DisplayName "PintxoMatch Image Server (8080)" `
  -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Any
```

---

## How to Switch Back to Cloudinary

In `app/build.gradle.kts`, change:
```kotlin
buildConfigField("String", "IMAGE_PROVIDER", "\"cloudinary\"")
```

No other code changes are needed — `ImageRepository` branches on this value at runtime.

For reliable deletion on Cloudinary, the long-term solution is a signed upload backend (e.g., a small Node.js server or Firebase Function on Blaze plan) that holds the `api_secret` and handles `destroy` calls when a pintxo image is replaced.

---

## Quick Reference: Dev Session Checklist

```bash
# Terminal 1 — start image server (only needed once, persists across reboots via restart: unless-stopped)
docker compose -f docker-compose.image-server.yml up -d

# Terminal 2 — after emulator boots
adb reverse tcp:8080 tcp:8080

# Verify server
# PowerShell:
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/health"
```

Then rebuild the app in Android Studio and run normally.

---

## Commands For Physical Phone

Use one of these two modes.

### Mode A: Phone connected by USB (recommended)

Keep the app configured with:
- `LOCAL_IMAGE_BASE_URL = "http://localhost:8080"`

Run:

```bash
# 1) Start Docker image server
docker compose -f docker-compose.image-server.yml up -d

# 2) Check server health on host
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/health"

# 3) Verify phone is detected
adb devices

# 4) Tunnel phone localhost:8080 -> PC localhost:8080
adb reverse tcp:8080 tcp:8080

# 5) Confirm reverse tunnel
adb reverse --list
```

If the phone reconnects/restarts, run steps 3-5 again.

### Mode B: Phone on same Wi-Fi (without USB)

In this mode there is no `adb reverse`, so the app must use your PC LAN IP.

1) Get your PC IPv4:

```powershell
ipconfig
```

2) Update these values to your LAN IP (example `192.168.1.50`):
- `app/build.gradle.kts` -> `LOCAL_IMAGE_BASE_URL = "http://192.168.1.50:8080"`
- `docker-compose.image-server.yml` -> `PUBLIC_BASE_URL: http://192.168.1.50:8080`

3) Restart container:

```bash
docker compose -f docker-compose.image-server.yml up -d
```

4) Ensure Windows Firewall allows TCP 8080:

```powershell
New-NetFirewallRule -DisplayName "PintxoMatch Image Server (8080)" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Any
```

Note: for LAN mode, add your LAN IP in `app/src/main/res/xml/network_security_config.xml` as an allowed `<domain>` if Android blocks cleartext HTTP.
