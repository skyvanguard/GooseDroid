#!/usr/bin/env bash
# Fija la etapa de evolución en un build de debug (0 huevo … 6 cósmico) y reinicia el overlay.
# Uso: tools/setstage.sh 3
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="${ADB:-/c/Android/sdk/platform-tools/adb.exe}"
PKG=com.skyvanguard.goozy
STAGE="${1:?etapa 0-6}"

"$ADB" shell am force-stop "$PKG"
"$ADB" shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/GooseEvolutionPrefs.xml'" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="stage" value="$STAGE" />
    <int name="currentLevel" value="$((STAGE * 5 + 1))" />
</map>
EOF
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
echo "etapa $STAGE"
