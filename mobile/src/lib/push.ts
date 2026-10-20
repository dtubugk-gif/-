import Constants from "expo-constants";
import * as Device from "expo-device";
import * as Notifications from "expo-notifications";
import { AndroidImportance, AndroidNotificationVisibility } from "expo-notifications";
import { Platform } from "react-native";
import { api } from "./api";

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldPlaySound: true,
    shouldSetBadge: false,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

export type PushState = "granted" | "denied" | "unavailable";

/**
 * Registers this device for call alerts. "unavailable" means the build has no push credentials
 * (EAS project id / Firebase) — the server then falls back to SMS alerts.
 */
export async function registerForPush(ask: boolean): Promise<PushState> {
  if (!Device.isDevice) return "unavailable";
  if (Platform.OS === "android") {
    // Must match the channelId the server sends ("calls").
    await Notifications.setNotificationChannelAsync("calls", {
      name: "Revealed calls",
      importance: AndroidImportance.MAX,
      vibrationPattern: [0, 250, 150, 250],
      lightColor: "#7C5CFF",
      lockscreenVisibility: AndroidNotificationVisibility.PRIVATE,
    });
  }
  let { status } = await Notifications.getPermissionsAsync();
  if (status !== "granted" && ask) status = (await Notifications.requestPermissionsAsync()).status;
  if (status !== "granted") return "denied";

  const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if (!projectId) return "unavailable";
  try {
    const { data } = await Notifications.getExpoPushTokenAsync({ projectId });
    await api("/v1/me/push-tokens", { body: { token: data, platform: Platform.OS === "ios" ? "ios" : "android" } });
    return "granted";
  } catch {
    return "unavailable";
  }
}

export async function unregisterPush() {
  try {
    const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
    if (!projectId) return;
    const { data } = await Notifications.getExpoPushTokenAsync({ projectId });
    await api(`/v1/me/push-tokens/${encodeURIComponent(data)}`, { method: "DELETE" });
  } catch {
    /* best effort */
  }
}
