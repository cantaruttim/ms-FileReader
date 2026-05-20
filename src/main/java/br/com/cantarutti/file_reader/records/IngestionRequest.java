package br.com.cantarutti.file_reader.records;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class IngestionRequest {
    private String targetDatabase;
    private String tableName;
    private boolean createTable = true;
    private Map<String, String> columnMappings;
    private List<String> uniqueKeys;
    private int batchSize = 500;
    private String sheetName;

    // Getters e Setters (obrigatórios)
    public String getTargetDatabase() { return targetDatabase; }
    public void setTargetDatabase(String targetDatabase) { this.targetDatabase = targetDatabase; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public boolean isCreateTable() { return createTable; }
    public void setCreateTable(boolean createTable) { this.createTable = createTable; }

    public Map<String, String> getColumnMappings() { return columnMappings; }
    public void setColumnMappings(Map<String, String> columnMappings) { this.columnMappings = columnMappings; }

    public List<String> getUniqueKeys() { return uniqueKeys; }
    public void setUniqueKeys(List<String> uniqueKeys) { this.uniqueKeys = uniqueKeys; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }

    public static IngestionRequest fromJson(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, IngestionRequest.class);
    }

    public void validate() {
        if (targetDatabase == null || targetDatabase.isBlank())
            throw new IllegalArgumentException("targetDatabase é obrigatório");
        if (tableName == null || tableName.isBlank())
            throw new IllegalArgumentException("tableName é obrigatório");
    }
}
