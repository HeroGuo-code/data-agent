package io.github.qifan777.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists the LLM + Embedding model profiles and seeds defaults from application.yml on first use. */
@Service
public class ModelConfigService {

    private final JdbcTemplate jdbc;

    @Value("${spring.ai.openai.base-url:}")
    private String llmBaseUrl;
    @Value("${spring.ai.openai.api-key:}")
    private String llmApiKey;
    @Value("${spring.ai.openai.chat.options.model:}")
    private String llmModel;
    @Value("${spring.ai.openai.embedding.base-url:}")
    private String embBaseUrl;
    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embModel;

    private final RowMapper<ModelProfile> MAPPER = (rs, i) -> {
        ModelProfile p = new ModelProfile();
        p.setKind(rs.getString("kind"));
        p.setName(rs.getString("name"));
        p.setProvider(rs.getString("provider"));
        p.setBaseUrl(rs.getString("base_url"));
        p.setApiKey(rs.getString("api_key"));
        p.setModelName(rs.getString("model_name"));
        Object d = rs.getObject("dimensions");
        p.setDimensions(d == null ? null : ((Number) d).intValue());
        p.setEnabled(rs.getBoolean("enabled"));
        return p;
    };

    public ModelConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS model_profile ("
            + "kind varchar(32) PRIMARY KEY, "
            + "name varchar(255) NOT NULL DEFAULT '', "
            + "provider varchar(255) NOT NULL DEFAULT '', "
            + "base_url varchar(512) NOT NULL DEFAULT '', "
            + "api_key varchar(512) NOT NULL DEFAULT '', "
            + "model_name varchar(255) NOT NULL DEFAULT '', "
            + "dimensions int, "
            + "enabled boolean NOT NULL DEFAULT true, "
            + "updated_at varchar(64))");
    }

    public synchronized void seedIfEmpty() {
        ensureSchema();
        int count = jdbc.queryForObject("SELECT count(*) FROM model_profile", Integer.class);
        if (count > 0) return;
        jdbc.update("INSERT INTO model_profile(kind,name,provider,base_url,api_key,model_name,dimensions,enabled,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
            "llm", "LLM ????", "OpenAI ??", nz(llmBaseUrl), nz(llmApiKey), nz(llmModel), null, true, now());
        jdbc.update("INSERT INTO model_profile(kind,name,provider,base_url,api_key,model_name,dimensions,enabled,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
            "embedding", "??????", "OpenAI ??", nz(embBaseUrl), "unused", nz(embModel), 512, true, now());
    }

    public List<ModelProfile> loadAll() {
        seedIfEmpty();
        return new ArrayList<>(jdbc.query("SELECT * FROM model_profile ORDER BY kind", MAPPER));
    }

    public ModelProfile load(String kind) {
        for (ModelProfile p : loadAll()) {
            if (kind.equals(p.getKind())) return p;
        }
        return null;
    }

    @Transactional
    public ModelProfile save(ModelProfile p) {
        ensureSchema();
        String kind = p.getKind() == null ? "" : p.getKind();
        jdbc.update("INSERT INTO model_profile(kind,name,provider,base_url,api_key,model_name,dimensions,enabled,updated_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT(kind) DO UPDATE SET name=EXCLUDED.name, provider=EXCLUDED.provider, "
                + "base_url=EXCLUDED.base_url, api_key=EXCLUDED.api_key, model_name=EXCLUDED.model_name, "
                + "dimensions=EXCLUDED.dimensions, enabled=EXCLUDED.enabled, updated_at=EXCLUDED.updated_at",
            kind, nz(p.getName()), nz(p.getProvider()), nz(p.getBaseUrl()), nz(p.getApiKey()),
            nz(p.getModelName()), p.getDimensions(), p.isEnabled(), now());
        return load(kind);
    }

    public Map<String, Object> snapshot() {
        List<ModelProfile> all = loadAll();
        ModelProfile llm = all.stream().filter(x -> "llm".equals(x.getKind())).findFirst().orElse(null);
        ModelProfile emb = all.stream().filter(x -> "embedding".equals(x.getKind())).findFirst().orElse(null);
        return Map.of("llm", llm == null ? new ModelProfile() : llm,
            "embedding", emb == null ? new ModelProfile() : emb);
    }

    private String nz(String s) { return s == null ? "" : s.trim(); }
    private String now() { return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
}
