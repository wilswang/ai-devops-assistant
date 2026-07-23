package com.devops.assistant.web;

import com.devops.assistant.agent.DiagnosticAgent;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST controller 切片測試（只載入 web 層，DiagnosticAgent 以 mock 取代，
 * 因此不需 Anthropic API key 也能跑）。
 */
@WebMvcTest(DiagnosticController.class)
class DiagnosticControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DiagnosticAgent agent;

    @MockitoBean
    private ContainerProvider containers;

    @Test
    void listsAllProbes() throws Exception {
        mvc.perform(get("/api/probes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void collectReturnsAllProbeResults() throws Exception {
        mvc.perform(get("/api/collect").param("container", "tomcat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void listsRunningContainers() throws Exception {
        given(containers.listRunning())
                .willReturn(List.of("apollo-portal", "apollo-db"));

        mvc.perform(get("/api/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("apollo-portal"))
                .andExpect(jsonPath("$[1]").value("apollo-db"));
    }

    @Test
    void diagnoseReturnsReport() throws Exception {
        given(agent.diagnose(any(), any()))
                .willReturn("## 現象\nTomcat 回應變慢");

        mvc.perform(post("/api/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"為什麼變慢？\",\"container\":\"tomcat\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report").value(org.hamcrest.Matchers.containsString("現象")));
    }

    @Test
    void blankQuestionReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\",\"container\":\"tomcat\"}"))
                .andExpect(status().isBadRequest());
    }
}
