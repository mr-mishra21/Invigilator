# Firebase setup runbook

Complete this once per environment (dev/staging/prod). The steps are the same for each; use different Firebase projects.

## Prerequisites

- [Firebase CLI](https://firebase.google.com/docs/cli) installed: `npm install -g firebase-tools`
- Logged in: `firebase login`
- Node 20+ for Cloud Functions

---

## 1. Create the Firebase project

1. Open [console.firebase.google.com](https://console.firebase.google.com).
2. **Add project** → name it `Invigilator` (or `Invigilator-staging` etc.).
3. Choose **Google Analytics: enabled** (used for Crashlytics).
4. **Default GCP resource location: `asia-south1` (Mumbai)** — this is where Firestore and Cloud Functions will run. You cannot change this after creation.

## 2. Register the Android app

1. In Project Overview → **Add app** → Android.
2. Package name: `app.invigilator`
3. App nickname: `Invigilator Android`
4. SHA-1 fingerprint — get it from:
   ```
   cd /path/to/Invigilator
   ./gradlew signingReport
   ```
   Copy the `SHA1:` line under the `debug` variant.
5. Download `google-services.json` → place it at `app/google-services.json`.
   This file is **gitignored** — never commit it.

## 3. Enable Phone Authentication

1. Build → **Authentication** → Get started → **Sign-in method** tab.
2. Enable **Phone**.
3. Scroll to **Phone numbers for testing** (important for development — no real SMS sent):
   - `+91 99999 99991` → OTP `123456`
   - `+91 99999 99992` → OTP `234567`
   - `+91 99999 99993` → OTP `345678`
4. Save.

## 4. Enable Firestore

1. Build → **Firestore Database** → Create database.
2. Start in **production mode** (rules come from `firestore.rules`).
3. Location: **`asia-south1`**.

## 5. Deploy Firestore security rules

```bash
cd backend
firebase deploy --only firestore:rules
```

Verify in the Firestore console → Rules tab that the deployed rules match `firestore.rules`.

## 6. Deploy Cloud Functions

```bash
cd backend/functions
npm install
npm run build

cd ..
firebase deploy --only functions
```

After deployment, confirm in the Firebase console → Functions tab that both `onConsentCreate` and `claimLinkingCode` appear with region `asia-south1`.

## 7. Enable Crashlytics (optional but recommended)

1. Release & Monitor → **Crashlytics** → Get started → follow prompts.
2. No extra config needed; `firebase-crashlytics` is already in the Android dependency tree.

## 8. Verify end-to-end with emulators

```bash
cd backend
firebase emulators:start
```

The emulator UI opens at `http://localhost:4000`. Use a test phone number from step 3 to run through the signup flow without touching production.

To point the Android app at the emulator, add to `MainActivity.onCreate` (dev build only):

```kotlin
if (BuildConfig.DEBUG) {
    FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
    FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
    FirebaseFunctions.getInstance("asia-south1").useEmulator("10.0.2.2", 5001)
}
```

`10.0.2.2` is the Android emulator's alias for `localhost` on the host machine. Use your machine's LAN IP if testing on a physical device.

---

## Checklist before going to production

- [ ] SHA-1 of release keystore added to Firebase console
- [ ] Test phone numbers removed from Auth console
- [ ] Firestore rules reviewed and deployed
- [ ] Cloud Functions deployed and smoke-tested
- [ ] `google-services.json` for prod is NOT committed to git
- [ ] Emulator code removed from `MainActivity`
