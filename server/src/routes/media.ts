import type { FastifyPluginAsync } from "fastify";
import { verifySignedPath } from "../lib/crypto.js";

type Q = { Querystring: { exp?: string; sig?: string } };

/** Signed, expiring URLs: Telnyx fetches greetings and the app streams audio/reports without headers. */
export const mediaRoutes: FastifyPluginAsync = async (app) => {
  const { db, cfg, media, now } = app.services;
  const ok = (path: string, q: Q["Querystring"]) => !!q.exp && !!q.sig && verifySignedPath(cfg.JWT_SECRET, path, q.exp, q.sig, now());

  app.get<Q & { Params: { userId: string } }>("/greetings/:userId", async (req, reply) => {
    if (!ok(`/media/greetings/${req.params.userId}`, req.query)) return reply.code(403).send();
    const u = db.prepare("SELECT greeting_audio_path FROM users WHERE id = ?").get(req.params.userId) as
      | { greeting_audio_path: string | null }
      | undefined;
    if (!u?.greeting_audio_path) return reply.code(404).send();
    return reply.type("audio/mpeg").send(await media.get(u.greeting_audio_path));
  });

  app.get<Q & { Params: { callId: string } }>("/recordings/:callId", async (req, reply) => {
    if (!ok(`/media/recordings/${req.params.callId}`, req.query)) return reply.code(403).send();
    const c = db.prepare("SELECT recording_path FROM calls WHERE id = ?").get(req.params.callId) as
      | { recording_path: string | null }
      | undefined;
    if (!c?.recording_path) return reply.code(404).send();
    return reply.type("audio/mpeg").header("Cache-Control", "private, max-age=3600").send(await media.get(c.recording_path));
  });

  app.get<Q & { Params: { file: string } }>("/reports/:file", async (req, reply) => {
    const m = req.params.file.match(/^([0-9a-f-]{36})\.(pdf|csv)$/);
    if (!m || !ok(`/media/reports/${req.params.file}`, req.query)) return reply.code(403).send();
    const exists = db.prepare("SELECT 1 FROM reports WHERE id = ?").get(m[1]!);
    if (!exists) return reply.code(404).send();
    const data = await media.get(`reports/${req.params.file}`);
    return reply
      .type(m[2] === "pdf" ? "application/pdf" : "text/csv; charset=utf-8")
      .header("Content-Disposition", `attachment; filename="call-report-${m[1]!.slice(0, 8)}.${m[2]}"`)
      .send(data);
  });
};
