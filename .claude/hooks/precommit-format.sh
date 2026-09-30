#!/bin/bash
# PreToolUse hook (Bash): runs ktfmtCheck and checkSortDependencies before a `git commit`, because CI
# runs both first and fails on either.
#
# Only blocks when one of THOSE tasks failed. Any other failure (no Android SDK, a broken build) is
# not a formatting problem and is not this hook's to report, so the commit proceeds. Skipped entirely
# when no Kotlin, build script or version catalog file has changed.

command -v jq >/dev/null || exit 0
cmd=$(jq -r '.tool_input.command // empty')
printf '%s' "$cmd" | grep -Eq '(^|[;&|[:space:]])git([[:space:]]+(-C[[:space:]]+[^[:space:]]+|-[^[:space:]]+))*[[:space:]]+commit([[:space:]]|$)' || exit 0

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
[ -n "$root" ] && [ -x "$root/gradlew" ] || exit 0
cd "$root" || exit 0

git status --porcelain -- '*.kt' '*.kts' 'gradle/libs.versions.toml' | grep -q . || exit 0

out=$(./gradlew ktfmtCheck checkSortDependencies --quiet --console=plain --continue 2>&1)
[ $? -eq 0 ] && exit 0

failed=$(printf '%s\n' "$out" | grep -Eo "Execution failed for task '[^']*(ktfmt|[sS]ortDependencies)[^']*'" | sort -u)
[ -n "$failed" ] || exit 0

files=$(printf '%s\n' "$out" | grep -Eo "$root/[^[:space:]':]+\.kts?" | sed "s|^$root/||" | sort -u | head -10)
jq -n --arg failed "$failed" --arg files "$files" '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "deny",
    permissionDecisionReason: ("CI would fail this commit:\n" + $failed + "\n" +
      (if $files == "" then "" else "Files:\n" + $files + "\n" end) +
      "Run ./gradlew ktfmtFormat sortDependencies, re-stage, and commit again.")
  }
}'
