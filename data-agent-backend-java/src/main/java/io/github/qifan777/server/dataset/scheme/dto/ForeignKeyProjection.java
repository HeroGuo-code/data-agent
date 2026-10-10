package io.github.qifan777.server.dataset.scheme.dto;

import java.util.UUID;

/** 外键 + 关联列/表信息的投影，对应原 Jimmer FETCHER 抓取的数据。 */
public class ForeignKeyProjection {
    private UUID id;
    private UUID sourceColumnId;
    private String sourceColumnName;
    private UUID sourceTableId;
    private String sourceTableName;
    private UUID targetColumnId;
    private String targetColumnName;
    private UUID targetTableId;
    private String targetTableName;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSourceColumnId() { return sourceColumnId; }
    public void setSourceColumnId(UUID v) { this.sourceColumnId = v; }
    public String getSourceColumnName() { return sourceColumnName; }
    public void setSourceColumnName(String v) { this.sourceColumnName = v; }
    public UUID getSourceTableId() { return sourceTableId; }
    public void setSourceTableId(UUID v) { this.sourceTableId = v; }
    public String getSourceTableName() { return sourceTableName; }
    public void setSourceTableName(String v) { this.sourceTableName = v; }
    public UUID getTargetColumnId() { return targetColumnId; }
    public void setTargetColumnId(UUID v) { this.targetColumnId = v; }
    public String getTargetColumnName() { return targetColumnName; }
    public void setTargetColumnName(String v) { this.targetColumnName = v; }
    public UUID getTargetTableId() { return targetTableId; }
    public void setTargetTableId(UUID v) { this.targetTableId = v; }
    public String getTargetTableName() { return targetTableName; }
    public void setTargetTableName(String v) { this.targetTableName = v; }
}
