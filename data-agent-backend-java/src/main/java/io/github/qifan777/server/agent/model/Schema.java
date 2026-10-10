package io.github.qifan777.server.agent.model;

import com.alibaba.cloud.ai.graph.OverAllState;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.dataset.scheme.dto.DbForeignKeySchemaView;
import io.github.qifan777.server.dataset.scheme.dto.DbTableSchemaView;
import io.github.qifan777.server.shared.datasource.SchemaDataSourceProvider;
import io.github.qifan777.server.shared.datasource.SqliteSchemaDataSourceProvider;
import io.github.qifan777.server.shared.json.JsonUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Schema {
    private String databaseId;
    private List<DbTableSchemaView> dbTables;
    private List<DbForeignKeySchemaView> dbForeignKeys;
    private boolean enableExampleSampling = false;

    public Schema() {}
    public Schema(String databaseId, List<DbTableSchemaView> dbTables, List<DbForeignKeySchemaView> dbForeignKeys) {
        this.databaseId = databaseId; this.dbTables = dbTables; this.dbForeignKeys = dbForeignKeys;
    }

    public static Schema fromState(OverAllState state) {
        String json = state.value(DataAgentSpec.Graph.StateKey.Recall.TABLE_RELATION, "");
        return JsonUtil.fromJson(json, Schema.class);
    }

    public String buildSchemePrompt() {
        return buildSchemePrompt(SqliteSchemaDataSourceProvider.INSTANCE);
    }

    public String buildSchemePrompt(SchemaDataSourceProvider dataSourceProvider) {
        StringBuilder schemeBuilder = new StringBuilder();
        schemeBuilder.append("【DB_ID】").append(databaseId).append("\n");
        for (DbTableSchemaView t : dbTables) {
            schemeBuilder.append(buildTablePrompt(t, dataSourceProvider));
        }
        StringBuilder keys = new StringBuilder();
        int i = 0;
        for (DbForeignKeySchemaView fk : dbForeignKeys) {
            if (i++ > 0) keys.append("\n");
            keys.append(fk.toExpression());
        }
        schemeBuilder.append("【Foreign keys】\n").append(keys);
        return schemeBuilder.toString();
    }

    public String buildTablePrompt(DbTableSchemaView dbTable, SchemaDataSourceProvider dataSourceProvider) {
        StringBuilder builder = new StringBuilder();
        Map<String,Boolean> primaryKeys = new HashMap<>();
        for (DbTableSchemaView.ColumnView c : dbTable.getColumns()) {
            if (c.isPrimaryKey()) primaryKeys.put(c.getName(), true);
        }
        Map<String, List<String>> examplesByColumn =
            enableExampleSampling ? loadExamples(dbTable, dataSourceProvider) : new HashMap<>();
        builder.append("# Table: ").append(dbTable.getName()).append("\n[\n");
        for (DbTableSchemaView.ColumnView columnDto : dbTable.getColumns()) {
            builder.append("(").append(columnDto.getName()).append(": ").append(columnDto.getType());
            builder.append("\n, ").append(columnDto.getDescription()).append(", ");
            if (primaryKeys.containsKey(columnDto.getName())) {
                builder.append("primaryKey, ");
            }
            List<String> examples = examplesByColumn.getOrDefault(columnDto.getName(), new ArrayList<>());
            builder.append("Examples: [").append(String.join(",", examples)).append("]),\n");
        }
        builder.append("]\n");
        return builder.toString();
    }

    private Map<String, List<String>> loadExamples(DbTableSchemaView dbTable, SchemaDataSourceProvider provider) {
        try {
            Connection connection = provider.get(databaseId).getConnection();
            try {
                Map<String,List<String>> result = new HashMap<>();
                for (DbTableSchemaView.ColumnView column : dbTable.getColumns()) {
                    result.put(column.getName(), fetchDistinctValues(connection, dbTable.getName(), column.getName(), 3));
                }
                return result;
            } finally {
                try { connection.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public List<String> fetchDistinctValues(Connection connection, String fullTableName, String columnName, int limit) {
        List<String> values = new ArrayList<>();
        String sql = String.format("SELECT DISTINCT `%s` FROM %s WHERE `%s` IS NOT NULL LIMIT %d",
            columnName, fullTableName, columnName, limit);
        try (var stmt = connection.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String value = rs.getString(1);
                if (value != null && !value.isBlank()) values.add(value);
            }
        } catch (SQLException e) {
            // ignore unsupported DISTINCT column types
        }
        return values;
    }

    public String toJson() {
        return JsonUtil.toJson(this);
    }

    public String getDatabaseId() { return databaseId; }
    public void setDatabaseId(String databaseId) { this.databaseId = databaseId; }
    public List<DbTableSchemaView> getDbTables() { return dbTables; }
    public void setDbTables(List<DbTableSchemaView> dbTables) { this.dbTables = dbTables; }
    public List<DbForeignKeySchemaView> getDbForeignKeys() { return dbForeignKeys; }
    public void setDbForeignKeys(List<DbForeignKeySchemaView> dbForeignKeys) { this.dbForeignKeys = dbForeignKeys; }
    public boolean isEnableExampleSampling() { return enableExampleSampling; }
    public void setEnableExampleSampling(boolean v) { this.enableExampleSampling = v; }
}
