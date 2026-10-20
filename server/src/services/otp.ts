import type { Config } from "../config.js";
import type { Db } from "../db.js";
import { hmac, otpCode, safeEqual } from "../lib/crypto.js";
import type { TelephonyClient } from "../telephony/telnyx.js";

export class OtpError extends Error {
  constructor(readonly code: "rate_limited" | "invalid" | "expired" | "too_many_attempts") {
    super(code);
  }
}

const CODE_TTL_MS = 10 * 60_000;
const MAX_SENDS_PER_HOUR = 5;
const MAX_ATTEMPTS = 5;

/**
 * Phone ownership check. In production Telnyx Verify sends and checks the code (it handles
 * sender registration per country, the #1 cause of "I never got my code"). The console provider
 * logs the code for local development only.
 */
export class OtpService {
  constructor(
    private readonly db: Db,
    private readonly cfg: Config,
    private readonly telephony: TelephonyClient,
    private readonly log: { info: (o: object, m: string) => void },
    private readonly now: () => number = Date.now,
  ) {}

  async send(phone: string): Promise<void> {
    const now = this.now();
    const recent = this.db
      .prepare("SELECT COUNT(*) AS n FROM otp_challenges WHERE phone = ? AND created_at > ?")
      .get(phone, now - 3_600_000) as { n: number };
    if (recent.n >= MAX_SENDS_PER_HOUR) throw new OtpError("rate_limited");

    let codeHash: string | null = null;
    if (this.cfg.OTP_PROVIDER === "access_code") {
      // Nothing to send: the owner already knows the code.
    } else if (this.cfg.OTP_PROVIDER === "console") {
      const code = otpCode();
      codeHash = hmac(this.cfg.JWT_SECRET, `${phone}:${code}`);
      this.log.info({ phone, code }, "OTP code (console provider)");
    } else {
      await this.telephony.startVerification(phone);
    }
    this.db
      .prepare("INSERT INTO otp_challenges (phone, code_hash, expires_at, created_at) VALUES (?, ?, ?, ?)")
      .run(phone, codeHash, now + CODE_TTL_MS, now);
  }

  async check(phone: string, code: string): Promise<void> {
    const now = this.now();
    const ch = this.db
      .prepare(
        "SELECT * FROM otp_challenges WHERE phone = ? AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1",
      )
      .get(phone) as { id: number; code_hash: string | null; attempts: number; expires_at: number } | undefined;
    if (!ch) throw new OtpError("invalid");
    if (ch.expires_at < now) throw new OtpError("expired");
    if (ch.attempts >= MAX_ATTEMPTS) throw new OtpError("too_many_attempts");
    this.db.prepare("UPDATE otp_challenges SET attempts = attempts + 1 WHERE id = ?").run(ch.id);

    let ok: boolean;
    if (this.cfg.OTP_PROVIDER === "access_code") ok = safeEqual(code, this.cfg.OWNER_ACCESS_CODE);
    else if (this.cfg.OTP_PROVIDER === "console")
      ok = ch.code_hash != null && safeEqual(ch.code_hash, hmac(this.cfg.JWT_SECRET, `${phone}:${code}`));
    else ok = /^\d{4,8}$/.test(code) && (await this.telephony.checkVerification(phone, code));
    if (!ok) throw new OtpError("invalid");
    this.db.prepare("UPDATE otp_challenges SET consumed_at = ? WHERE id = ?").run(now, ch.id);
  }
}
