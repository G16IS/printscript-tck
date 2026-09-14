package adapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Translates the TCK formatter config into the config our formatter expects.
 *
 * The TCK hands us a flat object keyed by ITS rule names:
 *   { "enforce-spacing-before-colon-in-declaration": true, "line-breaks-after-println": 2 }
 *
 * Our formatter reads a list of rule specs keyed by OUR rule names:
 *   { "rules": [ { "type": "space-before-colon", "enabled": true },
 *                { "type": "newlines-before-println", "count": 3 } ] }
 *
 * The RULES table below is the whole translation: left = their name, right = ours.
 * Names marked FILLER- are placeholders, replace them with the real rule names.
 */
public class TckFormatterConfigAdapter {

    private static final Map<String, Rule> RULES = Map.ofEntries(
            // --- 1.0 ---
            Map.entry("enforce-spacing-before-colon-in-declaration", Rule.enabled("space-before-colon")),
            Map.entry("enforce-spacing-after-colon-in-declaration", Rule.enabled("space-after-colon")),
            Map.entry("enforce-spacing-around-equals", Rule.enabled("space-around-assign")),
            // their "no spacing" flag is our "spacing" rule turned off
            Map.entry("enforce-no-spacing-around-equals", Rule.negated("space-around-assign")),
            // they count BLANK lines after the println, we count newlines before the next one
            Map.entry("line-breaks-after-println", Rule.count("newlines-before-println", blankLines -> blankLines + 1)),
            Map.entry("mandatory-line-break-after-statement", Rule.enabled("FILLER-newline-after-statement")),
            Map.entry("mandatory-space-surrounding-operations", Rule.enabled("FILLER-space-around-operator")),
            Map.entry("mandatory-single-space-separation", Rule.enabled("FILLER-single-space-separation")),

            // --- 1.1 ---
            Map.entry("if-brace-same-line", Rule.enabled("FILLER-if-brace-same-line")),
            Map.entry("if-brace-below-line", Rule.enabled("FILLER-if-brace-below-line")),
            Map.entry("indent-inside-if", Rule.count("FILLER-indent-inside-if", spaces -> spaces))
    );

    public InputStream adapt(InputStream tckConfig) {
        final var theirRules = FlatJson.parse(readAll(tckConfig));
        final var ourRules = new StringBuilder("{\"rules\":[");

        var first = true;
        for (final var theirRule : theirRules.entrySet()) {
            final var rule = RULES.get(theirRule.getKey());
            if (rule == null) {
                throw new IllegalArgumentException(
                        "Unmapped TCK formatter rule: '" + theirRule.getKey() + "'. Add it to TckFormatterConfigAdapter.RULES.");
            }
            if (!first) ourRules.append(',');
            ourRules.append(rule.toSpec(theirRule.getValue()));
            first = false;
        }

        ourRules.append("]}");
        return new ByteArrayInputStream(ourRules.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String readAll(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * One of our rules, plus how to derive its value from the TCK value.
     */
    private record Rule(String type, String param, boolean invert, IntUnaryOperator countMapper) {

        /** Boolean rule: their flag is our "enabled" as is. */
        static Rule enabled(String type) {
            return new Rule(type, "enabled", false, null);
        }

        /** Boolean rule: their flag is our "enabled" inverted (their "no-spacing" style flags). */
        static Rule negated(String type) {
            return new Rule(type, "enabled", true, null);
        }

        /** Numeric rule: their number mapped onto our "count". */
        static Rule count(String type, IntUnaryOperator countMapper) {
            return new Rule(type, "count", false, countMapper);
        }

        String toSpec(String theirValue) {
            return "{\"type\":\"" + type + "\",\"" + param + "\":" + value(theirValue) + "}";
        }

        private String value(String theirValue) {
            if (countMapper != null) {
                return String.valueOf(countMapper.applyAsInt(Integer.parseInt(theirValue)));
            }
            final var flag = Boolean.parseBoolean(theirValue);
            return String.valueOf(invert != flag);
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
