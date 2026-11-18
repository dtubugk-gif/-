#!/system/bin/sh
# lagcheck.sh - read-only "why is my phone slow?" report for Android.
# Tuned for Samsung Galaxy (made for an S23+), works on most Android 10+ phones.
#
# It runs ON the phone through adb and only READS information. It changes nothing.
#
#   adb push lagcheck.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/lagcheck.sh              > report.txt  # quick check
#   adb shell sh /data/local/tmp/lagcheck.sh --watch 60   > report.txt  # record while you use the phone
#   adb shell sh /data/local/tmp/lagcheck.sh --full 60    > report.txt  # both
#
# Progress messages go to stderr (your screen); the report goes to stdout (the file).

VERSION="1.0"
MODE="check"
WATCH_SECS=60

case "$1" in
  --watch|watch) MODE="watch" ;;
  --full|full|all) MODE="full" ;;
  ""|--check|check) MODE="check" ;;
  -h|--help|help)
    echo "usage: sh lagcheck.sh [--check | --watch SECONDS | --full SECONDS]"
    exit 0 ;;
  *) echo "unknown option: $1 (try --help)" >&2; exit 1 ;;
esac
case "$2" in ''|*[!0-9]*) ;; *) WATCH_SECS=$2 ;; esac
[ "$WATCH_SECS" -lt 15 ] && WATCH_SECS=15
[ "$WATCH_SECS" -gt 600 ] && WATCH_SECS=600

TMP=/data/local/tmp/lagcheck.$$
mkdir -p "$TMP" 2>/dev/null || { echo "cannot create $TMP" >&2; exit 1; }
trap 'rm -rf "$TMP"' EXIT
trap 'rm -rf "$TMP"; exit 1' INT TERM

# ---------------------------------------------------------------- helpers
p()   { printf '%s\n' "$*"; }
say() { printf '%s\n' "$*" >&2; }
hdr() { printf '\n========== %s ==========\n' "$*"; say "  ... $*"; }
# integer part of a number, digits only, no leading zeros ("08.5" -> 8, "" -> 0)
int() {
  _v=${1%%.*}
  _v=$(printf '%s' "$_v" | tr -cd '0-9' | sed 's/^0*//')
  printf '%s' "${_v:-0}"
}
# setting value, empty when unset
setting() { _s=$(settings get "$1" "$2" 2>/dev/null); [ "$_s" = "null" ] && _s=""; printf '%s' "$_s"; }

HIGH=""; MED=""; LOW=""; NFLAGS=0; HOT_FLAGGED=0
# flag SEVERITY "what is wrong" "what to do about it"
flag() {
  NFLAGS=$((NFLAGS + 1))
  _item="  * $2
      -> $3
"
  case "$1" in
    HIGH) HIGH="$HIGH$_item" ;;
    MED)  MED="$MED$_item" ;;
    *)    LOW="$LOW$_item" ;;
  esac
  printf '  [!] %s\n' "$2"
}

# per-app jank counters: "package total_frames janky_frames"
gfxsnap() {
  dumpsys gfxinfo 2>/dev/null | awk '
    /Graphics info for pid/ {
      s = index($0, "["); e = index($0, "]")
      pkg = (s > 0 && e > s) ? substr($0, s + 1, e - s - 1) : "?"
      tot = 0
    }
    /Total frames rendered:/ { tot = $NF }
    /Janky frames: / { T[pkg] += tot; J[pkg] += $3 }
    END { for (k in T) print k, T[k] + 0, J[k] + 0 }'
}

mem_avail_pct() {
  awk '/^MemTotal:/ {t=$2} /^MemAvailable:/ {a=$2} END {if (t > 0) printf "%d", a * 100 / t; else print 0}' /proc/meminfo
}
thermal_status() {
  _t=$(dumpsys thermalservice 2>/dev/null | awk -F': *' '/Thermal Status/ {print $2; exit}')
  int "$_t"
}
thermal_name() {
  case "$1" in
    0) echo "none" ;; 1) echo "light" ;; 2) echo "moderate (throttling)" ;;
    3) echo "severe (throttling)" ;; 4) echo "critical" ;; 5) echo "emergency" ;;
    6) echo "shutdown" ;; *) echo "unknown" ;;
  esac
}

START=$(date +%s)
say "lagcheck $VERSION - mode: $MODE - reading phone info (nothing is changed)..."

p "######################################################################"
p "#  PHONE LAG CHECK  v$VERSION   -   $(date '+%Y-%m-%d %H:%M')"
p "#  Read-only report. Lines marked [!] are likely causes of slowness."
p "#  A summary of problems + fixes is at the END of this file."
p "######################################################################"

# ======================================================================
run_check() {
# ---------------------------------------------------------------- CPU first (before our own dumpsys calls add load)
hdr "CPU - busiest processes over the last few minutes"
dumpsys cpuinfo > "$TMP/cpuinfo" 2>/dev/null
head -n 25 "$TMP/cpuinfo"
awk '/^ *[0-9.]+% [0-9]+\// {
       pct = $1; sub(/%/, "", pct)
       name = $2; sub(/^[0-9]+\//, "", name); sub(/:$/, "", name)
       print pct, name
     }' "$TMP/cpuinfo" > "$TMP/cpu_procs"
while read -r pct name; do
  pi=$(int "$pct")
  case "$name" in
    kswapd*)
      [ "$pi" -ge 10 ] && flag MED "kswapd (Android's memory swapper) is using ${pct}% CPU - the phone is short on RAM" \
        "See the MEMORY section: close or uninstall heavy apps you don't use." ;;
    *adbd*|dumpsys|top|logcat|sh|awk|toybox|cmd|system_server|surfaceflinger|*com.android.shell*) ;;
    *.*)
      [ "$pi" -ge 25 ] && flag MED "$name used ${pct}% CPU recently (if you weren't using it, it's misbehaving)" \
        "Update it, or Settings > Apps > (that app) > Force stop / Battery > Restricted, or uninstall it." ;;
  esac
done < "$TMP/cpu_procs"

p ""
p "Right now (5-second sample, top 12):"
top -b -n 2 -d 5 -m 12 > "$TMP/top" 2>/dev/null
if grep -q "Tasks:" "$TMP/top"; then
  awk '/Tasks:/ {f++} f >= 2' "$TMP/top"
else
  top -n 1 2>/dev/null | head -n 20
fi
p ""
p "Load average (1/5/15 min): $(cut -d' ' -f1-3 /proc/loadavg)   (8 CPU cores)"

# ---------------------------------------------------------------- device
hdr "DEVICE"
p "Model:            $(getprop ro.product.model)  ($(getprop ro.product.device))"
p "Android:          $(getprop ro.build.version.release)  (SDK $(getprop ro.build.version.sdk))"
oneui=$(int "$(getprop ro.build.version.oneui)")
[ "$oneui" -gt 0 ] && p "One UI:           $((oneui / 10000)).$(( (oneui / 100) % 100 ))"
p "Firmware:         $(getprop ro.build.display.id)"
patch=$(getprop ro.build.version.security_patch)
p "Security patch:   $patch"
case "$patch" in
  [0-9][0-9][0-9][0-9]-[0-9][0-9]-*)
    py=$(int "${patch%%-*}"); pm=${patch#*-}; pm=$(int "${pm%%-*}")
    ny=$(int "$(date +%Y)"); nm=$(int "$(date +%m)")
    age=$(( (ny * 12 + nm) - (py * 12 + pm) ))
    p "Patch age:        $age months"
    [ "$age" -ge 4 ] && flag LOW "Software is $age months old - updates often fix slowdowns" \
      "Settings > Software update > Download and install. Also update apps in Galaxy Store and Play Store."
    ;;
esac

up=$(int "$(cut -d' ' -f1 /proc/uptime)")
days=$((up / 86400)); hrs=$(( (up % 86400) / 3600 ))
p "Uptime:           ${days}d ${hrs}h since last restart"
if [ "$days" -ge 7 ]; then
  flag MED "Phone hasn't been restarted in $days days" \
    "Restart it. To automate: Settings > Device care > (3 dots) > Automation > Auto restart."
fi

# ---------------------------------------------------------------- storage
hdr "STORAGE"
df -Pk /data > "$TMP/df" 2>/dev/null || df /data > "$TMP/df" 2>/dev/null
cat "$TMP/df"
set -- $(tail -n 1 "$TMP/df")
used_pct=$(int "$5"); avail_k=$(int "$4"); total_k=$(int "$2")
if [ "$total_k" -gt 0 ]; then
  p "Free: $(awk -v a="$avail_k" 'BEGIN {printf "%.1f", a / 1048576}') GB of $(awk -v t="$total_k" 'BEGIN {printf "%.0f", t / 1048576}') GB  (${used_pct}% used)"
  if [ "$used_pct" -ge 90 ]; then
    flag HIGH "Storage is ${used_pct}% full - flash storage gets much slower when nearly full" \
      "Get it under ~80%: delete/move big videos, clear WhatsApp/Telegram media, uninstall unused apps (biggest apps listed in STORAGE)."
  elif [ "$used_pct" -ge 80 ]; then
    flag MED "Storage is ${used_pct}% full" \
      "Try to keep at least 15-20% free: Settings > Device care > Storage, or Files app > Storage analysis."
  fi
fi

dumpsys diskstats > "$TMP/diskstats" 2>/dev/null
lat=$(awk '/^Latency:/ {print $2; exit}' "$TMP/diskstats")
if [ -n "$lat" ]; then
  p "Storage write latency test: $lat"
  li=$(int "$lat")
  [ "$li" -ge 200 ] && flag MED "Storage write test was slow (${li}ms; normal is under ~20ms)" \
    "Free up space and restart. If it stays slow after that, run it again later - one slow sample can be a fluke."
fi
awk '
  function arr(s) { sub(/^[^[]*\[/, "", s); sub(/\].*$/, "", s); gsub(/"/, "", s); return s }
  /^Package Names:/  { np = split(arr($0), P, ",") }
  /^App Sizes:/      { split(arr($0), A, ",") }
  /^App Data Sizes:/ { split(arr($0), D, ",") }
  /^Cache Sizes:/    { split(arr($0), C, ",") }
  END { for (i = 1; i <= np; i++) printf "%.0f %.0f %s\n", (A[i] + D[i]) / 1048576, C[i] / 1048576, P[i] }
' "$TMP/diskstats" > "$TMP/appsizes"
if [ -s "$TMP/appsizes" ]; then
  p ""
  p "Biggest apps (app + data, MB)   [from Android's last storage scan]"
  sort -rn "$TMP/appsizes" | head -n 12 | awk '{printf "  %8d MB  %s\n", $1, $3}'
  p ""
  p "Biggest app caches (MB) - safe to clear: Settings > Apps > (app) > Storage > Clear cache"
  sort -k2,2 -rn "$TMP/appsizes" | head -n 10 | awk '{printf "  %8d MB  %s\n", $2, $3}'
  cache_total=$(awk '{s += $2} END {printf "%d", s}' "$TMP/appsizes")
  p "  Total cache across all apps: ${cache_total} MB"
  [ "$cache_total" -ge 8000 ] && flag LOW "Apps are holding ${cache_total} MB of cache" \
    "Clear cache for the biggest ones listed under STORAGE (not 'Clear data' - that logs you out)."
fi

# ---------------------------------------------------------------- memory
hdr "MEMORY (RAM)"
awk '/^(MemTotal|MemAvailable|SwapTotal|SwapFree):/ {printf "%-14s %6.2f GB\n", $1, $2 / 1048576}' /proc/meminfo
avail=$(mem_avail_pct)
swap_used=$(awk '/^SwapTotal:/ {t=$2} /^SwapFree:/ {f=$2} END {if (t > 0) printf "%d", (t - f) * 100 / t; else print 0}' /proc/meminfo)
p "Available RAM: ${avail}%    Swap (zRAM / RAM Plus) used: ${swap_used}%"
if [ "$avail" -lt 10 ]; then
  flag HIGH "Only ${avail}% of RAM is available - apps are fighting for memory" \
    "Close/uninstall heavy apps (see list below), restart the phone. Disable apps you never use."
elif [ "$avail" -lt 18 ]; then
  flag MED "RAM is getting tight (${avail}% available)" \
    "Check the biggest memory users below; uninstall or restrict ones you don't need."
fi
[ "$swap_used" -ge 85 ] && flag MED "Swap / RAM Plus is ${swap_used}% full - the phone is compressing memory constantly" \
  "Restart the phone. Optionally try Settings > Device care > Memory > RAM Plus = Off or smaller (some people find it smoother)."

p ""
p "Biggest memory users:"
dumpsys meminfo 2>/dev/null | awk '
  /Total (PSS|RSS) by process:/ { if (done) exit; on = 1; print; next }
  on && /^ *$/ { on = 0; done = 1; next }
  on && n < 15 { print; n++ }'

hdr "APPS KILLED / RESTARTED FOR LACK OF MEMORY"
logcat -d -b main,system > "$TMP/log" 2>/dev/null
logcat -d -b events > "$TMP/events" 2>/dev/null
span() {
  awk '$1 ~ /^[0-9][0-9]-[0-9][0-9]$/ && NF > 2 { if (!f) f = $1 " " substr($2, 1, 5); l = $1 " " substr($2, 1, 5) }
       END { if (f) print f " -> " l; else print "(empty)" }' "$1"
}
p "Main log covers:   $(span "$TMP/log")"
p "Events log covers: $(span "$TMP/events")"
grep -i "lowmemorykiller" "$TMP/log" | grep -o "Kill '[^']*'" | sed "s/^Kill '//; s/'\$//" > "$TMP/lmk"
lmk=$(wc -l < "$TMP/lmk" | tr -d ' ')
p "Low-memory killer kills: $lmk"
if [ "$lmk" -gt 0 ]; then
  p "Most killed:"
  sort "$TMP/lmk" | uniq -c | sort -rn | head -n 10
fi
grep " am_kill" "$TMP/events" | sed 's/.*am_kill *: *\[//; s/\]$//' > "$TMP/amkill"
amk=$(wc -l < "$TMP/amkill" | tr -d ' ')
p ""
p "Processes stopped by Android (am_kill): $amk   - top reasons:"
awk -F, '{r = $5; gsub(/#[0-9]+/, "#", r); print r}' "$TMP/amkill" | sort | uniq -c | sort -rn | head -n 6
if [ "$lmk" -ge 60 ]; then
  flag HIGH "Android killed $lmk apps for lack of memory in the log window - apps reload instead of resuming" \
    "Fewer apps running in the background: uninstall/disable unused apps, restrict battery for chatty ones, restart."
elif [ "$lmk" -ge 20 ]; then
  flag MED "Android killed $lmk apps for lack of memory in the log window" \
    "Uninstall or restrict apps you don't need running in the background."
fi

# ---------------------------------------------------------------- heat & battery
hdr "HEAT & BATTERY"
ts=$(thermal_status)
p "Thermal status: $ts = $(thermal_name "$ts")"
dumpsys thermalservice 2>/dev/null | awk '
  /Current temperatures from HAL/ { on = 1; next }
  on && /^[^ \t]/ && !/Temperature/ { on = 0 }
  on && /Temperature\{/ && tolower($0) ~ /skin|batt|cpu|gpu|ap|pa/ && n < 12 { print "  " $0; n++ }'
if [ "$ts" -ge 2 ]; then
  HOT_FLAGGED=1
  flag HIGH "The phone is HOT and throttling its CPU (thermal status: $(thermal_name "$ts"))" \
    "Let it cool, take off thick cases while charging/gaming, avoid charging + heavy use together, check CPU section for runaway apps."
elif [ "$ts" -eq 1 ]; then
  flag LOW "Phone is warm (light thermal status)" "Not a big deal on its own, but heat causes throttling."
fi

dumpsys battery > "$TMP/battery" 2>/dev/null
grep -E "^ +(level|temperature|health|status|voltage):" "$TMP/battery" | sed 's/^ */  /'
grep -iE "asoc|bsoh|cycle|health" "$TMP/battery" | grep -v "^ *health:" | head -n 6 | sed 's/^ */  /'
btemp=$(int "$(awk -F': *' '/^ +temperature:/ {print $2; exit}' "$TMP/battery")")
if [ "$btemp" -gt 0 ]; then
  p "Battery temperature: $((btemp / 10)).$((btemp % 10)) C"
  [ "$btemp" -ge 410 ] && flag MED "Battery is hot ($((btemp / 10)) C) - Samsung slows the phone down to protect it" \
    "Let it cool down; don't use fast charging while gaming; check for apps using lots of CPU."
fi
health=$(int "$(awk -F': *' '/^ +health:/ {print $2; exit}' "$TMP/battery")")
[ "$health" -ne 0 ] && [ "$health" -ne 2 ] && flag MED "Android reports battery health code $health (2 = good)" \
  "Check Samsung Members app > Diagnostics > Battery status."

if [ "$(setting global low_power)" = "1" ]; then
  flag MED "Power saving mode is ON - it limits CPU speed and refresh rate" \
    "Turn it off: Settings > Battery > Power saving."
fi

# ---------------------------------------------------------------- settings that cause lag
hdr "SETTINGS THAT AFFECT SMOOTHNESS"
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  v=$(setting global $k); p "$k = ${v:-1 (default)}"
  case "$v" in
    ''|0|0.0|0.5|1|1.0) ;;
    *) flag MED "Animation setting $k is $v (slower than normal)" \
         "Developer options > $k: set to 1x (or 0.5x for a snappier feel)." ;;
  esac
done
if [ "$(setting global always_finish_activities)" = "1" ]; then
  flag HIGH "Developer option \"Don't keep activities\" is ON - every app restarts when you switch away" \
    "Developer options > Don't keep activities: turn OFF."
fi
msaa=$(getprop debug.egl.force_msaa)
[ "$msaa" = "true" ] || [ "$msaa" = "1" ] && flag MED "Developer option \"Force 4x MSAA\" is ON (extra GPU work in every app)" \
  "Developer options > Force 4x MSAA: turn OFF."
prof=$(getprop debug.hwui.profile)
case "$prof" in ''|false) ;; *) flag LOW "GPU rendering profiler is on ($prof)" "Developer options > Profile HWUI rendering: Off." ;; esac
case "$(getprop debug.hwui.renderer) $(getprop debug.hwui.overdraw)" in
  *show*) flag LOW "\"Debug GPU overdraw\" is on" "Developer options > Debug GPU overdraw: Off." ;;
esac

p ""
p "Display refresh (Motion smoothness):"
for k in peak_refresh_rate min_refresh_rate; do
  v=$(setting system $k); [ -n "$v" ] && p "  system $k = $v"
done
v=$(setting secure refresh_rate_mode); [ -n "$v" ] && p "  secure refresh_rate_mode = $v"
dumpsys display 2>/dev/null | grep -m 1 -oE "renderFrameRate [0-9.]+|refreshRate=[0-9.]+" | sed 's/^/  current: /'
peak=$(int "$(setting system peak_refresh_rate)")
if [ "$peak" -gt 0 ] && [ "$peak" -le 60 ]; then
  flag LOW "Screen is limited to 60Hz - scrolling looks less smooth" \
    "Settings > Display > Motion smoothness > Adaptive (120Hz)."
fi

p ""
acc=$(setting secure enabled_accessibility_services)
if [ -n "$acc" ]; then
  n_acc=$(printf '%s' "$acc" | tr ':' '\n' | grep -c .)
  p "Accessibility services ON ($n_acc):"
  printf '%s\n' "$acc" | tr ':' '\n' | sed 's/^/  /'
  flag MED "$n_acc accessibility service(s) are running - they inspect every screen and are a classic cause of lag" \
    "Settings > Accessibility > Installed apps: turn off any you don't really need (see list under SETTINGS)."
else
  p "Accessibility services ON: none"
fi
nl=$(setting secure enabled_notification_listeners)
[ -n "$nl" ] && p "Apps reading notifications: $(printf '%s' "$nl" | tr ':' '\n' | grep -c .)"

home=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tail -n 1)
p "Home screen app: ${home:-unknown}"
case "$home" in
  ''|*com.sec.android.app.launcher*|*No\ activity*) ;;
  *) flag LOW "A third-party home screen is in use (${home%%/*})" \
       "If the home screen lags, test with One UI Home: Settings > Apps > Choose default apps > Home app." ;;
esac
p "Keyboard: $(setting secure default_input_method)"

p ""
p "Other power/performance related settings (for reference):"
for ns in global system secure; do
  settings list $ns 2>/dev/null | grep -iE "low_power|power_sav|perf|game|ram_expand|refresh|adaptive_battery" \
    | grep -viE "_pkg|package|history|_list" | head -n 15 | sed "s/^/  $ns: /"
done

# ---------------------------------------------------------------- jank
hdr "STUTTER (dropped frames per app since it started)"
gfxsnap > "$TMP/gfx"
p "  jank%   janky/total   app        (only apps with 500+ frames; under ~10% is normal)"
awk '$2 >= 500 {printf "%7.1f%%  %7d/%-8d %s\n", $3 * 100 / $2, $3, $2, $1}' "$TMP/gfx" | sort -rn | head -n 15
awk '$2 >= 1000 && $3 * 100 / $2 >= 25 {printf "%d %s\n", $3 * 100 / $2, $1}' "$TMP/gfx" | sort -rn | head -n 5 > "$TMP/jank_bad"
while read -r jp jpkg; do
  case "$jpkg" in
    *launcher*|*systemui*)
      flag HIGH "Home screen / system UI ($jpkg) drops $jp% of frames - this is the lag you see on home screen/notifications" \
        "Restart the phone; remove heavy widgets/live wallpaper; check accessibility services and storage." ;;
    *) flag MED "$jpkg drops $jp% of frames (stutters)" \
         "Update it, clear its cache, or check if it's an older app not optimised for your phone." ;;
  esac
done < "$TMP/jank_bad"

# ---------------------------------------------------------------- freezes & crashes
hdr "FREEZES (ANR) & CRASHES - last few days"
dumpsys dropbox > "$TMP/dropbox" 2>/dev/null
grep -oE " (data_app_anr|system_app_anr|data_app_crash|system_app_crash|data_app_native_crash|system_app_native_crash|SYSTEM_TOMBSTONE|system_server_watchdog|system_server_anr|system_server_crash)" "$TMP/dropbox" \
  | sort | uniq -c | sort -rn
p ""
p "Which apps:"
awk '/ (data|system)_app_(anr|crash|native_crash) / { want = $3; next }
     want && /Process:/ { sub(/.*Process: */, ""); print want, $1; want = "" }' "$TMP/dropbox" \
  | sort | uniq -c | sort -rn | head -n 12
anr=$(grep -cE " (data|system)_app_anr " "$TMP/dropbox")
wd=$(grep -cE " system_server_(watchdog|anr|crash) " "$TMP/dropbox")
[ "$wd" -gt 0 ] && flag HIGH "Android's core system froze/restarted $wd time(s) recently (watchdog)" \
  "Install software updates; if it keeps happening, back up and consider a factory reset or Samsung service."
[ "$anr" -ge 3 ] && flag MED "Apps froze (\"isn't responding\") $anr times recently - see the list in FREEZES" \
  "Update or reinstall the apps that appear most; uninstall ones you can live without."

# ---------------------------------------------------------------- background activity
hdr "BACKGROUND ACTIVITY"
p "Apps with the most running services:"
dumpsys activity services 2>/dev/null | grep -oE "ServiceRecord\{[^ ]+ u[0-9]+ [^/} ]+" | awk '{print $3}' \
  | sort | uniq -c | sort -rn | head -n 10
p ""
p "Wake locks held right now (keep the CPU awake):"
dumpsys power 2>/dev/null | grep -E "^ +(PARTIAL_WAKE_LOCK|FULL_WAKE_LOCK|SCREEN_[A-Z]+_WAKE_LOCK)" | head -n 12 | cut -c1-160
n3=$(pm list packages -3 2>/dev/null | grep -c .)
nall=$(pm list packages 2>/dev/null | grep -c .)
p ""
p "Installed apps: $n3 you installed, $nall total"
[ "$n3" -ge 200 ] && flag LOW "$n3 apps installed - many of them run things in the background" \
  "Uninstall ones you haven't used in months (Play Store > Manage apps > sort by Least used)."
}

# ======================================================================
run_watch() {
hdr "LIVE RECORDING ($WATCH_SECS s)"
gfxsnap > "$TMP/gfx_before"
frames=$((WATCH_SECS / 5 + 1))
say ""
say "  >>> RECORDING FOR $WATCH_SECS SECONDS - USE YOUR PHONE NOW <<<"
say "  >>> Do whatever feels laggy: scroll, open apps, swipe home, etc."
say ""
top -b -d 5 -n "$frames" -m 15 > "$TMP/watchtop" 2>/dev/null &
tp=$!
: > "$TMP/samples"
while kill -0 "$tp" 2>/dev/null; do
  printf '%s %s %s\n' "$(date +%H:%M:%S)" "$(mem_avail_pct)" "$(thermal_status)" >> "$TMP/samples"
  sleep 5
done
wait "$tp" 2>/dev/null
say "  ... recording done, analysing"
gfxsnap > "$TMP/gfx_after"

p "Samples (time, available RAM %, thermal status):"
sed 's/^/  /' "$TMP/samples"
minram=$(awk 'NR == 1 || $2 < m {m = $2} END {print m + 0}' "$TMP/samples")
maxth=$(awk '$3 > m {m = $3} END {print m + 0}' "$TMP/samples")
p "Lowest available RAM: ${minram}%    Highest thermal status: $maxth ($(thermal_name "$maxth"))"
[ "$maxth" -ge 2 ] && [ "$HOT_FLAGGED" -eq 0 ] && flag HIGH "Phone hit thermal throttling while you used it (status $maxth)" \
  "Heat is slowing it down: let it cool, remove case while charging, check for apps using lots of CPU."
[ "$minram" -lt 10 ] && flag HIGH "RAM dropped to ${minram}% available while you used it" \
  "Too many/heavy apps in the background - uninstall or restrict ones you don't need."

p ""
p "Stutter during recording (apps that drew 60+ frames):"
p "  jank%   janky/total   app"
awk 'NR == FNR {bt[$1] = $2; bj[$1] = $3; next}
     { dt = $2 - bt[$1]; dj = $3 - bj[$1]; if (dt < 0) {dt = $2; dj = $3}
       if (dt >= 60) printf "%7.1f%%  %7d/%-8d %s\n", dj * 100 / dt, dj, dt, $1 }' \
  "$TMP/gfx_before" "$TMP/gfx_after" | sort -rn | head -n 12 | tee "$TMP/watch_jank"
while read -r jp jcount jpkg; do
  jpi=$(int "$jp"); tot=${jcount#*/}
  [ "$(int "$tot")" -ge 120 ] && [ "$jpi" -ge 20 ] && flag MED "$jpkg dropped ${jp} of frames while you were using it" \
    "If that's the app/screen that lags: update it, clear its cache, remove heavy widgets if it's the home screen."
done < "$TMP/watch_jank"

p ""
p "Average CPU while recording (% of one core; 8 cores = 800%):"
awk '/Tasks:/ {f++; next}
     f >= 2 && $1 ~ /^[0-9]+$/ && NF >= 12 {
       name = $12
       if (name ~ /^(top|sh|sleep|dumpsys|awk|adbd|toybox)$/) next
       s[name] += $9
     }
     END { n = (f > 1) ? f - 1 : 1; for (k in s) if (s[k] / n >= 1) printf "%7.1f%%  %s\n", s[k] / n, k }' \
  "$TMP/watchtop" | sort -rn | head -n 12
}

# ======================================================================
case "$MODE" in
  check) run_check ;;
  watch) run_watch ;;
  full)  run_check; run_watch ;;
esac

# ---------------------------------------------------------------- summary
p ""
p "######################################################################"
p "#  SUMMARY - what is probably making the phone slow"
p "######################################################################"
if [ "$NFLAGS" -eq 0 ]; then
  p ""
  p "No obvious problems found. Nice. If it still feels laggy, run it again"
  p "with --watch while you do the thing that lags."
else
  [ -n "$HIGH" ] && { p ""; p "BIG IMPACT:"; printf '%s' "$HIGH"; }
  [ -n "$MED" ]  && { p ""; p "MEDIUM IMPACT:"; printf '%s' "$MED"; }
  [ -n "$LOW" ]  && { p ""; p "SMALL IMPACT:"; printf '%s' "$LOW"; }
fi
p ""
p "ALSO WORTH CHECKING BY HAND:"
p "  * Settings > Device care > Performance profile: 'Standard' (not 'Light')."
p "  * Remove widgets / live wallpaper you don't need from the home screen."
p "  * Galaxy Store + Play Store: update all apps."
p ""
p "OPTIONAL SAFE COMMANDS (run from your computer, one at a time):"
p "  adb shell cmd package bg-dexopt-job      # re-optimise all apps (takes a few minutes)"
p "  adb shell pm trim-caches 999G            # clear every app's cache (not your data)"
p "  adb shell settings put global window_animation_scale 0.5"
p "  adb shell settings put global transition_animation_scale 0.5"
p "  adb shell settings put global animator_duration_scale 0.5   # snappier animations"
p ""
p "Report took $(( $(date +%s) - START )) seconds. $NFLAGS issue(s) flagged."
say "  done - $NFLAGS issue(s) flagged."
