package io.github.qifan777.server.dataset.scheme.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.qifan777.server.dataset.scheme.domain.DbColumn;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DbColumnMapper extends BaseMapper<DbColumn> {

    @Select("SELECT c.* FROM db_column c JOIN db_table t ON t.id = c.table_id WHERE t.database_id = #{databaseId}")
    List<DbColumn> findByDatabaseId(@Param("databaseId") String databaseId);
}
