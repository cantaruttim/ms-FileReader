package br.com.cantarutti.file_reader.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DataSourceManager {
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    public DataSourceManager(AppProperties appProperties) {
        Map<String, AppProperties.DatasourceProperties> configs = appProperties.getDatasources();
        for (Map.Entry<String, AppProperties.DatasourceProperties> entry : configs.entrySet()) {
            String key = entry.getKey();
            AppProperties.DatasourceProperties props = entry.getValue();

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setDriverClassName(props.getDriverClassName());
            hikariConfig.setJdbcUrl(props.getJdbcUrl());
            hikariConfig.setUsername(props.getUsername());
            hikariConfig.setPassword(props.getPassword());
            hikariConfig.setMaximumPoolSize(props.getMaximumPoolSize());
            hikariConfig.setMinimumIdle(props.getMinimumIdle());
            hikariConfig.setPoolName("HikariPool-" + key);

            dataSources.put(key, new HikariDataSource(hikariConfig));
        }
    }

    public Connection getConnection(String databaseKey) throws SQLException {
        DataSource ds = dataSources.get(databaseKey);
        if (ds == null) {
            throw new IllegalArgumentException("DataSource não encontrado para: " + databaseKey);
        }
        return ds.getConnection();
    }
}
