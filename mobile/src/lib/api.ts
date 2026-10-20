import Constants from "expo-constants";
import * as SecureStore from "expo-secure-store";

const TOKEN_KEY = "unveil.token";
const SERVER_KEY = "unveil.server";

let token: string | null = null;
let baseUrl: string = (Constants.expoConfig?.extra?.apiUrl as string | undefined) ?? "";
let onUnauthorized: (() => void) | null = null;

export async function loadSession(): Promise<string | null> {
  const [savedToken, savedServer] = await Promise.all([
    SecureStore.getItemAsync(TOKEN_KEY),
    SecureStore.getItemAsync(SERVER_KEY),
  ]);
  if (savedServer) baseUrl = savedServer;
  token = savedToken;
  return token;
}

export async function saveToken(t: string | null) {
  token = t;
  if (t) await SecureStore.setItemAsync(TOKEN_KEY, t);
  else await SecureStore.deleteItemAsync(TOKEN_KEY);
}

export function getServerUrl() {
  return baseUrl;
}

export async function setServerUrl(url: string) {
  baseUrl = url.trim().replace(/\/+$/, "");
  await SecureStore.setItemAsync(SERVER_KEY, baseUrl);
}

export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
  ) {
    super(code);
  }
}

export async function api<T>(path: string, init: { method?: string; body?: unknown; form?: FormData } = {}): Promise<T> {
  if (!baseUrl) throw new ApiError(0, "no_server");
  let res: Response;
  try {
    res = await fetch(`${baseUrl}${path}`, {
      method: init.method ?? (init.body || init.form ? "POST" : "GET"),
      headers: {
        Accept: "application/json",
        ...(init.body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: init.form ?? (init.body !== undefined ? JSON.stringify(init.body) : undefined),
    });
  } catch {
    throw new ApiError(0, "network");
  }
  if (res.status === 401 && token) onUnauthorized?.();
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const json = text ? (JSON.parse(text) as Record<string, unknown>) : {};
  if (!res.ok) throw new ApiError(res.status, String(json.error ?? res.status));
  return json as T;
}

export interface Me {
  id: string;
  phone: string;
  country: string;
  carrier: string | null;
  forwardingMode: "decline" | "full";
  forwardingNumber: string | null;
  forwardingVerifiedAt: number | null;
  locale: "en" | "he";
  timezone: string;
  greetingText: string | null;
  hasGreetingAudio: boolean;
  entitled: boolean;
  subscriptionExpiresAt: number | null;
  entitlementMode: "revenuecat" | "free";
  demoLineNumber: string | null;
}

export interface CallItem {
  id: string;
  startedAt: number;
  endedAt: number | null;
  callerNumber: string | null;
  callerName: string | null;
  revealed: boolean;
  locked: boolean;
  wasHidden: boolean;
  source: string;
  status: "ringing" | "answered" | "voicemail" | "completed" | "blocked";
  hasVoicemail: boolean;
  voicemailSeconds: number | null;
  note: string | null;
}

export interface CallDetail extends CallItem {
  callsFromThisNumber: number;
  firstCallFromThisNumber: number;
  recordingUrl: string | null;
}

export interface DialStep {
  label: string;
  code: string;
  telUri: string;
}

export interface Instructions {
  carrier: { id: string; name: string };
  mode: "decline" | "full";
  forwardingNumber: string;
  enable: DialStep[];
  disable: DialStep[];
  status: DialStep[];
  manual: boolean;
  notes: string[];
}

export interface Carrier {
  id: string;
  name: string;
  manual: boolean;
  note?: string;
}
