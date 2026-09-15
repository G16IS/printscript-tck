package adapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Translates the TCK linter config into the config our linter expects.
 *
 * The TCK hands us a flat object keyed by ITS rule names:
 *   { "identifier_format": "camel case" }
 *   { "mandatory-variable-or-literal-in-println": true }
 *
 * Our linter takes a map keyed by G16 rule id, each with its own options:
 *   { "rules": { "identifier-format": { "enabled": true, "options": { "format": "camelCase" } },
 *                "println-simple-argument": { "enabled": false } } }
 *
 * A TCK key that is absent leaves its rule DISABLED. That is the whole point of the
 * `valid-no-rules` case, which sends `{}` over a source with a non-camelCase identifier and
 * expects no findings: the config replaces our internal `linter.config.v1.0.json` instead of
 * layering on top of it, so nothing is on unless the TCK turns it on.
 *
 * Note the value spellings differ: the TCK writes "camel case" / "snake case" (lowercase, with
 * a space), our IdentifierFormatRuleProvider only accepts "camelCase" / "snake_case".
 */
public class TckLinterConfigAdapter {

    private static final String IDENTIFIER_FORMAT = "identifier_format";
    private static final String PRINTLN_SIMPLE_ARGUMENT = "mandatory-variable-or-literal-in-println";
    private static final String READ_INPUT_SIMPLE_ARGUMENT = "mandatory-variable-or-literal-in-readInput";

    public InputStream adapt(InputStream tckConfig) {
        final var theirRules = FlatJson.parse(readAll(tckConfig));

        String identifierFormat = null;
        String simpleArgumentCallee = null;

        for (final var theirRule : theirRules.entrySet()) {
            final var key = theirRule.getKey();
            final var value = theirRule.getValue();
            switch (key) {
                case IDENTIFIER_FORMAT -> identifierFormat = ourLetterCase(FlatJson.unquote(value));
                case PRINTLN_SIMPLE_ARGUMENT -> {
                    if (Boolean.parseBoolean(value)) {
                        simpleArgumentCallee = callee(simpleArgumentCallee, "println");
                    }
                }
                /*case READ_INPUT_SIMPLE_ARGUMENT -> {
                    if (Boolean.parseBoolean(value)) {
                        simpleArgumentCallee = callee(simpleArgumentCallee, "readInput");
                    }
                }*/
                default -> throw new IllegalArgumentException(
                        "Unmapped TCK linter rule: '" + key + "'. Add it to TckLinterConfigAdapter.");
            }
        }

        final var ourRules = "{\"rules\":{"
                + "\"identifier-format\":" + rule("format", identifierFormat) + ","
                + "\"println-simple-argument\":" + rule("callee", simpleArgumentCallee)
                + "}}";
        return new ByteArrayInputStream(ourRules.getBytes(StandardCharsets.UTF_8));
    }

    /** A null [optionValue] means the TCK did not ask for this rule, so it stays off. */
    private static String rule(String optionName, String optionValue) {
        if (optionValue == null) {
            return "{\"enabled\":false}";
        }
        return "{\"enabled\":true,\"options\":{\"" + optionName + "\":\"" + optionValue + "\"}}";
    }

    private static String ourLetterCase(String tckFormat) {
        return switch (tckFormat) {
            case "camel case" -> "camelCase";
            case "snake case" -> "snake_case";
            default -> throw new IllegalArgumentException(
                    "Unknown TCK identifier format: '" + tckFormat + "'. Expected 'camel case' or 'snake case'.");
        };
    }

    /**
     * Both TCK keys map onto the same G16 rule id, which holds a single callee, so they cannot
     * both be on. No TCK case sends both today; fail loudly rather than silently drop one.
     */
    private static String callee(String alreadyChosen, String candidate) {
        if (alreadyChosen != null && !alreadyChosen.equals(candidate)) {
            throw new IllegalArgumentException(
                    "Cannot enable the simple-argument rule for both '" + alreadyChosen + "' and '" + candidate
                            + "': our linter holds one callee per rule id.");
        }
        return candidate;
    }

    private static String readAll(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
