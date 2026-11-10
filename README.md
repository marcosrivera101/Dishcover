# Dishcover

Rate the dishes you love. Know what to eat tonight.

Android app (Kotlin + Jetpack Compose) on Firebase, with Google Places for restaurants and Gemini to read menu photos.

## Try it

Every push builds the app on GitHub and attaches it to a **Test build** release: open the repo's **Releases** page on an Android phone, tap the `.apk`, open it and allow the install.

Until `app/google-services.json` (or the `GOOGLE_SERVICES_JSON` secret) is present the app runs in **demo mode**: sample places around you, nothing is saved, and the menu reader returns a sample result.

## Going live

1. In the Firebase project `godishcover` (Blaze plan, with budget alerts) add an Android app with package `app.godishcover` and SHA-1 `87:9A:D6:EA:29:31:6D:D8:51:CC:F6:EB:BC:66:42:D8:07:5E:D6:0D` (the shared test key in `app/debug.keystore`; add the release key's SHA-1 too when it exists).
2. Turn on Authentication (Google and Email/Password), Cloud Firestore, Storage and Cloud Messaging.
3. Download `google-services.json` and commit it at `app/google-services.json` or save its contents as the repository secret `GOOGLE_SERVICES_JSON`.
4. In Google Cloud, enable Places API (New), create an Android key restricted to `app.godishcover`, pin the quota at the free cap, and save it as the secret `MAPS_API_KEY`.
5. Create a service account key for the project and save the JSON as the secret `FIREBASE_SERVICE_ACCOUNT`. Builds on `main` then publish the database and storage rules and offer each new build through the in-app update.
6. Deploy the server code once: `firebase functions:secrets:set GEMINI_API_KEY`, then `firebase deploy --only functions`.

## Layout

- `app/` Android app. `data/` holds the backend (`FirebaseRepo`, `DemoRepo`), Google Places, and the suggestion ranking. `ui/screens/` holds the screens from the mockup.
- `functions/` Cloud Functions: `readMenu` (photo or link to draft dishes, with daily caps), `notifyOnFeed` (alerts for new places and dishes) and `cleanupGoogleCoords` (clears Google coordinates older than 30 days).
- `firestore.rules` and `storage.rules` who can read and write what.
- Feedback and error reports are emails the user sends to agentjarvis@mail.grokbot.com from Help.
