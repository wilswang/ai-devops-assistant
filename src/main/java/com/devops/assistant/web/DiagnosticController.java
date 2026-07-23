package com.devops.assistant.web;

import com.devops.assistant.agent.DiagnosticAgent;
import com.devops.assistant.probe.ProbeCollector;
import com.devops.assistant.probe.ProbeRegistry;
import com.devops.assistant.probe.ProbeResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API。
 * <ul>
 *   <li>GET  /api/probes            — 列出所有唯讀 probe</li>
 *   <li>GET  /api/containers        — 列出執行中的容器名稱（dashboard 下拉用）</li>
 *   <li>GET  /api/collect?container — 純蒐證（不呼叫 LLM）</li>
 *   <li>POST /api/diagnose          — 完整 LLM 診斷（需 ANTHROPIC_API_KEY）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class DiagnosticController {

    private final DiagnosticAgent agent;
    private final ContainerProvider containers;

    public DiagnosticController(DiagnosticAgent agent, ContainerProvider containers) {
        this.agent = agent;
        this.containers = containers;
    }

    public record ProbeInfo(String name, String category, boolean needsContainer,
                            List<String> requiredParams, String description) {
    }

    public record DiagnoseRequest(String question, String container) {
    }

    public record DiagnoseResponse(String report) {
    }

    @GetMapping("/probes")
    public List<ProbeInfo> probes() {
        return ProbeRegistry.all().values().stream()
                .map(p -> new ProbeInfo(p.name(), p.category(), p.needsContainer(),
                        p.requiredParams(), p.description()))
                .toList();
    }

    @GetMapping("/containers")
    public List<String> containers() {
        return containers.listRunning();
    }

    @GetMapping("/collect")
    public List<ProbeResult> collect(
            @RequestParam(defaultValue = "tomcat") String container) {
        return ProbeCollector.collectAll(container);
    }

    @PostMapping("/diagnose")
    public ResponseEntity<DiagnoseResponse> diagnose(@RequestBody DiagnoseRequest req) {
        if (req == null || req.question() == null || req.question().isBlank()) {
            return ResponseEntity.badRequest().body(new DiagnoseResponse("question 不可為空"));
        }
        String container = (req.container() == null || req.container().isBlank())
                ? "tomcat" : req.container();
        try {
            String report = agent.diagnose(req.question(), container);
            return ResponseEntity.ok(new DiagnoseResponse(report));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new DiagnoseResponse("診斷失敗：" + e.getMessage()
                            + "（請確認已設定 ANTHROPIC_API_KEY）"));
        }
    }
}
