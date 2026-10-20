import { AsYouType, parsePhoneNumberFromString, type CountryCode } from "libphonenumber-js";
import { getLocale, t } from "./i18n";

export function formatPhone(e164: string | null | undefined): string {
  if (!e164) return "";
  const p = parsePhoneNumberFromString(e164);
  return p ? p.formatInternational() : e164;
}

export function formatAsYouType(input: string, country: string): string {
  return new AsYouType(country as CountryCode).input(input);
}

export function toE164(input: string, country: string): string | null {
  const p = parsePhoneNumberFromString(input, country as CountryCode);
  return p?.isValid() ? p.number : null;
}

export function formatDateTime(ts: number): string {
  return new Intl.DateTimeFormat(getLocale() === "he" ? "he-IL" : undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(ts);
}

export function timeAgo(ts: number, now = Date.now()): string {
  const s = Math.round((now - ts) / 1000);
  if (s < 45) return t().justNow;
  const rtf = new Intl.RelativeTimeFormat(getLocale() === "he" ? "he" : "en", { numeric: "auto" });
  if (s < 3600) return rtf.format(-Math.round(s / 60), "minute");
  if (s < 86_400) return rtf.format(-Math.round(s / 3600), "hour");
  if (s < 7 * 86_400) return rtf.format(-Math.round(s / 86_400), "day");
  return formatDateTime(ts);
}
