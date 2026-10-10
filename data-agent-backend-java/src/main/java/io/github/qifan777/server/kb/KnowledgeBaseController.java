package io.github.qifan777.server.kb;

import org.springframework.jdbc.core.JdbcTemplate;

import io.github.qifan777.server.mall.controller.Page;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kb")
public class KnowledgeBaseController {

    private final JdbcTemplate jdbc;

    /** 知识库预分模块。 */
    public static final List<String> MODULES = List.of(
        "问答示例", "业务词表", "指标口径", "权限规则", "运营知识");

    public KnowledgeBaseController(KnowledgeConfig cfg) { this.jdbc = cfg.knowledgeJdbcTemplate(); }

    @GetMapping("/modules")
    public List<String> modules() { return MODULES; }

    @GetMapping("/items")
    public Page<Map<String,Object>> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String module,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String tag) {
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (module != null && !module.isBlank()) { where.add("module = ?"); args.add(module); }
        if (keyword != null && !keyword.isBlank()) {
            where.add("(title LIKE ? OR content LIKE ? OR tags LIKE ?)");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        if (tag != null && !tag.isBlank()) {
            where.add("(','||tags||',' LIKE '%,'||?||',%' OR tags LIKE ?)");
            args.add(tag); args.add("%" + tag + "%");
        }
        String w = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        long total = jdbc.queryForObject("SELECT count(*) FROM kb_items" + w, Long.class, args.toArray());
        int offset = (page - 1) * size;
        args.add(size); args.add(offset);
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT * FROM kb_items" + w + " ORDER BY id DESC LIMIT ? OFFSET ?", args.toArray());
        return new Page<>(total, rows);
    }

    /**
     * 标签模块：汇总知识库中所有标签及其出现次数，供标签管理/筛选使用。
     */
    @GetMapping("/tags")
    public List<Map<String,Object>> tags() {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id, tags FROM kb_items");
        Map<String,Integer> cnt = new LinkedHashMap<>();
        for (Map<String,Object> r : rows) {
            Object t = r.get("tags");
            if (t == null) continue;
            for (String tag : String.valueOf(t).split(",")) {
                String x = tag.trim();
                if (!x.isEmpty()) cnt.merge(x, 1, Integer::sum);
            }
        }
        List<Map<String,Object>> out = new ArrayList<>();
        cnt.forEach((k,v) -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("tag", k);
            m.put("count", v);
            out.add(m);
        });
        out.sort((a,b) -> Integer.compare((Integer)b.get("count"), (Integer)a.get("count")));
        return out;
    }

    /** 重命名标签：把所有条目里的旧标签改为新标签。 */
    @PutMapping("/tags/rename")
    public Map<String,Object> renameTag(@RequestBody Map<String,Object> body) {
        String from = str(body.get("from"));
        String to = str(body.get("to"));
        if (from.isEmpty() || to.isEmpty()) return Map.of("updated", 0);
        return Map.of("updated", renameOrDelete(from, to, false));
    }

    /** 删除标签：从所有条目中移除该标签。 */
    @DeleteMapping("/tags")
    public Map<String,Object> deleteTag(@RequestBody Map<String,Object> body) {
        String tag = str(body.get("tag"));
        if (tag.isEmpty()) return Map.of("updated", 0);
        return Map.of("updated", renameOrDelete(tag, "", true));
    }

    private int renameOrDelete(String from, String to, boolean delete) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id, tags FROM kb_items");
        String now = "2024-06-12 10:00:00";
        int updated = 0;
        for (Map<String,Object> r : rows) {
            Object t = r.get("tags");
            if (t == null) continue;
            List<String> out = new ArrayList<>();
            boolean changed = false;
            for (String tag : String.valueOf(t).split(",")) {
                String x = tag.trim();
                if (x.isEmpty()) continue;
                if (x.equals(from)) {
                    changed = true;
                    if (!delete) out.add(to);
                } else {
                    out.add(x);
                }
            }
            if (changed) {
                jdbc.update("UPDATE kb_items SET tags = ?, updated_at = ? WHERE id = ?",
                    String.join(",", out), now, r.get("id"));
                updated++;
            }
        }
        return updated;
    }

    @GetMapping("/items/{id}")
    public Map<String,Object> get(@PathVariable int id) {
        return jdbc.queryForMap("SELECT * FROM kb_items WHERE id = ?", id);
    }

    @PostMapping("/items")
    public Map<String,Object> create(@RequestBody Map<String,Object> body) {
        String module = str(body.getOrDefault("module", "问答示例"));
        String title = str(body.get("title"));
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
        String tags = body.get("tags") == null ? "" : String.valueOf(body.get("tags"));
        String now = "2024-06-12 10:00:00";
        Integer id = jdbc.queryForObject(
            "INSERT INTO kb_items(module,title,content,tags,created_at,updated_at) VALUES(?,?,?,?,?,?) RETURNING id",
            Integer.class, module, title, content, tags, now, now);
        return Map.of("id", id);
    }

    @PutMapping("/items/{id}")
    public void update(@PathVariable int id, @RequestBody Map<String,Object> body) {
        String module = body.get("module") == null ? null : String.valueOf(body.get("module"));
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        String tags = body.get("tags") == null ? null : String.valueOf(body.get("tags"));
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (module != null) { sets.add("module=?"); args.add(module); }
        if (title != null) { sets.add("title=?"); args.add(title); }
        if (content != null) { sets.add("content=?"); args.add(content); }
        if (tags != null) { sets.add("tags=?"); args.add(tags); }
        if (sets.isEmpty()) return;
        sets.add("updated_at=?");
        args.add("2024-06-12 10:00:00");
        args.add(id);
        jdbc.update("UPDATE kb_items SET " + String.join(",", sets) + " WHERE id=?", args.toArray());
    }

    @DeleteMapping("/items/{id}")
    public void delete(@PathVariable int id) {
        jdbc.update("DELETE FROM kb_items WHERE id=?", id);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}