package io.github.qifan777.server.dataset.scheme.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;

@TableName("db_table")
public class DbTable {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private String name;
    private String description;
    @TableField("database_id")
    private String databaseId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDatabaseId() { return databaseId; }
    public void setDatabaseId(String databaseId) { this.databaseId = databaseId; }
}
