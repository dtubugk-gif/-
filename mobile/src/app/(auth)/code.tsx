import { useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { Button, Field, Screen, T } from "@/components/ui";
import { api, type Me } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import { formatPhone } from "@/lib/format";
import { getLocale, useT } from "@/lib/i18n";
import { useSession } from "@/lib/session";

export default function Code() {
  const s = useT();
  const { signIn } = useSession();
  const { phone, country, method } = useLocalSearchParams<{ phone: string; country: string; method?: string }>();
  const accessCode = method === "access_code";
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resent, setResent] = useState(false);

  const verify = async (value = code) => {
    if (accessCode ? value.length < 4 : value.length !== 6) return;
    setBusy(true);
    setError(null);
    try {
      const res = await api<{ token: string; user: Me }>("/v1/auth/verify", {
        body: {
          phone,
          country,
          code: value,
          locale: getLocale(),
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        },
      });
      await signIn(res.token, res.user);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  const resend = async () => {
    try {
      await api("/v1/auth/start", { body: { phone, country } });
      setResent(true);
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <Screen edges={["bottom"]}>
      <T v="title">{accessCode ? s.accessCodeTitle : s.codeTitle}</T>
      <T v="muted">{accessCode ? s.accessCodeBody : s.codeBody(formatPhone(phone))}</T>
      {accessCode ? (
        <Field
          value={code}
          onChangeText={setCode}
          placeholder={s.accessCodePlaceholder}
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          autoFocus
          onSubmitEditing={() => verify()}
        />
      ) : (
        <Field
          value={code}
          onChangeText={(v) => {
            const digits = v.replace(/\D/g, "").slice(0, 6);
            setCode(digits);
            if (digits.length === 6) void verify(digits);
          }}
          keyboardType="number-pad"
          textContentType="oneTimeCode"
          autoComplete="sms-otp"
          autoFocus
          maxLength={6}
          style={{ fontSize: 28, letterSpacing: 8, textAlign: "center" }}
        />
      )}
      {error ? <T color="#F05252">{error}</T> : null}
      <Button title={s.verify} onPress={() => verify()} loading={busy} disabled={accessCode ? code.length < 4 : code.length !== 6} />
      {accessCode ? null : <Button title={resent ? "✓" : s.resend} variant="ghost" onPress={resend} disabled={resent} />}
    </Screen>
  );
}
