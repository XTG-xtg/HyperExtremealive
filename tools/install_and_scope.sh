#!/usr/bin/env bash
# Historical filename retained; no LSPosed database edits or automatic restarts.
set -euo pipefail
export MSYS_NO_PATHCONV=1
if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "Usage: $0 DEVICE_SERIAL CANDIDATE_APK [--install]"
  echo "Without --install, this prints the plan and makes no device changes."
  exit 2
fi
SERIAL="$1"
APK="$2"
ADB="${ADB:-adb}"
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 2; }
[[ $# -eq 2 || "$3" == --install ]] || { echo "Unknown option" >&2; exit 2; }
echo "Device: $SERIAL"
echo "Candidate: $APK"
echo "Plan: adb -s DEVICE install -r APK (preserve application data)."
echo "No uninstall, data clearing, wallpaper application, LSPosed DB edits, or restart."
if [[ $# -eq 2 ]]; then
  echo "Dry run only. Add --install after reviewing the candidate and device."
  exit 0
fi
"$ADB" -s "$SERIAL" get-state
"$ADB" -s "$SERIAL" install -r "$APK"
echo "Installation returned success. Confirm recommended scopes manually in LSPosed."
echo "Restart affected scopes/device yourself when convenient, then run the acceptance checklist."
