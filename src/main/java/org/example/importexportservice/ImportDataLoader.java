package org.example.importexportservice;

import lombok.RequiredArgsConstructor;
import org.example.importexportservice.service.TradeDataService;
import org.example.importexportservice.service.TradeDataServiceImpl;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportDataLoader {

    private final TradeDataService tradeDataService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        tradeDataService.importTradeStatistics();

    }
}
