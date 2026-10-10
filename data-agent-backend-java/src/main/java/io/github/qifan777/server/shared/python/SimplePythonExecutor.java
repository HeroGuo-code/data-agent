package io.github.qifan777.server.shared.python;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** ?? Docker ???? Python ??(??? SimplePythonExecutor)? */
public final class SimplePythonExecutor {
    private static final String DEFAULT_DOCKER_IMAGE = "data-agent-python:latest";
    private static final String DEFAULT_MEMORY_LIMIT = "512m";

    private SimplePythonExecutor() {}

    public static PythonExecutionResult execute(String pythonCode, String inputJson) {
        return execute(pythonCode, inputJson, 60);
    }

    public static PythonExecutionResult execute(String pythonCode, String inputJson, long timeoutSec) {
        File workDir;
        try {
            workDir = Files.createTempDirectory("ai_python_exec_").toFile();
        } catch (Exception e) {
            return new PythonExecutionResult(false, "", e.getMessage());
        }
        File scriptFile = new File(workDir, "script.py");
        File dataFile = new File(workDir, "input.json");
        try {
            java.nio.file.Files.write(scriptFile.toPath(), pythonCode.getBytes());
            java.nio.file.Files.write(dataFile.toPath(), inputJson.getBytes());

            if (!isDockerAvailable()) {
                return new PythonExecutionResult(false, "",
                    "Docker is required for Python sandbox execution but was not found. Please install/start Docker.");
            }
            String envImage = System.getenv("DATA_AGENT_PYTHON_DOCKER_IMAGE");
            String image = (envImage == null || envImage.trim().isEmpty()) ? DEFAULT_DOCKER_IMAGE : envImage.trim();
            String envMem = System.getenv("DATA_AGENT_PYTHON_MEMORY_LIMIT");
            String memoryLimit = (envMem == null || envMem.trim().isEmpty()) ? DEFAULT_MEMORY_LIMIT : envMem.trim();

            List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm", "-i",
                "--network", "none",
                "--cpus", "1",
                "--memory", memoryLimit,
                "--pids-limit", "128",
                "--security-opt", "no-new-privileges",
                "-v", workDir.getAbsolutePath() + ":/work:ro",
                "-w", "/work",
                image,
                "python", "/work/script.py"));
            PythonExecutionResult dockerResult = runCommand(cmd, dataFile, timeoutSec);
            if (dockerResult.isSuccess()) {
                return dockerResult;
            }
            return new PythonExecutionResult(false, dockerResult.getOutput(),
                dockerResult.getError() + "\n\nSandbox image: " + image);
        } catch (Exception e) {
            return new PythonExecutionResult(false, "", e.getMessage() == null ? "Unknown error" : e.getMessage());
        } finally {
            workDir.delete();
        }
    }

    private static PythonExecutionResult runCommand(List<String> command, File inputFile, long timeoutSec) {
        try {
            Process process = new ProcessBuilder(command).redirectInput(inputFile).start();
            boolean completed = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new PythonExecutionResult(false, "", "Execution timed out after " + timeoutSec + " seconds.");
            }
            int exitCode = process.exitValue();
            String stdout = new String(process.getInputStream().readAllBytes()).trim();
            String stderr = new String(process.getErrorStream().readAllBytes()).trim();
            return exitCode == 0
                ? new PythonExecutionResult(true, stdout, stderr)
                : new PythonExecutionResult(false, stdout, stderr);
        } catch (Exception e) {
            return new PythonExecutionResult(false, "", e.getMessage());
        }
    }

    private static boolean isDockerAvailable() {
        try {
            Process p = new ProcessBuilder("docker", "--version").start();
            return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
