package io.github.qifan777.server.asset;

import io.github.qifan777.server.dataset.scheme.EcommerceSchemaInitializer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据资产中心：把知识库/业务表/Schema/表关系这些元数据整理成可讲解的模块，
 * 并提供“重新入库召回”能力（重建元数据 + 向量化）。
 */
@RestController
@RequestMapping("/asset")
public class SourceAssetController {

    private static final String DB = "ecommerce_ops";
    private final JdbcTemplate jdbc;
    private final EcommerceSchemaInitializer initializer;

    public SourceAssetController(JdbcTemplate jdbc, EcommerceSchemaInitializer initializer) {
        this.jdbc = jdbc;
        this.initializer = initializer;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("databaseId", DB);
        m.put("tableCount", jdbc.queryForObject("SELECT count(*) FROM db_table WHERE database_id=?", Long.class, DB));
        m.put("columnCount", jdbc.queryForObject("SELECT count(*) FROM db_column WHERE table_id IN "
            + "(SELECT id FROM db_table WHERE database_id=?)", Long.class, DB));
        m.put("relationCount", jdbc.queryForObject("SELECT count(*) FROM db_foreign_key WHERE source_column_id IN "
            + "(SELECT id FROM db_column WHERE table_id IN (SELECT id FROM db_table WHERE database_id=?))", Long.class, DB));
        m.put("evidenceCount", jdbc.queryForObject("SELECT count(*) FROM question_knowledge WHERE database_id=?", Long.class, DB));
        m.put("glossaryCount", jdbc.queryForObject("SELECT count(*) FROM glossary_knowledge WHERE database_id=?", Long.class, DB));
        return m;
    }

    /** 业务表 + Schema 模块：每个表的字段元数据。 */
    @GetMapping("/tables")
    public List<Map<String, Object>> tables() {
        List<Map<String, Object>> tables = jdbc.queryForList(
            "SELECT id, name, description FROM db_table WHERE database_id=? ORDER BY name", DB);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> t : tables) {
            Object tid = t.get("id");
            List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT name, type, description, is_primary_key FROM db_column WHERE table_id=?::uuid ORDER BY name",
                String.valueOf(tid));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", t.get("name"));
            entry.put("description", t.get("description"));
            entry.put("columns", cols);
            result.add(entry);
        }
        return result;
    }

    /** 表关系模块：外键连接关系。 */
    @GetMapping("/relations")
    public List<Map<String, Object>> relations() {
        return jdbc.queryForList(
            "SELECT st.name AS src_table, sc.name AS src_col, tt.name AS tgt_table, tc.name AS tgt_col "
            + "FROM db_foreign_key fk "
            + "JOIN db_column sc ON sc.id = fk.source_column_id "
            + "JOIN db_column tc ON tc.id = fk.target_column_id "
            + "JOIN db_table st ON st.id = sc.table_id "
            + "JOIN db_table tt ON tt.id = tc.table_id "
            + "WHERE st.database_id = ? ORDER BY st.name, sc.name", DB);
    }

    /** 知识库模块：问答证据 + 业务词表。 */
    @GetMapping("/evidence")
    public Map<String, Object> evidence() {
        List<Map<String, Object>> questions = jdbc.queryForList(
            "SELECT id, question, answer FROM question_knowledge WHERE database_id=? ORDER BY question", DB);
        List<Map<String, Object>> glossary = jdbc.queryForList(
            "SELECT id, term, description, synonyms FROM glossary_knowledge WHERE database_id=? ORDER BY term", DB);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("questions", questions);
        m.put("glossary", glossary);
        return m;
    }

    /** 重新入库召回：清空 ecommerce_ops 的元数据与向量后重建。 */
    @PostMapping("/reindex")
    public Map<String, String> reindex() {
        try {
            initializer.reindex();
            return Map.of("status", "ok", "message", "重新入库召回完成");
        } catch (Exception e) {
            return Map.of("status", "error", "message", String.valueOf(e.getMessage()));
        }
    }
}
