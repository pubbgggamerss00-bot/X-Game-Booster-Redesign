#!/usr/bin/env bash
# X Game Booster 4.1.0 (redesign) — dependency-free APK build
set -euo pipefail

ROOT=/home/z/my-project
BT=$ROOT/work/sdk/bt/android-14
PLAT=$ROOT/work/sdk/plat/android-patched.jar
PROJ=$ROOT/XGameBooster
OUT=$PROJ/build
export JAVA_HOME=$(ls -d $ROOT/work/sdk/jdk-17* | head -1)
export PATH=$JAVA_HOME/bin:$PATH

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/apk" "$OUT/comp"

echo "== [1/6] aapt2 compile resources =="
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/res.zip"
python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$OUT/res.zip" "$OUT/comp"
FLATS=$(find "$OUT/comp" -name '*.flat' | sort | tr '\n' ' ')

echo "== [2/6] aapt2 link =="
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLAT" \
    --manifest "$PROJ/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version 23 --target-sdk-version 34 \
    --version-code 22 --version-name "4.1.0 Release" \
    $FLATS

echo "== [3/6] javac =="
find "$PROJ/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options \
    -bootclasspath "$PLAT" -d "$OUT/obj" @"$OUT/sources.txt"

echo "== [4/6] d8 (dex) =="
"$BT/d8" --release --lib "$PLAT" --min-api 23 --output "$OUT/apk" \
    $(find "$OUT/obj" -name '*.class')

echo "== [5/6] package =="
cd "$OUT/apk"
if command -v zip >/dev/null 2>&1; then
    zip -q "$OUT/base.apk" classes.dex
else
    python3 -c "import zipfile; z=zipfile.ZipFile('$OUT/base.apk','a'); z.write('classes.dex','classes.dex',zipfile.ZIP_DEFLATED); z.close()"
fi
cd "$OUT"
"$BT/zipalign" -f 4 base.apk aligned.apk

if [ ! -f "$OUT/xgb.keystore" ]; then
    keytool -genkeypair -keystore "$OUT/xgb.keystore" -alias xgb \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass xgb2026 -keypass xgb2026 \
        -dname "CN=X Game Booster, OU=Redesign, O=XGB, C=IR"
fi

echo "== [6/6] sign =="
"$BT/apksigner" sign --ks "$OUT/xgb.keystore" \
    --ks-pass pass:xgb2026 --key-pass pass:xgb2026 \
    --out "$OUT/X-Game-Booster-4.1.0-redesign.apk" aligned.apk

"$BT/apksigner" verify --print-certs "$OUT/X-Game-Booster-4.1.0-redesign.apk" | head -6
echo ""
echo "BUILD OK: $OUT/X-Game-Booster-4.1.0-redesign.apk"
ls -la "$OUT/X-Game-Booster-4.1.0-redesign.apk"
