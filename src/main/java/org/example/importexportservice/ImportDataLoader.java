package org.example.importexportservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.importexportservice.service.TradeDataService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImportDataLoader implements CommandLineRunner {

    private final TradeDataService tradeDataService;

    @Override
    public void run(String... args) throws Exception {
//        log.info("Import qilish boshlandi...🙂");
//        tradeDataService.importTradeStatistics();
        tradeDataService.updateTradeStatistics();
    }
}
