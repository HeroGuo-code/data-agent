package io.github.qifan777.server.mall.config;

import io.github.qifan777.server.shared.datasource.DataFileLocator;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteDataSource;

@Component
public class MallDataSourceConfig {

    private final DataFileLocator locator;

    public MallDataSourceConfig(DataFileLocator locator) { this.locator = locator; }

    public JdbcTemplate mallJdbcTemplate() {
        Path file = locator.locate("dev_20240627/dev_databases/ecommerce_ops", "ecommerce_ops.sqlite");
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + file.toString());
        return new JdbcTemplate(ds);
    }
}
