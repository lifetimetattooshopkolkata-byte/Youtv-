# YouTV & ADMIT — Complete Architecture, Security & Deployment Guide

## 1. Project Folder Structure
```text
├── app/                               # Android APK containing both launchers:
│   ├── google-services.json           # Provisioned Firebase project (gen-lang-client-0489321426)
│   └── src/main/
│       ├── AndroidManifest.xml        # Declares .MainActivity (YouTV) & .admit.AdmitActivity (ADMIT)
│       ├── res/
│       │   ├── drawable/youtv_logo.png
│       │   ├── font/{plus_jakarta_sans,space_grotesk}.ttf
│       │   ├── mipmap-*/ic_launcher*.png & ic_admit_launcher.png
│       │   └── values/{strings.xml,firebase_applet_config.xml}
│       └── java/com/example/
│           ├── MainActivity.kt        # YouTV launcher root + Google Credential Manager
│           ├── admit/                 # Separate ADMIT Owner Console (taskAffinity=com.aistudio.youtv.admit)
│           │   ├── AdmitActivity.kt
│           │   ├── AdmitApp.kt
│           │   ├── AdmitViewModel.kt
│           │   ├── AdmitDashboardAndModerationScreens.kt
│           │   └── AdmitAuditAndSettingsScreens.kt
│           ├── data/
│           │   ├── model/{Models.kt, UiState.kt}
│           │   └── firebase/{YouTvRepository.kt, AdmitRepository.kt, GoogleAuthHelper.kt, BackendApiClient.kt, FirestoreErrorHandler.kt}
│           ├── localization/{AppLanguage.kt, LanguagePreferenceStore.kt}
│           ├── ui/{components/*, screens/*, theme/*}
│           └── viewmodel/YouTvViewModel.kt
├── backend/                           # Cloud Run Node.js + TypeScript Secure API & Transcoder
│   ├── Dockerfile
│   ├── .env.example
│   ├── package.json
│   └── src/
│       ├── config/firebase.ts         # Firebase Admin SDK targeting named Firestore database
│       ├── middleware/auth.ts         # Verified ID token + owner custom-claim + OWNER_EMAIL gate
│       ├── routes/{admin.ts, health.ts, videos.ts}
│       ├── scripts/bootstrapOwner.ts  # One-time / idempotent verified owner claim bootstrap
│       ├── services/transcoder.ts     # GCS v4 signed URLs + Google Cloud Transcoder HLS job
│       └── tests/security.test.ts
├── firebase-blueprint.json            # Full 18-collection schema blueprint
├── firestore.rules                    # Zero-trust 8-pillar Firestore security rules (deployed)
├── firestore.indexes.json             # Composite query indexes
├── storage.rules                      # Firebase Storage security rules
├── firestore.test.js                  # Local Firestore Emulator rules test suite
└── docs/SETUP_AND_DEPLOYMENT.md
```

## 2. Provisioned Firebase Setup
- **Firebase Project ID**: `gen-lang-client-0489321426`
- **Android Application ID**: `com.aistudio.youtv.qrxmzk`
- **Named Firestore Database ID**: `ai-studio-android-youtvsui-f8408d23-5872-4e6d-ba45-b4798ace1352`
- **Authentication**: Google Sign-In via Android Jetpack `CredentialManager` (`GetSignInWithGoogleOption` + `GetGoogleIdOption`). Passwords are never stored in Firestore.

## 3. Firestore Collections & Schema Summary
All 18 collections are defined in `/firebase-blueprint.json`:
1. `admins/{uid}` — Immutable from clients (`allow write: if false`). Populated strictly by `bootstrapOwner.ts` via Admin SDK.
2. `users/{uid}` — Profile, `preferredLanguage` (`en`/`bn`/`hi`), `status` (`active`, `suspended`, `blocked`, `deletion_requested`), `uploadDisabled`, `restrictedMode`, `storageUsedBytes`.
3. `channels/{channelId}` — Creator channel metadata, unique `@handle`, subscriber/video/view counters.
4. `videos/{videoId}` — Title, description, thumbnail URL, playback URL, HLS manifest URL, captions URL, visibility (`PUBLIC`/`PRIVATE`/`UNLISTED`), processing status, moderation status.
5. `shorts/{shortId}` — Vertical short-form metadata & view/like counters.
6. `comments/{commentId}` — Threaded comments (`parentCommentId`) & creator moderation.
7. `likes/{likeId}` — Deterministic ID `${videoId}_${uid}` preventing duplicate likes/dislikes.
8. `subscriptions/{subId}` — Deterministic ID `${channelId}_${uid}` preventing duplicate subscriptions.
9. `playlists/{playlistId}` — Custom playlists & `WATCH_LATER`.
10. `watchHistory/{entryId}` — Per-user playback history & resume progress.
11. `searchHistory/{entryId}` — Per-user recent searches.
12. `notifications/{notificationId}` — Activity and moderation alerts.
13. `reports/{reportId}` — Abuse reports, copyright complaints (`copyrightWorkUrl`), and moderation appeals.
14. `moderationActions/{actionId}` — Immutable owner moderation audit records.
15. `auditLogs/{logId}` — Immutable security & admin audit trail.
16. `platformSettings/global` — Global feature flags, max file size, daily upload quota, categories, auto-flag keywords.
17. `videoProcessingJobs/{jobId}` — Transcoder job queue/status (`QUEUED`, `TRANSCODING`, `READY`, `FAILED`, `FALLBACK_DIRECT`).
18. `blockedUsers/{blockId}` — Deterministic `${blockerId}_${blockedUserId}` user-level block records.

## 4. Security Rules Summary (`firestore.rules` & `storage.rules`)
- Default deny-all fallback.
- `isSignedIn()` requires `request.auth != null && request.auth.token.email_verified == true`.
- `isAdmin()` requires verified email + `exists(/databases/$(database)/documents/admins/$(request.auth.uid))`.
- Users cannot escalate `status`, `uploadDisabled`, `moderatedBy`, or `moderationReason` on `/users/{uid}`.
- Ownership & counter-field invariants enforced on `channels`, `videos`, `shorts`, `comments`, `likes`, `subscriptions`, `playlists`, `watchHistory`, `searchHistory`, `blockedUsers`, and `reports`.
- `moderationActions` and `auditLogs` are owner-only read & immutable append (`allow update, delete: if false`).

## 5. Backend Environment Variables (`backend/.env.example`)
Configure these in your local `backend/.env` or Cloud Run runtime environment:
- `OWNER_EMAIL` — Single verified Google email authorized for ADMIT
- `FIREBASE_PROJECT_ID` — `gen-lang-client-0489321426`
- `FIRESTORE_DATABASE_ID` — `ai-studio-android-youtvsui-f8408d23-5872-4e6d-ba45-b4798ace1352`
- `GCS_BUCKET` — Google Cloud Storage bucket for raw uploads & HLS manifests
- `GCP_LOCATION` — Transcoder API region (default `us-central1`)
- `TRANSCODER_ENABLED` — `true` once bucket & Transcoder API are enabled
- `ALLOWED_ORIGINS` — Comma-separated allowed origins
- `PORT` — `8080`

## 6. Owner Bootstrap Instructions
1. Sign into the app once with your verified Google account so the Firebase Auth user exists.
2. In `/backend`, install dependencies (`npm install`) and set `OWNER_EMAIL` in environment variables (along with Application Default Credentials or `GOOGLE_APPLICATION_CREDENTIALS`).
3. Run:
   ```bash
   OWNER_EMAIL="your-verified-owner@gmail.com" npm --prefix backend run bootstrap-owner
   ```
4. This grants `{ owner: true, role: "OWNER" }` Firebase custom claims, creates `/admins/{uid}`, initializes `/platformSettings/global`, and logs `BOOTSTRAP_OWNER` in `/auditLogs`.
5. Re-open **ADMIT** and tap **Retry** (or sign out and sign back in to refresh your ID token).

## 7. Google Cloud Run & Transcoder API Deployment
1. Enable APIs in GCP (`gen-lang-client-0489321426`):
   ```bash
   gcloud services enable run.googleapis.com transcoder.googleapis.com storage.googleapis.com --project gen-lang-client-0489321426
   ```
2. Create a GCS bucket and grant the Cloud Run service account `roles/storage.objectAdmin`, `roles/transcoder.admin`, and `roles/datastore.user`.
3. Deploy `/backend`:
   ```bash
   gcloud run deploy youtv-admit-backend \
     --source ./backend \
     --region us-central1 \
     --allow-unauthenticated \
     --set-env-vars "FIREBASE_PROJECT_ID=gen-lang-client-0489321426,FIRESTORE_DATABASE_ID=ai-studio-android-youtvsui-f8408d23-5872-4e6d-ba45-b4798ace1352,OWNER_EMAIL=your-verified-owner@gmail.com,GCS_BUCKET=your-bucket,TRANSCODER_ENABLED=true"
   ```
4. Copy the Cloud Run HTTPS URL into AI Studio Secrets / `.env` as `YOUTV_BACKEND_URL=https://...` so Android `BuildConfig.YOUTV_BACKEND_URL` connects automatically.

## 8. Running Tests Locally
- **Firestore Security Rules Tests** (requires Firestore Emulator on `127.0.0.1:8085`):
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```
- **Android Unit & Robolectric Compose CUJ Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Backend TypeScript Build & Unit Tests**:
  ```bash
  npm --prefix backend install && npm --prefix backend run build && npm --prefix backend test
  ```

## 9. Android, Desktop/Tablet Adaptive & Release Packaging
- Both **YouTV** (`.MainActivity`) and **ADMIT** (`.admit.AdmitActivity`) ship with separate launcher icons and task affinities, adaptive `NavigationBar`/`NavigationRail` breakpoints (`720.dp`), and Media3 ExoPlayer HLS + MP4 playback.
- Build debug APK: `gradle :app:assembleDebug`
- Build signed release bundle (with keystore env vars): `gradle :app:bundleRelease`

## 10. Optional Web / PWA & iOS Capacitor Notes
- The `/backend` Cloud Run service exposes standard REST endpoints (`/health`, `/api/videos/transcode`, `/api/videos/:videoId/status`, `/api/admin/*`) protected by Firebase ID tokens so any companion Web/PWA or Capacitor iOS build can consume the exact same backend, Firestore database, and `firestore.rules` without code changes on the server.
