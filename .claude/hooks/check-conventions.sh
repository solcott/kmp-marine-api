#!/bin/bash
# PostToolUse hook (Edit|Write): flags the handful of build and source constructs that CLAUDE.md and
# .claude/rules/ say never to use, each of which fails late, far from the edit, or not at all.
#
# Advisory only, and pure grep so it stays inside its timeout. Comment lines are skipped: several of
# these names appear in KDoc explaining why they are not used.

command -v jq >/dev/null || exit 0
file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -n "$file" ] && [ -f "$file" ] || exit 0

problems=""
note() { problems="$problems  - $1"$'\n'; }

# Code lines only: drop //, /* and KDoc continuation lines.
code() { grep -Ev '^[[:space:]]*(//|/\*|\*)' "$file"; }
has() { code | grep -Eq "$1"; }

case "$file" in
*.gradle.kts)
  has '(id\("java(-library)?"\)|`java(-library)?`|jvm-toolchains)' &&
    note "applies the java/java-library/jvm-toolchains plugin. KGP rejects these as incompatible with KMP."
  has 'withJava\(\)' &&
    note "calls withJava() on the jvm target. KGP rejects it, and there are no Java sources to compile."
  has 'biz\.aQute\.bnd' &&
    note "applies bnd, which applies the java plugin. The OSGi headers are hand-written in marine-api/build.gradle.kts for that reason."
  has 'useJUnitPlatform' &&
    note "useJUnitPlatform(): kotlin-test must move to its JUnit 5 variant in the same change, or ZERO tests run while the build reports success. Check the count."
  has '(kotlin-kapt|kotlin\("kapt"\)|legacy-kapt)' &&
    note "kapt: built-in Kotlin on AGP 9 has no kapt plugin, and Hilt does not work with legacy-kapt. Hilt runs under KSP here."
  has '(kotlin-android|kotlin\("android"\)|org\.jetbrains\.kotlin\.android)' &&
    note "kotlin-android: AGP 9 has built-in Kotlin and rejects this plugin."
  has '(iosX64|macosX64)\(\)' &&
    note "x64 Apple target: this build is arm64-only; the x64 Apple variants are deprecated."
  ;;
*/marine-api/src/commonMain/*.kt | */examples/src/commonMain/*.kt)
  has 'Dispatchers\.IO' &&
    note "Dispatchers.IO in commonMain: it is public API on JVM/Android only (internal on Native, absent on JS/Wasm)."
  has '\.flowOn\(' &&
    note "flowOn in commonMain: the IO layer and the demos deliberately leave the dispatcher to the caller/entry point."
  ;;
esac

case "$file" in
*/marine-api/src/commonMain/*.kt)
  has '^import java\.' &&
    note "java.* import in commonMain: this compiles to JS, Wasm and Native too."
  has 'kotlin\.reflect|::class\.(members|constructors|declaredMemberProperties)' &&
    note "reflection in commonMain: it does not work on Native or JS."
  ;;
esac
case "$file" in
*/nmea/io/*.kt)
  has 'kotlinx\.io\.readLine' &&
    note "kotlinx.io readLine splits on LF only; corpus captures use lone CR. Keep NmeaLineReader."
  ;;
*/examples/src/commonMain/*.kt)
  has 'SystemFileSystem' &&
    note "SystemFileSystem in examples commonMain breaks the browser bundle. Take a Source from the caller."
  ;;
esac

[ -n "$problems" ] || exit 0
msg="Convention check on ${file##*/}:"$'\n'"$problems"
jq -n --arg m "$msg" '{
  systemMessage: $m,
  hookSpecificOutput: {hookEventName: "PostToolUse", additionalContext: $m}
}'
