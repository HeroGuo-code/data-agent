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

/** 用户表 users CRUD：管理员/运营/客服坐席账号，支撑数据权限讲解。 */
@RestController
@RequestMapping("/mall/users")
public class UserController {

    private final JdbcTemplate jdbc;
    private static final Set<String> ALLOWED = Set.of(
        "username", "phone", "role", "shop_id", "status", "created_at");

    public UserController(MallDataSourceConfig cfg) {
        this.jdbc = cfg.mallJdbcTemplate();
    }

    /** 业务表写入后精准淘汰读取该表的 SQL 缓存。 */

    @GetMapping
    public Page<Map<String,Object>> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String username,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) String status) {
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (username != null && !username.isBlank()) { where.add("username LIKE ?"); args.add("%" + username + "%"); }
        if (role != null && !role.isBlank()) { where.add("role = ?"); args.add(role); }
        if (status != null && !status.isBlank()) { where.add("status = ?"); args.add(status); }
        String w = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        long total = jdbc.queryForObject("SELECT count(*) FROM users" + w, Long.class, args.toArray());
        int offset = (page - 1) * size;
        args.add(size); args.add(offset);
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT * FROM users" + w + " ORDER BY user_id ASC LIMIT ? OFFSET ?", args.toArray());
        return new Page<>(total, rows);
    }

    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable int id) {
        return jdbc.queryForMap("SELECT * FROM users WHERE user_id = ?", id);
    }

    @PostMapping
    public Map<String,Object> create(@RequestBody Map<String,Object> body) {
        List<String> cols = new ArrayList<>(); cols.add("username");
        List<Object> vals = new ArrayList<>(); vals.add(body.getOrDefault("username",""));
        for (String k : List.of("phone","role","shop_id","status","created_at")) {
            Object v = body.get(k);
            if (v != null) { cols.add(k); vals.add(v); }
        }
        String sql = "INSERT INTO users (" + String.join(",", cols) + ") VALUES ("
            + String.join(",", java.util.Collections.nCopies(cols.size(), "?")) + ")";
        Integer id = jdbc.queryForObject(sql + " RETURNING user_id", Integer.class, vals.toArray());
        return Map.of("user_id", id);
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
        jdbc.update("UPDATE users SET " + String.join(",", sets) + " WHERE user_id = ?", vals.toArray());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id) {
        jdbc.update("DELETE FROM users WHERE user_id = ?", id);
    }
}
