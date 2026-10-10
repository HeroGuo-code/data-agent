package io.github.qifan777.server.dataset.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.qifan777.server.dataset.knowledge.domain.QuestionKnowledge;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface QuestionKnowledgeMapper extends BaseMapper<QuestionKnowledge> {

    @Select("SELECT * FROM question_knowledge WHERE database_id = #{databaseId}")
    List<QuestionKnowledge> findByDatabaseId(@Param("databaseId") String databaseId);

    @Select("<script>SELECT * FROM question_knowledge WHERE id IN "
        + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}::uuid</foreach>"
        + "</script>")
    List<QuestionKnowledge> findByIds(@Param("ids") List<String> ids);
}
