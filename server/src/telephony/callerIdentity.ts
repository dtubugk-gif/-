import { extractUserPart, looksAnonymous, toE164 } from "../lib/phone.js";

export interface SipHeader {
  name: string;
  value: string;
}

/** Everything the provider told us about an inbound call, normalized. */
export interface InboundSignals {
  from: string | null;
  to: string;
  callerIdName: string | null;
  headers: SipHeader[];
}

export type CallerSource =
  | "stir_shaken"
  | "p_asserted_identity"
  | "remote_party_id"
  | "from"
  | "p_charge_info"
  | "none";

export interface CallerIdentity {
  /** E.164 number of the real caller, or null if the network gave us nothing usable. */
  number: string | null;
  source: CallerSource;
  /** True when the caller asked the network to hide their number (*67, "Private", etc.). */
  wasHidden: boolean;
  /** STIR/SHAKEN attestation level of the originating carrier, when available. */
  attestation: "A" | "B" | "C" | null;
  callerName: string | null;
}

export interface Diversion {
  number: string;
  reason: string | null;
}

export function header(headers: SipHeader[], name: string): string[] {
  const lname = name.toLowerCase();
  return headers.filter((h) => h.name.toLowerCase() === lname).map((h) => h.value);
}

interface PassportPayload {
  attest?: string;
  orig?: { tn?: string | string[] };
  dest?: { tn?: string[] };
  div?: { tn?: string | string[] };
}

function decodeJwtPayload(jwt: string): PassportPayload | null {
  const parts = jwt.split(".");
  if (parts.length < 2) return null;
  try {
    return JSON.parse(Buffer.from(parts[1]!, "base64url").toString("utf8")) as PassportPayload;
  } catch {
    return null;
  }
}

interface ParsedPassport {
  ppt: string;
  payload: PassportPayload;
}

/**
 * Parses SIP `Identity` headers (RFC 8224). Each value is `<jwt>;info=<...>;alg=ES256;ppt=shaken`.
 * SHAKEN PASSporTs sign the originating TN even when the caller requested privacy, which makes
 * them the most trustworthy unmasking signal when a carrier passes them through.
 */
export function parsePassports(headers: SipHeader[]): ParsedPassport[] {
  const out: ParsedPassport[] = [];
  for (const value of header(headers, "Identity")) {
    for (const entry of value.split(/,(?=\s*ey)/)) {
      const [token, ...params] = entry.trim().split(";");
      if (!token) continue;
      const payload = decodeJwtPayload(token.replace(/^<|>$/g, ""));
      if (!payload) continue;
      const pptParam = params.find((p) => p.trim().toLowerCase().startsWith("ppt="));
      const ppt = pptParam ? pptParam.split("=")[1]!.replace(/"/g, "").trim().toLowerCase() : "shaken";
      out.push({ ppt, payload });
    }
  }
  return out;
}

function first(v: string | string[] | undefined): string | undefined {
  return Array.isArray(v) ? v[0] : v;
}

/** Diversion (RFC 5806) and History-Info (RFC 7044) tell us which subscriber forwarded the call. */
export function parseDiversions(headers: SipHeader[], defaultCountry: string): Diversion[] {
  const out: Diversion[] = [];
  for (const value of header(headers, "Diversion")) {
    for (const entry of value.split(/,(?=\s*<|\s*"|\s*sip|\s*tel)/i)) {
      const number = toE164(entry, defaultCountry);
      if (!number) continue;
      const reason = entry.match(/reason=("?)([^;"\s]+)\1/i)?.[2] ?? null;
      out.push({ number, reason });
    }
  }
  for (const value of header(headers, "History-Info")) {
    for (const entry of value.split(/,(?=\s*<)/)) {
      const number = toE164(entry, defaultCountry);
      if (number) out.push({ number, reason: entry.match(/cause%3D(\d+)/i)?.[1] ?? null });
    }
  }
  for (const p of parsePassports(headers)) {
    if (p.ppt !== "div") continue;
    const tn = first(p.payload.div?.tn);
    const number = tn ? toE164(`+${tn.replace(/^\+/, "")}`, defaultCountry) : null;
    if (number) out.push({ number, reason: "div-passport" });
  }
  return out;
}

function privacyRequested(signals: InboundSignals): boolean {
  for (const v of header(signals.headers, "Privacy")) {
    const tokens = v.toLowerCase().split(/[;,\s]+/);
    if (tokens.some((t) => t === "id" || t === "user" || t === "header" || t === "full")) return true;
  }
  for (const v of header(signals.headers, "Remote-Party-ID")) {
    if (/privacy=(full|uri|name)/i.test(v)) return true;
  }
  if (signals.callerIdName && looksAnonymous(signals.callerIdName)) return true;
  if (looksAnonymous(signals.from)) return true;
  return false;
}

export interface IdentityContext {
  /** The subscriber whose phone forwarded the call; never report them as the caller. */
  ownerNumber: string | null;
  /** Our own numbers (forwarding pool, verification caller ID). */
  ourNumbers: Set<string>;
  defaultCountry: string;
}

/**
 * Picks the best real-caller number from all available signals, in order of trust.
 * Forwarded calls frequently carry the forwarding subscriber in From/P-Charge-Info, so
 * the owner's number and our own numbers are always excluded.
 */
export function resolveCallerIdentity(signals: InboundSignals, ctx: IdentityContext): CallerIdentity {
  const { defaultCountry } = ctx;
  const reject = (n: string | null) => !n || n === ctx.ownerNumber || ctx.ourNumbers.has(n);

  const passports = parsePassports(signals.headers);
  const shaken = passports.find((p) => p.ppt === "shaken") ?? passports.find((p) => p.ppt === "div");
  const attestRaw = shaken?.payload.attest?.toUpperCase();
  const attestation = attestRaw === "A" || attestRaw === "B" || attestRaw === "C" ? attestRaw : null;

  const candidates: Array<{ source: CallerSource; raw: string | null | undefined }> = [];
  for (const p of passports) {
    const tn = first(p.payload.orig?.tn);
    if (tn) candidates.push({ source: "stir_shaken", raw: `+${tn.replace(/^\+/, "")}` });
  }
  for (const v of header(signals.headers, "P-Asserted-Identity")) {
    for (const part of v.split(/,(?=\s*<|\s*"|\s*sip|\s*tel)/i)) {
      candidates.push({ source: "p_asserted_identity", raw: part });
    }
  }
  for (const v of header(signals.headers, "Remote-Party-ID")) {
    candidates.push({ source: "remote_party_id", raw: v });
  }
  candidates.push({ source: "from", raw: signals.from });
  for (const v of header(signals.headers, "P-Charge-Info")) {
    candidates.push({ source: "p_charge_info", raw: v });
  }

  let picked: { source: CallerSource; number: string } | null = null;
  for (const c of candidates) {
    const number = toE164(c.raw, defaultCountry);
    if (!reject(number)) {
      picked = { source: c.source, number: number! };
      break;
    }
  }

  const name = signals.callerIdName?.trim();
  return {
    number: picked?.number ?? null,
    source: picked?.source ?? "none",
    wasHidden: privacyRequested(signals),
    attestation,
    callerName: name && !looksAnonymous(name) && !/^\+?\d[\d\s-]*$/.test(name) ? name : null,
  };
}

/** Exposed for diagnostics: what each signal said, so operators can see what their route delivers. */
export function describeSignals(signals: InboundSignals, defaultCountry: string) {
  return {
    from: { raw: signals.from, user: extractUserPart(signals.from), e164: toE164(signals.from, defaultCountry) },
    callerIdName: signals.callerIdName,
    passports: parsePassports(signals.headers).map((p) => ({
      ppt: p.ppt,
      attest: p.payload.attest ?? null,
      origTn: first(p.payload.orig?.tn) ?? null,
      divTn: first(p.payload.div?.tn) ?? null,
    })),
    pAssertedIdentity: header(signals.headers, "P-Asserted-Identity"),
    remotePartyId: header(signals.headers, "Remote-Party-ID"),
    privacy: header(signals.headers, "Privacy"),
    diversions: parseDiversions(signals.headers, defaultCountry),
    headerNames: signals.headers.map((h) => h.name),
  };
}
