package com.devops.assistant.probe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * dashboard 容器下拉 TDD — ContainerLister 名稱解析。目前為 stub，此測試預期為紅燈。
 */
class ContainerListerTest {

    @Test
    void parsesNamesLineByLineKeepingOrder() {
        List<String> names = ContainerLister.parseNames(
                "apollo-portal\napollo-adminservice\napollo-configservice\napollo-db");
        assertEquals(List.of("apollo-portal", "apollo-adminservice",
                "apollo-configservice", "apollo-db"), names);
    }

    @Test
    void trimsWhitespaceAndDropsBlankLines() {
        List<String> names = ContainerLister.parseNames("  apollo-portal \n\n\t apollo-db \n");
        assertEquals(List.of("apollo-portal", "apollo-db"), names);
    }

    @Test
    void returnsEmptyForNullOrBlank() {
        assertTrue(ContainerLister.parseNames(null).isEmpty(), "null 應回空清單");
        assertTrue(ContainerLister.parseNames("   \n  ").isEmpty(), "全空白應回空清單");
    }

    @Test
    void dropsGracefulDegradeMarkers() {
        // docker 不可用時 ProbeRunner 會回 [N/A]…；不應被當成容器名
        assertTrue(ContainerLister.parseNames("[N/A] 指令無法執行（可能不存在）: docker").isEmpty(),
                "graceful-degrade 標記不應被解析成容器名");
    }
}
