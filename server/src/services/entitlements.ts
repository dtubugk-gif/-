import type { Config } from "../config.js";

export interface EntitlementFields {
  subscription_expires_at: number | null;
}

/** Store billing is the source of truth (via RevenueCat webhooks); this only reads the cached expiry. */
export function isEntitled(cfg: Pick<Config, "ENTITLEMENT_MODE">, user: EntitlementFields, now = Date.now()): boolean {
  if (cfg.ENTITLEMENT_MODE === "free") return true;
  return user.subscription_expires_at != null && user.subscription_expires_at > now;
}
