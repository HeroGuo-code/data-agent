package io.github.qifan777.server.shared.python;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** 封装 Python 执行结果。 */
public class PythonExecutionResult {
    private final boolean success;
    private final String output;
    private final String error;

    public PythonExecutionResult(boolean success, String output, String error) {
        this.success = success; this.output = output; this.error = error;
    }
    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    @Override public String toString() {
        return "PythonExecutionResult(success=" + success + ", output=" + output + ", error=" + error + ")";
    }
}
