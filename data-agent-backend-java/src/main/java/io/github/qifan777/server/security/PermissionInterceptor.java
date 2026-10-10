package io.github.qifan777.server.security;

import io.github.qifan777.server.agent.model.SqlResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 权限拦截器（生产强制规则）。
 * 角色：
 *  - 运营(OPERATOR)：仅可访问所属店铺，禁止跨店铺；敏感字段脱敏。
 *  - 客服坐席(AGENT)：仅可查询指定单个买家数据，不支持批量导出；手机号/地址脱敏。
 *  - 管理员(ADMIN)：全量访问，但敏感字段仍脱敏。
 */
@Component
public class PermissionInterceptor {

    /** 敏感列：命中即脱敏（手机号保留前3后4，地址打码）。 */
    private static final Set<String> SENSITIVE_COLUMNS = new LinkedHashSet<>(List.of(
        "phone", "mobile", "tel", "address", "身份证", "手机号", "收货地址", "联系电话"));

    public static final String ROLE_OPERATOR = "运营";
    public static final String ROLE_AGENT = "客服坐席";
    public static final String ROLE_ADMIN = "管理员";

    public record AccessDecision(boolean allowed, String reason) {}

    /**
     * 越权拦截：客服坐席只能查询单个买家，运营只能查询所属店铺。
     */
    public AccessDecision checkQuery(String role, String question, Map<String, String> conditions) {
        if (ROLE_AGENT.equals(role)) {
            boolean singleBuyer = conditions != null
                && (conditions.containsKey("buyer_id") || conditions.containsKey("单买家"));
            if (!singleBuyer) {
                return new AccessDecision(false,
                    "客服坐席仅能查询指定单个买家数据，请补充买家ID后重试。涉及个人隐私，不支持批量导出。");
            }
        }
        if (ROLE_OPERATOR.equals(role)) {
            boolean hasStore = conditions != null
                && (conditions.containsKey("shop_id") || conditions.containsKey("所属店铺"));
            if (!hasStore) {
                return new AccessDecision(false,
                    "运营仅能访问所属店铺数据，请补充店铺ID，禁止跨店铺查询。");
            }
        }
        return new AccessDecision(true, null);
    }

    /** 对结果集按角色做敏感信息脱敏，返回脱敏后的新结果。 */
    public SqlResultSet mask(SqlResultSet result) {
        if (result.getData().isEmpty()) return result;
        List<String> maskedCols = new ArrayList<>();
        List<String> cols = result.getColumn();
        for (String c : cols) {
            maskedCols.add(SENSITIVE_COLUMNS.contains(norm(c)) ? maskLabel(c) : c);
        }
        List<Map<String, String>> maskedData = new ArrayList<>();
        for (Map<String, String> row : result.getData()) {
            Map<String, String> m = new HashMap<>();
            for (int i = 0; i < cols.size(); i++) {
                String key = cols.get(i);
                String val = row.get(key);
                if (val != null && SENSITIVE_COLUMNS.contains(norm(key))) {
                    m.put(maskedCols.get(i), maskValue(key, val));
                } else {
                    m.put(maskedCols.get(i), val == null ? "" : val);
                }
            }
            maskedData.add(m);
        }
        SqlResultSet out = new SqlResultSet(maskedCols, maskedData);
        out.setErrorMsg(result.getErrorMsg());
        return out;
    }

    private static String norm(String c) { return c.replace("`","").replace("\"","").trim().toLowerCase(Locale.ROOT); }
    private static String maskLabel(String col) { return col + "(脱敏)"; }

    private static String maskValue(String col, String val) {
        String n = norm(col);
        String v = val == null ? "" : val.trim();
        if (v.isEmpty()) return v;
        if (n.contains("phone") || n.contains("手机") || n.contains("mobile") || n.contains("tel")) {
            if (v.length() >= 7) return v.substring(0, 3) + "****" + v.substring(v.length() - 4);
            return "****";
        }
        // 地址/其他：保留前后
        if (v.length() <= 6) return "***";
        return v.substring(0, v.length() - 4) + "****";
    }

    /** 附加租户过滤（示例）：为运营人员自动追加 shop_id 条件。 */
    public String applyTenantFilter(String sql, Long shopId) {
        String s = sql.trim();
        s = s.replaceAll(";+$", "");
        String where = "shop_id = " + shopId;
        if (s.toLowerCase().contains(" where ")) {
            return s + " AND " + where;
        }
        return s + " WHERE " + where;
    }
}