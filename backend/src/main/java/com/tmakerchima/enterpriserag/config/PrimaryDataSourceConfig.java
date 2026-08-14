package com.tmakerchima.enterpriserag.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/** Primary database for corpus, ACL, feedback and interaction data. */
@Configuration(proxyBeanMethods = false)
public class PrimaryDataSourceConfig {

    @Bean(name = {"dataSource", "primaryDataSource"}, destroyMethod = "close")
    @Primary
    public HikariDataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.hikari.maximum-pool-size:10}") int maximumPoolSize,
            @Value("${spring.datasource.hikari.minimum-idle:1}") int minimumIdle,
            @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeoutMs) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("enterprise-rag-primary-pool");
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(Math.max(1, maximumPoolSize));
        dataSource.setMinimumIdle(Math.min(Math.max(0, minimumIdle), dataSource.getMaximumPoolSize()));
        dataSource.setConnectionTimeout(Math.max(250, connectionTimeoutMs));
        return dataSource;
    }

    @Bean(name = {"jdbcTemplate", "primaryJdbcTemplate"})
    @Primary
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return new JdbcTemplate(primaryDataSource);
    }
}
