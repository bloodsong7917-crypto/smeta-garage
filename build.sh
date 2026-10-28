#!/bin/sh
# Сборка APK без Gradle: aapt2 → javac → d8 → подпись
set -e
: "${SMETA_KS_PASS:?Задайте пароль ключа подписи: export SMETA_KS_PASS=...}"
B=/c/Users/Blood/android-build
P=$B/smeta
BT=$B/sdk/build-tools/34.0.0
JAR=$B/sdk/platforms/android-34/android.jar
export JAVA_HOME="$(cygpath -w $B/jdk/jdk-17.0.20.1+1)"
export PATH="$B/jdk/jdk-17.0.20.1+1/bin:$PATH"
cd $P
rm -rf out && mkdir -p out/gen out/classes out/dex

$BT/aapt2.exe compile --dir res -o out/res.zip
$BT/aapt2.exe link -I $JAR --manifest AndroidManifest.xml --java out/gen -o out/base.apk out/res.zip \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name 1.0

javac -nowarn -source 8 -target 8 -bootclasspath $JAR -d out/classes $(find src out/gen -name '*.java')
cmd //c "$(cygpath -w $BT/d8.bat)" --lib "$(cygpath -w $JAR)" --min-api 26 --output "$(cygpath -w out/dex)" $(find out/classes -name '*.class' | xargs -n1 cygpath -w)

cp out/dex/classes.dex out/classes.dex
(cd out && $BT/aapt.exe add base.apk classes.dex >/dev/null)
$BT/zipalign.exe -f -p 4 out/base.apk out/aligned.apk

[ -f $B/smeta.jks ] || keytool -genkeypair -keystore $B/smeta.jks -alias smeta -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass "$SMETA_KS_PASS" -keypass "$SMETA_KS_PASS" -dname "CN=Garage"
cmd //c "$(cygpath -w $BT/apksigner.bat)" sign --ks "$(cygpath -w $B/smeta.jks)" --ks-pass env:SMETA_KS_PASS \
  --out "$(cygpath -w out/smeta.apk)" "$(cygpath -w out/aligned.apk)"
cmd //c "$(cygpath -w $BT/apksigner.bat)" verify "$(cygpath -w out/smeta.apk)" && echo SIGNED_OK
