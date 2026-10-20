import type { FastifyPluginAsync } from "fastify";
import { forwardingInstructions, type ForwardingMode } from "../services/forwardingCodes.js";
import { ensureForwardingNumber } from "../services/numbers.js";

export const setupRoutes: FastifyPluginAsync = async (app) => {
  const { db, flow, now } = app.services;
  app.addHook("preHandler", app.authenticate);

  app.get("/instructions", async (req, reply) => {
    const u = db.prepare("SELECT id, country, carrier, forwarding_mode FROM users WHERE id = ?").get(req.user.sub) as {
      id: string;
      country: string;
      carrier: string | null;
      forwarding_mode: ForwardingMode;
    };
    const number = ensureForwardingNumber(db, u.id, u.country, now());
    if (!number) return reply.code(503).send({ error: "no_numbers_available" });
    return forwardingInstructions(u.carrier, u.forwarding_mode, number);
  });

  app.post("/verify", { config: { rateLimit: { max: 6, timeWindow: "15 minutes" } } }, async (req, reply) => {
    try {
      const id = await flow.startVerification(req.user.sub);
      return { verificationId: id };
    } catch (err) {
      req.log.error({ err }, "verification dial failed");
      return reply.code(502).send({ error: "dial_failed" });
    }
  });

  app.get<{ Params: { id: string } }>("/verify/:id", async (req, reply) => {
    const status = flow.verificationStatus(req.user.sub, req.params.id);
    if (!status) return reply.code(404).send({ error: "not_found" });
    return status;
  });
};
