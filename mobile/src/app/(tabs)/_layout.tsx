import { Tabs } from "expo-router/js-tabs";
import { Text, type ColorValue } from "react-native";
import { useColors } from "@/constants/theme";
import { useT } from "@/lib/i18n";
import { useSession } from "@/lib/session";

function icon(glyph: string) {
  return function TabIcon({ color }: { color: ColorValue }) {
    return <Text style={{ color, fontSize: 20 }}>{glyph}</Text>;
  };
}

export default function TabsLayout() {
  const s = useT();
  const c = useColors();
  const { me } = useSession();
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: c.bg },
        headerTintColor: c.text,
        headerShadowVisible: false,
        tabBarStyle: { backgroundColor: c.card, borderTopColor: c.border },
        tabBarActiveTintColor: c.primary,
        tabBarInactiveTintColor: c.muted,
        sceneStyle: { backgroundColor: c.bg },
      }}>
      <Tabs.Screen name="index" options={{ title: s.tabCalls, tabBarIcon: icon("☎") }} />
      <Tabs.Screen
        name="setup"
        options={{ title: s.tabSetup, tabBarIcon: icon("⚙"), tabBarBadge: me && !me.forwardingVerifiedAt ? "!" : undefined }}
      />
      <Tabs.Screen name="settings" options={{ title: s.tabSettings, tabBarIcon: icon("☰") }} />
    </Tabs>
  );
}
