package adapter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal reader for the TCK configs: a flat object of string keys to boolean/number/string values.
 * Values are kept as their raw text and interpreted by the rule they map to; use {@link #unquote}
 * for the ones that are JSON strings.
 *
 * Shared by {@link TckFormatterConfigAdapter} and {@link TckLinterConfigAdapter}.
 */
final class FlatJson {

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

    /** Strips the surrounding double quotes of a raw value that is a JSON string. */
    static String unquote(String rawValue) {
        if (rawValue.length() < 2 || rawValue.charAt(0) != '"' || !rawValue.endsWith("\"")) {
            throw new IllegalArgumentException("Expected a quoted string but was: " + rawValue);
        }
        return rawValue.substring(1, rawValue.length() - 1);
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
