#!/usr/bin/env bash
# Build the patched F-Droid APK and archive it under builds/ with a versioned name.
# Usage: ./build-apk.sh [release|debug]   (default: release)
#
# release: R8 minified + shrunk, signed with the local key. Small and smooth, use this daily.
# debug:   debuggable, no minification, much bigger and noticeably slower. Use for logcat.
#
# Works both when this script sits next to the fdroidclient checkout and when it is
# placed inside the repository root.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -x "$ROOT/fdroidclient/gradlew" ]; then
  REPO="$ROOT/fdroidclient"
else
  REPO="$ROOT"
fi
OUT="$ROOT/builds"
MANIFEST="$OUT/MANIFEST.md"

BUILD_TYPE="${1:-release}"
case "$BUILD_TYPE" in
  release)
    TASK=":app:assembleFullDefaultRelease"
    BUILT_REL="app/build/outputs/apk/fullDefault/release/app-full-default-release.apk"
    ;;
  debug)
    TASK=":app:assembleFullDefaultDebug"
    BUILT_REL="app/build/outputs/apk/fullDefault/debug/app-full-default-debug.apk"
    ;;
  *)
    echo "usage: $0 [release|debug]" >&2
    exit 1
    ;;
esac
LATEST="$ROOT/fdroid-translate-${BUILD_TYPE}.apk"

mkdir -p "$OUT"

# Build numbers never get reused, even if an archive is deleted: the counter lives on disk.
# It is only advanced once a build succeeded, so failed builds do not burn a number.
COUNTER="$OUT/.build-number"
version=$(( $(cat "$COUNTER" 2>/dev/null || echo 0) + 1 ))
stamp=$(date +%Y-%m-%d_%H%M)
human_stamp=$(date +"%Y-%m-%d %H:%M")
apk="$OUT/fdroid-translate-v${version}-${BUILD_TYPE}-${stamp}.apk"

echo "==> building v${version} (${BUILD_TYPE})"
"$REPO/gradlew" -p "$REPO" "$TASK" --console=plain

built="$REPO/$BUILT_REL"
if [ ! -f "$built" ]; then
  echo "build output not found: $built" >&2
  exit 1
fi

cp -f "$built" "$apk"
cp -f "$built" "$LATEST"
echo "$version" > "$COUNTER"

size=$(du -h "$apk" | cut -f1 | tr -d ' ')
sha=$(shasum -a 256 "$apk" | cut -d' ' -f1)
commit=$(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || echo unknown)
branch=$(git -C "$REPO" branch --show-current 2>/dev/null || echo unknown)
state=""
if [ -n "$(git -C "$REPO" status --porcelain 2>/dev/null || true)" ]; then
  state=" + 未提交改动"
fi

if [ ! -f "$MANIFEST" ]; then
  printf '# 构建归档\n\n| 版本 | 类型 | 构建时间 | 大小 | 源码版本 | SHA-256 |\n|---|---|---|---|---|---|\n' > "$MANIFEST"
fi
row=$(printf '| v%s | %s | %s | %s | %s@%s%s | `%s` |' \
  "$version" "$BUILD_TYPE" "$human_stamp" "$size" "$branch" "$commit" "$state" "$sha")
# Insert right after the last row of the first table (the archive table), so the notes
# and any summary tables further down stay untouched.
tmp_manifest=$(mktemp)
awk -v row="$row" '
  /^## / { stopped = 1 }
  !stopped && /^\| v[0-9]+ \|/ { last = NR }
  { lines[NR] = $0 }
  END {
    if (last == 0) {
      for (i = 1; i <= NR; i++) print lines[i]
      print row
      exit
    }
    for (i = 1; i <= NR; i++) { print lines[i]; if (i == last) print row }
  }
' "$MANIFEST" > "$tmp_manifest" && mv "$tmp_manifest" "$MANIFEST"

echo
echo "APK:    $apk"
echo "最新:   $LATEST"
echo "大小:   $size"
echo "SHA256: $sha"
echo "源码:   $branch@$commit$state"
