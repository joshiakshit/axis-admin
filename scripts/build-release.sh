#!/usr/bin/env bash
set -euo pipefail
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
signing_dir="${XDG_DATA_HOME:-$HOME/.local/share}/axis-admin/signing"
keystore="${AXIS_RELEASE_KEYSTORE:-$signing_dir/axis-admin-release.p12}"
password_file="${AXIS_RELEASE_PASSWORD_FILE:-$signing_dir/release.password}"
key_alias="${AXIS_RELEASE_KEY_ALIAS:-axis-admin}"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_dir" ]]; then
    sdk_dir="$(python3 - <<'PY'
from pathlib import Path
for line in Path('local.properties').read_text().splitlines():
    if line.startswith('sdk.dir='):
        print(line.split('=', 1)[1])
        break
PY
)"
fi
build_tools="${AXIS_BUILD_TOOLS_VERSION:-36.0.0}"
apksigner="$sdk_dir/build-tools/$build_tools/apksigner"
zipalign="$sdk_dir/build-tools/$build_tools/zipalign"

[[ -f "$keystore" && -f "$password_file" ]] || { echo "Release signing key and password file are required." >&2; exit 1; }
[[ -x "$apksigner" && -x "$zipalign" ]] || { echo "Android build-tools $build_tools are required." >&2; exit 1; }
./gradlew :app:assembleRelease --max-workers=2
unsigned="app/build/outputs/apk/release/app-release-unsigned.apk"
output="app/build/outputs/apk/release/axis-admin-release.apk"
"$zipalign" -c -P 16 4 "$unsigned"
"$apksigner" sign --ks "$keystore" --ks-key-alias "$key_alias" \
    --ks-pass "file:$password_file" \
    --out "$output" "$unsigned"
"$apksigner" verify --verbose --print-certs "$output"
printf '\nRelease APK: %s/%s\n' "$project_dir" "$output"
