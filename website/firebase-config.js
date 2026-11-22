/**
 * FVG Web App — Firebase Configuration
 * Shares the same Firebase Project & Named Firestore Database as the Android app:
 * - Project ID: gen-lang-client-0025296187
 * - Firestore Database ID: ai-studio-android-fvg-963bcf06-9233-4f62-aa0d-cdebb701a866
 *
 * To enable live Google Sign-In, Email/Password Auth & Cloud Firestore Sync on the website:
 * 1. Open Firebase Console -> Project gen-lang-client-0025296187 -> Project Settings -> Your Apps -> Add Web App.
 * 2. Paste your Web app's `apiKey` and `appId` below (keeping databaseId as-is).
 * 3. Enable Email/Password & Google providers and add your hosting domain under Firebase Auth -> Settings -> Authorized domains.
 *
 * Until Web App `apiKey`/`appId` are configured, the PWA automatically operates cleanly
 * in Local Browser Workspace Mode using the exact same financial engine & 7-day (168h) trial rules.
 */
export const FVG_FIREBASE_CONFIG = {
  apiKey: "",
  authDomain: "gen-lang-client-0025296187.firebaseapp.com",
  projectId: "gen-lang-client-0025296187",
  storageBucket: "gen-lang-client-0025296187.firebasestorage.app",
  messagingSenderId: "450806209679",
  appId: "",
  databaseId: "ai-studio-android-fvg-963bcf06-9233-4f62-aa0d-cdebb701a866"
};

export function isFirebaseWebConfigured() {
  return Boolean(FVG_FIREBASE_CONFIG.apiKey && FVG_FIREBASE_CONFIG.appId);
}
