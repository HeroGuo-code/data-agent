package io.github.qifan777.server.mall.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.github.qifan777.server.mall.config.MallDataSourceConfig;
import org.springframework.jdbc.core.JdbcTemplate;
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
@RequestMapping("/mall/shops")
public class ShopController {

    private final JdbcTemplate jdbc;
    private static final Set<String> ALLOWED = Set.of(
        "shop_name","owner_id","category","created_at");

    public ShopController(MallDataSourceConfig cfg) {
        this.jdbc = cfg.mallJdbcTemplate();
    }

    /** 业务表写入后精准淘汰读取该表的 SQL 缓存。 */

    @GetMapping
    public Page<Map<String,Object>> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String shopName,
                                         @RequestParam(required = false) String shop_name,
                                         @RequestParam(required = false) String category) {
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        String name = shopName != null ? shopName : shop_name;
        if (name != null && !name.isBlank()) { where.add("shop_name LIKE ?"); args.add("%" + name + "%"); }
        if (category != null && !category.isBlank()) { where.add("category = ?"); args.add(category); }
        String w = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        long total = jdbc.queryForObject("SELECT count(*) FROM shops" + w, Long.class, args.toArray());
        int offset = (page - 1) * size;
        args.add(size); args.add(offset);
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT * FROM shops" + w + " ORDER BY shop_id DESC LIMIT ? OFFSET ?", args.toArray());
        return new Page<>(total, rows);
    }

    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable int id) {
        return jdbc.queryForMap("SELECT * FROM shops WHERE shop_id = ?", id);
    }

    @PostMapping
    public Map<String,Object> create(@RequestBody Map<String,Object> body) {
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (String k : ALLOWED) {
            Object v = body.get(k);
            if (v != null) { cols.add(k); vals.add(v); }
        }
        if (cols.isEmpty()) throw new IllegalArgumentException("无可写入字段");
        String sql = "INSERT INTO shops (" + String.join(",", cols) + ") VALUES ("
            + String.join(",", java.util.Collections.nCopies(cols.size(), "?")) + ")";
        Integer id = jdbc.queryForObject(sql + " RETURNING " + "shop_id", Integer.class, vals.toArray());
        return Map.of("shop_id", id);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable int id, @RequestBody Map<String,Object> body) {
        List<String> sets = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (String k : ALLOWED) {
            if (body.containsKey(k)) { sets.add(k + " = ?"); vals.add(body.get(k)); }
        }
        if (sets.isEmpty()) return;
        vals.add(id);
        jdbc.update("UPDATE shops SET " + String.join(",", sets) + " WHERE shop_id = ?", vals.toArray());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id) {
        jdbc.update("DELETE FROM shops WHERE shop_id = ?", id);
    }
}
