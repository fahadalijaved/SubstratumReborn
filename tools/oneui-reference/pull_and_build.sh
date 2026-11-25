#!/usr/bin/env bash
#
# Copyright (c) 2026 SubstratumReborn
# SPDX-License-Identifier: GPL-3.0-Or-Later
#
# Pull the APKs of the packages a theme can target from a device over adb and
# build the reference with build_reference.py.
#
# Usage:
#   pull_and_build.sh [-s SERIAL] OUT_DIR [--from-theme THEME_PACKAGE] [PACKAGE ...]
#
# --from-theme takes the target list from an installed Substratum theme
# (its assets/overlays/<package> folders); only the folder names are read.
# Without packages, the framework, SystemUI and Settings are used.
set -euo pipefail

ADB=(adb)
if [[ "${1:-}" == "-s" ]]; then ADB=(adb -s "$2"); shift 2; fi
OUT="${1:?OUT_DIR required}"; shift
HERE="$(cd "$(dirname "$0")" && pwd)"
APKS="$OUT/apks"
mkdir -p "$APKS"

packages=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --from-theme)
      theme_apk="$APKS/theme-$2.apk"
      "${ADB[@]}" pull "$("${ADB[@]}" shell pm path "$2" | head -1 | cut -d: -f2 | tr -d '\r')" \
        "$theme_apk" >/dev/null
      while read -r p; do packages+=("$p"); done < <(
        unzip -Z1 "$theme_apk" | sed -n 's|^assets/overlays/\([^/]*\)/.*|\1|p' | sort -u)
      rm -f "$theme_apk"
      shift 2 ;;
    *) packages+=("$1"); shift ;;
  esac
done
[[ ${#packages[@]} -eq 0 ]] && packages=(android com.android.systemui com.android.settings)

pulled=()
for pkg in "${packages[@]}"; do
  if [[ "$pkg" == "android" ]]; then
    "${ADB[@]}" pull /system/framework/framework-res.apk "$APKS/android.apk" >/dev/null
    pulled+=("$APKS/android.apk"); echo "pulled android"; continue
  fi
  paths=$("${ADB[@]}" shell pm path "$pkg" 2>/dev/null | cut -d: -f2 | tr -d '\r' || true)
  if [[ -z "$paths" ]]; then echo "not installed: $pkg" >&2; continue; fi
  i=0
  for path in $paths; do
    dest="$APKS/$pkg$([[ $i -gt 0 ]] && echo ".split$i").apk"
    "${ADB[@]}" pull "$path" "$dest" >/dev/null && pulled+=("$dest")
    i=$((i + 1))
  done
  echo "pulled $pkg ($i apk)"
done

python3 "$HERE/build_reference.py" "$OUT/reference" "${pulled[@]}"
