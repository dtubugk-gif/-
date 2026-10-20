import type { FastifyPluginAsync, FastifyRequest } from "fastify";
import { safeEqual } from "../lib/crypto.js";
import { parseTelnyxEvent, verifyTelnyxSignature } from "../telephony/telnyx.js";

const PURCHASE_EVENTS = new Set([
  "INITIAL_PURCHASE",
  "RENEWAL",
  "PRODUCT_CHANGE",
  "UNCANCELLATION",
  "NON_RENEWING_PURCHASE",
  "SUBSCRIPTION_EXTENDED",
  "TEMPORARY_ENTITLEMENT_GRANT",
  "EXPIRATION",
  "CANCELLATION",
  "BILLING_ISSUE",
]);

export const webhookRoutes: FastifyPluginAsync = async (app) => {
  const { cfg, db, flow } = app.services;

  app.post("/telnyx", async (req: FastifyRequest & { rawBody?: string }, reply) => {
    if (cfg.WEBHOOK_SIGNATURE_REQUIRED) {
      const valid = verifyTelnyxSignature({
        publicKeyBase64: cfg.TELNYX_PUBLIC_KEY,
        signatureBase64: req.headers["telnyx-signature-ed25519"] as string | undefined,
        timestamp: req.headers["telnyx-timestamp"] as string | undefined,
        rawBody: req.rawBody ?? "",
      });
      if (!valid) return reply.code(401).send({ error: "bad_signature" });
    }
    const ev = parseTelnyxEvent(req.body);
    if (!ev) return reply.code(400).send({ error: "bad_event" });
    try {
      await flow.handle(ev);
    } catch (err) {
      // Still 200: a retry would replay side effects (alerts) for a failure that is ours to fix.
      req.log.error({ err, type: ev.type, call: ev.callControlId }, "telnyx event handling failed");
    }
    return { ok: true };
  });

  /** RevenueCat sends the configured Authorization value verbatim. */
  app.post("/revenuecat", async (req, reply) => {
    const auth = String(req.headers.authorization ?? "");
    if (!cfg.REVENUECAT_WEBHOOK_AUTH || !safeEqual(auth, cfg.REVENUECAT_WEBHOOK_AUTH)) {
      return reply.code(401).send({ error: "unauthorized" });
    }
    const ev = (req.body as { event?: Record<string, any> })?.event;
    if (!ev?.type) return reply.code(400).send({ error: "bad_event" });
    if (ev.type === "TEST" || !PURCHASE_EVENTS.has(ev.type)) return { ok: true };

    const ids: string[] = [ev.app_user_id, ev.original_app_user_id, ...(ev.aliases ?? [])].filter(Boolean);
    const placeholders = ids.map(() => "?").join(",");
    const user = ids.length
      ? (db.prepare(`SELECT id, subscription_event_at FROM users WHERE id IN (${placeholders}) LIMIT 1`).get(...ids) as
          | { id: string; subscription_event_at: number | null }
          | undefined)
      : undefined;
    if (!user) return { ok: true, ignored: "unknown_user" };

    const eventAt = Number(ev.event_timestamp_ms ?? Date.now());
    // Deliveries can arrive out of order; never let an older event overwrite a newer state.
    if (user.subscription_event_at && eventAt < user.subscription_event_at) return { ok: true, ignored: "stale" };
    const expires = ev.expiration_at_ms != null ? Number(ev.expiration_at_ms) : null;
    db.prepare(
      `UPDATE users SET subscription_expires_at = ?, subscription_product = COALESCE(?, subscription_product),
         subscription_event_at = ? WHERE id = ?`,
    ).run(expires, ev.product_id ?? null, eventAt, user.id);
    return { ok: true };
  });
};
