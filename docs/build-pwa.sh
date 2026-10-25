#!/bin/sh
# 从安卓 assets 生成 PWA 版 app.html（注入 manifest / 图标 / Service Worker）
set -e
cd "$(dirname "$0")"
SRC="../app/src/main/assets/index.html"
OUT="app.html"

python3 - "$SRC" "$OUT" <<'PY'
import sys, pathlib
src, out = sys.argv[1], sys.argv[2]
html = pathlib.Path(src).read_text(encoding='utf-8')

inject = '''<link rel="manifest" href="manifest.webmanifest">
<link rel="apple-touch-icon" href="icons/icon-180.png">
<link rel="icon" type="image/png" sizes="192x192" href="icons/icon-192.png">
<meta name="theme-color" content="#0B0B0C">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<meta name="mobile-web-app-capable" content="yes">
<script>
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
      navigator.serviceWorker.register('sw.js').catch(function () {});
    });
  }
</script>
'''
html = html.replace('</head>', inject + '</head>', 1)
pathlib.Path(out).write_text(html, encoding='utf-8')
print('已生成', out, len(html), '字符')
PY
