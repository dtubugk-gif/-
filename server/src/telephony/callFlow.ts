import type { Config } from "../config.js";
import { tx, type Db } from "../db.js";
import { newId, signPath } from "../lib/crypto.js";
import { nationalDigits, toE164 } from "../lib/phone.js";
import { isEntitled } from "../services/entitlements.js";
import type { Notifier } from "../services/notifications.js";
import type { MediaStore } from "../services/storage.js";
import { describeSignals, parseDiversions, resolveCallerIdentity } from "./callerIdentity.js";
import { decodeState, signalsFromTelnyx, type TelephonyClient, type TelnyxEvent, type TtsVoice } from "./telnyx.js";

type Step =
  | "voicemail-answer"
  | "greeting"
  | "record"
  | "verified"
  | "demo"
  | "verify-dial"
  | "verify-answered";

interface CallState {
  step: Step;
  callId?: string;
  verificationId?: string;
  number?: string | null;
}

interface UserRow {
  id: string;
  phone: string;
  country: string;
  locale: string;
  greeting_text: string | null;
  greeting_audio_path: string | null;
  forwarding_verified_at: number | null;
  subscription_expires_at: number | null;
}

export interface Logger {
  info(o: object, m: string): void;
  warn(o: object, m: string): void;
  error(o: object, m: string): void;
}

const DEFAULT_GREETING: Record<string, string> = {
  en: "The person you are calling is not available. Please leave a message after the tone.",
  he: "המנוי אינו זמין כרגע. נא להשאיר הודעה לאחר הצליל.",
};

/** A verification call stays open for this long waiting for the forwarded leg to come back. */
export const VERIFICATION_WINDOW_MS = 3 * 60_000;
/** After our outbound leg ends without a forwarded leg, wait this long before declaring failure. */
export const VERIFICATION_GRACE_MS = 20_000;

export class CallFlow {
  constructor(
    private readonly db: Db,
    private readonly cfg: Config,
    private readonly telephony: TelephonyClient,
    private readonly notifier: Notifier,
    private readonly media: MediaStore,
    private readonly log: Logger,
    private readonly now: () => number = Date.now,
  ) {}

  /** Returns false for duplicate deliveries, which Telnyx sends on retries. */
  async handle(ev: TelnyxEvent): Promise<boolean> {
    if (ev.id) {
      const res = this.db
        .prepare("INSERT OR IGNORE INTO webhook_dedupe (event_id, received_at) VALUES (?, ?)")
        .run(ev.id, this.now());
      if (res.changes === 0) return false;
    }
    this.db
      .prepare("INSERT INTO call_events (provider_call_id, event_type, payload, received_at) VALUES (?, ?, ?, ?)")
      .run(ev.callControlId, ev.type, JSON.stringify(ev.payload), this.now());

    const state = decodeState<CallState>(ev.clientState);
    switch (ev.type) {
      case "call.initiated":
        if (ev.direction === "incoming") await this.onInbound(ev);
        break;
      case "call.answered":
        await this.onAnswered(ev, state);
        break;
      case "call.speak.ended":
      case "call.playback.ended":
        await this.onPromptEnded(ev, state);
        break;
      case "call.recording.saved":
        await this.onRecordingSaved(ev, state);
        break;
      case "call.hangup":
        this.onHangup(ev, state);
        break;
    }
    return true;
  }

  private ourNumbers(): Set<string> {
    const rows = this.db.prepare("SELECT number FROM forwarding_numbers").all() as Array<{ number: string }>;
    const set = new Set(rows.map((r) => r.number));
    for (const n of [this.cfg.VERIFICATION_CALLER_ID, this.cfg.DEMO_LINE_NUMBER, this.cfg.SMS_FROM_NUMBER]) {
      const e = toE164(n, this.cfg.DEFAULT_COUNTRY);
      if (e) set.add(e);
    }
    return set;
  }

  private tts(locale: string): TtsVoice {
    return locale === "he"
      ? { voice: this.cfg.TTS_VOICE_HE, language: "he-IL" }
      : { voice: this.cfg.TTS_VOICE_EN, language: "en-US" };
  }

  private async onInbound(ev: TelnyxEvent) {
    const id = ev.callControlId!;
    const to = toE164(ev.to, this.cfg.DEFAULT_COUNTRY);
    const signals = signalsFromTelnyx(ev);

    if (to && to === toE164(this.cfg.DEMO_LINE_NUMBER, this.cfg.DEFAULT_COUNTRY)) {
      const identity = resolveCallerIdentity(signals, {
        ownerNumber: null,
        ourNumbers: this.ourNumbers(),
        defaultCountry: this.cfg.DEFAULT_COUNTRY,
      });
      this.log.info({ identity, signals: describeSignals(signals, this.cfg.DEFAULT_COUNTRY) }, "demo line call");
      await this.telephony.answer(id, { step: "demo", number: identity.number } satisfies CallState);
      if (identity.number) {
        const user = this.db.prepare("SELECT id FROM users WHERE phone = ?").get(identity.number) as
          | { id: string }
          | undefined;
        if (user) void this.notifier.notify(user.id, { kind: "demo", number: identity.number });
      }
      return;
    }

    const owner = to
      ? (this.db
          .prepare(
            `SELECT u.* FROM forwarding_numbers f JOIN users u ON u.id = f.user_id
             WHERE f.number = ? AND f.active = 1`,
          )
          .get(to) as UserRow | undefined)
      : undefined;
    if (!owner) {
      this.log.warn({ to }, "inbound call to unassigned number");
      await this.telephony.reject(id, "CALL_REJECTED");
      return;
    }

    const identity = resolveCallerIdentity(signals, {
      ownerNumber: owner.phone,
      ourNumbers: this.ourNumbers(),
      defaultCountry: owner.country,
    });
    const diversions = parseDiversions(signals.headers, owner.country);
    if (diversions.length && !diversions.some((d) => d.number === owner.phone)) {
      this.log.warn({ userId: owner.id, diversions }, "forwarded call diverted from an unexpected number");
    }

    const now = this.now();
    // Any forwarded call proves the user's carrier forwarding is live.
    if (!owner.forwarding_verified_at) {
      this.db.prepare("UPDATE users SET forwarding_verified_at = ? WHERE id = ?").run(now, owner.id);
    }

    const verificationCaller = toE164(this.cfg.VERIFICATION_CALLER_ID, this.cfg.DEFAULT_COUNTRY);
    if (verificationCaller && signalsMention(signals, verificationCaller, owner.country, identity.number)) {
      const pending = this.db
        .prepare(
          `SELECT id FROM setup_verifications WHERE user_id = ? AND status = 'calling' AND created_at > ?
           ORDER BY created_at DESC LIMIT 1`,
        )
        .get(owner.id, now - VERIFICATION_WINDOW_MS) as { id: string } | undefined;
      if (pending) {
        this.db
          .prepare("UPDATE setup_verifications SET status = 'verified', completed_at = ?, failure_reason = NULL WHERE id = ?")
          .run(now, pending.id);
        this.db.prepare("UPDATE users SET forwarding_verified_at = ? WHERE id = ?").run(now, owner.id);
        await this.telephony.answer(id, { step: "verified", verificationId: pending.id } satisfies CallState);
        void this.notifier.notify(owner.id, { kind: "verified" });
        return;
      }
    }

    if (identity.number) {
      const blocked = this.db
        .prepare("SELECT 1 FROM blocklist WHERE user_id = ? AND number = ?")
        .get(owner.id, identity.number);
      if (blocked) {
        this.insertCall(ev, owner, to!, identity, "blocked");
        await this.telephony.reject(id, "USER_BUSY");
        return;
      }
    }

    const callId = this.insertCall(ev, owner, to!, identity, "ringing");
    const entitled = isEntitled(this.cfg, owner, now);
    const alert = identity.number
      ? ({ kind: "revealed", callId, number: identity.number, wasHidden: identity.wasHidden, entitled } as const)
      : ({ kind: "unrevealed", callId } as const);

    // Alert and answer in parallel: the alert is the product, voicemail is secondary.
    const [answered, notified] = await Promise.allSettled([
      this.telephony.answer(id, { step: "voicemail-answer", callId } satisfies CallState),
      this.notifier.notify(owner.id, alert),
    ]);
    if (answered.status === "rejected") this.log.error({ err: answered.reason, callId }, "answer failed");
    if (notified.status === "rejected") this.log.error({ err: notified.reason, callId }, "notify failed");
  }

  private insertCall(
    ev: TelnyxEvent,
    owner: UserRow,
    forwardingNumber: string,
    identity: ReturnType<typeof resolveCallerIdentity>,
    status: "ringing" | "blocked",
  ): string {
    const callId = newId();
    this.db
      .prepare(
        `INSERT INTO calls (id, user_id, provider_call_id, provider_session_id, forwarding_number, caller_number,
          caller_source, caller_name, was_hidden, status, started_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(provider_call_id) DO NOTHING`,
      )
      .run(
        callId,
        owner.id,
        ev.callControlId!,
        ev.callSessionId,
        forwardingNumber,
        identity.number,
        identity.source,
        identity.callerName,
        identity.wasHidden ? 1 : 0,
        status,
        this.now(),
      );
    return callId;
  }

  private userForCall(callId: string): (UserRow & { call_id: string }) | undefined {
    return this.db
      .prepare("SELECT u.*, c.id AS call_id FROM calls c JOIN users u ON u.id = c.user_id WHERE c.id = ?")
      .get(callId) as (UserRow & { call_id: string }) | undefined;
  }

  private async onAnswered(ev: TelnyxEvent, state: CallState | null) {
    const id = ev.callControlId!;
    if (!state) return;

    if (state.step === "demo") {
      const text = state.number
        ? `Hello. Even with your caller ID blocked, we can see your number. It is ${spellDigits(state.number)}. Again: ${spellDigits(state.number)}. Goodbye.`
        : "Hello. Your carrier did not send us your number on this call. Goodbye.";
      await this.telephony.speak(id, text, this.tts("en"), { step: "demo" } satisfies CallState);
      return;
    }

    if (state.step === "verified") {
      await this.telephony.speak(
        id,
        "Success. Call forwarding is set up correctly. You can hang up now.",
        this.tts("en"),
        { step: "verified" } satisfies CallState,
      );
      return;
    }

    if (state.step === "verify-dial") {
      // The user answered our test call instead of declining it.
      if (state.verificationId) {
        this.db
          .prepare("UPDATE setup_verifications SET failure_reason = 'answered' WHERE id = ? AND status = 'calling'")
          .run(state.verificationId);
      }
      await this.telephony.speak(
        id,
        "This is a setup test. Please do not answer it. When we call again, press the red decline button. Goodbye.",
        this.tts("en"),
        { step: "verify-answered", verificationId: state.verificationId } satisfies CallState,
      );
      return;
    }

    if (state.step === "voicemail-answer" && state.callId) {
      this.db
        .prepare("UPDATE calls SET status = 'answered', answered_at = ? WHERE id = ? AND status = 'ringing'")
        .run(this.now(), state.callId);
      const user = this.userForCall(state.callId);
      if (!user) return;
      const next: CallState = { step: "greeting", callId: state.callId };
      try {
        if (user.greeting_audio_path) {
          const path = `/media/greetings/${user.id}`;
          await this.telephony.playAudio(id, `${this.cfg.PUBLIC_BASE_URL}${signPath(this.cfg.JWT_SECRET, path, 600)}`, next);
        } else {
          await this.telephony.speak(
            id,
            user.greeting_text?.trim() || DEFAULT_GREETING[user.locale] || DEFAULT_GREETING.en!,
            this.tts(user.locale),
            next,
          );
        }
      } catch (err) {
        // A failed prompt (e.g. unsupported TTS voice) must never cost the user the voicemail.
        this.log.warn({ err, callId: state.callId }, "greeting failed, recording without it");
        await this.telephony.startRecording(id, this.cfg.MAX_RECORDING_SECONDS, { step: "record", callId: state.callId });
      }
    }
  }

  private async onPromptEnded(ev: TelnyxEvent, state: CallState | null) {
    const id = ev.callControlId!;
    if (!state) return;
    if (ev.payload.status === "call_hangup") return;
    if (state.step === "greeting" && state.callId) {
      await this.telephony.startRecording(id, this.cfg.MAX_RECORDING_SECONDS, { step: "record", callId: state.callId });
    } else if (state.step === "demo" || state.step === "verified" || state.step === "verify-answered") {
      await this.telephony.hangup(id);
    }
  }

  private async onRecordingSaved(ev: TelnyxEvent, state: CallState | null) {
    if (!state?.callId) return;
    const urls = ev.payload.recording_urls ?? ev.payload.public_recording_urls ?? {};
    const url: string | undefined = urls.mp3 ?? urls.wav;
    if (!url) return;
    const started = Date.parse(ev.payload.recording_started_at ?? "");
    const ended = Date.parse(ev.payload.recording_ended_at ?? "");
    const seconds = Number.isFinite(started) && Number.isFinite(ended) ? Math.max(0, Math.round((ended - started) / 1000)) : null;

    // Telnyx recording URLs expire after minutes, so copy the audio to our own storage now.
    const audio = await this.telephony.download(url);
    const path = await this.media.put("recordings", `${state.callId}.mp3`, audio);
    this.db
      .prepare("UPDATE calls SET recording_path = ?, recording_seconds = ?, status = 'voicemail' WHERE id = ?")
      .run(path, seconds, state.callId);

    if (seconds === null || seconds >= 2) {
      const user = this.userForCall(state.callId);
      const call = this.db.prepare("SELECT caller_number FROM calls WHERE id = ?").get(state.callId) as
        | { caller_number: string | null }
        | undefined;
      if (user) {
        await this.notifier.notify(user.id, {
          kind: "voicemail",
          callId: state.callId,
          number: call?.caller_number ?? null,
          entitled: isEntitled(this.cfg, user, this.now()),
        });
      }
    }
  }

  private onHangup(ev: TelnyxEvent, state: CallState | null) {
    const now = this.now();
    if (ev.direction === "outgoing" && state?.verificationId) {
      const cause = String(ev.payload.hangup_cause ?? "unknown");
      tx(this.db, () => {
        this.db
          .prepare(
            `UPDATE setup_verifications SET failure_reason = COALESCE(failure_reason, ?), completed_at = ?
             WHERE id = ? AND status = 'calling'`,
          )
          .run(cause, now, state.verificationId!);
      });
      return;
    }
    if (ev.callControlId) {
      this.db
        .prepare(
          `UPDATE calls SET ended_at = ?,
             status = CASE WHEN status IN ('ringing', 'answered') THEN 'completed' ELSE status END
           WHERE provider_call_id = ?`,
        )
        .run(now, ev.callControlId);
    }
  }

  /** Rings the user from our verification number; they decline, and the forwarded leg proves setup. */
  async startVerification(userId: string): Promise<string> {
    const user = this.db.prepare("SELECT phone FROM users WHERE id = ?").get(userId) as { phone: string } | undefined;
    if (!user) throw new Error("user not found");
    if (!this.cfg.VERIFICATION_CALLER_ID) throw new Error("VERIFICATION_CALLER_ID is not configured");
    const id = newId();
    this.db
      .prepare("INSERT INTO setup_verifications (id, user_id, status, created_at) VALUES (?, ?, 'calling', ?)")
      .run(id, userId, this.now());
    try {
      const { callControlId } = await this.telephony.dial(user.phone, this.cfg.VERIFICATION_CALLER_ID, {
        step: "verify-dial",
        verificationId: id,
      } satisfies CallState);
      this.db.prepare("UPDATE setup_verifications SET outbound_call_id = ? WHERE id = ?").run(callControlId, id);
    } catch (err) {
      this.db
        .prepare("UPDATE setup_verifications SET status = 'failed', failure_reason = 'dial_failed', completed_at = ? WHERE id = ?")
        .run(this.now(), id);
      throw err;
    }
    return id;
  }

  verificationStatus(userId: string, id: string) {
    const row = this.db
      .prepare("SELECT * FROM setup_verifications WHERE id = ? AND user_id = ?")
      .get(id, userId) as
      | { id: string; status: string; failure_reason: string | null; created_at: number; completed_at: number | null }
      | undefined;
    if (!row) return null;
    const now = this.now();
    let status = row.status;
    if (status === "calling") {
      const ended = row.completed_at != null && now - row.completed_at > VERIFICATION_GRACE_MS;
      if (ended || now - row.created_at > VERIFICATION_WINDOW_MS) {
        status = "failed";
        this.db.prepare("UPDATE setup_verifications SET status = 'failed', completed_at = COALESCE(completed_at, ?) WHERE id = ?").run(now, id);
      }
    }
    return { id: row.id, status, reason: status === "verified" ? null : explainFailure(row.failure_reason, status) };
  }
}

function signalsMention(
  signals: ReturnType<typeof signalsFromTelnyx>,
  number: string,
  country: string,
  resolved: string | null,
): boolean {
  if (resolved === number) return true;
  if (toE164(signals.from, country) === number) return true;
  return signals.headers.some((h) => /identity|party|from/i.test(h.name) && toE164(h.value, country) === number);
}

export function explainFailure(reason: string | null, status: string): string | null {
  if (status === "calling") return null;
  switch (reason) {
    case "answered":
      return "answered";
    case "user_busy":
      // Busy came back to us instead of being forwarded: the carrier is not forwarding declined calls.
      return "not_forwarded";
    case "timeout":
    case "no_answer":
    case "originator_cancel":
      return "not_declined";
    case "dial_failed":
      return "dial_failed";
    default:
      return "not_forwarded";
  }
}

/** "+14155550123" -> "4 1 5, 5 5 5, 0 1 2 3" so TTS reads digits clearly. */
export function spellDigits(e164: string): string {
  const d = nationalDigits(e164);
  if (d.length === 10) return `${d.slice(0, 3).split("").join(" ")}, ${d.slice(3, 6).split("").join(" ")}, ${d.slice(6).split("").join(" ")}`;
  return d.split("").join(" ");
}
