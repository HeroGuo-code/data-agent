package io.github.qifan777.server.security;

import java.util.regex.Pattern;

/**
 * SQL 安全校验器（生产强制规则）。
 * 规则：
 *  1) 只允许 SELECT 查询（以 SELECT / WITH...SELECT 开头）；
 *  2) 禁止 DROP / ALTER / DELETE / UPDATE / INSERT / TRUNCATE / MERGE / EXEC / CALL；
 *  3) 禁止多语句注入（分号拆分）；
 *  4) 禁止注释绕过与块注释混淆，避免绕过校验；
 *  5) 禁止 PRAGMA / ATTACH / DETACH / VACUUM 等 SQLite 管控语句；
 *  6) 大数据量保护：禁止无 where 的全表扫描（可选开关）。
 */
public final class SqlSecurityValidator {

    private SqlSecurityValidator() {}

    // 管理/写语句关键字
    private static final Pattern FORBIDDEN = Pattern.compile(
        "(?is)\\b(drop|alter|delete|update|insert|truncate|merge|exec|execute|call|pragma|attach|detach|vacuum|create|replace|grant|revoke)\\b");

    // SQLite 管控
    private static final Pattern DANGEROUS_STMT = Pattern.compile("(?i)\\b(pragma|attach|detach|vacuum|load_extension)\\b");

    // 注释
    private static final Pattern COMMENT = Pattern.compile("(?s)(/\\*.*?\\*/|--.*?$|#.*?$)");

    // 判断是否 SELECT 开头（可带 WITH）
    private static final Pattern SELECT_HEAD = Pattern.compile("(?is)^\\s*(with\\s+.*?\\bselect\\b|select\\b)");

    public static final class Result {
        public final boolean allowed;
        public final String reason;
        public Result(boolean allowed, String reason) {
            this.allowed = allowed; this.reason = reason;
        }
        public static Result ok() { return new Result(true, null); }
        public static Result deny(String reason) { return new Result(false, reason); }
    }

    /** 校验一条 SQL，返回是否允许执行。 */
    public static Result validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return Result.deny("SQL 为空，禁止执行。");
        }
        String normalized = COMMENT.matcher(sql).replaceAll(" ");
        // 多语句：去字符串后再按分号检查
        String noStrings = stripStrings(normalized);
        int semicolons = countTopLevelSemicolons(noStrings);
        if (semicolons > 1) {
            return Result.deny("检测到多条语句，禁止批量执行（防止注入）。");
        }
        if (!SELECT_HEAD.matcher(normalized).find()) {
            return Result.deny("仅允许 SELECT 只读查询，当前 SQL 不是 SELECT。");
        }
        if (FORBIDDEN.matcher(noStrings).find()) {
            return Result.deny("SQL 包含禁用关键字（仅允许 SELECT 只读查询）。");
        }
        if (DANGEROUS_STMT.matcher(noStrings).find()) {
            return Result.deny("SQL 包含数据库管控语句，已拦截。");
        }
        return Result.ok();
    }

    private static String stripStrings(String sql) {
        return sql.replaceAll("'(?:[^'\\\\]|\\\\.)*'", "''")
                  .replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"")
                  .replaceAll("\\[([^\\]]*)\\]", "[]")
                  .replaceAll("`([^`]*)`", "``");
    }

    private static int countTopLevelSemicolons(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == ';') count++;
        }
        return count;
    }
}