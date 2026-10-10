package io.github.qifan777.server.dataset.scheme.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;

@TableName("db_column")
public class DbColumn {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private String name;
    private String type;
    private String description;
    @TableField("is_primary_key")
    private boolean primaryKey;
    @TableField("table_id")
    private UUID tableId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
    public UUID getTableId() { return tableId; }
    public void setTableId(UUID tableId) { this.tableId = tableId; }
}
