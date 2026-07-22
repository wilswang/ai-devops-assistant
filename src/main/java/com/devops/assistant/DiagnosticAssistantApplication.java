package com.devops.assistant;

import com.devops.assistant.agent.DiagnosticAgent;
import com.devops.assistant.probe.Probe;
import com.devops.assistant.probe.ProbeRegistry;
import com.devops.assistant.probe.ProbeResult;
import com.devops.assistant.probe.ProbeRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI 入口。三種模式：
 * <ul>
 *   <li>{@code --list-probes}：列出所有 probe</li>
 *   <li>{@code --collect-only}：不呼叫 LLM，跑全部 probe 印原始證據（可離線）</li>
 *   <li>{@code <問題>}：完整診斷（需 ANTHROPIC_API_KEY）</li>
 * </ul>
 *
 * 用法範例：
 * <pre>
 *   java -jar ai-devops-assistant.jar "為什麼 Tomcat 變慢？" --container tomcat
 *   java -jar ai-devops-assistant.jar --collect-only --container tomcat
 * </pre>
 */
@SpringBootApplication
public class DiagnosticAssistantApplication implements CommandLineRunner {

    private final ObjectProvider<DiagnosticAgent> agentProvider;

    public DiagnosticAssistantApplication(ObjectProvider<DiagnosticAgent> agentProvider) {
        this.agentProvider = agentProvider;
    }

    public static void main(String[] args) {
        SpringApplication.run(DiagnosticAssistantApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String container = "tomcat";
        boolean listProbes = false;
        boolean collectOnly = false;
        List<String> questionParts = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--list-probes" -> listProbes = true;
                case "--collect-only" -> collectOnly = true;
                case "--container" -> {
                    if (i + 1 < args.length) {
                        container = args[++i];
                    }
                }
                default -> questionParts.add(args[i]);
            }
        }

        if (listProbes) {
            listProbes();
            return;
        }
        if (collectOnly) {
            collectOnly(container);
            return;
        }
        if (questionParts.isEmpty()) {
            System.out.println("""
                    用法：
                      java -jar ai-devops-assistant.jar "<問題>" [--container <名稱>]
                      java -jar ai-devops-assistant.jar --collect-only [--container <名稱>]
                      java -jar ai-devops-assistant.jar --list-probes
                    """);
            return;
        }

        String question = String.join(" ", questionParts);
        String report = agentProvider.getObject().diagnose(question, container);
        System.out.println(report);
    }

    private void listProbes() {
        for (Probe p : ProbeRegistry.all().values()) {
            System.out.printf("%-22s [%s] %s%n", p.name(), p.category(), p.description());
        }
    }

    /** 不呼叫 LLM，跑全部 probe 印原始證據。順手把 JVM PID 傳給後續 tomcat probe。 */
    private void collectOnly(String container) {
        System.out.println("# 全 probe 蒐證（container=" + container + "）\n");
        String pid = "1";
        for (Probe probe : ProbeRegistry.all().values()) {
            Map<String, String> params = new HashMap<>();
            params.put("container", container);
            params.put("pid", pid);

            ProbeResult res = ProbeRunner.run(probe, params);
            String status = res.ok() ? "OK" : "SKIP/ERR";
            System.out.println("## [" + status + "] " + probe.name());
            System.out.println("$ " + String.join("; ", res.commands()));
            System.out.println(res.output().isBlank() ? "(無輸出)" : res.output());
            System.out.println();

            if ("tomcat_jvm_procs".equals(probe.name()) && res.ok()) {
                pid = firstPid(res.output(), pid);
            }
        }
    }

    private String firstPid(String jpsOutput, String fallback) {
        for (String line : jpsOutput.split("\n")) {
            String[] tok = line.trim().split("\\s+");
            if (tok.length > 0 && tok[0].matches("\\d+")) {
                return tok[0];
            }
        }
        return fallback;
    }
}
