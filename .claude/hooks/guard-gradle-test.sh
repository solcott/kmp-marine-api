#!/bin/bash
# PreToolUse hook (Bash): refuses a bare `./gradlew test`.
#
# The library has no `test` task, but the command does not fail: it resolves to
# `:examples-android:test`, the one non-KMP module, which runs the app's ViewModel tests and none of
# the library's. The build reports success having run nothing that matters -- the same
# green-and-meaningless shape as the useJUnit() trap -- so it is cheaper to stop it before it runs.
#
# `-x test`, `--tests ...` and project-qualified tasks (`:examples-android:test`) are left alone.

command -v jq >/dev/null || exit 0
cmd=$(jq -r '.tool_input.command // empty')
case "$cmd" in *gradlew*) ;; *) exit 0 ;; esac

# One segment per shell command; within a gradlew segment, look at each task token.
bare=$(printf '%s\n' "$cmd" | tr ';&|' '\n\n\n' | awk '
  {
    at = 0
    for (i = 1; i <= NF; i++) if ($i ~ /(^|\/)gradlew$/) { at = i; break }
    if (!at) next
    for (i = at + 1; i <= NF; i++) {
      if ($i ~ /^#/ || $i ~ /<</) break
      if ($i == "-x" || $i == "--exclude-task" || $i == "--tests") { i++; continue }
      if ($i == "test" || $i == ":test") { print "yes"; exit }
    }
  }')
[ "$bare" = "yes" ] || exit 0

jq -n '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "deny",
    permissionDecisionReason: "There is no library `test` task. A bare `test` resolves to :examples-android:test and runs none of :marine-api'"'"'s tests while reporting success. Use :marine-api:jvmTest (JVM), :marine-api:allTests (every target) or :examples-android:testDebugUnitTest (the app), and check the reported test count."
  }
}'
