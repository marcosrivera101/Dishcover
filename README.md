# Dishcover

Rate the dishes you love. Know what to eat tonight.

Android app (Kotlin + Jetpack Compose) on Firebase, with Google Places for restaurants and Gemini to read menu photos.

## Try it

Every push builds a debug APK on GitHub: open **Actions → Build app → the latest run → Artifacts → dishcover-debug-apk**, unzip it, and install the APK on an Android phone (allow "install unknown apps" when asked).

Until Firebase is connected the app runs in **demo mode**: sample places around you, nothing is saved, and the menu reader returns a sample result.

## Going live

1. Create a Firebase project (Blaze plan, with budget alerts) and add an Android app with package `app.godishcover`.
2. Turn on Authentication (Google and Email/Password) and Cloud Firestore.
3. Add the SHA-1 fingerprints to the Firebase Android app (needed for Google sign-in). Test builds use the shared `app/debug.keystore`: `87:9A:D6:EA:29:31:6D:D8:51:CC:F6:EB:BC:66:42:D8:07:5E:D6:0D`. Add the release key's SHA-1 too when it exists.
4. Download `google-services.json` and save its contents as the repository secret `GOOGLE_SERVICES_JSON`.
5. In Google Cloud, enable Places API (New), create an Android key restricted to `app.godishcover`, pin the quota at the free cap, and save it as the secret `MAPS_API_KEY`.
6. Deploy the rules and server code: `firebase deploy --only firestore:rules,functions` after `firebase functions:secrets:set GEMINI_API_KEY`.

## Layout

- `app/` Android app. `data/` holds the backend (`FirebaseRepo`, `DemoRepo`), Google Places, and the suggestion ranking. `ui/screens/` holds the screens from the mockup.
- `functions/` Cloud Functions: `readMenu` (photo or link to draft dishes, with daily caps) and `cleanupGoogleCoords` (clears Google coordinates older than 30 days).
- `firestore.rules` who can read and write what.
