package io.github.qifan777.server.shared.datasource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.sqlite.SQLiteDataSource;

/** 按 databaseId 提供 SQLite 数据源。 */
public final class SqliteSchemaDataSourceProvider implements SchemaDataSourceProvider {
    public static final SqliteSchemaDataSourceProvider INSTANCE = new SqliteSchemaDataSourceProvider();

    private final ConcurrentHashMap<String, DataSource> cache = new ConcurrentHashMap<>();

    private SqliteSchemaDataSourceProvider() {}

    @Override
    public DataSource get(String databaseId) {
        return cache.computeIfAbsent(databaseId, id -> {
            SQLiteDataSource ds = new SQLiteDataSource();
            Path dataFile = DataFileLocator.DATA_DIR.resolve(id + ".sqlite").toAbsolutePath();
            if (Files.exists(dataFile)) {
                ds.setUrl("jdbc:sqlite:" + dataFile.toString());
            } else {
                ds.setUrl("jdbc:sqlite::resource:dev_20240627/dev_databases/" + id + "/" + id + ".sqlite");
            }
            return ds;
        });
    }
}
