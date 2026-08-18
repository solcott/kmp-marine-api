#!/bin/bash
# PostToolUse hook: warns when a sentence or AIS message type has been written but not fully
# registered. CLAUDE.md names these as "the steps that get missed", and each of them fails a test
# rather than a compile, so the feedback would otherwise arrive a whole build later.
#
# Advisory only. FieldExposureTest.theExamplesCoverEveryRegisteredType and GpsdAisCheckTest remain
# the actual gates; this just shortens the loop. It must stay pure grep/awk -- a hook cannot run
# Gradle inside its 10s timeout.
#
# Reads the PostToolUse payload on stdin, emits {"systemMessage": ...} or nothing at all.

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -n "$file" ] || exit 0
[ -f "$file" ] || exit 0

# Everything is addressed relative to the module root, derived from the edited path so the hook
# needs no environment.
root="${file%%/marine-api/*}"
[ "$root" != "$file" ] || exit 0
src="$root/marine-api/src"

registry="$src/commonMain/kotlin/io/github/solcott/marineapi/nmea/SentenceRegistry.kt"
exposure="$src/commonTest/kotlin/io/github/solcott/marineapi/nmea/sentence/FieldExposureTest.kt"
ais_registry="$src/commonMain/kotlin/io/github/solcott/marineapi/ais/AisRegistry.kt"
ais_check="$src/jvmTest/kotlin/io/github/solcott/marineapi/ais/GpsdAisCheckTest.kt"

problems=""
note() { problems="$problems  $1"$'\n'; }

# The declaring class name, per declaration. Never derived from the id: the proprietary sentences
# break that (class Stalk declares ID "ALK", Pashr declares "ASHR", Pgrme declares "GRME").
declarations() {
  awk -v want="$1" '
    /^public data class |^public class |^internal data class / {
      cls = $0
      sub(/^(public|internal) (data )?class /, "", cls)
      sub(/[ (:<{].*/, "", cls)
    }
    want == "id" && /const val ID: String = "/ {
      if (match($0, /"[A-Z0-9]+"/)) print cls, substr($0, RSTART + 1, RLENGTH - 2)
    }
    want == "type" && /const val TYPE: Int =/ { print cls, $NF }
    want == "type" && /val TYPES: List<Int> =/ {
      line = $0; gsub(/[^0-9 ]/, " ", line); print cls, line
    }
  ' "$2"
}

case "$file" in
*/nmea/sentence/*.kt)
  while read -r class id; do
    [ -n "$id" ] || continue
    [ -f "$registry" ] && grep -q "$class\.ID to SentenceFactory" "$registry" ||
      note "$id: not registered in SentenceRegistry.Default -- add \`$class.ID to SentenceFactory($class::from)\` (alphabetically, with the import). Until then it parses as UnknownSentence."
    [ -f "$exposure" ] && grep -q "$id," "$exposure" ||
      note "$id: no example in FieldExposureTest.fullyPopulated -- add a FULLY populated line (every field non-empty) with a comment naming each field in order."
  done <<<"$(declarations id "$file")"
  ;;
*/ais/*.kt)
  # A class whose companion declares TYPE or TYPES is a decodable message type and must be in
  # AisRegistry.Default. Classes without one (ShipDimensions, EstimatedArrival) are not.
  while read -r class types; do
    [ -n "$class" ] || continue
    [ -f "$ais_registry" ] && grep -q "$class\.TYPE" "$ais_registry" ||
      note "$class: not registered in AisRegistry.Default -- payloads of this type decode to AisResult.Unsupported."
    if [ -f "$ais_check" ]; then
      # Per class, not per type: types 9 and 11 have no FIELDS_PER_TYPE entry because the corpus
      # carries none of them, and that is not a defect to nag about.
      #
      # AisStaticDataReport is exempt: gpsd emits no record at all for a type 24 part A, so there
      # is nothing for compare() to check it against. See aisRecordsOf's KDoc.
      [ "$class" = "AisStaticDataReport" ] || grep -q "is $class ->" "$ais_check" ||
        note "$class: no branch in GpsdAisCheckTest.compare(), so gpsd's own decode of it is never checked against. Add one (plus a FIELDS_PER_TYPE entry) -- this is the strongest evidence available."
      for n in $types; do
        grep -n 'mapOf([0-9]' "$ais_check" | grep -q "[(, ]$n to " &&
          note "type $n: still listed in GpsdAisCheckTest's pinned \`unsupported\` map -- remove it, or the build fails as though this were a regression."
      done
    fi
  done <<<"$(declarations type "$file")"
  ;;
esac

if [ -n "$problems" ]; then
  jq -Rn --arg body "$problems" \
    '{systemMessage: ("Registration steps still outstanding:\n" + $body)}'
fi
exit 0
