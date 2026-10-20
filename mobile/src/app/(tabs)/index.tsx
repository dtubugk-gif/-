import * as Notifications from "expo-notifications";
import { router, useFocusEffect } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { FlatList, Pressable, RefreshControl, StyleSheet, View } from "react-native";
import { Badge, Button, Card, T } from "@/components/ui";
import { Space, useColors } from "@/constants/theme";
import { api, type CallItem } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import { formatPhone, timeAgo } from "@/lib/format";
import { useT } from "@/lib/i18n";
import { useSession } from "@/lib/session";

export default function Calls() {
  const s = useT();
  const c = useColors();
  const { me, refresh } = useSession();
  const [calls, setCalls] = useState<CallItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res] = await Promise.all([api<{ calls: CallItem[] }>("/v1/calls?limit=100"), refresh()]);
      setCalls(res.calls);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [refresh]);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  // A revealed-call alert arriving while the app is open should show up without a manual refresh.
  useEffect(() => {
    const sub = Notifications.addNotificationReceivedListener(() => void load());
    return () => sub.remove();
  }, [load]);

  const header = (
    <View style={{ gap: Space.md, marginBottom: Space.md }}>
      {me && !me.forwardingVerifiedAt ? (
        <Card style={{ borderColor: c.primary }}>
          <T v="heading">{s.setupBannerTitle}</T>
          <T v="muted">{s.setupBannerBody}</T>
          <Button title={s.finishSetup} onPress={() => router.navigate("/(tabs)/setup")} />
        </Card>
      ) : null}
      {error ? (
        <Card>
          <T color={c.danger}>{error}</T>
          <Button title={s.retry} variant="secondary" onPress={load} />
        </Card>
      ) : null}
    </View>
  );

  return (
    <FlatList
      style={{ backgroundColor: c.bg }}
      contentContainerStyle={{ padding: Space.md, gap: Space.sm }}
      data={calls}
      keyExtractor={(i) => i.id}
      ListHeaderComponent={header}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={load} tintColor={c.primary} />}
      ListEmptyComponent={
        !loading && !error ? (
          <Card style={{ alignItems: "center", paddingVertical: Space.xl }}>
            <T style={{ fontSize: 40 }}>🕵️</T>
            <T v="heading">{s.noCallsTitle}</T>
            <T v="muted" style={{ textAlign: "center" }}>
              {s.noCallsBody}
            </T>
          </Card>
        ) : undefined
      }
      renderItem={({ item }) => <CallRow call={item} />}
    />
  );
}

function CallRow({ call }: { call: CallItem }) {
  const s = useT();
  const c = useColors();
  const title = call.callerNumber
    ? formatPhone(call.callerNumber)
    : call.locked
      ? s.locked
      : s.hiddenNotRevealed;
  const subtitle = call.status === "blocked" ? s.blocked : call.wasHidden ? (call.revealed ? s.hiddenRevealed : s.hiddenNotRevealed) : s.declined;
  return (
    <Pressable
      onPress={() => (call.locked ? router.push("/paywall") : router.push({ pathname: "/call/[id]", params: { id: call.id } }))}
      style={({ pressed }) => [styles.row, { backgroundColor: c.card, borderColor: c.border, opacity: pressed ? 0.85 : 1 }]}>
      <View style={[styles.avatar, { backgroundColor: call.wasHidden ? c.chip : c.border }]}>
        <T>{call.locked ? "🔒" : call.wasHidden ? "🎭" : "📞"}</T>
      </View>
      <View style={{ flex: 1, gap: 2 }}>
        <T style={{ fontWeight: "700" }}>{title}</T>
        {call.callerName ? <T v="small">{call.callerName}</T> : null}
        <View style={{ flexDirection: "row", gap: 6, flexWrap: "wrap" }}>
          <T v="small">{subtitle}</T>
          {call.hasVoicemail ? <Badge label={`▶ ${call.voicemailSeconds ?? ""}s`} tone="primary" /> : null}
        </View>
      </View>
      <T v="small">{timeAgo(call.startedAt)}</T>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", gap: Space.md, padding: Space.md, borderRadius: 14, borderWidth: StyleSheet.hairlineWidth },
  avatar: { width: 44, height: 44, borderRadius: 22, alignItems: "center", justifyContent: "center" },
});
