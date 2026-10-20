import { AudioModule, RecordingPresets, setAudioModeAsync, useAudioRecorder, useAudioRecorderState } from "expo-audio";
import { useState } from "react";
import { Platform } from "react-native";
import { Button, Card, Field, Screen, T } from "@/components/ui";
import { useColors } from "@/constants/theme";
import { api, type Me } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import { useT } from "@/lib/i18n";
import { useSession } from "@/lib/session";

export default function Greeting() {
  const s = useT();
  const c = useColors();
  const { me, setMe } = useSession();
  const [text, setText] = useState(me?.greetingText ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const rec = useAudioRecorderState(recorder);

  const saveText = async () => {
    setBusy(true);
    try {
      setMe(await api<Me>("/v1/me", { method: "PATCH", body: { greetingText: text.trim() || null } }));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const start = async () => {
    const perm = await AudioModule.requestRecordingPermissionsAsync();
    if (!perm.granted) return;
    await setAudioModeAsync({ playsInSilentMode: true, allowsRecording: true });
    await recorder.prepareToRecordAsync();
    recorder.record();
  };

  const stopAndUpload = async () => {
    await recorder.stop();
    await setAudioModeAsync({ allowsRecording: false });
    const uri = recorder.uri;
    if (!uri) return;
    setBusy(true);
    try {
      const form = new FormData();
      form.append("file", { uri, name: "greeting.m4a", type: Platform.OS === "ios" ? "audio/x-m4a" : "audio/mp4" } as unknown as Blob);
      setMe(await api<Me>("/v1/me/greeting", { method: "PUT", form }));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const removeRecording = async () => setMe(await api<Me>("/v1/me/greeting", { method: "DELETE" }));

  return (
    <Screen edges={["bottom"]}>
      <Card>
        <T v="heading">{s.greetingText}</T>
        <Field value={text} onChangeText={setText} placeholder={s.greetingTextPlaceholder} multiline maxLength={400} style={{ minHeight: 100, paddingTop: 12 }} />
        <Button title={s.saveGreeting} onPress={saveText} loading={busy} />
      </Card>
      <Card>
        {me?.hasGreetingAudio ? <T color={c.success}>{s.usingRecorded}</T> : null}
        {rec.isRecording ? (
          <>
            <T v="heading">● {Math.round(rec.durationMillis / 1000)}s</T>
            <Button title={s.stopRecording} onPress={stopAndUpload} />
          </>
        ) : (
          <Button title={s.recordGreeting} variant="secondary" onPress={start} loading={busy} />
        )}
        {me?.hasGreetingAudio ? <Button title={s.removeRecording} variant="ghost" onPress={removeRecording} /> : null}
      </Card>
      {error ? <T color={c.danger}>{error}</T> : null}
    </Screen>
  );
}
