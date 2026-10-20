import { useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { View } from "react-native";
import { Button, Field, Row, Screen, T } from "@/components/ui";
import { Space } from "@/constants/theme";
import { api } from "@/lib/api";
import { formatPhone, toE164 } from "@/lib/format";
import { useT } from "@/lib/i18n";
import { useSession } from "@/lib/session";

export default function Blocklist() {
  const s = useT();
  const { me } = useSession();
  const [numbers, setNumbers] = useState<{ number: string }[]>([]);
  const [input, setInput] = useState("");

  const load = useCallback(() => {
    api<{ numbers: { number: string }[] }>("/v1/blocklist")
      .then((r) => setNumbers(r.numbers))
      .catch(() => {});
  }, []);
  useFocusEffect(load);

  const e164 = toE164(input, me?.country ?? "US");

  return (
    <Screen edges={["bottom"]}>
      <T v="muted">{s.blocklistBody}</T>
      <View style={{ flexDirection: "row", gap: Space.sm }}>
        <Field value={input} onChangeText={setInput} keyboardType="phone-pad" placeholder={s.phonePlaceholder} style={{ flex: 1 }} />
        <Button
          title={s.addNumber}
          disabled={!e164}
          onPress={async () => {
            await api("/v1/blocklist", { body: { number: e164 } });
            setInput("");
            load();
          }}
        />
      </View>
      {numbers.length === 0 ? <T v="muted">{s.noneBlocked}</T> : null}
      {numbers.map((n) => (
        <Row
          key={n.number}
          title={formatPhone(n.number)}
          right={
            <Button
              title={s.unblock}
              variant="ghost"
              onPress={async () => {
                await api(`/v1/blocklist/${encodeURIComponent(n.number)}`, { method: "DELETE" });
                load();
              }}
            />
          }
        />
      ))}
    </Screen>
  );
}
