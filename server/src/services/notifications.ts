import type { Db } from "../db.js";
import type { TelephonyClient } from "../telephony/telnyx.js";
import { formatInternational } from "../lib/phone.js";

export interface PushMessage {
  to: string;
  title: string;
  body: string;
  data: Record<string, unknown>;
  sound: "default";
  priority: "high";
  channelId: string;
}

export interface PushSender {
  send(messages: PushMessage[]): Promise<Array<{ status: "ok" | "error"; details?: { error?: string } }>>;
}

/** Expo's push service fronts FCM and APNs, so the server needs no Google/Apple credentials. */
export class ExpoPushSender implements PushSender {
  async send(messages: PushMessage[]) {
    if (!messages.length) return [];
    const res = await fetch("https://exp.host/--/api/v2/push/send", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(messages),
      signal: AbortSignal.timeout(10_000),
    });
    if (!res.ok) throw new Error(`Expo push failed with ${res.status}`);
    const json = (await res.json()) as { data?: Array<{ status: "ok" | "error"; details?: { error?: string } }> };
    return json.data ?? [];
  }
}

type Locale = "en" | "he";

const T = {
  en: {
    revealedTitle: "Hidden caller revealed",
    lockedTitle: "Hidden caller unmasked",
    lockedBody: "Subscribe to see who it was.",
    declinedTitle: "Declined call",
    unknownTitle: "Hidden caller — number not revealed",
    unknownBody: "This caller's network withheld the number. The call was logged.",
    voicemailTitle: "New voicemail",
    voicemailBody: (who: string) => `${who} left a voicemail.`,
    unknownCaller: "Unknown caller",
    verifiedTitle: "Setup complete ✅",
    verifiedBody: "Call forwarding works. Decline any No Caller ID call to reveal it.",
    demoTitle: "Demo line",
    demoBody: (n: string) => `We saw your real number: ${n}`,
  },
  he: {
    revealedTitle: "מספר חסוי נחשף",
    lockedTitle: "מספר חסוי נחשף",
    lockedBody: "הצטרפו למנוי כדי לראות מי התקשר.",
    declinedTitle: "שיחה שנדחתה",
    unknownTitle: "מתקשר חסוי — המספר לא נחשף",
    unknownBody: "הרשת של המתקשר לא העבירה את המספר. השיחה נשמרה ביומן.",
    voicemailTitle: "הודעה קולית חדשה",
    voicemailBody: (who: string) => `${who} השאיר/ה הודעה קולית.`,
    unknownCaller: "מתקשר לא ידוע",
    verifiedTitle: "ההגדרה הושלמה ✅",
    verifiedBody: "העברת השיחות פועלת. דחו כל שיחה חסויה כדי לחשוף אותה.",
    demoTitle: "קו הדגמה",
    demoBody: (n: string) => `זיהינו את המספר האמיתי שלך: ${n}`,
  },
} as const;

const strings = (locale: string) => T[(locale === "he" ? "he" : "en") as Locale];

export type CallAlert =
  | { kind: "revealed"; callId: string; number: string; wasHidden: boolean; entitled: boolean }
  | { kind: "unrevealed"; callId: string }
  | { kind: "voicemail"; callId: string; number: string | null; entitled: boolean }
  | { kind: "verified" }
  | { kind: "demo"; number: string };

export class Notifier {
  constructor(
    private readonly db: Db,
    private readonly push: PushSender,
    private readonly telephony: TelephonyClient,
    private readonly smsFrom: string,
    private readonly log: { warn: (o: object, m: string) => void },
  ) {}

  compose(locale: string, alert: CallAlert): { title: string; body: string; data: Record<string, unknown> } {
    const s = strings(locale);
    switch (alert.kind) {
      case "revealed": {
        if (!alert.entitled) return { title: s.lockedTitle, body: s.lockedBody, data: { callId: alert.callId, locked: true } };
        const n = formatInternational(alert.number);
        return {
          title: alert.wasHidden ? s.revealedTitle : s.declinedTitle,
          body: n,
          data: { callId: alert.callId, number: alert.number },
        };
      }
      case "unrevealed":
        return { title: s.unknownTitle, body: s.unknownBody, data: { callId: alert.callId } };
      case "voicemail": {
        const who = alert.number && alert.entitled ? formatInternational(alert.number) : s.unknownCaller;
        return { title: s.voicemailTitle, body: s.voicemailBody(who), data: { callId: alert.callId, voicemail: true } };
      }
      case "verified":
        return { title: s.verifiedTitle, body: s.verifiedBody, data: { setup: "verified" } };
      case "demo":
        return { title: s.demoTitle, body: s.demoBody(formatInternational(alert.number)), data: {} };
    }
  }

  /** Push first; fall back to SMS when the user has no working device token. */
  async notify(userId: string, alert: CallAlert): Promise<"push" | "sms" | "none"> {
    const user = this.db.prepare("SELECT phone, locale FROM users WHERE id = ?").get(userId) as
      | { phone: string; locale: string }
      | undefined;
    if (!user) return "none";
    const msg = this.compose(user.locale, alert);
    const tokens = (this.db.prepare("SELECT token FROM push_tokens WHERE user_id = ?").all(userId) as Array<{ token: string }>).map(
      (r) => r.token,
    );

    let delivered = false;
    if (tokens.length) {
      try {
        const tickets = await this.push.send(
          tokens.map((to) => ({ to, ...msg, sound: "default", priority: "high", channelId: "calls" })),
        );
        tickets.forEach((t, i) => {
          if (t.status === "ok") delivered = true;
          else if (t.details?.error === "DeviceNotRegistered") {
            this.db.prepare("DELETE FROM push_tokens WHERE token = ?").run(tokens[i]!);
          }
        });
      } catch (err) {
        this.log.warn({ err }, "push delivery failed");
      }
    }
    if (delivered) return "push";

    if (this.smsFrom) {
      try {
        await this.telephony.sendSms(user.phone, this.smsFrom, `${msg.title}: ${msg.body}`);
        return "sms";
      } catch (err) {
        this.log.warn({ err }, "sms fallback failed");
      }
    }
    return "none";
  }
}
