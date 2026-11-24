#!/usr/bin/env bash
# Prueba de humo en emulador/dispositivo: instala, enciende el overlay,
# verifica que el ganso se anima (dentro y fuera de la app) y cuenta excepciones.
# Uso: tools/smoke.sh [directorio_de_salida]
set -u
export MSYS_NO_PATHCONV=1
ADB="${ADB:-/c/Android/sdk/platform-tools/adb.exe}"
PKG=com.skyvanguard.goozy
APK=app/build/outputs/apk/debug/app-debug.apk
OUT="${1:-build/smoke}"
mkdir -p "$OUT"

"$ADB" install -r "$APK" >/dev/null || { echo "FALLO: install"; exit 1; }
"$ADB" shell appops set $PKG SYSTEM_ALERT_WINDOW allow
"$ADB" shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
"$ADB" shell am force-stop $PKG
"$ADB" logcat -c
"$ADB" shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 3

# Localizar el switch principal y encenderlo si está apagado
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
NODE=$("$ADB" shell cat /sdcard/ui.xml | grep -o '<node[^>]*id/overlaySwitch"[^>]*>')
CHECKED=$(echo "$NODE" | grep -o 'checked="[a-z]*"' | cut -d'"' -f2)
read X1 Y1 X2 Y2 <<<"$(echo "$NODE" | grep -o 'bounds="[^"]*"' | tr -c '0-9' ' ')"
[ -z "${X2:-}" ] && { echo "FALLO: no se encontró el switch principal"; exit 1; }
if [ "$CHECKED" != "true" ]; then
  "$ADB" shell input tap $(( (X1+X2)/2 )) $(( (Y1+Y2)/2 ))
fi
sleep 4
"$ADB" exec-out screencap -p > "$OUT/app.png"

"$ADB" shell input keyevent KEYCODE_HOME
sleep 3
"$ADB" exec-out screencap -p > "$OUT/home1.png"
sleep 3
"$ADB" exec-out screencap -p > "$OUT/home2.png"

FAIL=0
if cmp -s "$OUT/home1.png" "$OUT/home2.png"; then
  echo "FALLO: el overlay no se anima fuera de la app (frames idénticos)"; FAIL=1
else
  echo "OK: el overlay se anima fuera de la app"
fi

"$ADB" logcat -d > "$OUT/logcat.txt"
EXC=$(grep -c -E "Error during (render|tick)|FATAL EXCEPTION" "$OUT/logcat.txt")
if [ "$EXC" -gt 0 ]; then
  echo "FALLO: $EXC excepciones en el loop"; FAIL=1
  grep -E "E GooseView: [a-z].*Exception|FATAL EXCEPTION" "$OUT/logcat.txt" | sed -E 's/^.*(GooseView|AndroidRuntime): //' | sort | uniq -c | sort -rn | head -5
else
  echo "OK: sin excepciones en el loop"
fi
exit $FAIL
