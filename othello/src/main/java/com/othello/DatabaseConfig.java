package com.othello;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Value("${DATABASE_URL}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        // postgresql:// → jdbc:postgresql:// に変換
        String jdbcUrl = databaseUrl.startsWith("jdbc:")
            ? databaseUrl
            : "jdbc:" + databaseUrl;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }

    @Bean
    public ApplicationRunner initDb(JdbcTemplate jdbc) {
        return args -> {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ranking (
                    id         SERIAL PRIMARY KEY,
                    name       VARCHAR(50)  NOT NULL,
                    score      INTEGER      NOT NULL,
                    difficulty VARCHAR(10)  NOT NULL,
                    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
            """);
        };
    }
}
