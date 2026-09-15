package implementation;

import adapter.AdaptedErrorHandler;
import adapter.InputStreamCodeReader;
import adapter.TckLinterConfigAdapter;
import interpreter.ErrorHandler;
import interpreter.PrintScriptLinter;
import printscript.reader.CodeReader;
import printscript.tck.PrintScript;

import java.io.InputStream;

public class PrintScriptLinterImpl implements PrintScriptLinter {
    @Override
    public void lint(InputStream src, String version, InputStream config, ErrorHandler handler) {
        CodeReader codeReader = new InputStreamCodeReader(src);
        InputStream configAdapted = new TckLinterConfigAdapter().adapt(config);
        printscript.ErrorHandler adaptedHandler = new AdaptedErrorHandler(handler);
        PrintScript.INSTANCE.lint(version, codeReader,configAdapted, adaptedHandler);
    }
}
