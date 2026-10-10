package io.github.qifan777.server.agentconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 智能助手配置的持久化：单行 JSON 存于 agent_config 表（数据库自动建表）。 */
@Service
public class AgentConfigService {
    private static final Logger log = LoggerFactory.getLogger(AgentConfigService.class);
    private static final long CONFIG_ID = 1L;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AgentConfigService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @PostConstruct
    public void ensureTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS agent_config (id BIGINT PRIMARY KEY, config_json TEXT NOT NULL)");
    }

    public AgentConfig getConfig() {
        try {
            List<String> rows = jdbc.query("SELECT config_json FROM agent_config WHERE id = ?",
                (rs, i) -> rs.getString(1), CONFIG_ID);
            if (rows != null && !rows.isEmpty()) {
                return mapper.readValue(rows.get(0), AgentConfig.class);
            }
        } catch (Exception e) {
            log.warn("读取 agent_config 失败，使用默认配置: {}", e.getMessage());
        }
        return new AgentConfig();
    }

    public AgentConfig saveConfig(AgentConfig cfg) {
        AgentConfig clean = new AgentConfig();
        clean.setOpeningMessage(cfg.getOpeningMessage());
        clean.setPersonaPrompt(cfg.getPersonaPrompt());
        clean.setPresets(cfg.getPresets());
        try {
            String json = mapper.writeValueAsString(clean);
            jdbc.update("INSERT INTO agent_config (id, config_json) VALUES (?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET config_json = EXCLUDED.config_json", CONFIG_ID, json);
        } catch (Exception e) {
            throw new RuntimeException("保存智能助手配置失败: " + e.getMessage(), e);
        }
        return clean;
    }
}
