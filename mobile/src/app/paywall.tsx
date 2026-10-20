import { router } from "expo-router";
import { useEffect, useState } from "react";
import type { PurchasesPackage } from "react-native-purchases";
import { Button, Card, Row, Screen, T } from "@/components/ui";
import { useColors } from "@/constants/theme";
import { errorMessage } from "@/lib/errors";
import { useT } from "@/lib/i18n";
import { buy, getPackages, purchasesAvailable, restore } from "@/lib/purchases";
import { useSession } from "@/lib/session";

export default function Paywall() {
  const s = useT();
  const c = useColors();
  const { refresh } = useSession();
  const [packages, setPackages] = useState<PurchasesPackage[]>([]);
  const [selected, setSelected] = useState<PurchasesPackage | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getPackages()
      .then((p) => {
        setPackages(p);
        setSelected(p.find((x) => x.packageType === "ANNUAL") ?? p[0] ?? null);
      })
      .catch((e) => setError(errorMessage(e)));
  }, []);

  /** The server learns about the purchase from RevenueCat's webhook; wait for it to land. */
  const waitForEntitlement = async () => {
    for (let i = 0; i < 15; i++) {
      const me = await refresh();
      if (me?.entitled) return router.back();
      await new Promise((r) => setTimeout(r, 2000));
    }
  };

  const purchase = async () => {
    if (!selected) return;
    setBusy(true);
    setError(null);
    try {
      if (await buy(selected)) await waitForEntitlement();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen edges={["bottom"]}>
      <T style={{ fontSize: 44 }}>🔓</T>
      <T v="title">{s.paywallTitle}</T>
      <T v="muted">{s.paywallBody}</T>
      {!purchasesAvailable ? (
        <Card>
          <T v="muted">{s.paywallUnavailable}</T>
        </Card>
      ) : (
        <>
          {packages.map((p) => (
            <Row
              key={p.identifier}
              title={p.product.title}
              subtitle={`${p.product.priceString}${p.product.introPrice ? ` · ${p.product.introPrice.priceString}` : ""}`}
              selected={selected?.identifier === p.identifier}
              onPress={() => setSelected(p)}
            />
          ))}
          <Button title={s.subscribe} onPress={purchase} loading={busy} disabled={!selected} />
          <Button
            title={s.restore}
            variant="ghost"
            onPress={async () => {
              setBusy(true);
              try {
                await restore();
                await waitForEntitlement();
              } finally {
                setBusy(false);
              }
            }}
          />
        </>
      )}
      {error ? <T color={c.danger}>{error}</T> : null}
    </Screen>
  );
}
