package io.github.qifan777.server.dataset.scheme.dto;

/** 外键视图：用于生成 "a.b = c.d" 的关联表达式。 */
public class DbForeignKeySchemaView {
    private String sourceTableName;
    private String sourceColumnName;
    private String targetTableName;
    private String targetColumnName;

    public String toExpression() {
        return sourceTableName + "." + sourceColumnName + " = " + targetTableName + "." + targetColumnName;
    }

    public String getSourceTableName() { return sourceTableName; }
    public void setSourceTableName(String v) { this.sourceTableName = v; }
    public String getSourceColumnName() { return sourceColumnName; }
    public void setSourceColumnName(String v) { this.sourceColumnName = v; }
    public String getTargetTableName() { return targetTableName; }
    public void setTargetTableName(String v) { this.targetTableName = v; }
    public String getTargetColumnName() { return targetColumnName; }
    public void setTargetColumnName(String v) { this.targetColumnName = v; }
}
