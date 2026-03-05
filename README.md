# 🍢 PintxoMatch - Tinder de Tortillas (Edición Gipuzkoa)

PintxoMatch es una app Android (Kotlin + Jetpack Compose) para descubrir pintxos, hacer match y hablar por chat en tiempo real.

## ✨ Funcionalidades principales

- **Autenticación con Firebase Auth** (registro/login con email y contraseña).
- **Feed swipeable de pintxos** desde Firestore.
- **Subida de pintxos sin Storage** (solo metadatos + URL externa de imagen).
- **Chat privado 1-a-1 en Realtime Database** por match de pintxo.
- **Panel de chats** con listado, último mensaje y borrado manual.
- **Limpieza de chats vacíos** al volver atrás.
- **Perfil de usuario** con edición y estadísticas de aportaciones por usuario.

---

## 🧱 Stack técnico

- **Android:** Kotlin, Jetpack Compose, Navigation Compose, Material 3
- **Backend:** Firebase Authentication, Cloud Firestore, Realtime Database
- **Imágenes:** Coil
- **Build:** Gradle Kotlin DSL

---

## 🗂️ Arquitectura de datos

### Firestore

Colección `Pintxos`:
- `nombre: String`
- `bar: String`
- `ubicacion: String`
- `precio: Double`
- `imageUrl: String`
- `timestamp: Long`
- `uploaderUid: String`
- `uploaderEmail: String`

### Realtime Database

- `waitingByPintxo/{pintxoId}/{uid}`
  - `displayName`
  - `timestamp`

- `chats/{chatId}`
  - `pintxoId`
  - `pintxoName`
  - `updatedAt`
  - `participants/{uid}: true`
  - `participantNames/{uid}: String`
  - `messages/{messageId}`
    - `senderId`
    - `senderName`
    - `text`
    - `timestamp`

---

## 🔐 Privacidad del chat

- Los chats se crean en formato **1-a-1** usando cola por `pintxoId`.
- El listado de chats solo muestra chats donde el usuario actual es participante.
- La pantalla de chat valida acceso por `participants/{uid}`.
- Compatible con chats antiguos: si un usuario ya escribió en un chat viejo sin participantes, se migra su acceso.

---

## 🚀 Puesta en marcha

### 1) Requisitos

- Android Studio (última versión estable)
- JDK 11+
- Cuenta Firebase

### 2) Clonar e instalar

```bash
git clone <TU_REPO>
cd PintxoMatch
```

### 3) Configurar Firebase

1. Crea proyecto en Firebase.
2. Registra la app Android con `applicationId`:
   - `com.example.pintxomatch`
3. Descarga `google-services.json`.
4. Colócalo en:
   - `app/google-services.json`

> Nota: `google-services.json` está ignorado en git por seguridad.

### 4) Activar productos Firebase

- **Authentication** → Email/Password
- **Cloud Firestore**
- **Realtime Database** (instancia europe-west1)

### 5) Ejecutar

```bash
./gradlew :app:assembleDebug
```

O desde Android Studio: **Run app**.

---

## 🛡️ Reglas recomendadas (Realtime Database)

> Ajusta estas reglas a tu entorno de producción. Estas son una base segura para participantes.

```json
{
  "rules": {
    "waitingByPintxo": {
      "$pintxoId": {
        "$uid": {
          ".read": "auth != null && auth.uid === $uid",
          ".write": "auth != null && auth.uid === $uid"
        }
      }
    },
    "chats": {
      "$chatId": {
        ".read": "auth != null && data.child('participants').child(auth.uid).val() === true",
        "participants": {
          "$uid": {
            ".write": "auth != null && auth.uid === $uid"
          }
        },
        "participantNames": {
          "$uid": {
            ".write": "auth != null && auth.uid === $uid"
          }
        },
        "messages": {
          "$messageId": {
            ".write": "auth != null && data.parent().parent().child('participants').child(auth.uid).val() === true",
            ".validate": "newData.hasChildren(['senderId','senderName','text','timestamp'])"
          }
        }
      }
    }
  }
}
```

---

## ⚠️ Notas conocidas

- Si existen pintxos antiguos sin `uploaderUid`, no contarán en estadísticas por usuario.
- El emparejamiento usa transacción para minimizar carreras entre dispositivos.
- El tema está forzado en modo claro para consistencia visual entre emulator y dispositivos reales.

---

## 📌 Roadmap sugerido

- Badge de chats no leídos.
- Estado online/escribiendo.
- Migración de documentos Firestore antiguos (`uploaderUid`).
- Tests de integración para flujo de match y chat.

---

## 👨‍🍳 Autoría

Proyecto personal creado para validar una arquitectura de matchmaking social con coste cero de almacenamiento de imágenes.
