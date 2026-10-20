import { z } from "zod";

const bool = z
  .enum(["true", "false", "1", "0"])
  .transform((v) => v === "true" || v === "1");

const schema = z.object({
  NODE_ENV: z.enum(["development", "production", "test"]).default("development"),
  HOST: z.string().default("0.0.0.0"),
  PORT: z.coerce.number().int().default(8080),
  /** Public HTTPS origin of this server, e.g. https://api.unveil.app (no trailing slash). */
  PUBLIC_BASE_URL: z.string().url().transform((u) => u.replace(/\/+$/, "")),
  DATA_DIR: z.string().default("./data"),
  JWT_SECRET: z.string().min(32, "JWT_SECRET must be at least 32 characters"),
  ADMIN_TOKEN: z.string().min(24).optional(),
  DEFAULT_COUNTRY: z.string().length(2).default("US"),

  TELNYX_API_KEY: z.string().default(""),
  /** Base64 Ed25519 public key from Mission Control > Keys & Credentials > Public Key. */
  TELNYX_PUBLIC_KEY: z.string().default(""),
  /** The Call Control (Voice API) application id that owns the forwarding numbers. */
  TELNYX_CONNECTION_ID: z.string().default(""),
  TELNYX_VERIFY_PROFILE_ID: z.string().default(""),
  TELNYX_MESSAGING_PROFILE_ID: z.string().default(""),
  /** Number that sends SMS alerts (must be SMS-enabled and registered for A2P/toll-free messaging). */
  SMS_FROM_NUMBER: z.string().default(""),
  /** Number used as caller ID when the server rings the user to verify forwarding. */
  VERIFICATION_CALLER_ID: z.string().default(""),
  /** Optional public test line: call it with *67 and it reads your real number back. */
  DEMO_LINE_NUMBER: z.string().default(""),
  WEBHOOK_SIGNATURE_REQUIRED: bool.default(true),

  OTP_PROVIDER: z.enum(["telnyx", "console"]).default("telnyx"),
  ENTITLEMENT_MODE: z.enum(["revenuecat", "free"]).default("revenuecat"),
  REVENUECAT_WEBHOOK_AUTH: z.string().default(""),

  /** Text-to-speech voices for the voicemail greeting, per app language. */
  TTS_VOICE_EN: z.string().default("female"),
  TTS_VOICE_HE: z.string().default("Azure.he-IL-HilaNeural"),

  FFMPEG_PATH: z.string().default("ffmpeg"),

  MAX_RECORDING_SECONDS: z.coerce.number().int().min(10).max(600).default(120),
  DIAGNOSTICS_RETENTION_DAYS: z.coerce.number().int().min(1).default(14),
});

export type Config = z.infer<typeof schema>;

export function loadConfig(env: Record<string, string | undefined> = process.env): Config {
  const parsed = schema.safeParse(env);
  if (!parsed.success) {
    const issues = parsed.error.issues.map((i) => `  ${i.path.join(".")}: ${i.message}`).join("\n");
    throw new Error(`Invalid configuration:\n${issues}`);
  }
  const cfg = parsed.data;
  if (cfg.NODE_ENV === "production") {
    const missing = (
      ["TELNYX_API_KEY", "TELNYX_PUBLIC_KEY", "TELNYX_CONNECTION_ID", "ADMIN_TOKEN"] as const
    ).filter((k) => !cfg[k]);
    if (missing.length) throw new Error(`Missing required production settings: ${missing.join(", ")}`);
    if (!cfg.WEBHOOK_SIGNATURE_REQUIRED) {
      throw new Error("WEBHOOK_SIGNATURE_REQUIRED cannot be disabled in production");
    }
    if (cfg.OTP_PROVIDER === "console") throw new Error("OTP_PROVIDER=console is not allowed in production");
  }
  return cfg;
}
