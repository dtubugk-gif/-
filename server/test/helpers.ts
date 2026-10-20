import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { buildApp } from "../src/app.js";
import { loadConfig, type Config } from "../src/config.js";
import { openDb } from "../src/db.js";
import type { PushMessage, PushSender } from "../src/services/notifications.js";
import type { TelephonyClient, TtsVoice } from "../src/telephony/telnyx.js";

export const VERIFY_CALLER = "+12125550000";
export const FWD = "+18005550100";
export const USER_PHONE = "+12125550111";
export const ADMIN = "admin-token-admin-token-123456";

export class FakeTelephony implements TelephonyClient {
  calls: Array<{ op: string; id?: string; args: unknown[] }> = [];
  failSpeak = false;
  private seq = 0;
  async answer(id: string, state: object) {
    this.calls.push({ op: "answer", id, args: [state] });
  }
  async reject(id: string, cause: string) {
    this.calls.push({ op: "reject", id, args: [cause] });
  }
  async hangup(id: string) {
    this.calls.push({ op: "hangup", id, args: [] });
  }
  async speak(id: string, text: string, voice: TtsVoice, state: object) {
    this.calls.push({ op: "speak", id, args: [text, voice, state] });
    if (this.failSpeak) throw new Error("unsupported voice");
  }
  async playAudio(id: string, url: string, state: object) {
    this.calls.push({ op: "play", id, args: [url, state] });
  }
  async startRecording(id: string, max: number, state: object) {
    this.calls.push({ op: "record", id, args: [max, state] });
  }
  async dial(to: string, from: string, state: object) {
    const callControlId = `out-${++this.seq}`;
    this.calls.push({ op: "dial", id: callControlId, args: [to, from, state] });
    return { callControlId };
  }
  async sendSms(to: string, from: string, text: string) {
    this.calls.push({ op: "sms", args: [to, from, text] });
  }
  async startVerification(phone: string) {
    this.calls.push({ op: "otp-send", args: [phone] });
  }
  async checkVerification(_phone: string, code: string) {
    return code === "123456";
  }
  async download() {
    return Buffer.from("ID3fake-mp3");
  }
  last(op: string) {
    return [...this.calls].reverse().find((c) => c.op === op);
  }
}

export class FakePush implements PushSender {
  sent: PushMessage[] = [];
  async send(messages: PushMessage[]) {
    this.sent.push(...messages);
    return messages.map(() => ({ status: "ok" as const }));
  }
}

export async function setup(env: Record<string, string> = {}) {
  const dir = mkdtempSync(join(tmpdir(), "unveil-"));
  const cfg: Config = loadConfig({
    NODE_ENV: "test",
    PUBLIC_BASE_URL: "https://api.example.test",
    DATA_DIR: dir,
    JWT_SECRET: "x".repeat(40),
    ADMIN_TOKEN: ADMIN,
    WEBHOOK_SIGNATURE_REQUIRED: "false",
    OTP_PROVIDER: "telnyx",
    ENTITLEMENT_MODE: "free",
    VERIFICATION_CALLER_ID: VERIFY_CALLER,
    REVENUECAT_WEBHOOK_AUTH: "Bearer rc-secret",
    ...env,
  });
  const db = openDb(join(dir, "test.db"));
  const telephony = new FakeTelephony();
  const push = new FakePush();
  let clock = Date.parse("2026-10-20T12:00:00Z");
  const now = () => clock;
  const app = await buildApp({ cfg, db, telephony, push, now });

  await app.inject({ method: "POST", url: "/admin/numbers", headers: { authorization: `Bearer ${ADMIN}` }, payload: { numbers: [FWD] } });

  async function signup(phone = USER_PHONE) {
    await app.inject({ method: "POST", url: "/v1/auth/start", payload: { phone } });
    const res = await app.inject({ method: "POST", url: "/v1/auth/verify", payload: { phone, code: "123456", timezone: "America/New_York" } });
    const body = res.json();
    return { token: body.token as string, user: body.user, auth: { authorization: `Bearer ${body.token}` } };
  }

  let evSeq = 0;
  async function event(type: string, payload: Record<string, unknown>, id = `ev-${++evSeq}`) {
    return app.inject({
      method: "POST",
      url: "/webhooks/telnyx",
      payload: { data: { record_type: "event", event_type: type, id, occurred_at: new Date(clock).toISOString(), payload } },
    });
  }

  return {
    app,
    db,
    cfg,
    telephony,
    push,
    signup,
    event,
    advance: (ms: number) => (clock += ms),
    now,
  };
}
