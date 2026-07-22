package com.devops.assistant.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 安全層驗證：唯讀白名單放行、破壞性/注入指令一律擋下。 */
class CommandValidatorTest {

    @Test
    void allowsReadOnlyCommands() {
        assertTrue(CommandValidator.isSafe(List.of("uptime")));
        assertTrue(CommandValidator.isSafe(List.of("free", "-m")));
        assertTrue(CommandValidator.isSafe(List.of("df", "-h")));
        assertTrue(CommandValidator.isSafe(List.of("ss", "-tlnp")));
        assertTrue(CommandValidator.isSafe(List.of("docker", "ps", "-a")));
        assertTrue(CommandValidator.isSafe(List.of("docker", "stats", "--no-stream", "tomcat")));
        assertTrue(CommandValidator.isSafe(List.of("docker", "exec", "tomcat", "jstack", "1")));
        assertTrue(CommandValidator.isSafe(List.of("docker", "exec", "tomcat", "jstat", "-gcutil", "1")));
    }

    @Test
    void blocksDestructiveCommands() {
        assertFalse(CommandValidator.isSafe(List.of("rm", "-rf", "/")));
        assertFalse(CommandValidator.isSafe(List.of("kill", "-9", "1")));
        assertFalse(CommandValidator.isSafe(List.of("docker", "stop", "tomcat")));
        assertFalse(CommandValidator.isSafe(List.of("docker", "rm", "tomcat")));
        assertFalse(CommandValidator.isSafe(List.of("docker", "exec", "tomcat", "rm", "-rf", "/")));
        assertFalse(CommandValidator.isSafe(List.of("docker", "exec", "tomcat", "kill", "1")));
    }

    @Test
    void blocksShellMetacharacters() {
        assertFalse(CommandValidator.isSafe(List.of("cat", "/etc/passwd;", "rm")));
        assertFalse(CommandValidator.isSafe(List.of("ss", "-tlnp", "&&", "kill")));
        assertFalse(CommandValidator.isSafe(List.of("df", "-h", "|", "sh")));
        assertFalse(CommandValidator.isSafe(List.of("cat", "$(whoami)")));
    }

    @Test
    void blocksEmptyOrUnknown() {
        assertFalse(CommandValidator.isSafe(List.of()));
        assertFalse(CommandValidator.isSafe(List.of("wget", "http://x")));
        assertFalse(CommandValidator.isSafe(List.of("curl", "http://x")));
    }
}
