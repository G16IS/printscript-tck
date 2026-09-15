package adapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
        var singleSpaceSeparation = false;

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
                case "mandatory-single-space-separation" ->
                        singleSpaceSeparation = Boolean.parseBoolean(theirRule.getValue());
                default -> throw new IllegalArgumentException(
                        "Unmapped TCK formatter rule: '" + key + "'. Add it to TckFormatterConfigAdapter.");
            }
        }

        final var ourRules = "{\"rules\":["
                + "{\"type\":\"single-space-separation\",\"enabled\":" + singleSpaceSeparation + "},"
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
}
