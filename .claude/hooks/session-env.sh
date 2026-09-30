#!/bin/bash
# SessionStart hook: surfaces the two environment facts that make Gradle fail for reasons unrelated
# to the code. Silent when the environment is fine.
#
# - The com.android.kotlin.multiplatform.library plugin fails CONFIGURATION without an Android SDK,
#   so even `:marine-api:jvmTest` needs one.
# - The Apple targets only link on macOS; `build` and `allTests` fail there on any other host.

root="${CLAUDE_PROJECT_DIR:-.}"
out=""

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ] &&
  ! grep -qs '^sdk\.dir=' "$root/local.properties"; then
  out="$out- No Android SDK found (ANDROID_HOME unset, no sdk.dir in local.properties). Every Gradle task, JVM-only ones included, will fail at configuration until one is set."$'\n'
fi

if [ "$(uname -s)" != "Darwin" ]; then
  out="$out- Not macOS: the Apple targets cannot link here, so \`build\` and \`allTests\` fail for that reason alone. Use :marine-api:jvmTest and :marine-api:linuxX64Test."$'\n'
fi

[ -n "$out" ] && printf 'Environment notes for this repository:\n%s' "$out"
exit 0
