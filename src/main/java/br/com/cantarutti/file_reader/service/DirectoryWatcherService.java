package br.com.cantarutti.file_reader.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DirectoryWatcherService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryWatcherService.class);

    private final IngestionService ingestionService;
    private final Path watchDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DirectoryWatcherService(IngestionService ingestionService,
                                   @Value("${app.watch.directory}") String directoryPath) {
        this.ingestionService = ingestionService;
        this.watchDir = Paths.get(directoryPath);
    }

    @Override
    public void run(String... args) throws Exception {
        if (!Files.isDirectory(watchDir)) {
            logger.error("Diretório de monitoramento não encontrado: {}", watchDir);
            return;
        }
        logger.info("Monitorando diretório: {}", watchDir);
        startWatching();
    }

    private void startWatching() {
        executor.submit(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                watchDir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key;
                    try {
                        key = watchService.take(); // bloqueia até um evento
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        Path fileName = (Path) event.context();
                        Path fullPath = watchDir.resolve(fileName);

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        if (!fileName.toString().endsWith(".xlsx")) {
                            continue;
                        }

                        logger.info("Evento detectado: {} - {}", kind, fullPath);

                        // Pequena pausa para garantir que o arquivo foi completamente escrito
                        if (!waitForFileStability(fullPath, 2000)) {
                            logger.warn("Arquivo não está estável, ignorando: {}", fullPath);
                            continue;
                        }

                        // Processa o arquivo
                        try {
                            processFile(fullPath);
                        } catch (Exception e) {
                            logger.error("Falha ao processar arquivo {}: {}", fullPath, e.getMessage());
                        }
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        logger.warn("WatchKey não é mais válido, saindo do monitoramento.");
                        break;
                    }
                }
            } catch (IOException e) {
                logger.error("Erro no WatchService: {}", e.getMessage());
            }
        });
    }

    /**
     * Aguarda o arquivo ficar estável (tamanho não muda por um período).
     */
    private boolean waitForFileStability(Path file, int maxWaitMs) {
        long start = System.currentTimeMillis();
        long lastSize = -1;
        while (System.currentTimeMillis() - start < maxWaitMs) {
            try {
                if (!Files.exists(file)) {
                    Thread.sleep(200);
                    continue;
                }
                long currentSize = Files.size(file);
                if (currentSize == lastSize) {
                    return true; // tamanho estabilizou
                }
                lastSize = currentSize;
                Thread.sleep(300);
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void processFile(Path filePath) {
        logger.info("Processando arquivo: {}", filePath.getFileName());

        // Parâmetros fixos (customize conforme necessário)
        String targetDatabase = "ms_upload_arquivos";
        String tableName = filePath.getFileName().toString().replace(".xlsx", "");
        String sheetName = "0"; // primeira aba
        boolean createTable = true;
        int batchSize = 500;
        java.util.List<String> uniqueKeys = java.util.Arrays.asList("ANOMES","CARD","ONWER", "DESCRIPTION");
        java.util.Map<String, String> columnMappings = null; 

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            int rows = ingestionService.processFile(
                    inputStream,
                    targetDatabase,
                    tableName,
                    createTable,
                    columnMappings,
                    uniqueKeys,
                    batchSize,
                    filePath.getFileName().toString(),
                    sheetName
            );
            logger.info("Importação concluída: {} linhas inseridas na tabela '{}'", rows, tableName);
        } catch (Exception e) {
            logger.error("Erro ao processar arquivo {}: {}", filePath, e.getMessage(), e);
        }
    }
}