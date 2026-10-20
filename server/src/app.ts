import Fastify, { type FastifyInstance, type FastifyReply, type FastifyRequest } from "fastify";
import fastifyJwt from "@fastify/jwt";
import rateLimit from "@fastify/rate-limit";
import multipart from "@fastify/multipart";
import type { Config } from "./config.js";
import type { Db } from "./db.js";
import { CallFlow } from "./telephony/callFlow.js";
import type { TelephonyClient } from "./telephony/telnyx.js";
import { Notifier, type PushSender } from "./services/notifications.js";
import { OtpService } from "./services/otp.js";
import { MediaStore } from "./services/storage.js";
import { authRoutes } from "./routes/auth.js";
import { meRoutes } from "./routes/me.js";
import { callRoutes } from "./routes/calls.js";
import { setupRoutes } from "./routes/setup.js";
import { webhookRoutes } from "./routes/webhooks.js";
import { adminRoutes } from "./routes/admin.js";
import { mediaRoutes } from "./routes/media.js";

export interface Deps {
  cfg: Config;
  db: Db;
  telephony: TelephonyClient;
  push: PushSender;
  now?: () => number;
}

export interface Services {
  cfg: Config;
  db: Db;
  telephony: TelephonyClient;
  media: MediaStore;
  notifier: Notifier;
  otp: OtpService;
  flow: CallFlow;
  now: () => number;
}

declare module "fastify" {
  interface FastifyInstance {
    services: Services;
    authenticate: (req: FastifyRequest, reply: FastifyReply) => Promise<void>;
  }
}

declare module "@fastify/jwt" {
  interface FastifyJWT {
    payload: { sub: string };
    user: { sub: string };
  }
}

export async function buildApp(deps: Deps): Promise<FastifyInstance> {
  const { cfg, db, telephony } = deps;
  const now = deps.now ?? Date.now;
  const app = Fastify({
    logger:
      cfg.NODE_ENV === "test"
        ? false
        : { level: cfg.NODE_ENV === "production" ? "info" : "debug", redact: ["req.headers.authorization"] },
    trustProxy: true,
    bodyLimit: 1_000_000,
  });

  const media = new MediaStore(cfg.DATA_DIR);
  const notifier = new Notifier(db, deps.push, telephony, cfg.SMS_FROM_NUMBER, app.log);
  const otp = new OtpService(db, cfg, telephony, app.log, now);
  const flow = new CallFlow(db, cfg, telephony, notifier, media, app.log, now);
  app.decorate("services", { cfg, db, telephony, media, notifier, otp, flow, now });

  // Keep the raw body: Telnyx signs the exact bytes it sent.
  app.addContentTypeParser("application/json", { parseAs: "string" }, (req, body, done) => {
    (req as FastifyRequest & { rawBody?: string }).rawBody = body as string;
    try {
      done(null, body ? JSON.parse(body as string) : {});
    } catch (err) {
      (err as Error & { statusCode?: number }).statusCode = 400;
      done(err as Error, undefined);
    }
  });

  await app.register(fastifyJwt, { secret: cfg.JWT_SECRET, sign: { expiresIn: "180d" } });
  await app.register(rateLimit, { global: false });
  await app.register(multipart, { limits: { fileSize: 5_000_000, files: 1 } });

  app.decorate("authenticate", async (req: FastifyRequest, reply: FastifyReply) => {
    try {
      await req.jwtVerify();
    } catch {
      return reply.code(401).send({ error: "unauthorized" });
    }
    const exists = db.prepare("SELECT 1 FROM users WHERE id = ?").get(req.user.sub);
    if (!exists) return reply.code(401).send({ error: "unauthorized" });
  });

  app.setErrorHandler((err: Error & { statusCode?: number; validation?: unknown }, req, reply) => {
    if (err.name === "ZodError") return reply.code(400).send({ error: "invalid_request", details: (err as any).issues });
    const status = err.statusCode && err.statusCode >= 400 ? err.statusCode : 500;
    if (status >= 500) req.log.error({ err }, "request failed");
    return reply.code(status).send({ error: status >= 500 ? "internal_error" : err.message });
  });

  app.get("/health", async () => ({ ok: true }));

  await app.register(authRoutes, { prefix: "/v1/auth" });
  await app.register(meRoutes, { prefix: "/v1" });
  await app.register(callRoutes, { prefix: "/v1" });
  await app.register(setupRoutes, { prefix: "/v1/setup" });
  await app.register(webhookRoutes, { prefix: "/webhooks" });
  await app.register(adminRoutes, { prefix: "/admin" });
  await app.register(mediaRoutes, { prefix: "/media" });

  return app;
}
