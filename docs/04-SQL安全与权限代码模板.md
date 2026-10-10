# SQL 安全校验 与 权限拦截 代码模板

> 已落地工程：`security/SqlSecurityValidator.java`、`security/PermissionInterceptor.java`，
> 并在 `graph/nodes/SqlExecuteNode.java` 中强制执行。

## 4.1 执行前强制校验（在 SqlExecuteNode 调用）

\`\`\`java
import io.github.qifan777.server.security.SqlSecurityValidator;

// SQL 执行前（只读强制）
SqlSecurityValidator.Result sec = SqlSecurityValidator.validate(sql);
if (!sec.allowed) {
    throw new RuntimeException("[安全管理] " + sec.reason); // 越权/违规直接拦截
}
java.sql.PreparedStatement ps = conn.prepareStatement(sql);
try (java.sql.ResultSet rs = ps.executeQuery()) {
    resultSetWrapper = ResultSetBuilder.buildFrom(rs);
}
\`\`\`

## 4.2 SqlSecurityValidator（只读 + 关键字 + 多语句）

\`\`\`java
public static Result validate(String sql) {
    if (sql == null || sql.trim().isEmpty()) return Result.deny("SQL 为空，禁止执行。");
    String normalized = COMMENT.matcher(sql).replaceAll(" ");   // 剥注释
    String noStrings = stripStrings(normalized);                 // 剥字符串
    if (countTopLevelSemicolons(noStrings) > 1) return Result.deny("检测到多条语句，禁止批量执行。");
    if (!SELECT_HEAD.matcher(normalized).find()) return Result.deny("仅允许 SELECT 只读查询。");
    if (FORBIDDEN.matcher(noStrings).find()) return Result.deny("SQL 含禁用关键字（DROP/ALTER/DELETE/TRUNCATE/…）");
    if (DANGEROUS_STMT.matcher(noStrings).find()) return Result.deny("SQL 含管控语句（PRAGMA/ATTACH/…）");
    return Result.ok();
}
// FORBIDDEN: drop|alter|delete|update|insert|truncate|merge|exec|execute|call|pragma|attach|detach|vacuum|create|replace|grant|revoke
\`\`\`

## 4.3 权限拦截（角色/店铺/单买家 + 脱敏）

\`\`\`java
import io.github.qifan777.server.security.PermissionInterceptor;

// 1) 越权预检（根据 metadata 中的 role 与条件）
AccessDecision d = interceptor.checkQuery(role, question, conditions);
if (!d.allowed()) return Result.deny(d.reason());

// 2) 结果集脱敏（SqlExecuteNode 已接入）
resultSetWrapper = interceptor.mask(resultSetWrapper); // phone→138****1234, address→***...****

// 3) 运营租户追加（示例）
String filteredSql = interceptor.applyTenantFilter(sql, currentShopId); // ... WHERE ... AND shop_id = x
\`\`\`

### 角色矩阵

| 角色 | 数据边界 | 敏感字段 | 越权处理 |
|------|----------|----------|----------|
| 运营 | shop_id=所属店铺 | 手机号/地址脱敏 | 拦截并返回提示 |
| 客服坐席 | 单个 buyer_id | 手机号/地址脱敏 | 批量即拦截 |
| 管理员 | 全量 | 手机号/地址脱敏 | - |
