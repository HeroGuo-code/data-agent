package io.github.qifan777.server.dataset.scheme.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;

@TableName("db_foreign_key")
public class DbForeignKey {
    @TableId(type = IdType.INPUT)
    private UUID id;
    @TableField("source_column_id")
    private UUID sourceColumnId;
    @TableField("target_column_id")
    private UUID targetColumnId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSourceColumnId() { return sourceColumnId; }
    public void setSourceColumnId(UUID sourceColumnId) { this.sourceColumnId = sourceColumnId; }
    public UUID getTargetColumnId() { return targetColumnId; }
    public void setTargetColumnId(UUID targetColumnId) { this.targetColumnId = targetColumnId; }
}
