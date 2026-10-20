import { createPublicKey, verify } from "node:crypto";
import type { InboundSignals, SipHeader } from "./callerIdentity.js";

export interface TtsVoice {
  /** "female" | "male" (basic engine) or a provider voice such as "Azure.he-IL-HilaNeural". */
  voice: string;
  language: string;
}

/** The subset of Telnyx Call Control we use. Swappable in tests. */
export interface TelephonyClient {
  answer(callControlId: string, clientState: object): Promise<void>;
  reject(callControlId: string, cause: "USER_BUSY" | "CALL_REJECTED"): Promise<void>;
  hangup(callControlId: string): Promise<void>;
  speak(callControlId: string, text: string, voice: TtsVoice, clientState: object): Promise<void>;
  playAudio(callControlId: string, audioUrl: string, clientState: object): Promise<void>;
  startRecording(callControlId: string, maxSeconds: number, clientState: object): Promise<void>;
  dial(to: string, from: string, clientState: object): Promise<{ callControlId: string }>;
  sendSms(to: string, from: string, text: string): Promise<void>;
  startVerification(phone: string): Promise<void>;
  checkVerification(phone: string, code: string): Promise<boolean>;
  download(url: string): Promise<Buffer>;
}

export class TelnyxError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly body: string,
  ) {
    super(message);
  }
}

const API = "https://api.telnyx.com/v2";

export function encodeState(state: object): string {
  return Buffer.from(JSON.stringify(state)).toString("base64");
}

function stepKey(action: string, state: object): string {
  const step = (state as { step?: unknown }).step;
  return typeof step === "string" ? `${action}:${step}` : action;
}

export function decodeState<T>(raw: string | null | undefined): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(Buffer.from(raw, "base64").toString("utf8")) as T;
  } catch {
    return null;
  }
}

export class TelnyxClient implements TelephonyClient {
  constructor(
    private readonly apiKey: string,
    private readonly connectionId: string,
    private readonly verifyProfileId: string,
    private readonly messagingProfileId: string,
  ) {}

  private async post(path: string, body: object): Promise<any> {
    const res = await fetch(`${API}${path}`, {
      method: "POST",
      headers: { Authorization: `Bearer ${this.apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(10_000),
    });
    const text = await res.text();
    if (!res.ok) throw new TelnyxError(`Telnyx ${path} failed with ${res.status}`, res.status, text);
    return text ? JSON.parse(text) : {};
  }

  /** command_id makes retried commands idempotent on Telnyx's side (duplicates within 60s are ignored). */
  private action(id: string, action: string, body: object, commandKey: string) {
    return this.post(`/calls/${encodeURIComponent(id)}/actions/${action}`, {
      ...body,
      command_id: `${commandKey}:${id}`.slice(0, 200),
    });
  }

  async answer(id: string, state: object) {
    await this.action(id, "answer", { client_state: encodeState(state) }, "answer");
  }

  async reject(id: string, cause: "USER_BUSY" | "CALL_REJECTED") {
    await this.action(id, "reject", { cause }, "reject");
  }

  async hangup(id: string) {
    await this.action(id, "hangup", {}, "hangup");
  }

  async speak(id: string, text: string, tts: TtsVoice, state: object) {
    await this.action(
      id,
      "speak",
      { payload: text, voice: tts.voice, language: tts.language, client_state: encodeState(state) },
      stepKey("speak", state),
    );
  }

  async playAudio(id: string, audioUrl: string, state: object) {
    await this.action(id, "playback_start", { audio_url: audioUrl, client_state: encodeState(state) }, stepKey("play", state));
  }

  async startRecording(id: string, maxSeconds: number, state: object) {
    await this.action(
      id,
      "record_start",
      {
        format: "mp3",
        channels: "single",
        play_beep: true,
        max_length: maxSeconds,
        timeout_secs: 8,
        client_state: encodeState(state),
      },
      stepKey("record", state),
    );
  }

  async dial(to: string, from: string, state: object) {
    const res = await this.post("/calls", {
      connection_id: this.connectionId,
      to,
      from,
      timeout_secs: 40,
      client_state: encodeState(state),
    });
    return { callControlId: String(res.data.call_control_id) };
  }

  async sendSms(to: string, from: string, text: string) {
    await this.post("/messages", {
      to,
      from,
      text,
      ...(this.messagingProfileId ? { messaging_profile_id: this.messagingProfileId } : {}),
    });
  }

  async startVerification(phone: string) {
    await this.post("/verifications/sms", { phone_number: phone, verify_profile_id: this.verifyProfileId });
  }

  async checkVerification(phone: string, code: string) {
    try {
      const res = await this.post(`/verifications/by_phone_number/${encodeURIComponent(phone)}/actions/verify`, {
        code,
        verify_profile_id: this.verifyProfileId,
      });
      return res?.data?.response_code === "accepted";
    } catch (err) {
      if (err instanceof TelnyxError && err.status >= 400 && err.status < 500) return false;
      throw err;
    }
  }

  async download(url: string) {
    const res = await fetch(url, { signal: AbortSignal.timeout(30_000) });
    if (!res.ok) throw new Error(`Recording download failed with ${res.status}`);
    return Buffer.from(await res.arrayBuffer());
  }
}

/**
 * Verifies `telnyx-signature-ed25519` over `${timestamp}|${rawBody}` and rejects stale timestamps
 * to block replays.
 */
export function verifyTelnyxSignature(opts: {
  publicKeyBase64: string;
  signatureBase64: string | undefined;
  timestamp: string | undefined;
  rawBody: string;
  toleranceSeconds?: number;
  now?: number;
}): boolean {
  const { publicKeyBase64, signatureBase64, timestamp, rawBody } = opts;
  if (!publicKeyBase64 || !signatureBase64 || !timestamp) return false;
  const ts = Number(timestamp);
  const now = opts.now ?? Math.floor(Date.now() / 1000);
  if (!Number.isFinite(ts) || Math.abs(now - ts) > (opts.toleranceSeconds ?? 300)) return false;
  try {
    const raw = Buffer.from(publicKeyBase64, "base64");
    // Telnyx publishes the raw 32-byte key; wrap it in an SPKI DER header for node:crypto.
    const der = raw.length === 32 ? Buffer.concat([Buffer.from("302a300506032b6570032100", "hex"), raw]) : raw;
    const key = createPublicKey({ key: der, format: "der", type: "spki" });
    return verify(null, Buffer.from(`${timestamp}|${rawBody}`), key, Buffer.from(signatureBase64, "base64"));
  } catch {
    return false;
  }
}

export interface TelnyxEvent {
  id: string;
  type: string;
  occurredAt: string;
  callControlId: string | null;
  callSessionId: string | null;
  direction: string | null;
  from: string | null;
  to: string | null;
  clientState: string | null;
  payload: Record<string, any>;
}

export function parseTelnyxEvent(body: any): TelnyxEvent | null {
  const data = body?.data;
  if (!data || typeof data.event_type !== "string") return null;
  const p = (data.payload ?? {}) as Record<string, any>;
  return {
    id: String(data.id ?? ""),
    type: data.event_type,
    occurredAt: String(data.occurred_at ?? ""),
    callControlId: p.call_control_id ?? null,
    callSessionId: p.call_session_id ?? null,
    direction: p.direction ?? null,
    from: p.from ?? null,
    to: p.to ?? null,
    clientState: p.client_state ?? null,
    payload: p,
  };
}

function headerList(v: unknown): SipHeader[] {
  if (!Array.isArray(v)) return [];
  return v
    .filter((h) => h && typeof h.name === "string" && h.value != null)
    .map((h) => ({ name: String(h.name), value: String(h.value) }));
}

/** Telnyx exposes SIP headers on call.initiated under `sip_headers` and `custom_headers`. */
export function signalsFromTelnyx(ev: TelnyxEvent): InboundSignals {
  const p = ev.payload;
  const headers = [...headerList(p.sip_headers), ...headerList(p.custom_headers)];
  if (typeof p.from_sip_uri === "string") headers.push({ name: "X-From-Sip-Uri", value: p.from_sip_uri });
  return {
    from: ev.from,
    to: ev.to ?? "",
    callerIdName: typeof p.caller_id_name === "string" ? p.caller_id_name : null,
    headers,
  };
}
