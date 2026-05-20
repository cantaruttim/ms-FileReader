package br.com.cantarutti.file_reader.records;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record IngestionMetadataRecord(
    Long id,
    String targetDatabase,
    String tableName,
    String fileName,
    boolean createdTable,
    List<Map<String, String>> columnsSchema,
    List<String> uniqueKeys,
    int totalRowsInFile,
    int newRowsInserted,
    int skippedRows,
    LocalDateTime importTimestamp,
    String status,
    String errorMessage
) {
    // Construtor compacto para valores default
    public IngestionMetadataRecord {
        if (importTimestamp == null) {
            importTimestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = "SUCCESS";
        }
    }

    // Construtor vazio para Jackson
    public IngestionMetadataRecord() {
        this(null, null, null, null, false, null, null, 0, 0, 0, null, null, null);
    }

    // Builder manual
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String targetDatabase;
        private String tableName;
        private String fileName;
        private boolean createdTable;
        private List<Map<String, String>> columnsSchema;
        private List<String> uniqueKeys;
        private int totalRowsInFile;
        private int newRowsInserted;
        private int skippedRows;
        private LocalDateTime importTimestamp;
        private String status;
        private String errorMessage;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder targetDatabase(String targetDatabase) { this.targetDatabase = targetDatabase; return this; }
        public Builder tableName(String tableName) { this.tableName = tableName; return this; }
        public Builder fileName(String fileName) { this.fileName = fileName; return this; }
        public Builder createdTable(boolean createdTable) { this.createdTable = createdTable; return this; }
        public Builder columnsSchema(List<Map<String, String>> columnsSchema) { this.columnsSchema = columnsSchema; return this; }
        public Builder uniqueKeys(List<String> uniqueKeys) { this.uniqueKeys = uniqueKeys; return this; }
        public Builder totalRowsInFile(int totalRowsInFile) { this.totalRowsInFile = totalRowsInFile; return this; }
        public Builder newRowsInserted(int newRowsInserted) { this.newRowsInserted = newRowsInserted; return this; }
        public Builder skippedRows(int skippedRows) { this.skippedRows = skippedRows; return this; }
        public Builder importTimestamp(LocalDateTime importTimestamp) { this.importTimestamp = importTimestamp; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public IngestionMetadataRecord build() {
            return new IngestionMetadataRecord(
                id, targetDatabase, tableName, fileName, createdTable,
                columnsSchema, uniqueKeys, totalRowsInFile, newRowsInserted,
                skippedRows, importTimestamp, status, errorMessage
            );
        }
    }
}