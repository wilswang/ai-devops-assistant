package com.devops.assistant.web;

import com.devops.assistant.probe.ContainerLister;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 提供執行中的容器名稱給 dashboard（薄封裝，讓 controller 可注入 / 測試可 mock）。
 * 實際邏輯委派給純 Java 的 {@link ContainerLister}。
 */
@Component
public class ContainerProvider {

    public List<String> listRunning() {
        return ContainerLister.listRunning();
    }
}
