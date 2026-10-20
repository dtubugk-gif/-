import { generateKeyPairSync, sign } from "node:crypto";
import { describe, expect, it } from "vitest";
import { decodeState, encodeState, verifyTelnyxSignature } from "../src/telephony/telnyx.js";
import { forwardingInstructions } from "../src/services/forwardingCodes.js";
import { FWD, USER_PHONE, VERIFY_CALLER, setup } from "./helpers.js";

const HIDDEN_CALLER = "+13105550199";

function inbound(id: string, over: Record<string, unknown> = {}) {
  return {
    call_control_id: id,
    call_session_id: `sess-${id}`,
    direction: "incoming",
    from: HIDDEN_CALLER,
    to: FWD,
    state: "parked",
    sip_headers: [
      { name: "Privacy", value: "id" },
      { name: "Diversion", value: `<sip:${USER_PHONE}@carrier.net>;reason=user-busy` },
    ],
    ...over,
  };
}

const stateOf = (call: { args: unknown[] } | undefined) => call?.args.at(-1) as Record<string, unknown>;

describe("hidden call -> reveal -> voicemail", () => {
  it("reveals the number, records voicemail and lists it", async () => {
    const t = await setup();
    const { auth, user } = await t.signup();
    expect(user.forwardingNumber).toBe(FWD);

    await t.event("call.initiated", inbound("c1"));
    const alert = t.push.sent.at(-1);
    // no push token registered yet -> nothing sent via push; register one and replay a new call
    expect(alert).toBeUndefined();
    expect(t.telephony.last("answer")?.id).toBe("c1");

    await t.app.inject({
      method: "POST",
      url: "/v1/me/push-tokens",
      headers: auth,
      payload: { token: "ExponentPushToken[abc]", platform: "android" },
    });

    await t.event("call.initiated", inbound("c2"));
    expect(t.push.sent.at(-1)).toMatchObject({ title: "Hidden caller revealed", body: "+1 310 555 0199", to: "ExponentPushToken[abc]" });

    const answerState = stateOf(t.telephony.last("answer"));
    await t.event("call.answered", { call_control_id: "c2", direction: "incoming", client_state: encodeState(answerState) });
    const speak = t.telephony.last("speak")!;
    expect(speak.args[0]).toMatch(/leave a message/);

    await t.event("call.speak.ended", { call_control_id: "c2", status: "completed", client_state: encodeState(stateOf(speak)) });
    const rec = t.telephony.last("record")!;
    expect(rec.args[0]).toBe(120);

    await t.event("call.recording.saved", {
      call_control_id: "c2",
      client_state: encodeState(stateOf(rec)),
      recording_urls: { mp3: "https://telnyx.example/rec.mp3" },
      recording_started_at: "2026-10-20T12:00:05Z",
      recording_ended_at: "2026-10-20T12:00:17Z",
    });
    expect(t.push.sent.at(-1)).toMatchObject({ title: "New voicemail" });
    await t.event("call.hangup", { call_control_id: "c2", direction: "incoming", hangup_cause: "normal_clearing" });

    const list = (await t.app.inject({ url: "/v1/calls", headers: auth })).json();
    expect(list.calls).toHaveLength(2);
    expect(list.calls[0]).toMatchObject({ callerNumber: HIDDEN_CALLER, wasHidden: true, status: "voicemail", voicemailSeconds: 12 });

    const detail = (await t.app.inject({ url: `/v1/calls/${list.calls[0].id}`, headers: auth })).json();
    expect(detail.callsFromThisNumber).toBe(2);
    const audio = await t.app.inject({ url: detail.recordingUrl.replace("https://api.example.test", "") });
    expect(audio.statusCode).toBe(200);
    expect(audio.headers["content-type"]).toBe("audio/mpeg");

    // Forwarding verified implicitly by the first forwarded call.
    const me = (await t.app.inject({ url: "/v1/me", headers: auth })).json();
    expect(me.forwardingVerifiedAt).not.toBeNull();
  });

  it("ignores duplicate webhook deliveries", async () => {
    const t = await setup();
    await t.signup();
    await t.event("call.initiated", inbound("dup"), "same-id");
    await t.event("call.initiated", inbound("dup"), "same-id");
    expect(t.telephony.calls.filter((c) => c.op === "answer")).toHaveLength(1);
  });

  it("records without a greeting when TTS fails", async () => {
    const t = await setup();
    await t.signup();
    await t.event("call.initiated", inbound("c3"));
    t.telephony.failSpeak = true;
    await t.event("call.answered", { call_control_id: "c3", client_state: encodeState(stateOf(t.telephony.last("answer"))) });
    expect(t.telephony.last("record")?.id).toBe("c3");
  });

  it("rejects blocked callers with busy and logs them", async () => {
    const t = await setup();
    const { auth } = await t.signup();
    await t.app.inject({ method: "POST", url: "/v1/blocklist", headers: auth, payload: { number: "(310) 555-0199" } });
    await t.event("call.initiated", inbound("c4"));
    expect(t.telephony.last("reject")).toMatchObject({ id: "c4", args: ["USER_BUSY"] });
    const list = (await t.app.inject({ url: "/v1/calls", headers: auth })).json();
    expect(list.calls[0].status).toBe("blocked");
  });

  it("logs unrevealed hidden calls honestly", async () => {
    const t = await setup();
    const { auth } = await t.signup();
    await t.app.inject({ method: "POST", url: "/v1/me/push-tokens", headers: auth, payload: { token: "ExponentPushToken[z]", platform: "ios" } });
    await t.event("call.initiated", inbound("c5", { from: "anonymous", sip_headers: [] }));
    expect(t.push.sent.at(-1)?.title).toBe("Hidden caller — number not revealed");
  });

  it("rejects calls to unassigned numbers", async () => {
    const t = await setup();
    await t.event("call.initiated", inbound("c6"));
    expect(t.telephony.last("reject")?.id).toBe("c6");
  });
});

describe("subscription gating", () => {
  it("locks numbers until RevenueCat reports an active subscription", async () => {
    const t = await setup({ ENTITLEMENT_MODE: "revenuecat" });
    const { auth, user } = await t.signup();
    await t.app.inject({ method: "POST", url: "/v1/me/push-tokens", headers: auth, payload: { token: "ExponentPushToken[a]", platform: "android" } });
    await t.event("call.initiated", inbound("s1"));
    expect(t.push.sent.at(-1)).toMatchObject({ title: "Hidden caller unmasked", body: "Subscribe to see who it was." });
    let calls = (await t.app.inject({ url: "/v1/calls", headers: auth })).json().calls;
    expect(calls[0]).toMatchObject({ callerNumber: null, locked: true });

    const bad = await t.app.inject({ method: "POST", url: "/webhooks/revenuecat", payload: { event: { type: "INITIAL_PURCHASE" } } });
    expect(bad.statusCode).toBe(401);

    const rc = await t.app.inject({
      method: "POST",
      url: "/webhooks/revenuecat",
      headers: { authorization: "Bearer rc-secret" },
      payload: {
        event: {
          type: "INITIAL_PURCHASE",
          app_user_id: user.id,
          expiration_at_ms: t.now() + 3 * 86_400_000,
          event_timestamp_ms: t.now(),
          product_id: "unveil_yearly",
        },
      },
    });
    expect(rc.statusCode).toBe(200);
    calls = (await t.app.inject({ url: "/v1/calls", headers: auth })).json().calls;
    expect(calls[0]).toMatchObject({ callerNumber: HIDDEN_CALLER, locked: false });

    // A stale EXPIRATION delivered late must not revoke the newer purchase.
    await t.app.inject({
      method: "POST",
      url: "/webhooks/revenuecat",
      headers: { authorization: "Bearer rc-secret" },
      payload: { event: { type: "EXPIRATION", app_user_id: user.id, expiration_at_ms: t.now() - 1000, event_timestamp_ms: t.now() - 60_000 } },
    });
    expect((await t.app.inject({ url: "/v1/me", headers: auth })).json().entitled).toBe(true);
  });
});

describe("setup verification call", () => {
  it("verifies when the declined test call is forwarded back", async () => {
    const t = await setup();
    const { auth } = await t.signup();
    const { verificationId } = (await t.app.inject({ method: "POST", url: "/v1/setup/verify", headers: auth })).json();
    const dial = t.telephony.last("dial")!;
    expect(dial.args.slice(0, 2)).toEqual([USER_PHONE, VERIFY_CALLER]);

    await t.event("call.initiated", inbound("v1", { from: VERIFY_CALLER, sip_headers: [] }));
    const status = (await t.app.inject({ url: `/v1/setup/verify/${verificationId}`, headers: auth })).json();
    expect(status).toMatchObject({ status: "verified", reason: null });
    // Out-of-order hangup of our outbound leg after success must not flip it to failed.
    await t.event("call.hangup", { call_control_id: dial.id, direction: "outgoing", hangup_cause: "normal_clearing", client_state: encodeState(stateOf(dial)) });
    t.advance(60_000);
    expect((await t.app.inject({ url: `/v1/setup/verify/${verificationId}`, headers: auth })).json().status).toBe("verified");
  });

  it("explains failure when the carrier returns busy instead of forwarding", async () => {
    const t = await setup();
    const { auth } = await t.signup();
    const { verificationId } = (await t.app.inject({ method: "POST", url: "/v1/setup/verify", headers: auth })).json();
    const dial = t.telephony.last("dial")!;
    await t.event("call.hangup", { call_control_id: dial.id, direction: "outgoing", hangup_cause: "user_busy", client_state: encodeState(stateOf(dial)) });
    expect((await t.app.inject({ url: `/v1/setup/verify/${verificationId}`, headers: auth })).json().status).toBe("calling");
    t.advance(25_000);
    expect((await t.app.inject({ url: `/v1/setup/verify/${verificationId}`, headers: auth })).json()).toMatchObject({
      status: "failed",
      reason: "not_forwarded",
    });
  });
});

describe("evidence reports", () => {
  it("builds a verifiable PDF and an injection-safe CSV", async () => {
    const t = await setup();
    const { auth } = await t.signup();
    await t.event("call.initiated", inbound("r1", { caller_id_name: "=HYPERLINK(evil)" }));
    const r = (await t.app.inject({ method: "POST", url: "/v1/reports", headers: auth, payload: {} })).json();
    expect(r.callCount).toBe(1);
    const pdf = await t.app.inject({ url: r.pdfUrl.replace("https://api.example.test", "") });
    expect(pdf.statusCode).toBe(200);
    expect(pdf.rawPayload.subarray(0, 4).toString()).toBe("%PDF");
    const csv = await t.app.inject({ url: r.csvUrl.replace("https://api.example.test", "") });
    expect(csv.body).toContain("'+13105550199");
    expect(csv.body).toContain("'=HYPERLINK(evil)");
    const tampered = await t.app.inject({ url: r.csvUrl.replace("https://api.example.test", "").replace(/sig=.*/, "sig=bad") });
    expect(tampered.statusCode).toBe(403);
    const v = (await t.app.inject({ url: `/v1/reports/verify/${r.id}` })).json();
    expect(v).toMatchObject({ valid: true, sha256: r.sha256, callCount: 1 });
  });
});

describe("account deletion", () => {
  it("quarantines the released number so a new user cannot inherit forwarded calls", async () => {
    const t = await setup();
    const { auth } = await t.signup();
    expect((await t.app.inject({ method: "DELETE", url: "/v1/me", headers: auth })).statusCode).toBe(204);
    const other = await t.signup("+12125550222");
    expect(other.user.forwardingNumber).toBeNull();
    t.advance(91 * 86_400_000);
    expect((await t.app.inject({ url: "/v1/me", headers: other.auth })).json().forwardingNumber).toBe(FWD);
  });
});

describe("auth", () => {
  it("rejects bad codes and unauthenticated access", async () => {
    const t = await setup();
    await t.app.inject({ method: "POST", url: "/v1/auth/start", payload: { phone: USER_PHONE } });
    const bad = await t.app.inject({ method: "POST", url: "/v1/auth/verify", payload: { phone: USER_PHONE, code: "000000" } });
    expect(bad.json().error).toBe("invalid");
    expect((await t.app.inject({ url: "/v1/me" })).statusCode).toBe(401);
    expect((await t.app.inject({ url: "/admin/stats" })).statusCode).toBe(401);
  });
});

describe("webhook signatures", () => {
  it("accepts Telnyx-signed bodies and rejects forgeries and replays", () => {
    const { publicKey, privateKey } = generateKeyPairSync("ed25519");
    const raw32 = publicKey.export({ format: "der", type: "spki" }).subarray(-32).toString("base64");
    const body = JSON.stringify({ data: { event_type: "call.initiated" } });
    const ts = "1760961600";
    const signature = sign(null, Buffer.from(`${ts}|${body}`), privateKey).toString("base64");
    const now = 1760961600;
    expect(verifyTelnyxSignature({ publicKeyBase64: raw32, signatureBase64: signature, timestamp: ts, rawBody: body, now })).toBe(true);
    expect(verifyTelnyxSignature({ publicKeyBase64: raw32, signatureBase64: signature, timestamp: ts, rawBody: body + " ", now })).toBe(false);
    expect(verifyTelnyxSignature({ publicKeyBase64: raw32, signatureBase64: signature, timestamp: ts, rawBody: body, now: now + 3600 })).toBe(false);
  });

  it("enforces signatures on the endpoint when required", async () => {
    const t = await setup({ WEBHOOK_SIGNATURE_REQUIRED: "true", TELNYX_PUBLIC_KEY: Buffer.alloc(32, 1).toString("base64") });
    const res = await t.event("call.initiated", inbound("x"));
    expect(res.statusCode).toBe(401);
  });

  it("round-trips client_state", () => {
    expect(decodeState(encodeState({ step: "greeting", callId: "a" }))).toEqual({ step: "greeting", callId: "a" });
  });
});

describe("forwarding codes", () => {
  it("GSM decline-only uses busy forwarding", () => {
    const i = forwardingInstructions("att", "decline", "+18005550100");
    expect(i.enable[0]).toMatchObject({ code: "**67*+18005550100#", telUri: "tel:**67*%2B18005550100%23" });
    expect(i.disable[0]!.code).toBe("##67#");
  });

  it("Verizon uses *71 with the 10-digit number", () => {
    const i = forwardingInstructions("verizon", "full", "+18005550100");
    expect(i.enable[0]!.code).toBe("*718005550100");
    expect(i.disable[0]!.code).toBe("*73");
  });

  it("manual carriers carry a note", () => {
    expect(forwardingInstructions("visible", "decline", "+18005550100").manual).toBe(true);
  });
});

describe("personal server (access code, no SMS)", () => {
  const env = { OTP_PROVIDER: "access_code", OWNER_ACCESS_CODE: "my-secret-code-123", ALLOWED_PHONES: "+972552691740" };

  it("signs the owner in with the access code and sends no SMS", async () => {
    const t = await setup({ ...env, DEFAULT_COUNTRY: "IL" });
    const start = await t.app.inject({ method: "POST", url: "/v1/auth/start", payload: { phone: "0552691740", country: "IL" } });
    expect(start.json()).toMatchObject({ ok: true, phone: "+972552691740", method: "access_code" });
    expect(t.telephony.calls.filter((c) => c.op === "otp-send")).toHaveLength(0);

    const wrong = await t.app.inject({ method: "POST", url: "/v1/auth/verify", payload: { phone: "0552691740", country: "IL", code: "guess-guess" } });
    expect(wrong.json().error).toBe("invalid");
    const ok = await t.app.inject({ method: "POST", url: "/v1/auth/verify", payload: { phone: "0552691740", country: "IL", code: "my-secret-code-123" } });
    expect(ok.statusCode).toBe(200);
    expect(ok.json().token).toBeTruthy();
  });

  it("refuses every other number", async () => {
    const t = await setup(env);
    const res = await t.app.inject({ method: "POST", url: "/v1/auth/start", payload: { phone: USER_PHONE } });
    expect(res.statusCode).toBe(403);
    expect(res.json().error).toBe("phone_not_allowed");
  });

  it("rejects a weak or missing access code configuration", async () => {
    await expect(setup({ OTP_PROVIDER: "access_code", OWNER_ACCESS_CODE: "short", ALLOWED_PHONES: "+972552691740" })).rejects.toThrow(/at least 8/);
    await expect(setup({ OTP_PROVIDER: "access_code", OWNER_ACCESS_CODE: "long-enough-code" })).rejects.toThrow(/ALLOWED_PHONES/);
  });
});
