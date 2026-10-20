import { describe, expect, it } from "vitest";
import { parseDiversions, resolveCallerIdentity, type InboundSignals } from "../src/telephony/callerIdentity.js";
import { looksAnonymous, toE164 } from "../src/lib/phone.js";

const OWNER = "+12125550111";
const OURS = new Set(["+18005550100"]);
const ctx = { ownerNumber: OWNER, ourNumbers: OURS, defaultCountry: "US" };

const sig = (over: Partial<InboundSignals>): InboundSignals => ({
  from: null,
  to: "+18005550100",
  callerIdName: null,
  headers: [],
  ...over,
});

function passport(payload: object, ppt = "shaken"): string {
  const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "ES256", ppt, typ: "passport" })}.${b64(payload)}.c2ln;info=<https://cert.example/x.pem>;alg=ES256;ppt=${ppt}`;
}

describe("phone normalization", () => {
  it.each([
    ["+13105550199", "+13105550199"],
    ["13105550199", "+13105550199"],
    ["3105550199", "+13105550199"],
    ["sip:+13105550199@10.0.0.1;user=phone", "+13105550199"],
    ['"Bob" <sip:+13105550199@carrier.net>;tag=abc', "+13105550199"],
    ["tel:+13105550199", "+13105550199"],
    ["+1 (310) 555-0199", "+13105550199"],
  ])("%s -> %s", (raw, e164) => expect(toE164(raw, "US")).toBe(e164));

  it.each(["anonymous", "Anonymous", "sip:anonymous@anonymous.invalid", "+266696687", "Restricted", "", "0000000000", "Unavailable"])(
    "treats %j as withheld",
    (raw) => {
      expect(looksAnonymous(raw)).toBe(true);
      expect(toE164(raw, "US")).toBeNull();
    },
  );

  it("parses Israeli numbers", () => {
    expect(toE164("0541234567", "IL")).toBe("+972541234567");
    expect(toE164("+972-54-123-4567", "IL")).toBe("+972541234567");
  });
});

describe("resolveCallerIdentity", () => {
  it("toll-free ANI: the real number arrives in From", () => {
    const id = resolveCallerIdentity(sig({ from: "+13105550199", headers: [{ name: "Privacy", value: "id" }] }), ctx);
    expect(id).toMatchObject({ number: "+13105550199", source: "from", wasHidden: true });
  });

  it("anonymous From, real number in P-Asserted-Identity", () => {
    const id = resolveCallerIdentity(
      sig({
        from: "sip:anonymous@anonymous.invalid",
        headers: [{ name: "P-Asserted-Identity", value: "<sip:+13105550199@ims.carrier.net;user=phone>" }],
      }),
      ctx,
    );
    expect(id).toMatchObject({ number: "+13105550199", source: "p_asserted_identity", wasHidden: true });
  });

  it("STIR/SHAKEN PASSporT outranks everything else and reports attestation", () => {
    const id = resolveCallerIdentity(
      sig({
        from: "anonymous",
        headers: [
          { name: "Identity", value: passport({ attest: "A", orig: { tn: "13105550199" }, dest: { tn: ["12125550111"] } }) },
          { name: "P-Asserted-Identity", value: "<sip:+19995550000@x>" },
        ],
      }),
      ctx,
    );
    expect(id).toMatchObject({ number: "+13105550199", source: "stir_shaken", attestation: "A", wasHidden: true });
  });

  it("never reports the forwarding subscriber as the caller", () => {
    const id = resolveCallerIdentity(
      sig({
        from: OWNER, // some carriers rewrite From to the forwarding party
        headers: [{ name: "P-Asserted-Identity", value: "<tel:+13105550199>" }],
      }),
      ctx,
    );
    expect(id.number).toBe("+13105550199");
  });

  it("returns none when only the owner and our numbers are present", () => {
    const id = resolveCallerIdentity(
      sig({ from: OWNER, headers: [{ name: "P-Charge-Info", value: `<sip:${OWNER}@x>` }] }),
      ctx,
    );
    expect(id).toMatchObject({ number: null, source: "none" });
  });

  it("a normal (not hidden) declined call is not flagged hidden", () => {
    const id = resolveCallerIdentity(sig({ from: "+13105550199", callerIdName: "JOHN SMITH" }), ctx);
    expect(id).toMatchObject({ number: "+13105550199", wasHidden: false, callerName: "JOHN SMITH" });
  });

  it("Remote-Party-ID privacy marks the call hidden", () => {
    const id = resolveCallerIdentity(
      sig({ from: "+13105550199", headers: [{ name: "Remote-Party-ID", value: "<sip:+13105550199@x>;privacy=full;screen=yes" }] }),
      ctx,
    );
    expect(id.wasHidden).toBe(true);
  });
});

describe("parseDiversions", () => {
  it("reads Diversion and DIV PASSporT", () => {
    const d = parseDiversions(
      [
        { name: "Diversion", value: "<sip:+12125550111@carrier.net>;reason=user-busy;counter=1" },
        { name: "Identity", value: passport({ orig: { tn: "13105550199" }, div: { tn: "12125550111" } }, "div") },
      ],
      "US",
    );
    expect(d).toEqual([
      { number: OWNER, reason: "user-busy" },
      { number: OWNER, reason: "div-passport" },
    ]);
  });
});
