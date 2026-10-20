import { useAudioPlayer, useAudioPlayerStatus } from "expo-audio";
import * as Clipboard from "expo-clipboard";
import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useState } from "react";
import { Alert, Linking, View } from "react-native";
import { Badge, Button, Card, Field, Screen, T } from "@/components/ui";
import { Space, useColors } from "@/constants/theme";
import { api, type CallDetail } from "@/lib/api";
import { contactName } from "@/lib/contacts";
import { errorMessage } from "@/lib/errors";
import { formatDateTime, formatPhone } from "@/lib/format";
import { useT } from "@/lib/i18n";
import { useSession } from "@/lib/session";

const SOURCE: Record<string, string> = {
  stir_shaken: "STIR/SHAKEN",
  p_asserted_identity: "Network identity",
  remote_party_id: "Network identity",
  from: "Toll-free ANI",
  p_charge_info: "Charge number",
  none: "—",
};

export default function CallScreen() {
  const s = useT();
  const c = useColors();
  const { me } = useSession();
  const { id } = useLocalSearchParams<{ id: string }>();
  const [call, setCall] = useState<CallDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [contact, setContact] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    api<CallDetail>(`/v1/calls/${id}`)
      .then((d) => {
        setCall(d);
        setNote(d.note ?? "");
        if (d.callerNumber && me) void contactName(d.callerNumber, me.country).then(setContact);
      })
      .catch((e) => setError(errorMessage(e)));
  }, [id, me]);

  if (error) return <Screen edges={["bottom"]}><T color={c.danger}>{error}</T></Screen>;
  if (!call) return null;

  const block = async () => {
    await api("/v1/blocklist", { body: { callId: call.id } });
    Alert.alert("✓", s.blocked);
  };

  const remove = () =>
    Alert.alert(s.deleteCall, s.deleteCallConfirm, [
      { text: s.cancel, style: "cancel" },
      {
        text: s.delete,
        style: "destructive",
        onPress: async () => {
          await api(`/v1/calls/${call.id}`, { method: "DELETE" });
          router.back();
        },
      },
    ]);

  return (
    <Screen edges={["bottom"]}>
      <Card style={{ alignItems: "center", paddingVertical: Space.lg }}>
        <View style={{ flexDirection: "row", gap: Space.sm }}>
          {call.wasHidden ? <Badge label={s.hidden} tone="warning" /> : null}
          {call.status === "blocked" ? <Badge label={s.blocked} tone="danger" /> : null}
        </View>
        <T v="small">{s.realNumber}</T>
        <T v="mono" selectable style={{ fontSize: 30 }}>
          {call.callerNumber ? formatPhone(call.callerNumber) : "—"}
        </T>
        {contact ? <T color={c.success}>👤 {contact} · {s.inContacts}</T> : null}
        {call.callerName ? <T v="muted">{call.callerName}</T> : null}
        <T v="small">{formatDateTime(call.startedAt)}</T>
      </Card>

      <Card>
        <T v="muted">{call.callerNumber ? (call.wasHidden ? s.callerHid : s.callerNotHid) : s.notRevealedExplain}</T>
        {call.callerNumber ? (
          <>
            <T v="small">
              {s.identifiedVia}: {SOURCE[call.source] ?? call.source}
            </T>
            <T v="small">{s.callsFromNumber(call.callsFromThisNumber)}</T>
          </>
        ) : null}
      </Card>

      {call.recordingUrl ? <Voicemail url={call.recordingUrl} seconds={call.voicemailSeconds} /> : null}

      {call.callerNumber ? (
        <View style={{ flexDirection: "row", gap: Space.sm }}>
          <Button title={s.callBack} onPress={() => Linking.openURL(`tel:${call.callerNumber}`)} style={{ flex: 1 }} />
          <Button
            title={copied ? s.copied : s.copy}
            variant="secondary"
            onPress={async () => {
              await Clipboard.setStringAsync(call.callerNumber!);
              setCopied(true);
            }}
            style={{ flex: 1 }}
          />
        </View>
      ) : null}

      <Card>
        <Field value={note} onChangeText={setNote} placeholder={s.addNote} multiline style={{ minHeight: 80, paddingTop: 12 }} />
        <Button
          title={s.saveNote}
          variant="secondary"
          onPress={() => api(`/v1/calls/${call.id}`, { method: "PATCH", body: { note: note || null } })}
        />
      </Card>

      {call.callerNumber ? (
        <>
          <Button title={s.reportNumber} variant="secondary" onPress={() => router.push({ pathname: "/report", params: { number: call.callerNumber! } })} />
          <Button title={s.block} variant="danger" onPress={block} />
        </>
      ) : null}
      <Button title={s.deleteCall} variant="ghost" onPress={remove} />
    </Screen>
  );
}

function Voicemail({ url, seconds }: { url: string; seconds: number | null }) {
  const s = useT();
  const player = useAudioPlayer(url);
  const status = useAudioPlayerStatus(player);
  const pos = Math.round(status.currentTime ?? 0);
  return (
    <Card>
      <T v="heading">{s.voicemail}</T>
      <T v="small">
        {pos}s / {seconds ?? Math.round(status.duration ?? 0)}s
      </T>
      <Button
        title={status.playing ? s.pause : s.playVoicemail}
        onPress={() => {
          if (status.playing) player.pause();
          else {
            if (status.didJustFinish || (status.duration && pos >= Math.floor(status.duration))) player.seekTo(0);
            player.play();
          }
        }}
      />
    </Card>
  );
}
