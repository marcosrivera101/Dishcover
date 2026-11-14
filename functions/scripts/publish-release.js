// Uploads the built APK to Firebase Storage and tells the app a new version exists
// (Firestore document config/app). Run by GitHub Actions on main when the
// FIREBASE_SERVICE_ACCOUNT secret is set.
const admin = require("firebase-admin");
const fs = require("node:fs");

const [apkPath, versionCode, versionName] = process.argv.slice(2);
const bucketName = process.env.STORAGE_BUCKET || "godishcover.firebasestorage.app";

async function main() {
  admin.initializeApp({ storageBucket: bucketName });
  // APK_URL points at a copy hosted elsewhere (the GitHub test-builds release); otherwise
  // the APK goes to Firebase Storage.
  let apkUrl = process.env.APK_URL;
  if (!apkUrl) {
    const dest = `releases/dishcover-${versionCode}.apk`;
    await admin.storage().bucket().upload(apkPath, {
      destination: dest,
      metadata: { contentType: "application/vnd.android.package-archive" },
    });
    apkUrl = `https://firebasestorage.googleapis.com/v0/b/${bucketName}/o/${encodeURIComponent(dest)}?alt=media`;
  }
  await admin.firestore().doc("config/app").set({
    versionCode: Number(versionCode),
    versionName,
    apkUrl,
    notes: process.env.RELEASE_NOTES || "",
    publishedAt: Date.now(),
  });
  console.log(`Published ${versionName} (${versionCode}) at ${apkUrl}`);
  if (!fs.existsSync(apkPath)) process.exit(1);
}

main().catch((e) => { console.error(e); process.exit(1); });
