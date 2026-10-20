import type { FastifyInstance, FastifyPluginAsync } from "fastify";
import { z } from "zod";
import { isEntitled } from "../services/entitlements.js";
import { carriersFor } from "../services/forwardingCodes.js";
import { ensureForwardingNumber, releaseForwardingNumber } from "../services/numbers.js";
import { transcodeToMp3 } from "../services/audio.js";

interface UserRow {
  id: string;
  phone: string;
  country: string;
  carrier: string | null;
  forwarding_mode: string;
  locale: string;
  timezone: string;
  greeting_text: string | null;
  greeting_audio_path: string | null;
  forwarding_verified_at: number | null;
  subscription_expires_at: number | null;
  subscription_product: string | null;
  created_at: number;
}

export function serializeUser(app: FastifyInstance, userId: string) {
  const { db, cfg, now } = app.services;
  const u = db.prepare("SELECT * FROM users WHERE id = ?").get(userId) as UserRow | undefined;
  if (!u) return null;
  const forwardingNumber = ensureForwardingNumber(db, u.id, u.country, now());
  return {
    id: u.id,
    phone: u.phone,
    country: u.country,
    carrier: u.carrier,
    forwardingMode: u.forwarding_mode,
    forwardingNumber,
    forwardingVerifiedAt: u.forwarding_verified_at,
    locale: u.locale,
    timezone: u.timezone,
    greetingText: u.greeting_text,
    hasGreetingAudio: !!u.greeting_audio_path,
    entitled: isEntitled(cfg, u, now()),
    subscriptionExpiresAt: u.subscription_expires_at,
    entitlementMode: cfg.ENTITLEMENT_MODE,
    demoLineNumber: cfg.DEMO_LINE_NUMBER || null,
    createdAt: u.created_at,
  };
}

const patchBody = z.object({
  carrier: z.string().max(40).nullable().optional(),
  forwardingMode: z.enum(["decline", "full"]).optional(),
  locale: z.enum(["en", "he"]).optional(),
  timezone: z.string().max(64).optional(),
  greetingText: z.string().max(400).nullable().optional(),
});

const pushBody = z.object({
  token: z.string().regex(/^(ExponentPushToken|ExpoPushToken)\[.+\]$/),
  platform: z.enum(["ios", "android"]),
});

export const meRoutes: FastifyPluginAsync = async (app) => {
  const { db, media, now } = app.services;
  app.addHook("preHandler", app.authenticate);

  app.get("/me", async (req) => serializeUser(app, req.user.sub));

  app.patch("/me", async (req) => {
    const b = patchBody.parse(req.body);
    const sets: string[] = [];
    const vals: Array<string | null> = [];
    const map: Record<string, string> = {
      carrier: "carrier",
      forwardingMode: "forwarding_mode",
      locale: "locale",
      timezone: "timezone",
      greetingText: "greeting_text",
    };
    for (const [k, col] of Object.entries(map)) {
      const v = (b as Record<string, string | null | undefined>)[k];
      if (v !== undefined) {
        sets.push(`${col} = ?`);
        vals.push(v);
      }
    }
    if (b.carrier !== undefined || b.forwardingMode !== undefined) sets.push("forwarding_verified_at = NULL");
    if (sets.length) db.prepare(`UPDATE users SET ${sets.join(", ")} WHERE id = ?`).run(...vals, req.user.sub);
    return serializeUser(app, req.user.sub);
  });

  app.delete("/me", async (req, reply) => {
    const u = db.prepare("SELECT greeting_audio_path FROM users WHERE id = ?").get(req.user.sub) as
      | { greeting_audio_path: string | null }
      | undefined;
    const recs = db.prepare("SELECT recording_path FROM calls WHERE user_id = ? AND recording_path IS NOT NULL").all(
      req.user.sub,
    ) as Array<{ recording_path: string }>;
    const reports = db.prepare("SELECT id FROM reports WHERE user_id = ?").all(req.user.sub) as Array<{ id: string }>;
    releaseForwardingNumber(db, req.user.sub, now());
    db.prepare("DELETE FROM users WHERE id = ?").run(req.user.sub);
    await Promise.all([
      media.remove(u?.greeting_audio_path),
      ...recs.map((r) => media.remove(r.recording_path)),
      ...reports.flatMap((r) => [media.remove(`reports/${r.id}.pdf`), media.remove(`reports/${r.id}.csv`)]),
    ]);
    return reply.code(204).send();
  });

  app.post("/me/push-tokens", async (req) => {
    const b = pushBody.parse(req.body);
    db.prepare(
      `INSERT INTO push_tokens (token, user_id, platform, updated_at) VALUES (?, ?, ?, ?)
       ON CONFLICT(token) DO UPDATE SET user_id = excluded.user_id, platform = excluded.platform, updated_at = excluded.updated_at`,
    ).run(b.token, req.user.sub, b.platform, now());
    return { ok: true };
  });

  app.delete<{ Params: { token: string } }>("/me/push-tokens/:token", async (req) => {
    db.prepare("DELETE FROM push_tokens WHERE token = ? AND user_id = ?").run(req.params.token, req.user.sub);
    return { ok: true };
  });

  app.put("/me/greeting", async (req, reply) => {
    const file = await req.file();
    if (!file) return reply.code(400).send({ error: "missing_file" });
    if (!/^audio\//.test(file.mimetype) && file.mimetype !== "application/octet-stream") {
      return reply.code(415).send({ error: "unsupported_audio" });
    }
    let mp3: Buffer;
    try {
      // Phones record AAC/M4A; Telnyx playback needs MP3 or WAV.
      mp3 = await transcodeToMp3(await file.toBuffer(), app.services.cfg.FFMPEG_PATH);
    } catch (err) {
      req.log.warn({ err }, "greeting transcode failed");
      return reply.code(422).send({ error: "unsupported_audio" });
    }
    const path = await media.put("greetings", `${req.user.sub}.mp3`, mp3);
    db.prepare("UPDATE users SET greeting_audio_path = ? WHERE id = ?").run(path, req.user.sub);
    return serializeUser(app, req.user.sub);
  });

  app.delete("/me/greeting", async (req) => {
    const u = db.prepare("SELECT greeting_audio_path FROM users WHERE id = ?").get(req.user.sub) as
      | { greeting_audio_path: string | null }
      | undefined;
    await media.remove(u?.greeting_audio_path);
    db.prepare("UPDATE users SET greeting_audio_path = NULL WHERE id = ?").run(req.user.sub);
    return serializeUser(app, req.user.sub);
  });

  app.get<{ Querystring: { country?: string } }>("/carriers", async (req) => {
    const u = db.prepare("SELECT country FROM users WHERE id = ?").get(req.user.sub) as { country: string };
    return carriersFor(req.query.country ?? u.country).map(({ id, name, scheme, note }) => ({ id, name, manual: scheme === "manual", note }));
  });
};
