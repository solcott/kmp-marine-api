---
name: add-sentence
description: Add support for a new NMEA 0183 sentence type to this library — interface, parser, SentenceId entry, SentenceFactory registration, and a JUnit 4 test. Use when asked to add, implement, or support a new NMEA sentence (e.g. "add MWV support", "implement the XDR sentence").
---

# Adding a new NMEA sentence

Takes a three-letter sentence id (e.g. `XYZ`) and an example sentence string. If the user did not supply an example NMEA string for the sentence, ask for one — the test and the interface Javadoc both need it.

Every new file needs the LGPL v3 header. Copy it verbatim from a neighbouring file in the same package and change only the filename line and the `Copyright (C) <year> <author>` line. Indent with **tabs**.

## Steps

1. **Read a close analogue first.** Find an existing sentence with a similar field layout (`DPT`, `GGA`, `HDG` are good starting points) and read its interface, parser, and test together. Match their structure rather than inventing one.

2. **Interface** — `src/main/java/net/sf/marineapi/nmea/sentence/XYZSentence.java`, extending `Sentence` (or a more specific interface like `DepthSentence`/`PositionSentence` if one fits). Javadoc every method. The type-level Javadoc documents the field layout and ends with an `Example:` line carrying a real sentence string, following the style of the neighbouring interfaces.

3. **Parser** — `src/main/java/net/sf/marineapi/nmea/parser/XYZParser.java`, extending `SentenceParser` and implementing `XYZSentence`. It needs:
   - `private static final int` field-index constants, zero-based, one per data field.
   - `public XYZParser(String nmea) { super(nmea, SentenceId.XYZ); }`
   - `public XYZParser(TalkerId talker) { super(talker, SentenceId.XYZ, <fieldCount>); }`
   - Accessors implemented with the protected helpers on `SentenceParser` — `getStringValue`, `getDoubleValue`, `getCharValue`, `setStringValue`, `setDoubleValue`, etc. Do not parse the raw sentence yourself.

4. **`SentenceId`** — add `XYZ` to the enum in `src/main/java/net/sf/marineapi/nmea/sentence/SentenceId.java`, **in alphabetical order**, with a one-line Javadoc comment describing the sentence.

5. **Register the parser** — add `registerParser(tempParsers, "XYZ", XYZParser.class);` to `SentenceFactory.reset()` in `src/main/java/net/sf/marineapi/nmea/parser/SentenceFactory.java`, keeping the list alphabetical, and add the import. **This step is the one that gets missed** — without it `SentenceFactory.createParser()` and `SentenceReader` will not produce the new type.

6. **Test** — `src/test/java/net/sf/marineapi/nmea/parser/XYZTest.java`. Note the name: the test is named after the **sentence**, not the parser (`GGATest` tests `GGAParser`). JUnit 4 only — `org.junit.Test`, `org.junit.Before`, static `org.junit.Assert.*`. Declare `public static final String EXAMPLE = "$GPXYZ,...";`, construct the parser from `EXAMPLE` in a `@Before` method, and cover every accessor plus the `TalkerId` constructor.

7. **Verify**: `mvn test -Dtest=XYZTest`, then `mvn package` to confirm nothing else broke (Javadoc errors fail the build).

## Notes

- Do not add JUnit 5, and do not add a formatter — this repo is JUnit 4 and convention-formatted.
- `SentenceFactory`'s class-level Javadoc documents the same recipe from the perspective of an external consumer registering a parser at runtime; keep the two consistent if you change the flow.
