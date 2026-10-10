package io.github.qifan777.server.shared.datasource;

import io.github.qifan777.server.agent.model.SqlResultSet;
import java.sql.ResultSet;

/** 结果集构建工具(对应原 ResultSetBuilder)。 */
public final class ResultSetBuilder {
    private ResultSetBuilder() {}

    public static SqlResultSet buildFrom(ResultSet rs) throws java.sql.SQLException {
        var meta = rs.getMetaData();
        int columnsCount = meta.getColumnCount();
        java.util.List<String> rowHead = new java.util.ArrayList<>();
        for (int i = 1; i <= columnsCount; i++) rowHead.add(meta.getColumnLabel(i));

        java.util.List<java.util.Map<String,String>> rawData = new java.util.ArrayList<>();
        int count = 0;
        while (rs.next() && count < 1000) {
            java.util.Map<String,String> kv = new java.util.LinkedHashMap<>();
            for (String h : rowHead) kv.put(h, rs.getString(h) == null ? "" : rs.getString(h));
            rawData.add(kv);
            count++;
        }
        java.util.List<String> cleanedHead = cleanColumnNames(rowHead);
        java.util.List<java.util.Map<String,String>> cleanedData = cleanResultSet(rawData);
        return new SqlResultSet(cleanedHead, cleanedData);
    }

    private static java.util.List<String> cleanColumnNames(java.util.List<String> columnNames) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String c : columnNames) out.add(c.replace("`", "").replace("\"", ""));
        return out;
    }

    private static java.util.List<java.util.Map<String,String>> cleanResultSet(
            java.util.List<java.util.Map<String,String>> data) {
        java.util.List<java.util.Map<String,String>> out = new java.util.ArrayList<>();
        for (java.util.Map<String,String> row : data) {
            java.util.Map<String,String> m = new java.util.LinkedHashMap<>();
            for (var e : row.entrySet()) m.put(e.getKey().replace("`","").replace("\"",""), e.getValue());
            out.add(m);
        }
        return out;
    }
}
