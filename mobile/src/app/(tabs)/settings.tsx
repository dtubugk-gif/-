import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { Alert, I18nManager, View } from "react-native";
import { Button, Card, Row, Screen, T } from "@/components/ui";
import { Space, useColors } from "@/constants/theme";
import { api, getServerUrl, type Me } from "@/lib/api";
import { formatDateTime, formatPhone } from "@/lib/format";
import { useT, type Locale } from "@/lib/i18n";
import { logOutPurchases } from "@/lib/purchases";
import { registerForPush, unregisterPush, type PushState } from "@/lib/push";
import { useSession } from "@/lib/session";

export default function Settings() {
  const s = useT();
  const c = useColors();
  const { me, setMe, signOut } = useSession();
  const [push, setPush] = useState<PushState | null>(null);

  useFocusEffect(
    useCallback(() => {
      void registerForPush(false).then(setPush);
    }, []),
  );

  const setLanguage = async (locale: Locale) => {
    setMe(await api<Me>("/v1/me", { method: "PATCH", body: { locale } }));
    const rtl = locale === "he";
    if (I18nManager.isRTL !== rtl) {
      I18nManager.allowRTL(rtl);
      I18nManager.forceRTL(rtl);
    }
  };

  const doSignOut = async () => {
    await unregisterPush();
    await logOutPurchases();
    await signOut();
  };

  const deleteAccount = () =>
    Alert.alert(s.deleteAccount, s.deleteAccountConfirm, [
      { text: s.cancel, style: "cancel" },
      {
        text: s.delete,
        style: "destructive",
        onPress: async () => {
          await api("/v1/me", { method: "DELETE" });
          await logOutPurchases();
          await signOut();
        },
      },
    ]);

  if (!me) return null;
  const sub =
    me.entitlementMode === "free"
      ? s.subscriptionFree
      : me.entitled && me.subscriptionExpiresAt
        ? s.subscriptionActive(formatDateTime(me.subscriptionExpiresAt))
        : s.subscriptionNone;

  return (
    <Screen edges={[]}>
      <Card>
        <T v="small">{s.account}</T>
        <T v="heading">{formatPhone(me.phone)}</T>
      </Card>

      <Card>
        <T v="small">{s.subscription}</T>
        <T color={me.entitled ? c.success : c.text}>{sub}</T>
        {!me.entitled ? <Button title={s.manageSubscription} onPress={() => router.push("/paywall")} /> : null}
      </Card>

      <Card>
        <T v="small">{s.notifications}</T>
        <T color={push === "granted" ? c.success : c.warning}>{push === "granted" ? s.notificationsOn : s.notificationsOff}</T>
        {push === "denied" ? <Button title={s.enable} variant="secondary" onPress={() => registerForPush(true).then(setPush)} /> : null}
      </Card>

      <View style={{ gap: Space.sm }}>
        <Row title={s.greeting} right={<T v="muted">›</T>} onPress={() => router.push("/greeting")} />
        <Row title={s.blocklist} right={<T v="muted">›</T>} onPress={() => router.push("/blocklist")} />
        <Row title={s.reports} right={<T v="muted">›</T>} onPress={() => router.push("/report")} />
      </View>

      <T v="small">{s.language}</T>
      <View style={{ flexDirection: "row", gap: Space.sm }}>
        <Button title="English" variant={me.locale === "en" ? "primary" : "secondary"} onPress={() => setLanguage("en")} style={{ flex: 1 }} />
        <Button title="עברית" variant={me.locale === "he" ? "primary" : "secondary"} onPress={() => setLanguage("he")} style={{ flex: 1 }} />
      </View>

      <Row title={s.server} subtitle={getServerUrl()} onPress={() => router.push("/server")} />

      <Button title={s.signOut} variant="secondary" onPress={doSignOut} />
      <Button title={s.deleteAccount} variant="danger" onPress={deleteAccount} />
    </Screen>
  );
}
