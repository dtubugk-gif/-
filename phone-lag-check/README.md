# Phone Lag Check (Samsung Galaxy S23+)

A **read-only** script that looks for the usual reasons an Android phone gets laggy
and writes a report with a plain-English summary and fixes at the end.
It changes nothing on the phone.

It checks:

| Area | What it looks for |
|---|---|
| CPU | Apps burning CPU in the background, `kswapd` (memory pressure) |
| Storage | Phone too full (>80%), slow storage writes, biggest apps and caches |
| RAM | Low available memory, swap/RAM Plus full, apps killed for lack of memory |
| Heat & battery | Thermal throttling, hot battery, Power saving mode |
| Settings | Slow animations, "Don't keep activities", Force 4x MSAA, 60Hz screen, accessibility services, third-party launcher |
| Stutter | Dropped-frame (jank) % for each app, including the home screen |
| Freezes | Apps that froze ("isn't responding") or crashed in the last few days |
| Background | Apps with most services, wake locks, number of installed apps |
| Live mode | Records 60 s **while you use the phone**: jank, CPU, RAM and heat during that time |

## 1. One-time setup on the phone

1. **Settings > About phone > Software information** → tap **Build number** 7 times → enter your PIN.
   "Developer options" now appears at the bottom of Settings.
2. **Settings > Developer options** → turn on **USB debugging** (and/or **Wireless debugging**).

## 2. One-time setup on the computer

Install **adb**:
- **Windows:** download *SDK Platform-Tools for Windows* from
  <https://developer.android.com/tools/releases/platform-tools>, unzip it, and copy
  `adb.exe`, `AdbWinApi.dll`, `AdbWinUsbApi.dll` into this `phone-lag-check` folder.
- **Mac:** `brew install android-platform-tools`
- **Linux:** `sudo apt install adb`

## 3. Connect

**USB cable:** plug in → on the phone tap **Allow** on "Allow USB debugging?".
Check with `adb devices`. It should say `device`, not `unauthorized`.

**Wireless (same Wi-Fi):**
1. Phone: **Developer options > Wireless debugging** → on → **Pair device with pairing code**.
2. Computer: `adb pair 192.168.x.x:PAIRPORT` → type the 6-digit code.
3. Back on the Wireless debugging screen, note the **IP address & Port** (a different port) →
   `adb connect 192.168.x.x:PORT`
4. `adb devices` should list it.

## 4. Run it

- **Windows:** double-click `run.bat`
- **Mac/Linux:** `./run.sh`

Choose:
1. **Quick check:** about a minute. Leave the phone alone.
2. **Watch while I use:** 60 s recording. Use the phone during it and do whatever feels laggy
   (scroll, open apps, go home, open the notification shade).
3. **Both:** recommended for the first run.

The report is saved as `report-<mode>-<date>.txt` next to the script. Read the **SUMMARY** at the bottom first.

### Manual way (any OS)

```
adb push lagcheck.sh /data/local/tmp/
adb shell sh /data/local/tmp/lagcheck.sh --full 60 > report.txt
```

Options: `--check` (default), `--watch SECONDS`, `--full SECONDS`.

## Notes

- Nothing is changed. The few commands that *do* change things (clear all caches, faster
  animations, re-optimize apps) are only **printed** at the end of the report for you to run if you want.
- Results like "apps killed" and "freezes" depend on how far back the phone's logs go
  (the report shows the time window).
- When done, you can turn **USB/Wireless debugging** off again.
- If two devices are connected (e.g. USB + wireless), unplug one or set `ANDROID_SERIAL`.
