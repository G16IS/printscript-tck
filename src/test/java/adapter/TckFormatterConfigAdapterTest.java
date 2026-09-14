package adapter;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TckFormatterConfigAdapterTest {

    @Test
    public void missingKeysAreDisabledNotDefaultOn() {
        var json = adapt("{ \"enforce-no-spacing-around-equals\": true }");
        assertTrue(json.contains("\"type\":\"space-around-assign\",\"enabled\":false"));
        assertTrue(json.contains("\"type\":\"space-before-colon\",\"enabled\":false"));
        assertTrue(json.contains("\"type\":\"space-after-colon\",\"enabled\":false"));
        assertTrue(json.contains("\"type\":\"newlines-before-println\",\"count\":0"));
        assertFalse(json.contains("FILLER-"));
        assertFalse(json.contains("if-brace"));
    }

    @Test
    public void printlnCountIsIdentity() {
        var json = adapt("{ \"line-breaks-after-println\": 0 }");
        assertTrue(json.contains("\"type\":\"newlines-before-println\",\"count\":0"));
        assertFalse(json.contains("\"count\":1"));
    }

    @Test
    public void v11KeysAreDropped() {
        var json = adapt("{ \"if-brace-same-line\": true }");
        assertTrue(json.contains("\"type\":\"space-before-colon\",\"enabled\":false"));
        assertTrue(json.contains("\"type\":\"space-after-colon\",\"enabled\":false"));
        assertTrue(json.contains("\"type\":\"space-around-assign\",\"enabled\":false"));
        assertTrue(json.contains("\"type\":\"newlines-before-println\",\"count\":0"));
        assertFalse(json.contains("FILLER-"));
        assertFalse(json.contains("if-brace"));
    }

    private static String adapt(String tckJson) {
        try {
            var stream = new TckFormatterConfigAdapter()
                    .adapt(new ByteArrayInputStream(tckJson.getBytes(StandardCharsets.UTF_8)));
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
