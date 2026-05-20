package br.com.cantarutti.file_reader.service;

import br.com.cantarutti.file_reader.config.DataSourceManager;
import br.com.cantarutti.file_reader.model.ExcelToRelationalIncrementalImporter;
import br.com.cantarutti.file_reader.records.IngestionMetadataRecord;

import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
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
                           String fileName,
                           String sheetName) throws Exception {

        // Converter InputStream para bytes para poder reusar
        byte[] fileBytes = inputStream.readAllBytes();

        // Contar total de linhas
        int totalRows = countRows(fileBytes, sheetName);

        // Obter schema das colunas
        List<Map<String, String>> columnsSchema = getColumnsSchema(fileBytes, columnMappings, sheetName);

        Connection connection = null;
        try {
            connection = dataSourceManager.getConnection(targetDatabase);

            boolean tableCreated = false;
            if (createTable) {
                DynamicTableCreator.createTableIfNotExists(
                        new ByteArrayInputStream(fileBytes), tableName, connection, columnMappings, sheetName);
                tableCreated = true;
            }

            int newRows = ExcelToRelationalIncrementalImporter.importSheetIncremental(
                    new ByteArrayInputStream(fileBytes),
                    tableName, connection, columnMappings, uniqueKeys, batchSize, sheetName
            );

            // Registrar metadados de SUCESSO
            IngestionMetadataRecord metadata = new IngestionMetadataRecord(
                    null, targetDatabase, tableName, fileName,
                    tableCreated, columnsSchema, uniqueKeys,
                    totalRows, newRows, totalRows - newRows,
                    LocalDateTime.now(), "SUCCESS", null
            );

            metadataService.registerImport(metadata);
            return newRows;

        } catch (Exception e) {
            // Registrar metadados de ERRO
            IngestionMetadataRecord errorMetadata = new IngestionMetadataRecord(
                    null, targetDatabase, tableName, fileName,
                    false, null, null, 0, 0, 0,
                    LocalDateTime.now(), "ERROR", e.getMessage()
            );

            try {
                metadataService.registerImport(errorMetadata);
            } catch (Exception ex) {
                System.err.println("Erro ao registrar metadados de falha: " + ex.getMessage());
            }
            throw e;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    System.err.println("Erro ao fechar conexão: " + e.getMessage());
                }
            }
        }
    }

    private int countRows(byte[] fileBytes, String sheetName) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = getSheet(workbook, sheetName);
            return sheet.getLastRowNum();
        }
    }

    private List<Map<String, String>> getColumnsSchema(byte[] fileBytes,
                                                        Map<String, String> columnMappings,
                                                        String sheetName) throws Exception {
        List<Map<String, String>> schema = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = getSheet(workbook, sheetName);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                return schema;
            }

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
        }
        return schema;
    }

    private Sheet getSheet(Workbook workbook, String sheetName) {
        if (sheetName == null || sheetName.equals("0") || sheetName.isEmpty()) {
            return workbook.getSheetAt(0);
        }
        try {
            // Tenta como índice numérico
            int index = Integer.parseInt(sheetName);
            return workbook.getSheetAt(index);
        } catch (NumberFormatException e) {
            // Tenta como nome da aba
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Aba não encontrada: " + sheetName);
            }
            return sheet;
        }
    }
}