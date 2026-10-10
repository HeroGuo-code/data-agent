package io.github.qifan777.server.dataset.scheme.dto;

import java.util.List;

/** 表的结构化视图：仅包含生成 schema 提示词所需的字段。 */
public class DbTableSchemaView {
    private String name;
    private List<ColumnView> columns;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<ColumnView> getColumns() { return columns; }
    public void setColumns(List<ColumnView> columns) { this.columns = columns; }

    public static class ColumnView {
        private String name;
        private String type;
        private String description;
        private boolean primaryKey;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isPrimaryKey() { return primaryKey; }
        public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
    }
}
