package br.com.cantarutti.file_reader.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

public class DynamicTableCreator {

    private static final int SAMPLE_ROWS = 50;

    /**
     * Cria a tabela no banco de dados com base nos cabeçalhos do Excel e inferência de tipos.
     *
     * @param inputStream      Stream do arquivo Excel
     * @param tableName        Nome da tabela a ser criada
     * @param connection       Conexão ativa com o banco de dados
     * @param columnMappings   Mapeamento opcional: cabeçalho Excel -> nome da coluna no BD
     * @param sheetName        Nome ou índice da aba ("0" para primeira, ou nome exato)
     */
    public static void createTableIfNotExists(InputStream inputStream,
                                              String tableName,
                                              Connection connection,
                                              Map<String, String> columnMappings,
                                              String sheetName) throws Exception {
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = getSheet(workbook, sheetName);
        Row headerRow = sheet.getRow(0);

        // Mapear cabeçalhos para nomes de colunas no banco
        List<String> dbColumnNames = new ArrayList<>();
        for (Cell cell : headerRow) {
            String excelHeader = cell.getStringCellValue().trim();
            String dbCol = (columnMappings != null && columnMappings.containsKey(excelHeader))
                    ? columnMappings.get(excelHeader)
                    : excelHeader;
            dbColumnNames.add(dbCol);
        }

        // Inferir tipos analisando uma amostra de linhas
        Map<String, String> columnTypes = inferTypes(sheet, dbColumnNames, SAMPLE_ROWS);

        // Montar comando CREATE TABLE
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        ddl.append(tableName).append(" (");
        for (int i = 0; i < dbColumnNames.size(); i++) {
            if (i > 0) ddl.append(", ");
            ddl.append(dbColumnNames.get(i)).append(" ").append(columnTypes.get(dbColumnNames.get(i)));
        }
        ddl.append(")");

        // Executar DDL
        Statement stmt = null;
        try {
            stmt = connection.createStatement();
            stmt.execute(ddl.toString());
        } finally {
            if (stmt != null) {
                try { stmt.close(); } catch (Exception e) {}
            }
        }

        workbook.close();
    }

    private static Sheet getSheet(Workbook workbook, String sheetName) {
        if (sheetName == null || sheetName.equals("0") || sheetName.isEmpty()) {
            return workbook.getSheetAt(0);
        }
        try {
            int index = Integer.parseInt(sheetName);
            return workbook.getSheetAt(index);
        } catch (NumberFormatException e) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Aba não encontrada: " + sheetName);
            }
            return sheet;
        }
    }

    private static Map<String, String> inferTypes(Sheet sheet, List<String> columnNames, int maxRows) {
        Map<String, List<Cell>> samplesByColumn = new LinkedHashMap<>();
        for (String col : columnNames) {
            samplesByColumn.put(col, new ArrayList<>());
        }

        int lastRow = Math.min(sheet.getLastRowNum(), maxRows);
        for (int r = 1; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < columnNames.size(); c++) {
                Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                samplesByColumn.get(columnNames.get(c)).add(cell);
            }
        }

        Map<String, String> types = new LinkedHashMap<>();
        for (String col : columnNames) {
            types.put(col, inferTypeForColumn(samplesByColumn.get(col)));
        }
        return types;
    }

    private static String inferTypeForColumn(List<Cell> cells) {
        boolean hasDate = false;
        boolean hasDouble = false;
        boolean hasLong = false;
        boolean hasString = false;
        int maxLength = 0;

        for (Cell cell : cells) {
            if (cell == null || cell.getCellType() == CellType.BLANK) continue;

            switch (cell.getCellType()) {
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        hasDate = true;
                    } else {
                        double num = cell.getNumericCellValue();
                        if (num == Math.floor(num) && !Double.isInfinite(num)) {
                            hasLong = true;
                        } else {
                            hasDouble = true;
                        }
                    }
                    break;
                case STRING:
                    hasString = true;
                    maxLength = Math.max(maxLength, cell.getStringCellValue().length());
                    break;
                case BOOLEAN:
                    hasString = true;
                    break;
                case FORMULA:
                    try {
                        if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                            hasDouble = true;
                        } else {
                            hasString = true;
                            maxLength = Math.max(maxLength, cell.getStringCellValue().length());
                        }
                    } catch (Exception e) {
                        hasString = true;
                    }
                    break;
                default:
                    hasString = true;
            }
        }

        if (hasString) {
            return "VARCHAR(" + Math.max(maxLength, 255) + ")";
        } else if (hasDate && !hasDouble && !hasLong) {
            return "DATE";
        } else if (hasDouble) {
            return "DOUBLE";
        } else if (hasLong) {
            return "BIGINT";
        } else {
            return "VARCHAR(255)";
        }
    }
}