package br.com.cantarutti.file_reader.model;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class ExcelToRelationalIncrementalImporter {

    /**
     * Importa linhas novas com base em chave única. Retorna número de linhas inseridas.
     */
    public static int importSheetIncremental(
            InputStream inputStream,
            String tableName,
            Connection connection,
            Map<String, String> columnMappings,
            List<String> uniqueKeyColumns,
            int batchSize,
            String sheetName) throws Exception {

        boolean temChave = uniqueKeyColumns != null && !uniqueKeyColumns.isEmpty();
        int totalInserted = 0;

        // 1. Abrir Excel e ler cabeçalhos
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = getSheet(workbook, sheetName);
        Row headerRow = sheet.getRow(0);
        List<String> excelHeaders = new ArrayList<>();
        for (Cell cell : headerRow) {
            excelHeaders.add(cell.getStringCellValue().trim());
        }

        // 2. Mapear Excel -> BD
        Map<String, String> mapExcelParaBD = (columnMappings != null) ? columnMappings : identityMap();
        List<String> dbColumns = new ArrayList<>();
        for (String h : excelHeaders) {
            dbColumns.add(mapExcelParaBD.getOrDefault(h, h));
        }

        // 3. Validar chaves únicas
        if (temChave) {
            List<String> chavesNaoEncontradas = uniqueKeyColumns.stream()
                    .filter(col -> !dbColumns.contains(col))
                    .collect(Collectors.toList());
            if (!chavesNaoEncontradas.isEmpty()) {
                throw new IllegalArgumentException("Colunas de chave única não encontradas: " + chavesNaoEncontradas);
            }
        }

        // 4. Índices das chaves no Excel
        Map<String, Integer> indiceChave = new LinkedHashMap<>();
        if (temChave) {
            for (int i = 0; i < excelHeaders.size(); i++) {
                String dbCol = mapExcelParaBD.getOrDefault(excelHeaders.get(i), excelHeaders.get(i));
                if (uniqueKeyColumns.contains(dbCol)) {
                    indiceChave.put(dbCol, i);
                }
            }
        }

        // 5. SQL INSERT
        String placeholders = String.join(",", Collections.nCopies(dbColumns.size(), "?"));
        String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName, String.join(",", dbColumns), placeholders);

        // 6. Processar lotes
        connection.setAutoCommit(false);
        try {
            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i += batchSize) {
                int fimLote = Math.min(i + batchSize - 1, lastRow);
                List<Row> loteLinhas = new ArrayList<>();
                for (int r = i; r <= fimLote; r++) {
                    Row row = sheet.getRow(r);
                    if (row != null) loteLinhas.add(row);
                }
                if (loteLinhas.isEmpty()) continue;

                if (temChave) {
                    String tempTable = "temp_keys_" + System.nanoTime();

                    // Criar tabela temporária
                    criarTabelaTemporaria(connection, tempTable, tableName, uniqueKeyColumns);

                    // Extrair chaves do lote
                    List<Object[]> chavesLote = new ArrayList<>();
                    for (Row row : loteLinhas) {
                        Object[] valoresChave = new Object[uniqueKeyColumns.size()];
                        int idx = 0;
                        for (String dbCol : uniqueKeyColumns) {
                            int excelIdx = indiceChave.get(dbCol);
                            Cell cell = row.getCell(excelIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                            valoresChave[idx++] = valorCelula(cell);
                        }
                        chavesLote.add(valoresChave);
                    }

                    // Inserir chaves na temporária
                    String insertTemp = "INSERT INTO " + tempTable + " (" +
                            String.join(",", uniqueKeyColumns) + ") VALUES (" +
                            String.join(",", Collections.nCopies(uniqueKeyColumns.size(), "?")) + ")";
                    try (PreparedStatement psTemp = connection.prepareStatement(insertTemp)) {
                        for (Object[] chave : chavesLote) {
                            for (int j = 0; j < chave.length; j++) {
                                setParameterFromObject(psTemp, j + 1, chave[j]);
                            }
                            psTemp.addBatch();
                        }
                        psTemp.executeBatch();
                    }

                    // Buscar chaves existentes
                    Set<List<Object>> chavesExistentes = buscarChavesExistentes(connection, tempTable, tableName, uniqueKeyColumns);

                    // Inserir apenas novas linhas
                    try (PreparedStatement psInsert = connection.prepareStatement(insertSql)) {
                        int novos = 0;
                        for (int r = 0; r < loteLinhas.size(); r++) {
                            if (!chavesExistentes.contains(Arrays.asList(chavesLote.get(r)))) {
                                Row row = loteLinhas.get(r);
                                for (int j = 0; j < dbColumns.size(); j++) {
                                    Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                                    setParameter(psInsert, j + 1, cell);
                                }
                                psInsert.addBatch();
                                novos++;
                            }
                        }
                        if (novos > 0) {
                            psInsert.executeBatch();
                            totalInserted += novos;
                        }
                    }

                    // Limpar temporária
                    try (Statement st = connection.createStatement()) {
                        st.execute("DROP TEMPORARY TABLE IF EXISTS " + tempTable);
                    }

                } else {
                    // Modo sem chave: insere tudo
                    try (PreparedStatement psInsert = connection.prepareStatement(insertSql)) {
                        for (Row row : loteLinhas) {
                            for (int j = 0; j < dbColumns.size(); j++) {
                                Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                                setParameter(psInsert, j + 1, cell);
                            }
                            psInsert.addBatch();
                        }
                        psInsert.executeBatch();
                        totalInserted += loteLinhas.size();
                    }
                }
            }
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
            workbook.close();
        }

        return totalInserted;
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

    // ---------- MÉTODOS AUXILIARES ----------

    private static Map<String, String> identityMap() {
        return new HashMap<String, String>() {
            @Override
            public String get(Object key) {
                return (String) key;
            }
        };
    }

    private static Object valorCelula(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new java.sql.Date(cell.getDateCellValue().getTime());
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num) && !Double.isInfinite(num)) {
                        return (long) num;
                    } else {
                        return num;
                    }
                }
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                return cell.getCellFormula(); // simplificação
            default:
                return null;
        }
    }

    private static void setParameterFromObject(PreparedStatement ps, int index, Object valor) throws SQLException {
        if (valor == null) {
            ps.setNull(index, Types.NULL);
        } else if (valor instanceof String) {
            ps.setString(index, (String) valor);
        } else if (valor instanceof Long) {
            ps.setLong(index, (Long) valor);
        } else if (valor instanceof Double) {
            ps.setDouble(index, (Double) valor);
        } else if (valor instanceof java.sql.Date) {
            ps.setDate(index, (java.sql.Date) valor);
        } else if (valor instanceof Boolean) {
            ps.setBoolean(index, (Boolean) valor);
        } else {
            ps.setObject(index, valor);
        }
    }

    private static void setParameter(PreparedStatement pstmt, int index, Cell cell) throws SQLException {
        if (cell == null) {
            pstmt.setNull(index, Types.NULL);
            return;
        }
        switch (cell.getCellType()) {
            case STRING:
                pstmt.setString(index, cell.getStringCellValue());
                break;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    pstmt.setDate(index, new java.sql.Date(cell.getDateCellValue().getTime()));
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num) && !Double.isInfinite(num)) {
                        pstmt.setLong(index, (long) num);
                    } else {
                        pstmt.setDouble(index, num);
                    }
                }
                break;
            case BOOLEAN:
                pstmt.setBoolean(index, cell.getBooleanCellValue());
                break;
            case FORMULA:
                pstmt.setString(index, cell.getCellFormula());
                break;
            default:
                pstmt.setNull(index, Types.NULL);
                break;
        }
    }

    private static void criarTabelaTemporaria(Connection conn, String tempTable,
                                              String targetTable, List<String> keyCols) throws SQLException {
        StringBuilder sqlCreate = new StringBuilder("CREATE TEMPORARY TABLE ");
        sqlCreate.append(tempTable).append(" (");
        try (PreparedStatement psMeta = conn.prepareStatement(
                "SELECT " + String.join(",", keyCols) + " FROM " + targetTable + " WHERE 1=0")) {
            ResultSetMetaData meta = psMeta.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (i > 1) sqlCreate.append(", ");
                sqlCreate.append(meta.getColumnName(i)).append(" ").append(meta.getColumnTypeName(i));
                if ("VARCHAR".equalsIgnoreCase(meta.getColumnTypeName(i)) ||
                    "CHAR".equalsIgnoreCase(meta.getColumnTypeName(i))) {
                    sqlCreate.append("(").append(meta.getPrecision(i)).append(")");
                }
            }
        }
        sqlCreate.append(")");
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TEMPORARY TABLE IF EXISTS " + tempTable);
            st.execute(sqlCreate.toString());
        }
    }

    private static Set<List<Object>> buscarChavesExistentes(Connection conn, String tempTable,
                                                            String targetTable,
                                                            List<String> keyCols) throws SQLException {
        Set<List<Object>> existentes = new HashSet<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(keyCols.stream().map(c -> "t." + c).collect(Collectors.joining(", ")));
        sql.append(" FROM ").append(tempTable).append(" t WHERE EXISTS (SELECT 1 FROM ").append(targetTable).append(" trg WHERE ");
        String condicoes = keyCols.stream()
                .map(c -> "trg." + c + " = t." + c)
                .collect(Collectors.joining(" AND "));
        sql.append(condicoes).append(")");

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql.toString())) {
            while (rs.next()) {
                List<Object> chave = new ArrayList<>();
                for (int i = 1; i <= keyCols.size(); i++) {
                    chave.add(rs.getObject(i));
                }
                existentes.add(chave);
            }
        }
        return existentes;
    }
}