import { getLocales } from "expo-localization";
import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { View } from "react-native";
import { Button, Field, Row, Screen, T } from "@/components/ui";
import { api, getServerUrl } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import { formatAsYouType, toE164 } from "@/lib/format";
import { useT } from "@/lib/i18n";

const COUNTRIES = [
  { code: "US", label: "🇺🇸 United States (+1)" },
  { code: "IL", label: "🇮🇱 ישראל (+972)" },
  { code: "GB", label: "🇬🇧 United Kingdom (+44)" },
  { code: "CA", label: "🇨🇦 Canada (+1)" },
];

export default function Phone() {
  const s = useT();
  const region = getLocales()[0]?.regionCode ?? "US";
  const [country, setCountry] = useState(COUNTRIES.some((c) => c.code === region) ? region : "US");
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [picking, setPicking] = useState(false);
  const [noServer, setNoServer] = useState(!getServerUrl());
  // Re-check after returning from the server screen.
  useFocusEffect(useCallback(() => setNoServer(!getServerUrl()), []));
  const e164 = toE164(input, country);

  const submit = async () => {
    if (!e164) return setError(s.errorInvalidPhone);
    setBusy(true);
    setError(null);
    try {
      const res = await api<{ method: "sms" | "access_code" }>("/v1/auth/start", { body: { phone: e164, country } });
      router.push({ pathname: "/(auth)/code", params: { phone: e164, country, method: res.method ?? "sms" } });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen edges={["bottom"]}>
      <T v="title">{s.phoneTitle}</T>
      <T v="muted">{s.phoneBody}</T>
      <Row
        title={COUNTRIES.find((c) => c.code === country)!.label}
        right={<T v="muted">▾</T>}
        onPress={() => setPicking(!picking)}
      />
      {picking ? (
        <View style={{ gap: 8 }}>
          {COUNTRIES.map((c) => (
            <Row
              key={c.code}
              title={c.label}
              selected={c.code === country}
              onPress={() => {
                setCountry(c.code);
                setPicking(false);
              }}
            />
          ))}
        </View>
      ) : null}
      <Field
        value={input}
        onChangeText={(v) => setInput(formatAsYouType(v, country))}
        placeholder={s.phonePlaceholder}
        keyboardType="phone-pad"
        textContentType="telephoneNumber"
        autoComplete="tel"
        autoFocus
        onSubmitEditing={submit}
      />
      {noServer ? (
        <>
          <T color="#F05252">{s.errorNoServer}</T>
          <Button title={s.setServer} variant="secondary" onPress={() => router.push("/server")} />
        </>
      ) : error ? (
        <T color="#F05252">{error}</T>
      ) : null}
      <Button title={s.sendCode} onPress={submit} loading={busy} disabled={!e164 || noServer} />
    </Screen>
  );
}
