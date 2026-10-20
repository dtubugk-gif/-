import { parsePhoneNumberFromString, type CountryCode } from "libphonenumber-js/max";

/** Values carriers put in caller-ID fields when the number is withheld. */
const ANONYMOUS_PATTERNS = [
  /anonymous/i,
  /restricted/i,
  /private/i,
  /unavailable/i,
  /unknown/i,
  /withheld/i,
  /blocked/i,
  /^\+?266696687$/, // "ANONYMOUS" on a keypad; used by several US carriers
  /^\+?7378742833$/, // "RESTRICTED"
  /^\+?86282452253$/, // "UNAVAILABLE"
  /^\+?8656696$/, // "UNKNOWN"
  /^\+?0+$/,
];

/**
 * Pulls the user part out of anything a SIP stack might hand us:
 * `"Bob" <sip:+15551234567@host;user=phone>`, `tel:+15551234567`, `+1-555-123-4567`.
 */
export function extractUserPart(raw: string | null | undefined): string | null {
  if (!raw) return null;
  let s = raw.trim();
  const angle = s.match(/<([^>]+)>/);
  if (angle) s = angle[1]!;
  s = s.replace(/^(sips?|tel):/i, "");
  s = s.split(/[@;]/)[0] ?? "";
  try {
    s = decodeURIComponent(s);
  } catch {
    /* keep as-is */
  }
  s = s.trim();
  return s.length ? s : null;
}

export function looksAnonymous(raw: string | null | undefined): boolean {
  const user = extractUserPart(raw);
  if (!user) return true;
  return ANONYMOUS_PATTERNS.some((re) => re.test(user));
}

/** Normalizes to E.164, or null if the value is not a plausible phone number. */
export function toE164(raw: string | null | undefined, defaultCountry: string): string | null {
  const user = extractUserPart(raw);
  if (!user || looksAnonymous(user)) return null;
  const digits = user.replace(/[^\d+]/g, "");
  if (digits.replace(/\D/g, "").length < 6) return null;
  const parsed = parsePhoneNumberFromString(digits, defaultCountry.toUpperCase() as CountryCode);
  if (parsed?.isPossible()) return parsed.number;
  // Some trunks deliver 11-digit NANP without '+', which the parser above already handles;
  // anything else with a leading country code but no '+' gets one more attempt.
  if (!digits.startsWith("+")) {
    const intl = parsePhoneNumberFromString(`+${digits}`);
    if (intl?.isPossible()) return intl.number;
  }
  return null;
}

export function countryOf(e164: string): string | null {
  return parsePhoneNumberFromString(e164)?.country ?? null;
}

export function formatNational(e164: string): string {
  const p = parsePhoneNumberFromString(e164);
  return p ? p.formatNational() : e164;
}

export function formatInternational(e164: string): string {
  const p = parsePhoneNumberFromString(e164);
  return p ? p.formatInternational() : e164;
}

/** National significant number, e.g. 8005551234 for +18005551234. */
export function nationalDigits(e164: string): string {
  const p = parsePhoneNumberFromString(e164);
  return p ? String(p.nationalNumber) : e164.replace(/\D/g, "");
}

export function lineType(e164: string): string | undefined {
  return parsePhoneNumberFromString(e164)?.getType();
}
