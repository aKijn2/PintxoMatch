# Chat notifications

This project can notify the other user when a direct chat message is created.

## Android app

The app now:

- registers FCM tokens for the logged-in user under `Users/{uid}/deviceTokens/{token}`
- requests `POST_NOTIFICATIONS` permission on Android 13+
- creates the `chat_messages` notification channel
- shows a local notification when an FCM data message is received while the app is running

## Backend

The Cloud Function in `functions/index.js`:

- listens to `/chats/{chatId}/messages/{messageId}` in Realtime Database
- loads the other chat participant
- loads that user's device tokens from Firestore
- sends an FCM push notification
- removes invalid tokens automatically

## Deploy

From the repository root:

```bash
npm install -g firebase-tools
firebase login
firebase init functions
```

When Firebase asks:

- choose the existing project `pintxomatch`
- use the existing `functions` folder
- JavaScript is enough for this setup

Then install and deploy:

```bash
cd functions
npm install
cd ..
firebase deploy --only functions
```

## Notes

- The destination user must have opened the app at least once after login so their FCM token is stored.
- Android 13+ users must accept notification permission.
- If you want the notification to open the exact chat screen on tap, add deep-link handling for `open_chat_id`.
