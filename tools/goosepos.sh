#!/usr/bin/env bash
# Imprime el centro (x y) de la ventana chica del ganso, en píxeles de pantalla.
export MSYS_NO_PATHCONV=1
ADB="${ADB:-/c/Android/sdk/platform-tools/adb.exe}"
"$ADB" shell dumpsys window windows | tr -d '\r' | awk '
  /Window #/ { own = ($0 ~ /com\.skyvanguard\.goozy/ && $0 !~ /MainActivity/) }
  own && match($0, /[ (]frame=\[[0-9-]+,[0-9-]+\]\[[0-9-]+,[0-9-]+\]/) {
    s = substr($0, RSTART, RLENGTH); gsub(/[^0-9-]+/, " ", s); split(s, a, " ")
    w = a[3]-a[1]; h = a[4]-a[2]
    if (w < 1000) { print int((a[1]+a[3])/2), int((a[2]+a[4])/2), w, h }
  }' | head -1
