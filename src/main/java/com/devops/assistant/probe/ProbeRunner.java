package com.devops.assistant.probe;

import com.devops.assistant.safety.CommandValidator;
import com.devops.assistant.safety.UnsafeCommandException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * probe 執行器。每條 argv 執行前一律過 {@link CommandValidator}，
 * 以 {@link ProcessBuilder}（不經 shell）執行，並 graceful degrade：
 * 指令不存在 → [N/A]、逾時 → [TIMEOUT]、被安全層擋下 → [SAFETY BLOCKED]。
 */
public final class ProbeRunner {

    private static final int TIMEOUT_SECONDS = 15;

    private ProbeRunner() {
    }

    public static ProbeResult run(Probe probe, Map<String, String> params) {
        List<List<String>> argvList = probe.build().apply(params);
        List<String> commands = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        boolean allOk = true;

        for (List<String> argv : argvList) {
            commands.add(String.join(" ", argv));
            RunOutcome outcome = execute(argv);
            allOk = allOk && outcome.ok();
            outputs.add(outcome.output());
        }
        return new ProbeResult(probe.name(), commands,
                String.join("\n", outputs).strip(), allOk);
    }

    private record RunOutcome(boolean ok, String output) {
    }

    private static RunOutcome execute(List<String> argv) {
        try {
            CommandValidator.validate(argv);
        } catch (UnsafeCommandException e) {
            return new RunOutcome(false, "[SAFETY BLOCKED] " + e.getMessage());
        }

        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            // 指令不存在於此主機時 ProcessBuilder.start() 拋 IOException
            return new RunOutcome(false, "[N/A] 指令無法執行（可能不存在）: " + argv.get(0));
        }

        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new RunOutcome(false, "[TIMEOUT] " + String.join(" ", argv));
            }
        } catch (IOException e) {
            return new RunOutcome(false, "[ERROR] " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RunOutcome(false, "[INTERRUPTED] " + String.join(" ", argv));
        }

        return new RunOutcome(process.exitValue() == 0, output.strip());
    }
}
