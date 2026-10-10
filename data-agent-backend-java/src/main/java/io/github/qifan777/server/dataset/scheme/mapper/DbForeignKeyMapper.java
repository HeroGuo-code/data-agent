package io.github.qifan777.server.dataset.scheme.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.qifan777.server.dataset.scheme.domain.DbForeignKey;
import io.github.qifan777.server.dataset.scheme.dto.ForeignKeyProjection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DbForeignKeyMapper extends BaseMapper<DbForeignKey> {

    @Select("SELECT fk.id AS id, "
        + "fk.source_column_id AS sourceColumnId, sc.name AS sourceColumnName, "
        + "st.id AS sourceTableId, st.name AS sourceTableName, "
        + "fk.target_column_id AS targetColumnId, tc.name AS targetColumnName, "
        + "tt.id AS targetTableId, tt.name AS targetTableName "
        + "FROM db_foreign_key fk "
        + "JOIN db_column sc ON sc.id = fk.source_column_id "
        + "JOIN db_table st ON st.id = sc.table_id "
        + "JOIN db_column tc ON tc.id = fk.target_column_id "
        + "JOIN db_table tt ON tt.id = tc.table_id "
        + "WHERE st.database_id = #{databaseId}")
    List<ForeignKeyProjection> findSchemaByDatabaseId(@Param("databaseId") String databaseId);
}
