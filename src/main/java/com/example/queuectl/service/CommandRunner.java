package com.example.queuectl.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

@Service
public class CommandRunner {

    public static final class Result {
        public final int exitCode;
        public final String output;
        public Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    public Result run(String command, int timeoutSeconds) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb = windows
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("/bin/sh", "-lc", command);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<String> outFut = pool.submit(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append(System.lineSeparator());
                    }
                    return sb.toString();
                }
            });
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                pool.shutdownNow();
                return new Result(124, "Timed out after " + timeoutSeconds + "s");
            }
            int code = p.exitValue();
            String output = outFut.get(1, TimeUnit.SECONDS);
            pool.shutdown();
            return new Result(code, output);
        } catch (Exception e) {
            return new Result(127, e.getMessage());
        }
    }
}
