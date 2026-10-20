import { router } from "expo-router";
import { Pressable, StyleSheet, View } from "react-native";
import { Button, Screen, T } from "@/components/ui";
import { Space, useColors } from "@/constants/theme";
import { useT } from "@/lib/i18n";

export default function Welcome() {
  const s = useT();
  const c = useColors();
  return (
    <Screen edges={["top", "bottom"]}>
      <View style={styles.hero}>
        <View style={[styles.logo, { backgroundColor: c.primary }]}>
          <T style={styles.logoGlyph} color="#fff">
            ◐
          </T>
        </View>
        <T v="small" color={c.primary} style={{ fontWeight: "700", letterSpacing: 1 }}>
          {s.tagline.toUpperCase()}
        </T>
        <T v="title">{s.welcomeTitle}</T>
        <T v="muted">{s.welcomeBody}</T>
      </View>
      {[s.welcomePoint1, s.welcomePoint2, s.welcomePoint3].map((p) => (
        <View key={p} style={styles.point}>
          <T color={c.success}>✓</T>
          <T style={{ flex: 1 }}>{p}</T>
        </View>
      ))}
      <View style={{ flex: 1 }} />
      <Button title={s.getStarted} onPress={() => router.push("/(auth)/phone")} />
      <Pressable onPress={() => router.push("/server")} hitSlop={12} style={{ alignSelf: "center" }}>
        <T v="small">{s.server}</T>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: { gap: Space.md, paddingTop: Space.xl, paddingBottom: Space.md },
  logo: { width: 64, height: 64, borderRadius: 18, alignItems: "center", justifyContent: "center" },
  logoGlyph: { fontSize: 36, lineHeight: 40 },
  point: { flexDirection: "row", gap: Space.sm, alignItems: "flex-start" },
});
