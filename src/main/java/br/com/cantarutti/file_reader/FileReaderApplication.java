package br.com.cantarutti.file_reader;

import br.com.cantarutti.file_reader.service.MetadataService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class FileReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileReaderApplication.class, args);
    }

    @Bean
    CommandLineRunner init(MetadataService metadataService) {
        return args -> {
            metadataService.initializeMetadataTable();
            System.out.println("✅ Tabela de metadados inicializada!");
        };
    }
}