import { existsSync } from "node:fs";
import type { ExpoConfig } from "expo/config";

/**
 * Build-time settings (set as env vars locally or as GitHub/EAS secrets):
 *   EXPO_PUBLIC_API_URL                 Your deployed server, e.g. https://api.unveil.app
 *   EAS_PROJECT_ID                      Expo project id — required for push notifications
 *   GOOGLE_SERVICES_JSON                Path to Firebase google-services.json — required for Android push
 *   EXPO_PUBLIC_REVENUECAT_ANDROID_KEY  RevenueCat public SDK key (Play Store builds)
 *   EXPO_PUBLIC_REVENUECAT_IOS_KEY      RevenueCat public SDK key (App Store builds)
 */
const googleServicesFile = process.env.GOOGLE_SERVICES_JSON ?? "./google-services.json";
const projectId = process.env.EAS_PROJECT_ID;

const config: ExpoConfig = {
  name: "Unveil",
  slug: "unveil",
  version: "1.0.0",
  orientation: "portrait",
  icon: "./assets/images/icon.png",
  scheme: "unveil",
  userInterfaceStyle: "automatic",
  ios: {
    bundleIdentifier: "app.unveil.callerid",
    supportsTablet: false,
    infoPlist: { ITSAppUsesNonExemptEncryption: false },
  },
  android: {
    package: "app.unveil.callerid",
    versionCode: 1,
    adaptiveIcon: {
      backgroundColor: "#0B1020",
      foregroundImage: "./assets/images/android-icon-foreground.png",
      backgroundImage: "./assets/images/android-icon-background.png",
      monochromeImage: "./assets/images/android-icon-monochrome.png",
    },
    ...(existsSync(googleServicesFile) ? { googleServicesFile } : {}),
    permissions: ["android.permission.POST_NOTIFICATIONS", "android.permission.VIBRATE"],
    blockedPermissions: [
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.WRITE_CONTACTS",
      "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.WRITE_EXTERNAL_STORAGE",
    ],
    predictiveBackGestureEnabled: false,
  },
  plugins: [
    "./plugins/withReleaseSigning",
    "expo-router",
    [
      "expo-splash-screen",
      { backgroundColor: "#0B1020", image: "./assets/images/splash-icon.png", imageWidth: 120 },
    ],
    "expo-secure-store",
    [
      "expo-audio",
      {
        microphonePermission: "Unveil uses the microphone only when you record your own voicemail greeting.",
        enableBackgroundPlayback: false,
      },
    ],
    "expo-sharing",
    ["expo-localization", { supportsRTL: true }],
    [
      "expo-notifications",
      { color: "#7C5CFF", defaultChannel: "calls" },
    ],
    [
      "expo-contacts",
      { contactsPermission: "Unveil matches revealed numbers against your contacts on this device. Contacts never leave your phone." },
    ],
  ],
  experiments: { typedRoutes: true, reactCompiler: true },
  extra: {
    apiUrl: process.env.EXPO_PUBLIC_API_URL ?? "",
    ...(projectId ? { eas: { projectId } } : {}),
  },
};

export default config;
