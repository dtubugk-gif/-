import { createHash, createHmac, randomInt, randomUUID, timingSafeEqual } from "node:crypto";

export const newId = (): string => randomUUID();

export function sha256Hex(data: string | Buffer): string {
  return createHash("sha256").update(data).digest("hex");
}

export function hmac(secret: string, data: string): string {
  return createHmac("sha256", secret).update(data).digest("base64url");
}

export function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  return ab.length === bb.length && timingSafeEqual(ab, bb);
}

export function otpCode(): string {
  return String(randomInt(0, 1_000_000)).padStart(6, "0");
}

/** Short-lived URL signature for media Telnyx must fetch without our auth header. */
export function signPath(secret: string, path: string, ttlSeconds: number, nowMs = Date.now()): string {
  const exp = Math.floor(nowMs / 1000) + ttlSeconds;
  return `${path}?exp=${exp}&sig=${hmac(secret, `${path}:${exp}`)}`;
}

export function verifySignedPath(secret: string, path: string, exp: string, sig: string, nowMs = Date.now()): boolean {
  const e = Number(exp);
  if (!Number.isFinite(e) || e < Math.floor(nowMs / 1000)) return false;
  return safeEqual(hmac(secret, `${path}:${e}`), sig);
}
