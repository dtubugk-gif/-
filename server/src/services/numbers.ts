import { tx, type Db } from "../db.js";

/**
 * Each user gets a dedicated forwarding number. Dedicated numbers make ownership of every
 * forwarded call unambiguous even on carriers that strip the Diversion header.
 */
export function ensureForwardingNumber(db: Db, userId: string, country: string, now = Date.now()): string | null {
  return tx(db, () => {
    const existing = db.prepare("SELECT number FROM forwarding_numbers WHERE user_id = ?").get(userId) as
      | { number: string }
      | undefined;
    if (existing) return existing.number;
    const free = db
      .prepare(
        `SELECT number FROM forwarding_numbers
         WHERE user_id IS NULL AND active = 1 AND country = ?
           AND (available_after IS NULL OR available_after <= ?)
         ORDER BY CASE kind WHEN 'toll_free' THEN 0 ELSE 1 END, created_at
         LIMIT 1`,
      )
      .get(country.toUpperCase(), now) as { number: string } | undefined;
    if (!free) return null;
    db.prepare("UPDATE forwarding_numbers SET user_id = ?, assigned_at = ? WHERE number = ?").run(userId, now, free.number);
    return free.number;
  });
}

/**
 * A departing user may forget to turn forwarding off, so a released number is quarantined
 * before reuse; otherwise the next owner would receive the previous owner's declined calls.
 */
export const RELEASE_QUARANTINE_MS = 90 * 24 * 3_600_000;

export function releaseForwardingNumber(db: Db, userId: string, now = Date.now()): void {
  db.prepare(
    "UPDATE forwarding_numbers SET user_id = NULL, assigned_at = NULL, available_after = ? WHERE user_id = ?",
  ).run(now + RELEASE_QUARANTINE_MS, userId);
}

export function poolStats(db: Db, now = Date.now()) {
  return db
    .prepare(
      `SELECT country, kind, COUNT(*) AS total, SUM(CASE WHEN user_id IS NULL AND active = 1 AND (available_after IS NULL OR available_after <= ?) THEN 1 ELSE 0 END) AS free
       FROM forwarding_numbers GROUP BY country, kind`,
    )
    .all(now);
}
