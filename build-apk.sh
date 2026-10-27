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
KS_ALIAS="studyrank"
PASS_FILE="$APP_DIR/build/.keystore.pass"

# 签名密码不写进仓库：优先读环境变量 PROM_KS_PASS，其次 build/.keystore.pass
# （首次运行自动生成 32 位随机密码并写 600 权限文件，两者都在 .gitignore 里）
if [ -n "${PROM_KS_PASS:-}" ]; then
    KS_PASS="$PROM_KS_PASS"
elif [ -f "$PASS_FILE" ]; then
    KS_PASS="$(cat "$PASS_FILE")"
else
    KS_PASS="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
    mkdir -p "$(dirname "$PASS_FILE")"
    (umask 077; printf '%s' "$KS_PASS" >"$PASS_FILE")
    chmod 600 "$PASS_FILE"
    echo "▸ 已生成随机签名密码 → $PASS_FILE（勿提交，已在 .gitignore）"
fi
export PROM_KS_PASS="$KS_PASS"

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
    --version-code 48 --version-name 12.9

# 4) 编译 Java -> DEX
javac --release 17 -encoding UTF-8 -classpath "$AJ" -d build/classes \
    $(find app/src/main/java -name '*.java')
"$BT/d8" --lib "$AJ" --min-api 26 --output build/dex \
    $(find build/classes -name '*.class')

# 5) 合并 dex、对齐
cp build/apk/base-unsigned.apk build/apk/with-dex.apk
(cd build/dex && zip -q -j "$OUT_DIR/with-dex.apk" classes.dex)
"$BT/zipalign" -f -p 4 build/apk/with-dex.apk build/apk/aligned.apk

# 6) 签名（首次运行时生成 keystore；密码只经环境变量传给 keytool / apksigner）
if [ -f "$KS" ] && ! keytool -list -keystore "$KS" -storepass:env PROM_KS_PASS -alias "$KS_ALIAS" >/dev/null 2>&1; then
    echo "✗ 打不开 $KS：密码不匹配。设置 PROM_KS_PASS 或修正 $PASS_FILE 后重试" >&2
    exit 1
fi

if [ ! -f "$KS" ]; then
    echo "▸ 生成新 keystore（装机升级会失败：已有安装必须是同一把钥匙才能覆盖）"
    keytool -genkeypair -v -keystore "$KS" -storetype PKCS12 -alias "$KS_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -storepass:env PROM_KS_PASS -keypass:env PROM_KS_PASS \
        -dname "CN=StudyRank, OU=Mobile, O=StudyRank, L=Beijing, ST=Beijing, C=CN"
fi

"$BT/apksigner" sign \
    --ks "$KS" --ks-key-alias "$KS_ALIAS" \
    --ks-pass env:PROM_KS_PASS --key-pass env:PROM_KS_PASS \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT_DIR/StudyRankApp-12.9-release.apk" build/apk/aligned.apk

"$BT/apksigner" verify --verbose "$OUT_DIR/StudyRankApp-12.9-release.apk"
"$BT/aapt2" dump badging "$OUT_DIR/StudyRankApp-12.9-release.apk" | head -6
echo "✅ 输出：$OUT_DIR/StudyRankApp-12.9-release.apk"
