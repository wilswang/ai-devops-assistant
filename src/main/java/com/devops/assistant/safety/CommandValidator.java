package com.devops.assistant.safety;

import java.util.List;
import java.util.Set;

/**
 * 安全層：預設拒絕的唯讀指令驗證。
 *
 * <p>設計原則：
 * <ol>
 *   <li>白名單（allowlist），不是黑名單。未列出的 binary 一律拒絕。</li>
 *   <li>指令以 argv list 形式執行（ProcessBuilder，不經過 shell），
 *       從根本消除 shell injection / metacharacter 風險。</li>
 *   <li>只允許唯讀診斷指令。任何變更狀態的動作（kill / rm / restart /
 *       docker stop ...）都不在白名單，MVP 完全沒有變更路徑。</li>
 * </ol>
 *
 * <p>Phase 5（自動修復）若要加入變更操作，應在此擴充三層分類：
 * READ_ONLY 自動 / REVERSIBLE 需確認 / DESTRUCTIVE 需確認+二次打字確認。
 * 目前只實作 READ_ONLY。
 */
public final class CommandValidator {

    /** 允許執行的 binary（絕對唯讀）。 */
    public static final Set<String> ALLOWED_BINARIES = Set.of(
            // 通用 / Linux
            "uptime", "cat", "free", "df", "ps", "ss", "top", "vmstat",
            "iostat", "mpstat", "uname", "nproc", "who", "w",
            // macOS 對應唯讀工具
            "vm_stat", "netstat",
            "docker"
    );

    /** docker 只允許這些唯讀子命令。 */
    public static final Set<String> DOCKER_READONLY_SUBCMDS = Set.of(
            "ps", "stats", "inspect", "top", "logs", "version", "info"
    );

    /** 允許在 container 內（docker exec）執行的唯讀 binary。 */
    public static final Set<String> DOCKER_EXEC_ALLOWED = Set.of(
            "jps", "jstat", "jstack", "jcmd", "ps", "cat", "jinfo"
    );

    /** 明確禁止出現的字元：從源頭杜絕拼接/注入。 */
    private static final Set<Character> FORBIDDEN_CHARS =
            Set.of(';', '|', '&', '$', '`', '>', '<', '\n', '\\');

    private CommandValidator() {
    }

    /** 驗證一條 argv 指令是否為允許的唯讀指令；不通過拋 {@link UnsafeCommandException}。 */
    public static void validate(List<String> argv) {
        if (argv == null || argv.isEmpty()) {
            throw new UnsafeCommandException("空指令");
        }
        checkTokens(argv);

        String binary = argv.get(0);
        if (!ALLOWED_BINARIES.contains(binary)) {
            throw new UnsafeCommandException("binary 不在白名單: " + binary);
        }
        if ("docker".equals(binary)) {
            validateDocker(argv);
        }
    }

    /** 是否為安全指令（不拋例外的版本）。 */
    public static boolean isSafe(List<String> argv) {
        try {
            validate(argv);
            return true;
        } catch (UnsafeCommandException e) {
            return false;
        }
    }

    private static void checkTokens(List<String> argv) {
        for (String tok : argv) {
            for (int i = 0; i < tok.length(); i++) {
                if (FORBIDDEN_CHARS.contains(tok.charAt(i))) {
                    throw new UnsafeCommandException("指令參數含禁用字元: " + tok);
                }
            }
        }
    }

    private static void validateDocker(List<String> argv) {
        // 找到第一個非 flag 的 token 當作子命令
        String sub = null;
        int restIdx = 1;
        for (int i = 1; i < argv.size(); i++) {
            if (!argv.get(i).startsWith("-")) {
                sub = argv.get(i);
                restIdx = i + 1;
                break;
            }
        }
        if (sub == null) {
            throw new UnsafeCommandException("docker 缺少子命令");
        }
        if (!DOCKER_READONLY_SUBCMDS.contains(sub) && !"exec".equals(sub)) {
            throw new UnsafeCommandException("docker 子命令非唯讀白名單: " + sub);
        }

        if ("exec".equals(sub)) {
            // docker exec [flags] <container> <inner-binary> [args...]
            // 非 flag token 依序為 container、inner-binary、inner-args
            List<String> nonFlags = argv.subList(restIdx, argv.size()).stream()
                    .filter(t -> !t.startsWith("-"))
                    .toList();
            if (nonFlags.size() < 2) {
                throw new UnsafeCommandException("docker exec 參數不足");
            }
            String innerBinary = nonFlags.get(1);
            if (!DOCKER_EXEC_ALLOWED.contains(innerBinary)) {
                throw new UnsafeCommandException("docker exec 內層指令非白名單: " + innerBinary);
            }
        }
    }
}
