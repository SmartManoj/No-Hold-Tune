#!/bin/bash
# Gradle-free Play bundle: run build.sh first (it produces build/dex), then
# aapt2 --proto-format -> base module zip -> bundletool build-bundle -> jarsigner.
# Sign with your Play upload key: KS=path/to/upload.jks KS_ALIAS=upload KS_PASS=... bash bundle.sh
# (defaults to the debug key, which Play rejects; fine for local testing with bundletool).
set -e
cd "$(dirname "$0")"
SDK="$LOCALAPPDATA/Android/Sdk"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-36/android.jar"
BUNDLETOOL="${BUNDLETOOL:-$HOME/.android/tools/bundletool-all-1.18.3.jar}"
KS="${KS:-$HOME/.android/debug.keystore}"
KS_ALIAS="${KS_ALIAS:-androiddebugkey}"
KS_PASS="${KS_PASS:-android}"

[ -f build/dex/classes.dex ] || { echo "run build.sh first"; exit 1; }
rm -rf build/aab && mkdir -p build/aab/base/manifest build/aab/base/dex

"$BT/aapt2.exe" link --proto-format -I "$JAR" --manifest AndroidManifest.xml build/res.zip -o build/aab/proto.apk
python -I - <<'EOF'
import zipfile
src = zipfile.ZipFile('build/aab/proto.apk')
out = zipfile.ZipFile('build/aab/base.zip', 'w', zipfile.ZIP_DEFLATED)
for n in src.namelist():  # bundle layout: manifest/, res/, resources.pb at module root
    dest = 'manifest/AndroidManifest.xml' if n == 'AndroidManifest.xml' else n
    out.writestr(dest, src.read(n))
out.write('build/dex/classes.dex', 'dex/classes.dex')
out.close()
EOF
java -jar "$BUNDLETOOL" build-bundle --modules=build/aab/base.zip --output=build/NoHoldTune.aab --overwrite
jarsigner -keystore "$KS" -storepass "$KS_PASS" build/NoHoldTune.aab "$KS_ALIAS" >/dev/null
echo "built build/NoHoldTune.aab (signed with $(basename "$KS"))"
