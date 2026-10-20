import { File, Paths } from "expo-file-system";
import { router, useLocalSearchParams } from "expo-router";
import * as Sharing from "expo-sharing";
import { useState } from "react";
import { Button, Card, Row, Screen, T } from "@/components/ui";
import { useColors } from "@/constants/theme";
import { api, ApiError } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import { formatPhone } from "@/lib/format";
import { useT } from "@/lib/i18n";

type Range = "all" | "30d";

export default function Report() {
  const s = useT();
  const c = useColors();
  const { number } = useLocalSearchParams<{ number?: string }>();
  const [range, setRange] = useState<Range>("all");
  const [busy, setBusy] = useState<"pdf" | "csv" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const make = async (kind: "pdf" | "csv") => {
    setBusy(kind);
    setError(null);
    try {
      const r = await api<{ id: string; pdfUrl: string; csvUrl: string }>("/v1/reports", {
        body: { ...(range === "30d" ? { from: Date.now() - 30 * 86_400_000 } : {}), ...(number ? { number } : {}) },
      });
      const target = new File(Paths.cache, `call-report-${r.id.slice(0, 8)}.${kind}`);
      if (target.exists) target.delete();
      const file = await File.downloadFileAsync(kind === "pdf" ? r.pdfUrl : r.csvUrl, target);
      await Sharing.shareAsync(file.uri, {
        mimeType: kind === "pdf" ? "application/pdf" : "text/csv",
        dialogTitle: s.reportTitle,
        UTI: kind === "pdf" ? "com.adobe.pdf" : "public.comma-separated-values-text",
      });
    } catch (e) {
      if (e instanceof ApiError && e.status === 402) router.push("/paywall");
      else setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  return (
    <Screen edges={["bottom"]}>
      <T v="muted">{s.reportBody}</T>
      {number ? (
        <Card>
          <T v="small">{s.reportNumber}</T>
          <T v="heading">{formatPhone(number)}</T>
        </Card>
      ) : null}
      <Row title={s.reportAll} selected={range === "all"} onPress={() => setRange("all")} />
      <Row title={s.report30} selected={range === "30d"} onPress={() => setRange("30d")} />
      <Button title={s.createPdf} onPress={() => make("pdf")} loading={busy === "pdf"} disabled={!!busy} />
      <Button title={s.createCsv} variant="secondary" onPress={() => make("csv")} loading={busy === "csv"} disabled={!!busy} />
      {error ? <T color={c.danger}>{error}</T> : null}
    </Screen>
  );
}
