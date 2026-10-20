import PDFDocument from "pdfkit";
import { formatInternational } from "../lib/phone.js";
import { sha256Hex } from "../lib/crypto.js";

export interface ReportCall {
  id: string;
  started_at: number;
  ended_at: number | null;
  caller_number: string | null;
  caller_source: string;
  caller_name: string | null;
  was_hidden: number;
  status: string;
  recording_seconds: number | null;
  note: string | null;
}

export interface ReportMeta {
  reportId: string;
  accountPhone: string;
  timezone: string;
  generatedAt: number;
  rangeFrom: number | null;
  rangeTo: number | null;
  verifyUrl: string;
}

const SOURCE_LABEL: Record<string, string> = {
  stir_shaken: "Carrier-signed (STIR/SHAKEN)",
  p_asserted_identity: "Network-asserted identity",
  remote_party_id: "Network party ID",
  from: "Toll-free ANI",
  p_charge_info: "Charge number",
  none: "Not delivered",
};

function fmt(ts: number, tz: string): string {
  try {
    return new Intl.DateTimeFormat("en-US", {
      timeZone: tz,
      year: "numeric",
      month: "short",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      timeZoneName: "short",
    }).format(ts);
  } catch {
    return new Date(ts).toISOString();
  }
}

/** Canonical form of the records; its hash is printed on the report and stored for verification. */
export function recordsDigest(calls: ReportCall[]): string {
  const canonical = calls
    .map((c) => [c.id, c.started_at, c.ended_at ?? "", c.caller_number ?? "", c.was_hidden, c.status, c.recording_seconds ?? ""].join("|"))
    .join("\n");
  return sha256Hex(canonical);
}

function csvCell(v: string | number | null): string {
  let s = v == null ? "" : String(v);
  // Neutralize spreadsheet formula injection (a caller name like "=HYPERLINK(...)").
  if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`;
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

export function buildCsv(calls: ReportCall[], tz: string): string {
  const rows = [
    ["call_id", "date_time_local", "date_time_utc", "caller_number", "caller_name", "number_was_hidden", "identification_method", "status", "voicemail_seconds", "note"],
    ...calls.map((c) => [
      c.id,
      fmt(c.started_at, tz),
      new Date(c.started_at).toISOString(),
      c.caller_number,
      c.caller_name,
      c.was_hidden ? "yes" : "no",
      SOURCE_LABEL[c.caller_source] ?? c.caller_source,
      c.status,
      c.recording_seconds,
      c.note,
    ]),
  ];
  return "﻿" + rows.map((r) => r.map((v) => csvCell(v as string | number | null)).join(",")).join("\r\n") + "\r\n";
}

export async function buildPdf(calls: ReportCall[], meta: ReportMeta, digest: string): Promise<Buffer> {
  const doc = new PDFDocument({ size: "LETTER", margin: 48, info: { Title: `Call Record Report ${meta.reportId}` } });
  const chunks: Buffer[] = [];
  doc.on("data", (c: Buffer) => chunks.push(c));
  const done = new Promise<Buffer>((res) => doc.on("end", () => res(Buffer.concat(chunks))));

  const tz = meta.timezone;
  doc.font("Helvetica-Bold").fontSize(18).text("Call Record Report");
  doc.moveDown(0.3).font("Helvetica").fontSize(10).fillColor("#444");
  doc.text(`Account phone number: ${formatInternational(meta.accountPhone)}`);
  doc.text(`Generated: ${fmt(meta.generatedAt, tz)}  (${new Date(meta.generatedAt).toISOString()})`);
  const period =
    meta.rangeFrom || meta.rangeTo
      ? `${meta.rangeFrom ? fmt(meta.rangeFrom, tz) : "beginning"} — ${meta.rangeTo ? fmt(meta.rangeTo, tz) : "now"}`
      : "All recorded calls";
  doc.text(`Period: ${period}`);
  doc.text(`Report ID: ${meta.reportId}`);
  doc.fillColor("#000").moveDown();

  const hidden = calls.filter((c) => c.was_hidden);
  const byNumber = new Map<string, { count: number; first: number; last: number }>();
  for (const c of calls) {
    if (!c.caller_number) continue;
    const e = byNumber.get(c.caller_number) ?? { count: 0, first: c.started_at, last: c.started_at };
    e.count++;
    e.first = Math.min(e.first, c.started_at);
    e.last = Math.max(e.last, c.started_at);
    byNumber.set(c.caller_number, e);
  }

  doc.font("Helvetica-Bold").fontSize(13).text("Summary");
  doc.font("Helvetica").fontSize(10);
  doc.text(`Total calls recorded: ${calls.length}`);
  doc.text(`Calls where the caller hid their number: ${hidden.length}`);
  doc.text(`Hidden calls where the real number was identified: ${hidden.filter((c) => c.caller_number).length}`);
  doc.text(`Distinct identified numbers: ${byNumber.size}`);
  doc.moveDown(0.5);

  const top = [...byNumber.entries()].sort((a, b) => b[1].count - a[1].count).slice(0, 10);
  if (top.length) {
    doc.font("Helvetica-Bold").text("Most frequent callers");
    doc.font("Helvetica");
    for (const [n, e] of top) {
      doc.text(`${formatInternational(n)} — ${e.count} call(s), first ${fmt(e.first, tz)}, last ${fmt(e.last, tz)}`);
    }
    doc.moveDown();
  }

  doc.font("Helvetica-Bold").fontSize(13).text("Detailed call log");
  doc.moveDown(0.3).fontSize(9);
  const cols = [
    { title: "#", w: 24 },
    { title: "Date / time", w: 150 },
    { title: "Caller number", w: 105 },
    { title: "Hidden", w: 40 },
    { title: "Identified via", w: 110 },
    { title: "Voicemail", w: 55 },
  ];
  const x0 = doc.page.margins.left;
  const row = (vals: string[], bold = false) => {
    if (doc.y > doc.page.height - doc.page.margins.bottom - 40) doc.addPage();
    const y = doc.y;
    let x = x0;
    doc.font(bold ? "Helvetica-Bold" : "Helvetica");
    vals.forEach((v, i) => {
      doc.text(v, x, y, { width: cols[i]!.w - 4, lineBreak: true });
      x += cols[i]!.w;
    });
    doc.x = x0;
    doc.y = Math.max(doc.y, y + 12);
  };
  row(cols.map((c) => c.title), true);
  calls.forEach((c, i) => {
    row([
      String(i + 1),
      fmt(c.started_at, tz),
      c.caller_number ? formatInternational(c.caller_number) : "Not delivered",
      c.was_hidden ? "Yes" : "No",
      SOURCE_LABEL[c.caller_source] ?? c.caller_source,
      c.recording_seconds ? `${c.recording_seconds}s` : "—",
    ]);
    if (c.note) {
      doc.font("Helvetica-Oblique").text(`Note: ${c.note}`, x0 + 24, doc.y, { width: 460 });
      doc.x = x0;
    }
  });

  doc.moveDown(1.5).font("Helvetica-Bold").fontSize(10).text("Record integrity", x0);
  doc.font("Helvetica").fontSize(8.5).fillColor("#333");
  doc.text(`SHA-256 of the records in this report: ${digest}`);
  doc.text(`Verify this report: ${meta.verifyUrl}`);
  doc.moveDown(0.5);
  doc.text(
    "Caller numbers were captured automatically from telephone network signaling when calls declined on the account holder's phone were forwarded to the service's toll-free line. The service cannot detect numbers that a caller has spoofed (falsified), and a number belongs to a line, not necessarily to a specific person. Timestamps are recorded by the server at the moment the network delivered the call.",
  );

  doc.end();
  return done;
}
