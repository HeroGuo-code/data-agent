package io.github.qifan777.server.shared.datasource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

/**
 * 将缺省的演示 SQLite 从 classpath 复制到运行目录 data/ 下，保证可写。
 */
@Component
public class DataFileLocator {

    public static final Path DATA_DIR = Paths.get(System.getProperty("user.dir"), "data");

    /** 返回可写的文件路径：存在则直接用，否则从 classpath 复制。 */
    public synchronized Path locate(String classpathRoot, String fileName) {
        Path target = DATA_DIR.resolve(fileName).toAbsolutePath();
        if (Files.exists(target)) {
            return target;
        }
        try {
            Files.createDirectories(target.getParent());
            String resource = classpathRoot + "/" + fileName;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Classpath resource not found: " + resource);
                }
                Files.copy(in, target);
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare sqlite file: " + target, e);
        }
    }
}
