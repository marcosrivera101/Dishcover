// Publishes the Firestore and Storage security rules and the Firestore indexes with the
// Admin SDK, so the service account needs no extra Google Cloud roles (firebase deploy
// asks for Service Usage permissions that the default Firebase admin account lacks).
const admin = require("firebase-admin");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..", "..");
const project = process.env.FIREBASE_PROJECT || "godishcover";
const bucket = process.env.STORAGE_BUCKET || "godishcover.firebasestorage.app";

async function createIndexes() {
  const { indexes } = JSON.parse(fs.readFileSync(path.join(root, "firestore.indexes.json"), "utf8"));
  const { access_token } = await admin.app().options.credential.getAccessToken();
  for (const ix of indexes) {
    const url = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/collectionGroups/${ix.collectionGroup}/indexes`;
    const res = await fetch(url, {
      method: "POST",
      headers: { Authorization: `Bearer ${access_token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ queryScope: ix.queryScope, fields: ix.fields }),
    });
    const body = await res.text();
    if (res.ok) console.log(`Index on ${ix.collectionGroup}: creating`);
    else if (res.status === 409 || body.includes("ALREADY_EXISTS")) console.log(`Index on ${ix.collectionGroup}: already there`);
    else throw new Error(`Index on ${ix.collectionGroup}: HTTP ${res.status} ${body.slice(0, 300)}`);
  }
}

async function main() {
  admin.initializeApp({ storageBucket: bucket });
  const rules = admin.securityRules();
  await rules.releaseFirestoreRulesetFromSource(fs.readFileSync(path.join(root, "firestore.rules"), "utf8"));
  console.log("Firestore rules published");
  try {
    await rules.releaseStorageRulesetFromSource(fs.readFileSync(path.join(root, "storage.rules"), "utf8"), bucket);
    console.log("Storage rules published");
  } catch (e) {
    // Storage needs the Blaze plan on new projects; photo uploads stay off until it is turned on.
    console.log(`Storage rules skipped: ${e.message}`);
  }
  await createIndexes();
}

main().catch((e) => { console.error(e.message || e); process.exit(1); });
