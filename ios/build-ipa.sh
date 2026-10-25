#!/bin/sh
# 构建未签名 IPA（Payload/Prom.app 打包）
# 需要 Xcode + iOS SDK；不需要开发者账号，也不需要证书。
set -e
cd "$(dirname "$0")"

APP_NAME="Prom"
VERSION=$(/usr/libexec/PlistBuddy -c "Print :MARKETING_VERSION" Prom.xcodeproj/project.pbxproj 2>/dev/null | head -1 || echo "12.8")
OUT="Prom-${VERSION:-12.8}-unsigned.ipa"

echo "▸ 编译（arm64 / Release / 不签名）"
rm -rf build ipa
xcodebuild -project Prom.xcodeproj -target "$APP_NAME" \
  -sdk iphoneos -configuration Release \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" \
  SYMROOT="$PWD/build/out" OBJROOT="$PWD/build/obj" build \
  | tail -3

APP="build/out/Release-iphoneos/$APP_NAME.app"
[ -d "$APP" ] || { echo "✗ 没有产物：$APP"; exit 1; }

echo "▸ 打包 Payload"
mkdir -p ipa/Payload
cp -R "$APP" ipa/Payload/
( cd ipa && zip -qry "../$OUT" Payload )

echo "✓ $OUT  $(du -h "$OUT" | cut -f1)"
echo "  未签名 —— 装到 iPhone 前需要用 Sideloadly / AltStore / ESign 自签。"
