#!/usr/bin/env bash
# 离线打包脚本：不依赖 Gradle / 网络，直接用 Android SDK 命令行工具构建 APK
# 用法：./build-apk.sh
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
AJ="$SDK/platforms/android-34/android.jar"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$APP_DIR/build/apk"
KS="$APP_DIR/build/studyrank-release.jks"
KS_PASS="StudyRank2026"
KS_ALIAS="studyrank"

[ -x "$BT/aapt2" ] || { echo "缺少 aapt2：$BT"; exit 1; }
[ -f "$AJ" ]       || { echo "缺少 android.jar：$AJ"; exit 1; }

cd "$APP_DIR"
rm -rf build/res build/gen build/classes build/dex build/verify
mkdir -p build/res build/gen build/classes build/dex build/apk

# 1) 编译资源
"$BT/aapt2" compile --dir app/src/main/res -o build/res/resources.zip

# 2) AGP 会从 namespace 注入 manifest 的 package 属性；命令行需显式提供
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.studyrank.app">|' \
    app/src/main/AndroidManifest.xml > build/AndroidManifest.xml

# 3) 链接资源 + assets -> 未签名 APK
"$BT/aapt2" link --auto-add-overlay \
    -o build/apk/base-unsigned.apk \
    -I "$AJ" \
    --manifest build/AndroidManifest.xml \
    -R build/res/resources.zip \
    -A app/src/main/assets \
    --java build/gen \
    --min-sdk-version 26 --target-sdk-version 34 \
    --version-code 47 --version-name 12.8

# 4) 编译 Java -> DEX
javac --release 17 -encoding UTF-8 -classpath "$AJ" -d build/classes \
    $(find app/src/main/java -name '*.java')
"$BT/d8" --lib "$AJ" --min-api 26 --output build/dex \
    $(find build/classes -name '*.class')

# 5) 合并 dex、对齐
cp build/apk/base-unsigned.apk build/apk/with-dex.apk
(cd build/dex && zip -q -j "$OUT_DIR/with-dex.apk" classes.dex)
"$BT/zipalign" -f -p 4 build/apk/with-dex.apk build/apk/aligned.apk

# 6) 签名（首次运行时生成 keystore）
if [ ! -f "$KS" ]; then
    keytool -genkeypair -v -keystore "$KS" -storetype PKCS12 -alias "$KS_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -dname "CN=StudyRank, OU=Mobile, O=StudyRank, L=Beijing, ST=Beijing, C=CN"
fi

"$BT/apksigner" sign \
    --ks "$KS" --ks-key-alias "$KS_ALIAS" \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT_DIR/StudyRankApp-12.8-release.apk" build/apk/aligned.apk

"$BT/apksigner" verify --verbose "$OUT_DIR/StudyRankApp-12.8-release.apk"
"$BT/aapt2" dump badging "$OUT_DIR/StudyRankApp-12.8-release.apk" | head -6
echo "✅ 输出：$OUT_DIR/StudyRankApp-12.8-release.apk"
