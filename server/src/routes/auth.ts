import type { FastifyPluginAsync } from "fastify";
import { z } from "zod";
import { newId } from "../lib/crypto.js";
import { countryOf, toE164 } from "../lib/phone.js";
import { OtpError } from "../services/otp.js";
import { ensureForwardingNumber } from "../services/numbers.js";
import { serializeUser } from "./me.js";

const startBody = z.object({ phone: z.string().min(4).max(32), country: z.string().length(2).optional() });
const verifyBody = startBody.extend({
  code: z.string().min(4).max(128),
  locale: z.enum(["en", "he"]).optional(),
  timezone: z.string().max(64).optional(),
});

const normalize = (phone: string, country: string | undefined, fallback: string) => toE164(phone, country ?? fallback);

/** On a personal server only the owner's numbers may sign in. */
function allowed(phone: string, list: string, fallbackCountry: string): boolean {
  const entries = list.split(",").map((p) => p.trim()).filter(Boolean);
  return !entries.length || entries.some((p) => toE164(p, fallbackCountry) === phone);
}

export const authRoutes: FastifyPluginAsync = async (app) => {
  const { db, otp, cfg, now } = app.services;
  const limit = { config: { rateLimit: { max: 10, timeWindow: "15 minutes" } } };

  app.post("/start", limit, async (req, reply) => {
    const body = startBody.parse(req.body);
    const phone = normalize(body.phone, body.country, cfg.DEFAULT_COUNTRY);
    if (!phone) return reply.code(400).send({ error: "invalid_phone" });
    if (!allowed(phone, cfg.ALLOWED_PHONES, cfg.DEFAULT_COUNTRY)) return reply.code(403).send({ error: "phone_not_allowed" });
    try {
      await otp.send(phone);
    } catch (err) {
      if (err instanceof OtpError) return reply.code(429).send({ error: err.code });
      throw err;
    }
    return { ok: true, phone, method: cfg.OTP_PROVIDER === "access_code" ? "access_code" : "sms" };
  });

  app.post("/verify", limit, async (req, reply) => {
    const body = verifyBody.parse(req.body);
    const phone = normalize(body.phone, body.country, cfg.DEFAULT_COUNTRY);
    if (!phone) return reply.code(400).send({ error: "invalid_phone" });
    if (!allowed(phone, cfg.ALLOWED_PHONES, cfg.DEFAULT_COUNTRY)) return reply.code(403).send({ error: "phone_not_allowed" });
    try {
      await otp.check(phone, body.code);
    } catch (err) {
      if (err instanceof OtpError) return reply.code(400).send({ error: err.code });
      throw err;
    }

    let user = db.prepare("SELECT * FROM users WHERE phone = ?").get(phone) as { id: string; country: string } | undefined;
    if (!user) {
      const id = newId();
      db.prepare(
        "INSERT INTO users (id, phone, country, locale, timezone, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).run(id, phone, countryOf(phone) ?? cfg.DEFAULT_COUNTRY, body.locale ?? "en", body.timezone ?? "UTC", now());
      user = { id, country: countryOf(phone) ?? cfg.DEFAULT_COUNTRY };
    } else if (body.locale || body.timezone) {
      db.prepare("UPDATE users SET locale = COALESCE(?, locale), timezone = COALESCE(?, timezone) WHERE id = ?").run(
        body.locale ?? null,
        body.timezone ?? null,
        user.id,
      );
    }
    ensureForwardingNumber(db, user.id, user.country, now());
    const token = app.jwt.sign({ sub: user.id });
    return { token, user: serializeUser(app, user.id) };
  });
};
