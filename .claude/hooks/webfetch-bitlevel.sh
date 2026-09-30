#!/bin/bash
# PostToolUse hook (WebFetch): a WebFetch result is a small model's summary of the page, not the page.
# Its rendering of gpsd's AIVDM bit tables for AIS types 21, 24 and 27 was internally inconsistent
# and contradicted payloads that decode correctly. When a fetch touches gpsd, AIS or NMEA layouts,
# remind that bit ranges and field layouts come from the corpus .chk files or raw source.

command -v jq >/dev/null || exit 0
subject=$(jq -r '[.tool_input.url, .tool_input.prompt] | map(. // "") | join(" ")')
printf '%s' "$subject" | grep -Eiq 'gpsd|aivdm|\bais\b|nmea|sixbit|bit (range|table|layout)' || exit 0

jq -n '{hookSpecificOutput: {hookEventName: "PostToolUse", additionalContext:
  "That WebFetch result is a model-written summary. Do not take AIS bit ranges or NMEA field layouts from it: derive them from the gpsd .log.chk records under marine-api/src/jvmTest/resources/data/gpsd/ (raw, \"scaled\":false) or from raw source, and say in the KDoc when neither covers a type."}}'
