import * as Notifications from "expo-notifications";
import { router, Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { useEffect } from "react";
import { StatusBar, useColorScheme } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { useColors } from "@/constants/theme";
import { useT } from "@/lib/i18n";
import { initPurchases } from "@/lib/purchases";
import { registerForPush } from "@/lib/push";
import { SessionProvider, useSession } from "@/lib/session";

SplashScreen.preventAutoHideAsync();

function openFromNotification(data: Record<string, unknown> | undefined) {
  if (typeof data?.callId === "string") router.push({ pathname: "/call/[id]", params: { id: data.callId } });
  else if (data?.setup === "verified") router.push("/(tabs)/setup");
}

function RootStack() {
  const { ready, signedIn, me } = useSession();
  const c = useColors();
  const s = useT();
  const scheme = useColorScheme();

  useEffect(() => {
    if (ready) SplashScreen.hideAsync();
  }, [ready]);

  const userId = me?.id;
  useEffect(() => {
    if (!signedIn || !userId) return;
    void registerForPush(false);
    void initPurchases(userId);
  }, [signedIn, userId]);

  useEffect(() => {
    const sub = Notifications.addNotificationResponseReceivedListener((r) =>
      openFromNotification(r.notification.request.content.data),
    );
    const last = Notifications.getLastNotificationResponse();
    if (last) openFromNotification(last.notification.request.content.data);
    return () => sub.remove();
  }, []);

  if (!ready) return null;

  return (
    <>
      <StatusBar barStyle={scheme === "light" ? "dark-content" : "light-content"} />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: c.bg },
          headerTintColor: c.text,
          headerShadowVisible: false,
          contentStyle: { backgroundColor: c.bg },
          headerBackButtonDisplayMode: "minimal",
        }}>
        <Stack.Protected guard={!signedIn}>
          <Stack.Screen name="(auth)" options={{ headerShown: false }} />
        </Stack.Protected>
        <Stack.Protected guard={signedIn}>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="call/[id]" options={{ title: s.callDetail }} />
          <Stack.Screen name="greeting" options={{ title: s.greetingTitle }} />
          <Stack.Screen name="blocklist" options={{ title: s.blocklistTitle }} />
          <Stack.Screen name="report" options={{ title: s.reportTitle }} />
          <Stack.Screen name="paywall" options={{ title: "", presentation: "modal" }} />
        </Stack.Protected>
        <Stack.Screen name="server" options={{ title: s.serverTitle }} />
      </Stack>
    </>
  );
}

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <SessionProvider>
        <RootStack />
      </SessionProvider>
    </SafeAreaProvider>
  );
}
