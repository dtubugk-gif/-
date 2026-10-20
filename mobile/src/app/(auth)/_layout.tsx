import { Stack } from "expo-router";
import { useColors } from "@/constants/theme";

export default function AuthLayout() {
  const c = useColors();
  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: c.bg },
        headerTintColor: c.text,
        headerShadowVisible: false,
        headerTitle: "",
        contentStyle: { backgroundColor: c.bg },
      }}>
      <Stack.Screen name="index" options={{ headerShown: false }} />
    </Stack>
  );
}
