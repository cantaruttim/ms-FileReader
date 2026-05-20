package br.com.cantarutti.file_reader.service;

import java.sql.Connection;
import java.sql.Statement;

public class MetadataTableCreator {

    public static void createMetadataTableIfNotExists(Connection connection) throws Exception {
        String ddl = """
            CREATE TABLE IF NOT EXISTS ingestion_metadata (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                target_database VARCHAR(100) NOT NULL,
                table_name VARCHAR(255) NOT NULL,
                file_name VARCHAR(255),
                created_table BOOLEAN DEFAULT FALSE,
                columns_schema JSON,
                unique_keys VARCHAR(500),
                total_rows_in_file INT,
                new_rows_inserted INT,
                skipped_rows INT,
                import_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                status VARCHAR(50) DEFAULT 'SUCCESS',
                error_message TEXT
            )
        """;
        
        Statement stmt = null;
        try {
            stmt = connection.createStatement();
            stmt.execute(ddl);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (Exception e) {
                    // log silencioso
                }
            }
        }
    }
}