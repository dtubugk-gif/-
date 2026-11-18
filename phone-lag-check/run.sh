#!/usr/bin/env bash
# Phone lag check - Mac / Linux launcher. Needs adb (Android platform-tools).
# Usage: ./run.sh [check | watch SECONDS | full SECONDS]
set -e
cd "$(dirname "$0")"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Mac: 'brew install android-platform-tools'   Linux: 'sudo apt install adb'"
  exit 1
fi

mode="${1:-}"
secs="${2:-60}"
if [ -z "$mode" ]; then
  echo
  echo "  1) Quick check        (about 1 minute, just leave the phone)"
  echo "  2) Watch while I use  (60 s recording - use the phone during it)"
  echo "  3) Both"
  echo
  read -r -p "Choose 1, 2 or 3 [1]: " choice
  case "$choice" in 2) mode=watch ;; 3) mode=full ;; *) mode=check ;; esac
fi

adb devices
if ! adb get-state >/dev/null 2>&1; then
  echo "No phone found (or more than one). Check the cable / wireless debugging and accept the"
  echo "'Allow USB debugging?' prompt on the phone. With several devices: export ANDROID_SERIAL=<id>"
  exit 1
fi

out="report-$mode-$(date +%Y%m%d-%H%M%S).txt"
adb push lagcheck.sh /data/local/tmp/lagcheck.sh >/dev/null
echo "Running... progress shows below, the report is saved to $out"
adb shell sh /data/local/tmp/lagcheck.sh "--$mode" "$secs" > "$out"
adb shell rm -f /data/local/tmp/lagcheck.sh
echo
echo "Report saved: $PWD/$out"
echo "----- summary -----"
sed -n '/#  SUMMARY/,$p' "$out"
