package com.devops.assistant;

import com.devops.assistant.agent.DiagnosticAgent;
import com.devops.assistant.probe.Probe;
import com.devops.assistant.probe.ProbeCollector;
import com.devops.assistant.probe.ProbeRegistry;
import com.devops.assistant.probe.ProbeResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 入口。依是否帶命令列參數決定模式：
 * <ul>
 *   <li><b>無參數</b> → 啟動 REST API + dashboard（web server，持續執行）。</li>
 *   <li><b>帶參數</b> → CLI 模式（不啟動 web server），支援：
 *     <ul>
 *       <li>{@code --list-probes}</li>
 *       <li>{@code --collect-only [--container X]}</li>
 *       <li>{@code "<問題>" [--container X]}（需 ANTHROPIC_API_KEY）</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@SpringBootApplication
public class DiagnosticAssistantApplication implements CommandLineRunner {

    private final ObjectProvider<DiagnosticAgent> agentProvider;

    public DiagnosticAssistantApplication(ObjectProvider<DiagnosticAgent> agentProvider) {
        this.agentProvider = agentProvider;
    }

    public static void main(String[] args) {
        // 有參數 → 純 CLI（不啟 web server）；無參數 → REST API server
        WebApplicationType webType = args.length > 0
                ? WebApplicationType.NONE
                : WebApplicationType.SERVLET;
        new SpringApplicationBuilder(DiagnosticAssistantApplication.class)
                .web(webType)
                .run(args);
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            // web 模式：CommandLineRunner 不做事，web server 持續服務
            System.out.println("REST API 已啟動：POST /api/diagnose、GET /api/collect、GET /api/probes；dashboard 於 /");
            return;
        }

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
            for (Probe p : ProbeRegistry.all().values()) {
                System.out.printf("%-22s [%s] %s%n", p.name(), p.category(), p.description());
            }
            return;
        }
        if (collectOnly) {
            printCollect(container);
            return;
        }
        if (questionParts.isEmpty()) {
            System.out.println("""
                    用法：
                      java -jar ai-devops-assistant.jar                       # 啟動 REST API + dashboard
                      java -jar ai-devops-assistant.jar "<問題>" [--container X]
                      java -jar ai-devops-assistant.jar --collect-only [--container X]
                      java -jar ai-devops-assistant.jar --list-probes
                    """);
            return;
        }

        String question = String.join(" ", questionParts);
        System.out.println(agentProvider.getObject().diagnose(question, container));
    }

    private void printCollect(String container) {
        System.out.println("# 全 probe 蒐證（container=" + container + "）\n");
        for (ProbeResult res : ProbeCollector.collectAll(container)) {
            String status = res.ok() ? "OK" : "SKIP/ERR";
            System.out.println("## [" + status + "] " + res.probe());
            System.out.println("$ " + String.join("; ", res.commands()));
            System.out.println(res.output().isBlank() ? "(無輸出)" : res.output());
            System.out.println();
        }
    }
}
