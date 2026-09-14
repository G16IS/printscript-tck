package implementation;

import adapter.InputStreamCodeReader;
import interpreter.PrintScriptFormatter;
import printscript.reader.CodeReader;
import printscript.tck.PrintScript;

import java.io.InputStream;
import java.io.Writer;

public class PrintScriptFormatterImpl implements PrintScriptFormatter {
    @Override
    public void format(InputStream src, String version, InputStream config, Writer writer) {
        CodeReader codeReaderAdapted = new InputStreamCodeReader(src);
        PrintScript.INSTANCE.format(version, codeReaderAdapted, config, writer);
    }
}
