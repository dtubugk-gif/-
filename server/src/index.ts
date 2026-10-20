import { join } from "node:path";
import { buildApp } from "./app.js";
import { loadConfig } from "./config.js";
import { openDb } from "./db.js";
import { ExpoPushSender } from "./services/notifications.js";
import { TelnyxClient } from "./telephony/telnyx.js";

const cfg = loadConfig();
const db = openDb(join(cfg.DATA_DIR, "unveil.db"));
const telephony = new TelnyxClient(
  cfg.TELNYX_API_KEY,
  cfg.TELNYX_CONNECTION_ID,
  cfg.TELNYX_VERIFY_PROFILE_ID,
  cfg.TELNYX_MESSAGING_PROFILE_ID,
);
const app = await buildApp({ cfg, db, telephony, push: new ExpoPushSender() });

// Retention: raw signaling contains callers' numbers; keep it only as long as it is useful for debugging.
const purge = () => {
  const cutoff = Date.now() - cfg.DIAGNOSTICS_RETENTION_DAYS * 86_400_000;
  db.prepare("DELETE FROM call_events WHERE received_at < ?").run(cutoff);
  db.prepare("DELETE FROM webhook_dedupe WHERE received_at < ?").run(Date.now() - 86_400_000);
  db.prepare("DELETE FROM otp_challenges WHERE created_at < ?").run(Date.now() - 86_400_000);
};
purge();
setInterval(purge, 3_600_000).unref();

const shutdown = async () => {
  await app.close();
  db.close();
  process.exit(0);
};
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);

await app.listen({ host: cfg.HOST, port: cfg.PORT });
