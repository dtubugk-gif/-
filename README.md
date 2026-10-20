# Unveil — Reveal No Caller ID

An Android/iOS app + backend that shows the real number behind **No Caller ID / Private / Blocked** calls,
keeps a timestamped record with voicemails, and exports evidence reports (PDF/CSV) — the same product
category as MaskOff and TrapCall.

```
 Hidden caller ──► your phone (shows "No Caller ID")
                      │  you press DECLINE
                      ▼  carrier "forward when busy"  (**67*<number>#)
              your dedicated Unveil toll-free number (Telnyx)
                      │  toll-free lines receive the caller's real number (ANI);
                      │  the server also reads STIR/SHAKEN + P-Asserted-Identity
                      ▼
              server ──► push notification "Hidden caller revealed: +1 310 555 0199"
                     └─► greets the caller, records voicemail, logs everything
```

| Folder | What |
| --- | --- |
| `server/` | Node 22 + TypeScript (Fastify, SQLite). Telnyx Call Control webhooks, caller-ID extraction, alerts, voicemail, reports, billing webhooks, admin diagnostics. 42 tests. |
| `mobile/` | Expo SDK 58 / React Native app, English + Hebrew (RTL). |
| `.github/workflows/android-apk.yml` | Builds the **APK** on every push and publishes it as a GitHub Release. |

## Honest limits (read before launch)

- **The phone app alone cannot reveal anything.** The reveal happens in the telephone network when the
  declined call is forwarded to a toll-free number. The app is the remote control and the inbox.
- **Spoofed numbers can't be detected** — a caller using a fake caller ID shows the fake number. Same for every app in this category.
- **Some VoIP callers** (apps that place calls over the internet with *67) may reach the toll-free line without
  a real number. The app logs those honestly as "Hidden — not revealed".
- **Your carrier must allow conditional forwarding.** Most US/IL/UK carriers do; a few plans require calling the carrier.
- **Which provider you use matters.** Telnyx documents that toll-free numbers always receive the real caller ID.
  Twilio is reported *not* to pass it. Verify on your own route with the diagnostics endpoint before launch (step 4).
- **Israel:** the code supports Israeli carriers and Hebrew, but confirm with a test call that Israeli 1-800 numbers
  from your provider receive withheld numbers before marketing there.

## Go-live checklist

### 1. Telnyx (≈30 min)
1. Create an account at portal.telnyx.com and add funds.
2. **Voice → Programmable Voice → Create Voice API application.** Webhook URL: `https://<your-server>/webhooks/telnyx`, API v2.
3. **Numbers → Buy:** toll-free numbers (one per user; ~$1–2/month each) **+ one regular local number** (used to place the setup test call).
   Assign all of them to the application from step 2.
4. **Voice → Outbound Voice Profiles → Create**, allow your countries, and attach it to the application
   (the server places the setup test call through it).
5. **Verify → Create profile (SMS)** for sign-up codes.
6. Copy: API key, **Public key** (Keys & Credentials), application (connection) id, verify profile id.

### 2. Deploy the server
Any Docker host with a persistent volume (Fly.io, Render, Railway, a VPS):
```bash
cd server
cp .env.example .env      # fill it in — every setting is documented there
docker build -t unveil-server .
docker run -d --env-file .env -v unveil-data:/data -p 8080:8080 unveil-server
curl https://<your-server>/health   # {"ok":true}
```
Put it behind HTTPS (Telnyx webhooks and the app require it).

### 3. Register your forwarding numbers
```bash
curl -X POST https://<your-server>/admin/numbers \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"numbers":["+18885550100","+18885550101"]}'
curl https://<your-server>/admin/numbers -H "Authorization: Bearer $ADMIN_TOKEN"   # pool status
```
Each new user is assigned a free number automatically. Keep the pool topped up (`/admin/stats`).

### 4. Prove it works on your route (do not skip)
1. From several phones/carriers, dial `*67` + one of your toll-free numbers.
2. Open `GET /admin/diagnostics/inbound` (admin token). For each call you'll see what every signal carried
   (From, STIR/SHAKEN PASSporT, P-Asserted-Identity, Privacy, Diversion) and which number was chosen.
3. Optional public demo line: set `DEMO_LINE_NUMBER`; anyone calling it with *67 hears their own number read back.

### 5. Install the app (APK)
Every push to `mobile/` builds an APK: **GitHub → Releases → "Unveil APK #N" → download the .apk on the phone → open → install**
(allow "install unknown apps" once). In the app, tap **Server** on the welcome screen and enter your server URL —
or bake it in with the repository variable `API_URL`.

Optional repository settings (Settings → Secrets and variables → Actions):

| Name | Type | Purpose |
| --- | --- | --- |
| `API_URL` | variable | Default server URL compiled into the app |
| `EAS_PROJECT_ID` | variable | Expo project id (`npx eas-cli init`) — needed for push notifications |
| `GOOGLE_SERVICES_JSON_B64` | secret | `base64 -w0 google-services.json` from a Firebase project — needed for Android push |
| `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | secrets | Your own release signing key (otherwise a fixed debug key is used, fine for sideloading) |
| `REVENUECAT_ANDROID_KEY` | variable | Enables the in-app subscription (Play Store builds only) |

**Until push is configured, alerts arrive by SMS** if `SMS_FROM_NUMBER` + `TELNYX_MESSAGING_PROFILE_ID` are set
(US SMS requires toll-free or 10DLC registration with Telnyx).

### 6. In the app
Sign in with your number → **Setup** tab → pick carrier → tap **Open dialer** (Android pre-fills the code; iPhone: copy & paste)
→ **Call me now** → press **Decline** when it rings. You'll see "Forwarding works ✅" within seconds. If not, the app tells you exactly why
(answered instead of declining / carrier returned busy = forwarding not enabled / etc.).

### 7. Charging money
Set `ENTITLEMENT_MODE=free` for beta. For launch: create subscriptions in Play Console / App Store Connect, connect them in
RevenueCat, point RevenueCat's webhook to `https://<your-server>/webhooks/revenuecat` with the `REVENUECAT_WEBHOOK_AUTH` value,
and build with the RevenueCat key. Non-subscribers still get the alert ("Hidden caller unmasked — subscribe to see who"),
which is the conversion moment. Store billing does not work in sideloaded APKs — publish via Play for payments
(`npx eas-cli build -p android --profile production` produces the AAB).

## Development
```bash
cd server && npm install && npm test                    # 42 tests incl. full webhook call flows
OTP_PROVIDER=console WEBHOOK_SIGNATURE_REQUIRED=false PUBLIC_BASE_URL=http://localhost:8080 \
  JWT_SECRET=$(openssl rand -hex 32) npm run dev        # sign-up codes print to the log
cd mobile && npm install && npx tsc --noEmit && npx expo lint
```

## Legal & privacy notes
- Toll-free ANI delivery is a long-standing feature of the US network (the toll-free subscriber pays for the call); TrapCall has
  operated on it since 2009. It is still your responsibility to get legal advice for each country you launch in.
- Recordings: the default greeting tells callers they're leaving a message. Some jurisdictions (e.g. California) require all-party
  consent for recording live conversations — Unveil only records voicemail after a greeting, never live calls.
- Raw signaling is kept for `DIAGNOSTICS_RETENTION_DAYS` (default 14) and then purged. Contacts matching happens on-device only.
- Account deletion removes calls, voicemails and reports, and quarantines the user's number for 90 days so a new user never
  receives the previous owner's forwarded calls.
