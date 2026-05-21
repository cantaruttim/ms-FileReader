-- Ver todas as tabelas do banco
USE upload_files;
SHOW TABLES;

-- Consultar dados importados
USE upload_files;

-- Total de linhas
SELECT COUNT(*) AS total_rows_in_file FROM ingestion_metadata;

-- Últimas importações
SELECT 
    id,
    table_name,
    total_rows_in_file,
    new_rows_inserted,
    skipped_rows,
    status,
    import_timestamp
FROM ingestion_metadata 
ORDER BY import_timestamp DESC 
LIMIT 5;