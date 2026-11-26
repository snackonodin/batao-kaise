#!/usr/bin/env bash
# Renders the fake sample bill to PNG and writes its precomputed OCR line boxes.
# Usage: web-demo/tools/bill/render.sh   (from anywhere; macOS Google Chrome required)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
SRC="file://$HERE/sample_bill.html"
FLAGS=(--headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=1080,1440)

mkdir -p "$ROOT/assets/demo" "$ROOT/web-demo/public"
"$CHROME" "${FLAGS[@]}" --screenshot="$ROOT/assets/demo/sample_bill.png" "$SRC" 2>/dev/null
cp "$ROOT/assets/demo/sample_bill.png" "$ROOT/web-demo/public/sample_bill.png"

"$CHROME" "${FLAGS[@]}" --dump-dom "$SRC?boxes" 2>/dev/null \
  | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{
      const m=s.match(/<pre id="ocr">([\s\S]*?)<\/pre>/); if(!m) throw new Error("no boxes");
      const txt=m[1].replace(/&amp;/g,"&").replace(/&lt;/g,"<").replace(/&gt;/g,">").replace(/&quot;/g,"\"");
      process.stdout.write(JSON.stringify(JSON.parse(txt),null,1)+"\n");})' \
  > "$ROOT/web-demo/public/sample_bill.ocr.json"
echo "wrote assets/demo/sample_bill.png, web-demo/public/sample_bill.png, web-demo/public/sample_bill.ocr.json"
