import { DatabaseSync } from "node:sqlite";
import { mkdirSync } from "node:fs";
import { dirname } from "node:path";

export type Db = DatabaseSync;

/**
 * Ordered, append-only migrations. Never edit a shipped entry; add a new one.
 * The index (+1) is stored in PRAGMA user_version.
 */
const MIGRATIONS: string[] = [
  `
  CREATE TABLE users (
    id TEXT PRIMARY KEY,
    phone TEXT NOT NULL UNIQUE,
    country TEXT NOT NULL,
    carrier TEXT,
    forwarding_mode TEXT NOT NULL DEFAULT 'decline',
    locale TEXT NOT NULL DEFAULT 'en',
    timezone TEXT NOT NULL DEFAULT 'UTC',
    greeting_text TEXT,
    greeting_audio_path TEXT,
    forwarding_verified_at INTEGER,
    subscription_expires_at INTEGER,
    subscription_product TEXT,
    subscription_event_at INTEGER,
    created_at INTEGER NOT NULL
  );

  CREATE TABLE forwarding_numbers (
    number TEXT PRIMARY KEY,
    country TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('toll_free', 'local')),
    user_id TEXT UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    assigned_at INTEGER,
    active INTEGER NOT NULL DEFAULT 1,
    available_after INTEGER,
    created_at INTEGER NOT NULL
  );

  CREATE TABLE push_tokens (
    token TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform TEXT NOT NULL,
    updated_at INTEGER NOT NULL
  );
  CREATE INDEX push_tokens_user ON push_tokens(user_id);

  CREATE TABLE otp_challenges (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone TEXT NOT NULL,
    code_hash TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at INTEGER NOT NULL,
    consumed_at INTEGER,
    created_at INTEGER NOT NULL
  );
  CREATE INDEX otp_phone ON otp_challenges(phone, created_at);

  CREATE TABLE calls (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_call_id TEXT NOT NULL UNIQUE,
    provider_session_id TEXT,
    forwarding_number TEXT NOT NULL,
    caller_number TEXT,
    caller_source TEXT NOT NULL,
    caller_name TEXT,
    was_hidden INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    answered_at INTEGER,
    ended_at INTEGER,
    recording_path TEXT,
    recording_seconds INTEGER,
    note TEXT
  );
  CREATE INDEX calls_user_time ON calls(user_id, started_at DESC);
  CREATE INDEX calls_user_caller ON calls(user_id, caller_number);

  CREATE TABLE blocklist (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    number TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (user_id, number)
  );

  CREATE TABLE setup_verifications (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('calling', 'verified', 'failed')),
    outbound_call_id TEXT,
    failure_reason TEXT,
    created_at INTEGER NOT NULL,
    completed_at INTEGER
  );
  CREATE INDEX setup_verifications_user ON setup_verifications(user_id, created_at DESC);

  CREATE TABLE call_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_call_id TEXT,
    event_type TEXT NOT NULL,
    payload TEXT NOT NULL,
    received_at INTEGER NOT NULL
  );
  CREATE INDEX call_events_call ON call_events(provider_call_id);
  CREATE INDEX call_events_time ON call_events(received_at);

  CREATE TABLE webhook_dedupe (
    event_id TEXT PRIMARY KEY,
    received_at INTEGER NOT NULL
  );

  CREATE TABLE reports (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sha256 TEXT NOT NULL,
    call_count INTEGER NOT NULL,
    range_from INTEGER,
    range_to INTEGER,
    created_at INTEGER NOT NULL
  );
  `,
];

export function openDb(file: string): Db {
  if (file !== ":memory:") mkdirSync(dirname(file), { recursive: true });
  const db = new DatabaseSync(file);
  db.exec("PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;");
  migrate(db);
  return db;
}

function migrate(db: Db): void {
  const row = db.prepare("PRAGMA user_version").get() as { user_version: number };
  for (let v = row.user_version; v < MIGRATIONS.length; v++) {
    db.exec("BEGIN");
    try {
      db.exec(MIGRATIONS[v]!);
      db.exec(`PRAGMA user_version = ${v + 1}`);
      db.exec("COMMIT");
    } catch (err) {
      db.exec("ROLLBACK");
      throw err;
    }
  }
}

export function tx<T>(db: Db, fn: () => T): T {
  db.exec("BEGIN IMMEDIATE");
  try {
    const out = fn();
    db.exec("COMMIT");
    return out;
  } catch (err) {
    db.exec("ROLLBACK");
    throw err;
  }
}
