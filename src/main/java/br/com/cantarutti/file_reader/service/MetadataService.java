package br.com.cantarutti.file_reader.service;

import br.com.cantarutti.file_reader.config.DataSourceManager;
import br.com.cantarutti.file_reader.records.IngestionMetadataRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MetadataService {

    private final DataSourceManager dataSourceManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetadataService(DataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
    }

    /**
     * Inicializa a tabela de metadados no banco de controle
     */
    public void initializeMetadataTable() throws Exception {
        Connection conn = null;
        try {
            conn = dataSourceManager.getConnection("ms_upload_arquivos");
            MetadataTableCreator.createMetadataTableIfNotExists(conn);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception e) {}
            }
        }
    }

    /**
     * Registra uma importação na tabela de metadados
     */
    public Long registerImport(IngestionMetadataRecord metadata) throws Exception {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dataSourceManager.getConnection("ms_upload_arquivos");
            
            String sql = """
                INSERT INTO ingestion_metadata 
                (target_database, table_name, file_name, created_table, columns_schema, 
                 unique_keys, total_rows_in_file, new_rows_inserted, skipped_rows, 
                 import_timestamp, status, error_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, metadata.targetDatabase());
            ps.setString(2, metadata.tableName());
            ps.setString(3, metadata.fileName());
            ps.setBoolean(4, metadata.createdTable());
            ps.setString(5, metadata.columnsSchema() != null ? objectMapper.writeValueAsString(metadata.columnsSchema()) : null);
            ps.setString(6, metadata.uniqueKeys() != null ? objectMapper.writeValueAsString(metadata.uniqueKeys()) : null);
            ps.setInt(7, metadata.totalRowsInFile());
            ps.setInt(8, metadata.newRowsInserted());
            ps.setInt(9, metadata.skippedRows());
            ps.setTimestamp(10, metadata.importTimestamp() != null ? 
                Timestamp.valueOf(metadata.importTimestamp()) : 
                Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(11, metadata.status() != null ? metadata.status() : "SUCCESS");
            ps.setString(12, metadata.errorMessage());
            
            ps.executeUpdate();
            
            rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
            return null;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception e) {}
            if (ps != null) try { ps.close(); } catch (Exception e) {}
            if (conn != null) try { conn.close(); } catch (Exception e) {}
        }
    }

    /**
     * Consulta histórico de importações
     */
    public List<IngestionMetadataRecord> getImportHistory(int limit) throws Exception {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dataSourceManager.getConnection("ms_upload_arquivos");
            String sql = "SELECT * FROM ingestion_metadata ORDER BY import_timestamp DESC LIMIT ?";
            List<IngestionMetadataRecord> results = new ArrayList<>();
            
            ps = conn.prepareStatement(sql);
            ps.setInt(1, limit);
            rs = ps.executeQuery();
            
            while (rs.next()) {
                IngestionMetadataRecord record = new IngestionMetadataRecord(
                    rs.getLong("id"),
                    rs.getString("target_database"),
                    rs.getString("table_name"),
                    rs.getString("file_name"),
                    rs.getBoolean("created_table"),
                    null, // columnsSchema
                    null, // uniqueKeys
                    rs.getInt("total_rows_in_file"),
                    rs.getInt("new_rows_inserted"),
                    rs.getInt("skipped_rows"),
                    rs.getTimestamp("import_timestamp") != null ? 
                        rs.getTimestamp("import_timestamp").toLocalDateTime() : null,
                    rs.getString("status"),
                    rs.getString("error_message")
                );
                results.add(record);
            }
            return results;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception e) {}
            if (ps != null) try { ps.close(); } catch (Exception e) {}
            if (conn != null) try { conn.close(); } catch (Exception e) {}
        }
    }
}