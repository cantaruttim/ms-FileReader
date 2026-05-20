package br.com.cantarutti.file_reader.controller;

import br.com.cantarutti.file_reader.records.IngestionRequest;
import br.com.cantarutti.file_reader.service.IngestionService;
import br.com.cantarutti.file_reader.service.MetadataService;
import br.com.cantarutti.file_reader.records.IngestionMetadataRecord;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;
    private final MetadataService metadataService;

    public IngestionController(IngestionService ingestionService, MetadataService metadataService) {
        this.ingestionService = ingestionService;
        this.metadataService = metadataService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("metadata") String metadataJson) {

        try {
            IngestionRequest request = IngestionRequest.fromJson(metadataJson);
            request.validate();

            InputStream inputStream = new BufferedInputStream(file.getInputStream());
            inputStream.mark(Integer.MAX_VALUE);

            int importedRows = ingestionService.processFile(
                    inputStream,
                    request.getTargetDatabase(),
                    request.getTableName(),
                    request.isCreateTable(),
                    request.getColumnMappings(),
                    request.getUniqueKeys(),
                    request.getBatchSize(),
                    file.getOriginalFilename(),  // ← nome do arquivo
                    request.getSheetName()       // ← nome da aba
            );

            return ResponseEntity.ok("Importação concluída. Linhas novas inseridas: " + importedRows);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Requisição inválida: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro: " + e.getMessage());
        }
    }

    // Novo endpoint para consultar histórico
    @GetMapping("/history")
    public ResponseEntity<List<IngestionMetadataRecord>> getHistory(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<IngestionMetadataRecord> history = metadataService.getImportHistory(limit);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }
}