import type { ReactNode } from "react";
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type RefreshControlProps,
  type StyleProp,
  type TextInputProps,
  type TextStyle,
  type ViewStyle,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Radius, Space, useColors } from "@/constants/theme";

export function Screen({
  children,
  scroll = true,
  refreshControl,
  edges = ["top"],
}: {
  children: ReactNode;
  scroll?: boolean;
  refreshControl?: React.ReactElement<RefreshControlProps>;
  edges?: ("top" | "bottom")[];
}) {
  const c = useColors();
  return (
    <SafeAreaView edges={edges} style={{ flex: 1, backgroundColor: c.bg }}>
      {scroll ? (
        <ScrollView
          contentContainerStyle={styles.screen}
          keyboardShouldPersistTaps="handled"
          refreshControl={refreshControl}>
          {children}
        </ScrollView>
      ) : (
        <View style={[styles.screen, { flex: 1 }]}>{children}</View>
      )}
    </SafeAreaView>
  );
}

type Variant = "title" | "heading" | "body" | "muted" | "small" | "mono";

export function T({
  children,
  v = "body",
  style,
  color,
  selectable,
}: {
  children: ReactNode;
  v?: Variant;
  style?: StyleProp<TextStyle>;
  color?: string;
  selectable?: boolean;
}) {
  const c = useColors();
  const base = { title: styles.title, heading: styles.heading, body: styles.body, muted: styles.body, small: styles.small, mono: styles.mono }[v];
  return (
    <Text selectable={selectable} style={[base, { color: color ?? (v === "muted" || v === "small" ? c.muted : c.text) }, style]}>
      {children}
    </Text>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const c = useColors();
  return <View style={[styles.card, { backgroundColor: c.card, borderColor: c.border }, style]}>{children}</View>;
}

export function Button({
  title,
  onPress,
  variant = "primary",
  loading,
  disabled,
  style,
}: {
  title: string;
  onPress: () => void;
  variant?: "primary" | "secondary" | "danger" | "ghost";
  loading?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const c = useColors();
  const bg = { primary: c.primary, secondary: c.chip, danger: "transparent", ghost: "transparent" }[variant];
  const fg = { primary: c.primaryText, secondary: c.primary, danger: c.danger, ghost: c.primary }[variant];
  const off = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={off ? undefined : onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg, opacity: off ? 0.5 : pressed ? 0.8 : 1 },
        variant === "danger" && { borderWidth: 1, borderColor: c.danger },
        style,
      ]}>
      {loading ? <ActivityIndicator color={fg} /> : <Text style={[styles.buttonText, { color: fg }]}>{title}</Text>}
    </Pressable>
  );
}

export function Field(props: TextInputProps) {
  const c = useColors();
  return (
    <TextInput
      placeholderTextColor={c.muted}
      {...props}
      style={[styles.field, { color: c.text, borderColor: c.border, backgroundColor: c.card }, props.style]}
    />
  );
}

export function Badge({ label, tone = "neutral" }: { label: string; tone?: "neutral" | "success" | "warning" | "danger" | "primary" }) {
  const c = useColors();
  const color = { neutral: c.muted, success: c.success, warning: c.warning, danger: c.danger, primary: c.primary }[tone];
  return (
    <View style={[styles.badge, { borderColor: color }]}>
      <Text style={[styles.badgeText, { color }]}>{label}</Text>
    </View>
  );
}

export function Row({
  title,
  subtitle,
  right,
  onPress,
  selected,
}: {
  title: string;
  subtitle?: string;
  right?: ReactNode;
  onPress?: () => void;
  selected?: boolean;
}) {
  const c = useColors();
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      style={({ pressed }) => [
        styles.row,
        { borderColor: selected ? c.primary : c.border, backgroundColor: c.card, opacity: pressed ? 0.85 : 1 },
        selected && { borderWidth: 2 },
      ]}>
      <View style={{ flex: 1 }}>
        <T>{title}</T>
        {subtitle ? <T v="small">{subtitle}</T> : null}
      </View>
      {right}
    </Pressable>
  );
}

export function Gap({ size = Space.md }: { size?: number }) {
  return <View style={{ height: size }} />;
}

const styles = StyleSheet.create({
  screen: { padding: Space.md, paddingBottom: Space.xl * 2, gap: Space.md },
  title: { fontSize: 28, fontWeight: "700", lineHeight: 34 },
  heading: { fontSize: 18, fontWeight: "700", lineHeight: 24 },
  body: { fontSize: 16, lineHeight: 22 },
  small: { fontSize: 13, lineHeight: 18 },
  mono: { fontSize: 22, fontWeight: "700", letterSpacing: 1, fontVariant: ["tabular-nums"] },
  card: { borderRadius: Radius.md, borderWidth: StyleSheet.hairlineWidth, padding: Space.md, gap: Space.sm },
  button: { minHeight: 50, borderRadius: Radius.md, alignItems: "center", justifyContent: "center", paddingHorizontal: Space.md },
  buttonText: { fontSize: 16, fontWeight: "700" },
  field: { minHeight: 50, borderWidth: 1, borderRadius: Radius.md, paddingHorizontal: Space.md, fontSize: 17 },
  badge: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 8, paddingVertical: 2, alignSelf: "flex-start" },
  badgeText: { fontSize: 12, fontWeight: "700" },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: Space.sm,
    borderRadius: Radius.md,
    borderWidth: StyleSheet.hairlineWidth,
    padding: Space.md,
  },
});
