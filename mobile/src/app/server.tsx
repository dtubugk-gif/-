import { router } from "expo-router";
import { useState } from "react";
import { Button, Field, Screen, T } from "@/components/ui";
import { getServerUrl, setServerUrl } from "@/lib/api";
import { useT } from "@/lib/i18n";

export default function Server() {
  const s = useT();
  const [url, setUrl] = useState(getServerUrl());
  const valid = /^https?:\/\/[^\s]+$/.test(url.trim());
  return (
    <Screen edges={["bottom"]}>
      <T v="muted">{s.serverBody}</T>
      <Field value={url} onChangeText={setUrl} autoCapitalize="none" autoCorrect={false} keyboardType="url" placeholder="https://api.example.com" />
      <Button
        title={s.save}
        disabled={!valid}
        onPress={async () => {
          await setServerUrl(url);
          router.back();
        }}
      />
    </Screen>
  );
}
