import { ApiError } from "./api";
import { t } from "./i18n";

export function errorMessage(err: unknown): string {
  const s = t();
  if (err instanceof ApiError) {
    switch (err.code) {
      case "network":
        return s.errorNetwork;
      case "no_server":
        return s.errorNoServer;
      case "invalid_phone":
        return s.errorInvalidPhone;
      case "invalid":
        return s.errorInvalidCode;
      case "expired":
        return s.errorExpired;
      case "rate_limited":
      case "too_many_attempts":
        return s.errorRateLimited;
    }
  }
  return s.error;
}
