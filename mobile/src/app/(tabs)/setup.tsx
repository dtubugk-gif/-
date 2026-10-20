import * as Clipboard from "expo-clipboard";
import * as Haptics from "expo-haptics";
import { useFocusEffect } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { Linking, Platform, View } from "react-native";
import { Badge, Button, Card, Row, Screen, T } from "@/components/ui";
import { Space, useColors } from "@/constants/theme";
import { api, ApiError, type Carrier, type DialStep, type Instructions, type Me } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import { formatPhone } from "@/lib/format";
import { useT } from "@/lib/i18n";
import { useSession } from "@/lib/session";

type TestState =
  | { phase: "idle" }
  | { phase: "calling"; id: string }
  | { phase: "verified" }
  | { phase: "failed"; reason: string | null };

export default function Setup() {
  const s = useT();
  const c = useColors();
  const { me, setMe, refresh } = useSession();
  const [carriers, setCarriers] = useState<Carrier[]>([]);
  const [instr, setInstr] = useState<Instructions | null>(null);
  const [noNumbers, setNoNumbers] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [test, setTest] = useState<TestState>({ phase: "idle" });
  const [showCarriers, setShowCarriers] = useState(false);
  const poll = useRef<ReturnType<typeof setInterval> | null>(null);

  const loadInstructions = useCallback(async () => {
    try {
      setInstr(await api<Instructions>("/v1/setup/instructions"));
      setNoNumbers(false);
    } catch (err) {
      if (err instanceof ApiError && err.code === "no_numbers_available") setNoNumbers(true);
      else setError(errorMessage(err));
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void api<Carrier[]>("/v1/carriers").then(setCarriers).catch(() => {});
      void loadInstructions();
    }, [loadInstructions]),
  );

  useEffect(() => () => {
    if (poll.current) clearInterval(poll.current);
  }, []);

  const update = async (patch: Partial<Pick<Me, "carrier" | "forwardingMode">>) => {
    try {
      setMe(await api<Me>("/v1/me", { method: "PATCH", body: patch }));
      setTest({ phase: "idle" });
      await loadInstructions();
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  const startTest = async () => {
    setError(null);
    try {
      const { verificationId } = await api<{ verificationId: string }>("/v1/setup/verify", { method: "POST" });
      setTest({ phase: "calling", id: verificationId });
      if (poll.current) clearInterval(poll.current);
      poll.current = setInterval(async () => {
        try {
          const st = await api<{ status: string; reason: string | null }>(`/v1/setup/verify/${verificationId}`);
          if (st.status === "calling") return;
          if (poll.current) clearInterval(poll.current);
          if (st.status === "verified") {
            void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
            setTest({ phase: "verified" });
            void refresh();
          } else {
            void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
            setTest({ phase: "failed", reason: st.reason });
          }
        } catch {
          /* keep polling */
        }
      }, 2000);
    } catch {
      setTest({ phase: "failed", reason: "dial_failed" });
    }
  };

  const verified = !!me?.forwardingVerifiedAt || test.phase === "verified";
  const carrier = carriers.find((x) => x.id === me?.carrier);
  const failText = (reason: string | null) =>
    ({ answered: s.failAnswered, not_declined: s.failNotDeclined, dial_failed: s.failDial })[reason ?? ""] ?? s.failNotForwarded;

  return (
    <Screen edges={[]}>
      {verified ? (
        <Card style={{ borderColor: c.success }}>
          <T v="heading" color={c.success}>
            {s.verified}
          </T>
          <T v="muted">{s.verifiedBody}</T>
        </Card>
      ) : null}
      {error ? <T color={c.danger}>{error}</T> : null}

      <T v="heading">{s.step1}</T>
      <Row
        title={carrier?.name ?? "—"}
        subtitle={carrier?.manual ? s.manualCarrier : undefined}
        right={<T v="muted">▾</T>}
        onPress={() => setShowCarriers(!showCarriers)}
        selected={!me?.carrier}
      />
      {showCarriers ? (
        <View style={{ gap: Space.sm }}>
          {carriers.map((x) => (
            <Row
              key={x.id}
              title={x.name}
              selected={x.id === me?.carrier}
              onPress={() => {
                setShowCarriers(false);
                void update({ carrier: x.id });
              }}
            />
          ))}
        </View>
      ) : null}

      <T v="heading">{s.step2}</T>
      <Row
        title={s.modeDecline}
        subtitle={s.modeDeclineBody}
        selected={me?.forwardingMode === "decline"}
        onPress={() => update({ forwardingMode: "decline" })}
      />
      <Row
        title={s.modeFull}
        subtitle={s.modeFullBody}
        selected={me?.forwardingMode === "full"}
        onPress={() => update({ forwardingMode: "full" })}
      />

      <T v="heading">{s.step3}</T>
      {noNumbers ? (
        <Card>
          <T v="muted">{s.noNumbers}</T>
        </Card>
      ) : instr ? (
        <Card>
          <T v="small">{s.yourNumber}</T>
          <T v="mono" selectable>
            {formatPhone(instr.forwardingNumber)}
          </T>
          {instr.manual ? null : instr.enable.map((st) => <DialCard key={st.code} step={st} />)}
          {instr.notes.map((n) => (
            <T key={n} v="small">
              {n}
            </T>
          ))}
        </Card>
      ) : null}

      <T v="heading">{s.step4}</T>
      <Card>
        <T v="muted">{s.testBody}</T>
        {test.phase === "calling" ? (
          <View style={{ gap: Space.sm }}>
            <Badge label="●  LIVE" tone="primary" />
            <T v="heading">{s.testing}</T>
          </View>
        ) : null}
        {test.phase === "failed" ? <T color={c.danger}>{failText(test.reason)}</T> : null}
        <Button
          title={test.phase === "failed" ? s.tryAgain : s.testNow}
          onPress={startTest}
          loading={test.phase === "calling"}
          disabled={!instr}
        />
      </Card>

      {me?.demoLineNumber ? (
        <Card>
          <T v="heading">{s.demoTitle}</T>
          <T v="muted">{s.demoBody(formatPhone(me.demoLineNumber))}</T>
        </Card>
      ) : null}

      {instr && instr.disable.length ? (
        <Card>
          <T v="heading">{s.turnOffTitle}</T>
          <T v="muted">{s.turnOffBody}</T>
          {instr.disable.map((st) => (
            <DialCard key={st.code} step={st} />
          ))}
        </Card>
      ) : null}
    </Screen>
  );
}

function DialCard({ step }: { step: DialStep }) {
  const s = useT();
  const c = useColors();
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await Clipboard.setStringAsync(step.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };
  return (
    <View style={{ gap: Space.sm, paddingTop: Space.sm, borderTopWidth: 1, borderTopColor: c.border }}>
      <T v="small">{step.label}</T>
      <T v="mono" selectable style={{ fontSize: 20 }}>
        {step.code}
      </T>
      {Platform.OS === "android" ? (
        <>
          <Button title={s.openDialer} onPress={() => Linking.openURL(step.telUri)} />
          <T v="small">{s.androidDialHint}</T>
        </>
      ) : (
        <>
          <Button title={copied ? s.copied : s.copyCode} onPress={copy} />
          <T v="small">{s.iosDialHint}</T>
        </>
      )}
    </View>
  );
}
