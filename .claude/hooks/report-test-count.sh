#!/bin/bash
# PostToolUse hook (Bash): after a Gradle command that runs tests, reports how many tests the result
# XML actually records.
#
# This build has a documented failure where a kotlin-test/JUnit runner mismatch makes ZERO tests run
# while the build reports success, so the exit code proves nothing. The count does. Expected figures
# live in CLAUDE.md ("The regression signal") and deliberately are not repeated here.

command -v jq >/dev/null || exit 0
cmd=$(jq -r '.tool_input.command // empty')
case "$cmd" in *gradlew*) ;; *) exit 0 ;; esac

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
[ -n "$root" ] || exit 0

# Task names from every gradlew segment, with any project prefix stripped.
tasks=$(printf '%s\n' "$cmd" | tr ';&|' '\n\n\n' | awk '
  {
    at = 0
    for (i = 1; i <= NF; i++) if ($i ~ /(^|\/)gradlew$/) { at = i; break }
    if (!at) next
    for (i = at + 1; i <= NF; i++) {
      if ($i ~ /^#/ || $i ~ /<</) break
      if ($i == "-x" || $i == "--tests" || $i ~ /^-P/ && $i !~ /=/) { i++; continue }
      if ($i ~ /^-/) continue
      t = $i; sub(/.*:/, "", t); print t
    }
  }')
[ -n "$tasks" ] || exit 0

dirs=""
for t in $tasks; do
  case "$t" in
  allTests | build | check) dirs="$dirs $(ls -d "$root"/marine-api/build/test-results/*/ 2>/dev/null)" ;;
  test) dirs="$dirs $(ls -d "$root"/examples-android/build/test-results/test*UnitTest/ 2>/dev/null)" ;;
  *Test) dirs="$dirs $(ls -d "$root"/{marine-api,examples,examples-android}/build/test-results/"$t"/ 2>/dev/null)" ;;
  esac
done
[ -n "${dirs// /}" ] || exit 0

mtime() { stat -c %Y "$1" 2>/dev/null || stat -f %m "$1" 2>/dev/null; }
now=$(date +%s)
report=""
zero=""
for d in $(printf '%s\n' $dirs | sort -u); do
  set -- "$d"*.xml
  [ -f "$1" ] || { report="$report  ${d#$root/}: no result XML"$'\n'; zero=1; continue; }
  counts=$(cat "$d"*.xml | grep -o '<testsuite [^>]*>' |
    awk '{ for (i = 1; i <= NF; i++) if (match($i, /^(tests|skipped|failures|errors)="[0-9]+"/)) {
             split($i, kv, "\""); k = substr($i, 1, index($i, "=") - 1); s[k] += kv[2] } }
         END { printf "%d %d %d", s["tests"], s["skipped"], s["failures"] + s["errors"] }')
  set -- $counts
  newest=$(ls -t "$d"*.xml | head -1)
  age=$((now - $(mtime "$newest")))
  name=${d#"$root"/}; name=${name%/}
  line="$name: $1 tests, $2 skipped, $3 failed"
  [ "$age" -gt 600 ] && line="$line (results are ${age}s old -- task was UP-TO-DATE or did not run; use --rerun-tasks for a fresh count)"
  report="$report  $line"$'\n'
  [ "$1" -eq 0 ] && zero=1
done

msg="Test counts from result XML:"$'\n'"$report"
[ -n "$zero" ] && msg="$msg""ZERO TESTS recorded for at least one task. A green build here proves nothing -- check the kotlin-test/JUnit wiring (see .claude/rules/build-scripts.md)."$'\n'
msg="$msg""Compare against CLAUDE.md's 'The regression signal'; a --tests filter legitimately lowers the count."

jq -n --arg m "$msg" '{hookSpecificOutput: {hookEventName: "PostToolUse", additionalContext: $m}}'
