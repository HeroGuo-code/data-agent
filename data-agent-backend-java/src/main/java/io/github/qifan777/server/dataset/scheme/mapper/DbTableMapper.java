package io.github.qifan777.server.dataset.scheme.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.qifan777.server.dataset.scheme.domain.DbTable;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DbTableMapper extends BaseMapper<DbTable> {

    @Select("SELECT * FROM db_table WHERE database_id = #{databaseId}")
    List<DbTable> findByDatabaseId(@Param("databaseId") String databaseId);

    @Select("<script>"
        + "SELECT * FROM db_table WHERE database_id = #{databaseId} AND name IN "
        + "<foreach collection='names' item='n' open='(' separator=',' close=')'>#{n}</foreach>"
        + "</script>")
    List<DbTable> findByDatabaseIdAndNames(@Param("databaseId") String databaseId,
                                           @Param("names") List<String> names);
}
