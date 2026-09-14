package adapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Translates the TCK formatter config into the config our formatter expects.
 *
 * The TCK hands us a flat object keyed by ITS rule names:
 *   { "enforce-spacing-before-colon-in-declaration": true, "line-breaks-after-println": 2 }
 *
 * Our formatter always receives all four G16 userBinding rules:
 *   { "rules": [ { "type": "space-before-colon", "enabled": false },
 *                { "type": "space-after-colon", "enabled": false },
 *                { "type": "space-around-assign", "enabled": false },
 *                { "type": "newlines-before-println", "count": 0 } ] }
 *
 * Missing TCK keys stay disabled / count 0. Language-fixed and v1.1 keys are ignored.
 */
public class TckFormatterConfigAdapter {

    private static final Set<String> IGNORED = Set.of(
            "mandatory-line-break-after-statement",
            "mandatory-space-surrounding-operations",
            "mandatory-single-space-separation",
            "if-brace-same-line",
            "if-brace-below-line",
            "indent-inside-if"
    );

    public InputStream adapt(InputStream tckConfig) {
        final var theirRules = FlatJson.parse(readAll(tckConfig));

        var spaceBeforeColon = false;
        var spaceAfterColon = false;
        var spaceAroundAssign = false;
        var newlinesBeforePrintln = 0;

        for (final var theirRule : theirRules.entrySet()) {
            final var key = theirRule.getKey();
            if (IGNORED.contains(key)) {
                continue;
            }
            switch (key) {
                case "enforce-spacing-before-colon-in-declaration" ->
                        spaceBeforeColon = Boolean.parseBoolean(theirRule.getValue());
                case "enforce-spacing-after-colon-in-declaration" ->
                        spaceAfterColon = Boolean.parseBoolean(theirRule.getValue());
                case "enforce-spacing-around-equals" ->
                        spaceAroundAssign = Boolean.parseBoolean(theirRule.getValue());
                case "enforce-no-spacing-around-equals" ->
                        spaceAroundAssign = !Boolean.parseBoolean(theirRule.getValue());
                case "line-breaks-after-println" ->
                        newlinesBeforePrintln = Integer.parseInt(theirRule.getValue());
                default -> throw new IllegalArgumentException(
                        "Unmapped TCK formatter rule: '" + key + "'. Add it to TckFormatterConfigAdapter.");
            }
        }

        final var ourRules = "{\"rules\":["
                + "{\"type\":\"space-before-colon\",\"enabled\":" + spaceBeforeColon + "},"
                + "{\"type\":\"space-after-colon\",\"enabled\":" + spaceAfterColon + "},"
                + "{\"type\":\"space-around-assign\",\"enabled\":" + spaceAroundAssign + "},"
                + "{\"type\":\"newlines-before-println\",\"count\":" + newlinesBeforePrintln + "}"
                + "]}";
        return new ByteArrayInputStream(ourRules.getBytes(StandardCharsets.UTF_8));
    }

    private static String readAll(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Minimal reader for the TCK configs: a flat object of string keys to boolean/number values.
     * Values are kept as their raw text and interpreted by the rule they map to.
     */
    private static final class FlatJson {

        private final String json;
        private int cursor;

        private FlatJson(String json) {
            this.json = json;
        }

        static Map<String, String> parse(String json) {
            final var parser = new FlatJson(json);
            final var entries = new LinkedHashMap<String, String>();

            parser.expect('{');
            parser.skipWhitespace();
            while (parser.peek() != '}') {
                final var key = parser.readString();
                parser.skipWhitespace();
                parser.expect(':');
                entries.put(key, parser.readValue());
                parser.skipWhitespace();
                if (parser.peek() == ',') {
                    parser.cursor++;
                    parser.skipWhitespace();
                }
            }
            return entries;
        }

        private String readString() {
            expect('"');
            final var start = cursor;
            while (json.charAt(cursor) != '"') cursor++;
            return json.substring(start, cursor++);
        }

        private String readValue() {
            skipWhitespace();
            final var start = cursor;
            while (cursor < json.length() && json.charAt(cursor) != ',' && json.charAt(cursor) != '}') cursor++;
            return json.substring(start, cursor).trim();
        }

        private void expect(char expected) {
            skipWhitespace();
            if (peek() != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at index " + cursor + " of: " + json);
            }
            cursor++;
        }

        private char peek() {
            if (cursor >= json.length()) {
                throw new IllegalArgumentException("Unexpected end of config: " + json);
            }
            return json.charAt(cursor);
        }

        private void skipWhitespace() {
            while (cursor < json.length() && Character.isWhitespace(json.charAt(cursor))) cursor++;
        }
    }
}
