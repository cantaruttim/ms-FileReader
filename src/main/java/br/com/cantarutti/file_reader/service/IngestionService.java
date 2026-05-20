package br.com.cantarutti.file_reader.service;

import br.com.cantarutti.file_reader.config.DataSourceManager;
import br.com.cantarutti.file_reader.model.ExcelToRelationalIncrementalImporter;
import br.com.cantarutti.file_reader.records.IngestionMetadataRecord;

import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class IngestionService {

    private final DataSourceManager dataSourceManager;
    private final MetadataService metadataService;

    public IngestionService(DataSourceManager dataSourceManager, MetadataService metadataService) {
        this.dataSourceManager = dataSourceManager;
        this.metadataService = metadataService;
    }

    public int processFile(InputStream inputStream,
                           String targetDatabase,
                           String tableName,
                           boolean createTable,
                           Map<String, String> columnMappings,
                           List<String> uniqueKeys,
                           int batchSize,
                           String fileName) throws Exception {

        if (!inputStream.markSupported()) {
            throw new IllegalArgumentException("InputStream deve suportar mark/reset");
        }
        inputStream.mark(Integer.MAX_VALUE);

        // Contar total de linhas no arquivo
        int totalRows = countRows(inputStream);
        inputStream.reset();

        // Obter schema das colunas
        List<Map<String, String>> columnsSchema = getColumnsSchema(inputStream, columnMappings);
        inputStream.reset();

        Connection connection = null;
        try {
            connection = dataSourceManager.getConnection(targetDatabase);
            
            boolean tableCreated = false;
            if (createTable) {
                DynamicTableCreator.createTableIfNotExists(inputStream, tableName, connection, columnMappings);
                tableCreated = true;
                inputStream.reset();
            }

            int newRows = ExcelToRelationalIncrementalImporter.importSheetIncremental(
                    inputStream, tableName, connection, columnMappings, uniqueKeys, batchSize
            );

            // Registrar metadados de SUCESSO usando o construtor do Record
            IngestionMetadataRecord metadata = new IngestionMetadataRecord(
                    null,                    // id (auto-generated)
                    targetDatabase,
                    tableName,
                    fileName,
                    tableCreated,
                    columnsSchema,
                    uniqueKeys,
                    totalRows,
                    newRows,
                    totalRows - newRows,     // skippedRows
                    LocalDateTime.now(),
                    "SUCCESS",
                    null                     // errorMessage
            );

            metadataService.registerImport(metadata);

            return newRows;
            
        } catch (Exception e) {
            // Registrar metadados de ERRO usando o construtor do Record
            IngestionMetadataRecord errorMetadata = new IngestionMetadataRecord(
                    null,                    // id
                    targetDatabase,
                    tableName,
                    fileName,
                    false,                   // createdTable
                    null,                    // columnsSchema
                    null,                    // uniqueKeys
                    0,                       // totalRowsInFile
                    0,                       // newRowsInserted
                    0,                       // skippedRows
                    LocalDateTime.now(),
                    "ERROR",
                    e.getMessage()
            );
            
            try {
                metadataService.registerImport(errorMetadata);
            } catch (Exception ex) {
                // Log silencioso do erro ao registrar metadados
                System.err.println("Erro ao registrar metadados de falha: " + ex.getMessage());
            }
            throw e;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // Log silencioso
                }
            }
        }
    }

    private int countRows(InputStream inputStream) throws Exception {
        Workbook workbook = null;
        try {
            workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            return sheet.getLastRowNum(); // desconta o cabeçalho
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    private List<Map<String, String>> getColumnsSchema(InputStream inputStream, 
                                                        Map<String, String> columnMappings) throws Exception {
        List<Map<String, String>> schema = new ArrayList<>();
        Workbook workbook = null;
        try {
            workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            for (Cell cell : headerRow) {
                String excelHeader = cell.getStringCellValue().trim();
                String dbColumn = (columnMappings != null && columnMappings.containsKey(excelHeader))
                        ? columnMappings.get(excelHeader)
                        : excelHeader;

                Map<String, String> colInfo = new HashMap<>();
                colInfo.put("excel_header", excelHeader);
                colInfo.put("db_column", dbColumn);
                schema.add(colInfo);
            }
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
        return schema;
    }
}