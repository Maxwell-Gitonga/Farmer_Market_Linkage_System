package ke.ac.strathmore.fmls.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * XAMPP's MariaDB may create the database with the latin1 character set. This converts the database and its
 * tables to utf8mb4 once (before any data is loaded) so names, messages and symbols are stored correctly.
 * Runs before {@link DataSeeder}; does nothing on other databases (e.g. H2 in tests).
 */
@Component
@Order(0)
public class DatabaseCharsetInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCharsetInitializer.class);
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public DatabaseCharsetInitializer(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            if (!product.contains("mysql") && !product.contains("mariadb")) return;
        } catch (Exception e) {
            return;
        }
        try {
            String charset = jdbc.queryForObject("SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA "
                    + "WHERE SCHEMA_NAME = DATABASE()", String.class);
            if (charset == null || !charset.startsWith("utf8mb4")) {
                jdbc.execute("ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                log.info("Database character set changed from {} to utf8mb4", charset);
            }
            List<String> tables = jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = "
                    + "DATABASE() AND TABLE_TYPE = 'BASE TABLE' AND TABLE_COLLATION NOT LIKE 'utf8mb4%'", String.class);
            for (String t : tables) {
                jdbc.execute("ALTER TABLE `" + t + "` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            if (!tables.isEmpty()) log.info("Converted {} table(s) to utf8mb4", tables.size());
        } catch (Exception e) {
            log.warn("Could not verify the database character set: {}", e.getMessage());
        }
    }
}
