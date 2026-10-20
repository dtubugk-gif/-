import { nationalDigits } from "../lib/phone.js";

/**
 * "decline": only calls you reject (busy) are forwarded — your carrier voicemail keeps handling
 *            missed calls. Recommended.
 * "full":    busy + no answer + unreachable, i.e. this service also becomes your voicemail.
 */
export type ForwardingMode = "decline" | "full";

type Scheme = "gsm" | "verizon" | "manual";

export interface Carrier {
  id: string;
  name: string;
  country: string;
  scheme: Scheme;
  note?: string;
}

export const CARRIERS: Carrier[] = [
  { id: "att", name: "AT&T", country: "US", scheme: "gsm" },
  { id: "tmobile", name: "T-Mobile", country: "US", scheme: "gsm" },
  { id: "verizon", name: "Verizon", country: "US", scheme: "verizon" },
  { id: "metro", name: "Metro by T-Mobile", country: "US", scheme: "gsm" },
  { id: "mint", name: "Mint Mobile", country: "US", scheme: "gsm" },
  { id: "cricket", name: "Cricket Wireless", country: "US", scheme: "gsm" },
  { id: "uscellular", name: "UScellular", country: "US", scheme: "verizon" },
  { id: "spectrum", name: "Spectrum Mobile", country: "US", scheme: "verizon" },
  { id: "xfinity", name: "Xfinity Mobile", country: "US", scheme: "verizon" },
  {
    id: "visible",
    name: "Visible",
    country: "US",
    scheme: "manual",
    note: "Visible does not accept conditional-forwarding codes. Ask Visible support to enable busy forwarding to your Unveil number.",
  },
  {
    id: "googlefi",
    name: "Google Fi",
    country: "US",
    scheme: "manual",
    note: "Google Fi does not support conditional forwarding codes. Contact Fi support to request busy forwarding.",
  },
  { id: "partner", name: "Partner", country: "IL", scheme: "gsm" },
  { id: "cellcom", name: "Cellcom", country: "IL", scheme: "gsm" },
  { id: "pelephone", name: "Pelephone", country: "IL", scheme: "gsm" },
  { id: "hotmobile", name: "HOT Mobile", country: "IL", scheme: "gsm" },
  { id: "golan", name: "Golan Telecom", country: "IL", scheme: "gsm" },
  { id: "ee", name: "EE", country: "GB", scheme: "gsm" },
  { id: "o2", name: "O2", country: "GB", scheme: "gsm" },
  { id: "vodafone_uk", name: "Vodafone", country: "GB", scheme: "gsm" },
  { id: "three_uk", name: "Three", country: "GB", scheme: "gsm" },
];

const OTHER: Carrier = { id: "other", name: "Other carrier", country: "*", scheme: "gsm" };

export function carriersFor(country: string): Carrier[] {
  return [...CARRIERS.filter((c) => c.country === country.toUpperCase()), { ...OTHER, country }];
}

export function findCarrier(id: string | null | undefined): Carrier {
  return CARRIERS.find((c) => c.id === id) ?? OTHER;
}

export interface DialStep {
  label: string;
  code: string;
  /** Android can prefill the dialer with this. iOS refuses tel: links containing * or #. */
  telUri: string;
}

export interface ForwardingInstructions {
  carrier: Carrier;
  mode: ForwardingMode;
  forwardingNumber: string;
  enable: DialStep[];
  disable: DialStep[];
  status: DialStep[];
  manual: boolean;
  notes: string[];
}

const step = (label: string, code: string): DialStep => ({
  label,
  code,
  telUri: `tel:${encodeURIComponent(code)}`,
});

export function forwardingInstructions(
  carrierId: string | null | undefined,
  mode: ForwardingMode,
  forwardingNumber: string,
): ForwardingInstructions {
  const carrier = findCarrier(carrierId);
  const notes: string[] = [];
  let enable: DialStep[] = [];
  let disable: DialStep[] = [];
  let status: DialStep[] = [];

  if (carrier.scheme === "gsm") {
    // 3GPP TS 22.030 supplementary-service codes: 67 = busy, 61 = no reply, 62 = not reachable,
    // 004 = all conditional forwarding.
    const n = forwardingNumber;
    if (mode === "decline") {
      enable = [step("Forward declined calls", `**67*${n}#`)];
      disable = [step("Turn off", `##67#`)];
      status = [step("Check status", `*#67#`)];
    } else {
      enable = [step("Forward declined, missed and unreachable calls", `**004*${n}#`)];
      disable = [step("Turn off", `##004#`)];
      status = [step("Check busy forwarding", `*#67#`), step("Check no-answer forwarding", `*#61#`)];
      notes.push("If the combined code is rejected, dial these one at a time:", `**67*${n}#`, `**61*${n}**20#`, `**62*${n}#`);
    }
  } else if (carrier.scheme === "verizon") {
    // Verizon-style feature codes take the 10-digit national number. *71 covers busy + no answer;
    // Verizon has no busy-only code that works on every plan, so both modes use it.
    const n = nationalDigits(forwardingNumber);
    enable = [step("Forward declined and missed calls", `*71${n}`)];
    disable = [step("Turn off", `*73`)];
    if (mode === "decline") {
      notes.push(
        `${carrier.name} forwards unanswered calls together with declined ones, so Unveil will also take your voicemail.`,
      );
    }
  }

  const manual = carrier.scheme === "manual";
  if (manual && carrier.note) notes.push(carrier.note);
  notes.push(
    "If your phone shows 'Setting not available' or a network error, your plan needs conditional call forwarding enabled. Call your carrier and ask them to turn on \"call forwarding when busy\" to this number — not unconditional forwarding.",
  );

  return { carrier, mode, forwardingNumber, enable, disable, status, manual, notes };
}
