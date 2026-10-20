import type { FastifyPluginAsync } from "fastify";
import { z } from "zod";
import { newId, signPath } from "../lib/crypto.js";
import { toE164 } from "../lib/phone.js";
import { isEntitled } from "../services/entitlements.js";
import { buildCsv, buildPdf, recordsDigest, type ReportCall } from "../services/reports.js";

interface CallRow extends ReportCall {
  user_id: string;
  answered_at: number | null;
  recording_path: string | null;
}

const listQuery = z.object({
  before: z.coerce.number().int().optional(),
  limit: z.coerce.number().int().min(1).max(100).default(50),
  number: z.string().optional(),
});

export const callRoutes: FastifyPluginAsync = async (app) => {
  const { db, cfg, media, now } = app.services;

  /** Public: lets a police officer or attorney confirm a report was issued unaltered. */
  app.get<{ Params: { id: string } }>("/reports/verify/:id", async (req, reply) => {
    const r = db.prepare("SELECT id, sha256, call_count, created_at FROM reports WHERE id = ?").get(req.params.id) as
      | { id: string; sha256: string; call_count: number; created_at: number }
      | undefined;
    if (!r) return reply.code(404).send({ valid: false });
    return { valid: true, reportId: r.id, sha256: r.sha256, callCount: r.call_count, issuedAt: new Date(r.created_at).toISOString() };
  });

  app.register(async (authed) => {
    authed.addHook("preHandler", app.authenticate);

    const entitled = (userId: string) => {
      const u = db.prepare("SELECT subscription_expires_at FROM users WHERE id = ?").get(userId) as {
        subscription_expires_at: number | null;
      };
      return isEntitled(cfg, u, now());
    };

    const serialize = (c: CallRow, unlocked: boolean) => ({
      id: c.id,
      startedAt: c.started_at,
      endedAt: c.ended_at,
      callerNumber: unlocked ? c.caller_number : null,
      callerName: unlocked ? c.caller_name : null,
      revealed: !!c.caller_number,
      locked: !unlocked && !!c.caller_number,
      wasHidden: !!c.was_hidden,
      source: c.caller_source,
      status: c.status,
      hasVoicemail: !!c.recording_path,
      voicemailSeconds: c.recording_seconds,
      note: c.note,
    });

    authed.get("/calls", async (req) => {
      const q = listQuery.parse(req.query);
      const unlocked = entitled(req.user.sub);
      const number = q.number ? toE164(q.number, cfg.DEFAULT_COUNTRY) : null;
      const rows = db
        .prepare(
          `SELECT * FROM calls WHERE user_id = ? AND started_at < ? ${number ? "AND caller_number = ?" : ""}
           ORDER BY started_at DESC, rowid DESC LIMIT ?`,
        )
        .all(...([req.user.sub, q.before ?? Number.MAX_SAFE_INTEGER, ...(number ? [number] : []), q.limit] as Array<string | number>)) as unknown as CallRow[];
      return { calls: rows.map((c) => serialize(c, unlocked)), nextBefore: rows.length === q.limit ? rows.at(-1)!.started_at : null };
    });

    const own = (userId: string, id: string) =>
      db.prepare("SELECT * FROM calls WHERE id = ? AND user_id = ?").get(id, userId) as CallRow | undefined;

    authed.get<{ Params: { id: string } }>("/calls/:id", async (req, reply) => {
      const c = own(req.user.sub, req.params.id);
      if (!c) return reply.code(404).send({ error: "not_found" });
      const unlocked = entitled(req.user.sub);
      const stats = c.caller_number
        ? (db
            .prepare("SELECT COUNT(*) AS n, MIN(started_at) AS first FROM calls WHERE user_id = ? AND caller_number = ?")
            .get(req.user.sub, c.caller_number) as { n: number; first: number })
        : null;
      return {
        ...serialize(c, unlocked),
        callsFromThisNumber: stats?.n ?? 1,
        firstCallFromThisNumber: stats?.first ?? c.started_at,
        recordingUrl:
          c.recording_path && unlocked
            ? `${cfg.PUBLIC_BASE_URL}${signPath(cfg.JWT_SECRET, `/media/recordings/${c.id}`, 3600, now())}`
            : null,
      };
    });

    authed.patch<{ Params: { id: string } }>("/calls/:id", async (req, reply) => {
      const b = z.object({ note: z.string().max(2000).nullable() }).parse(req.body);
      const res = db.prepare("UPDATE calls SET note = ? WHERE id = ? AND user_id = ?").run(b.note, req.params.id, req.user.sub);
      if (!res.changes) return reply.code(404).send({ error: "not_found" });
      return { ok: true };
    });

    authed.delete<{ Params: { id: string } }>("/calls/:id", async (req, reply) => {
      const c = own(req.user.sub, req.params.id);
      if (!c) return reply.code(404).send({ error: "not_found" });
      await media.remove(c.recording_path);
      db.prepare("DELETE FROM calls WHERE id = ?").run(c.id);
      return reply.code(204).send();
    });

    authed.get("/blocklist", async (req) => ({
      numbers: db.prepare("SELECT number, created_at AS createdAt FROM blocklist WHERE user_id = ? ORDER BY created_at DESC").all(req.user.sub),
    }));

    authed.post("/blocklist", async (req, reply) => {
      const b = z.object({ number: z.string().optional(), callId: z.string().optional() }).parse(req.body);
      let number: string | null = null;
      if (b.callId) number = own(req.user.sub, b.callId)?.caller_number ?? null;
      else if (b.number) number = toE164(b.number, cfg.DEFAULT_COUNTRY);
      if (!number) return reply.code(400).send({ error: "invalid_phone" });
      db.prepare("INSERT OR IGNORE INTO blocklist (user_id, number, created_at) VALUES (?, ?, ?)").run(req.user.sub, number, now());
      return { ok: true };
    });

    authed.delete<{ Params: { number: string } }>("/blocklist/:number", async (req) => {
      db.prepare("DELETE FROM blocklist WHERE user_id = ? AND number = ?").run(req.user.sub, req.params.number);
      return { ok: true };
    });

    authed.post("/reports", { config: { rateLimit: { max: 20, timeWindow: "1 hour" } } }, async (req, reply) => {
      if (!entitled(req.user.sub)) return reply.code(402).send({ error: "subscription_required" });
      const b = z
        .object({ from: z.number().int().optional(), to: z.number().int().optional(), number: z.string().optional() })
        .parse(req.body ?? {});
      const user = db.prepare("SELECT phone, timezone FROM users WHERE id = ?").get(req.user.sub) as { phone: string; timezone: string };
      const number = b.number ? toE164(b.number, cfg.DEFAULT_COUNTRY) : null;
      const calls = db
        .prepare(
          `SELECT * FROM calls WHERE user_id = ? AND status != 'blocked' AND started_at >= ? AND started_at <= ?
           ${number ? "AND caller_number = ?" : ""} ORDER BY started_at ASC`,
        )
        .all(...([req.user.sub, b.from ?? 0, b.to ?? Number.MAX_SAFE_INTEGER, ...(number ? [number] : [])] as Array<string | number>)) as unknown as CallRow[];

      const id = newId();
      const digest = recordsDigest(calls);
      const generatedAt = now();
      const pdf = await buildPdf(
        calls,
        {
          reportId: id,
          accountPhone: user.phone,
          timezone: user.timezone,
          generatedAt,
          rangeFrom: b.from ?? null,
          rangeTo: b.to ?? null,
          verifyUrl: `${cfg.PUBLIC_BASE_URL}/v1/reports/verify/${id}`,
        },
        digest,
      );
      await media.put("reports", `${id}.pdf`, pdf);
      await media.put("reports", `${id}.csv`, Buffer.from(buildCsv(calls, user.timezone)));
      db.prepare(
        "INSERT INTO reports (id, user_id, sha256, call_count, range_from, range_to, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).run(id, req.user.sub, digest, calls.length, b.from ?? null, b.to ?? null, generatedAt);

      const url = (ext: string) => `${cfg.PUBLIC_BASE_URL}${signPath(cfg.JWT_SECRET, `/media/reports/${id}.${ext}`, 3600, now())}`;
      return { id, callCount: calls.length, sha256: digest, pdfUrl: url("pdf"), csvUrl: url("csv") };
    });
  });
};
