#!/bin/bash
# Gradle-free build: aapt2 -> javac -> d8 -> zipalign -> apksigner
set -e
cd "$(dirname "$0")"
SDK="$LOCALAPPDATA/Android/Sdk"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-33/android.jar"
rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -I "$JAR" --manifest AndroidManifest.xml build/res.zip \
  --java build/gen -o build/unsigned.apk
javac -source 11 -target 11 -encoding UTF-8 -Xlint:-options -classpath "$JAR" -d build/classes \
  $(find src build/gen -name '*.java')
"$BT/d8.bat" --release --min-api 33 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
python -c "import zipfile; z=zipfile.ZipFile('build/unsigned.apk','a'); z.write('build/dex/classes.dex','classes.dex'); z.close()"
"$BT/zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner.bat" sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out build/NoHoldTune.apk build/aligned.apk
echo "built build/NoHoldTune.apk"
