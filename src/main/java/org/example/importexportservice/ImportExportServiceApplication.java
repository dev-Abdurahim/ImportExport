package org.example.importexportservice;

import org.example.importexportservice.service.TradeDataService;
import org.example.importexportservice.token.TokenHolder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import static reactor.netty.http.HttpConnectionLiveness.log;

@SpringBootApplication
@EnableScheduling
public class ImportExportServiceApplication{

    public static void main(String[] args) {
        SpringApplication.run(ImportExportServiceApplication.class, args);
    }


}
