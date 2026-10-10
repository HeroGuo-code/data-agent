package io.github.qifan777.server.kb;

import io.github.qifan777.server.shared.datasource.DataFileLocator;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteDataSource;

@Component
public class KnowledgeConfig {

    private final DataFileLocator locator;

    public KnowledgeConfig(DataFileLocator locator) { this.locator = locator; }

    public JdbcTemplate knowledgeJdbcTemplate() {
        Path file = locator.locate("dev_20240627", "knowledge_base.sqlite");
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + file.toString());
        return new JdbcTemplate(ds);
    }
}
