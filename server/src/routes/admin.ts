import type { FastifyPluginAsync } from "fastify";
import { z } from "zod";
import { safeEqual } from "../lib/crypto.js";
import { countryOf, lineType, toE164 } from "../lib/phone.js";
import { describeSignals } from "../telephony/callerIdentity.js";
import { parseTelnyxEvent, signalsFromTelnyx } from "../telephony/telnyx.js";
import { poolStats } from "../services/numbers.js";

export const adminRoutes: FastifyPluginAsync = async (app) => {
  const { cfg, db, now } = app.services;

  app.addHook("onRequest", async (req, reply) => {
    const token = String(req.headers.authorization ?? "").replace(/^Bearer\s+/i, "");
    if (!cfg.ADMIN_TOKEN || !token || !safeEqual(token, cfg.ADMIN_TOKEN)) return reply.code(401).send({ error: "unauthorized" });
  });

  app.get("/numbers", async () => ({
    pool: poolStats(db, now()),
    numbers: db.prepare("SELECT number, country, kind, user_id AS userId, active, available_after AS availableAfter FROM forwarding_numbers").all(),
  }));

  /** Register numbers bought in Telnyx (assign them to the Call Control app there first). */
  app.post("/numbers", async (req, reply) => {
    const b = z.object({ numbers: z.array(z.string()).min(1).max(500) }).parse(req.body);
    const added: string[] = [];
    const rejected: string[] = [];
    for (const raw of b.numbers) {
      const n = toE164(raw, cfg.DEFAULT_COUNTRY);
      const country = n ? countryOf(n) : null;
      if (!n || !country) {
        rejected.push(raw);
        continue;
      }
      const kind = lineType(n) === "TOLL_FREE" ? "toll_free" : "local";
      db.prepare("INSERT OR IGNORE INTO forwarding_numbers (number, country, kind, created_at) VALUES (?, ?, ?, ?)").run(n, country, kind, now());
      added.push(n);
    }
    return reply.code(rejected.length && !added.length ? 400 : 200).send({ added, rejected });
  });

  app.patch<{ Params: { number: string } }>("/numbers/:number", async (req) => {
    const b = z.object({ active: z.boolean() }).parse(req.body);
    db.prepare("UPDATE forwarding_numbers SET active = ? WHERE number = ?").run(b.active ? 1 : 0, req.params.number);
    return { ok: true };
  });

  /**
   * The single most important operational view: for recent inbound calls, which signal carried
   * the real number. Call your forwarding number with *67 from several carriers and check here.
   */
  app.get<{ Querystring: { limit?: string } }>("/diagnostics/inbound", async (req) => {
    const limit = Math.min(Number(req.query.limit ?? 20) || 20, 200);
    const rows = db
      .prepare("SELECT provider_call_id, payload, received_at FROM call_events WHERE event_type = 'call.initiated' ORDER BY id DESC LIMIT ?")
      .all(limit) as Array<{ provider_call_id: string; payload: string; received_at: number }>;
    return rows.map((r) => {
      const payload = JSON.parse(r.payload);
      const ev = parseTelnyxEvent({ data: { event_type: "call.initiated", payload } })!;
      const call = db.prepare("SELECT caller_number, caller_source, was_hidden, status FROM calls WHERE provider_call_id = ?").get(r.provider_call_id);
      return {
        receivedAt: new Date(r.received_at).toISOString(),
        direction: payload.direction,
        to: payload.to,
        result: call ?? null,
        signals: describeSignals(signalsFromTelnyx(ev), cfg.DEFAULT_COUNTRY),
      };
    });
  });

  app.post<{ Params: { id: string } }>("/users/:id/grant", async (req, reply) => {
    const b = z.object({ days: z.number().int().min(1).max(3650) }).parse(req.body);
    const res = db.prepare("UPDATE users SET subscription_expires_at = ? WHERE id = ?").run(now() + b.days * 86_400_000, req.params.id);
    if (!res.changes) return reply.code(404).send({ error: "not_found" });
    return { ok: true };
  });

  app.get("/stats", async () => ({
    users: (db.prepare("SELECT COUNT(*) AS n FROM users").get() as { n: number }).n,
    verifiedUsers: (db.prepare("SELECT COUNT(*) AS n FROM users WHERE forwarding_verified_at IS NOT NULL").get() as { n: number }).n,
    calls24h: (db.prepare("SELECT COUNT(*) AS n FROM calls WHERE started_at > ?").get(now() - 86_400_000) as { n: number }).n,
    hiddenRevealed24h: (
      db.prepare("SELECT COUNT(*) AS n FROM calls WHERE started_at > ? AND was_hidden = 1 AND caller_number IS NOT NULL").get(now() - 86_400_000) as { n: number }
    ).n,
    hiddenUnrevealed24h: (
      db.prepare("SELECT COUNT(*) AS n FROM calls WHERE started_at > ? AND caller_number IS NULL").get(now() - 86_400_000) as { n: number }
    ).n,
  }));
};
