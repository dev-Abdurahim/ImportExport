package org.example.importexportservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.importexportservice.service.TradeDataService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@RequiredArgsConstructor
public class TradeUpdatedCron {

    private final TradeDataService tradeDataService;
    private final AtomicBoolean running = new AtomicBoolean(false);

//    @Scheduled(cron = "${app.api.cron.trade-update}")
    public void cronImportTradeStatistics() {

        if (!running.compareAndSet(false, true)) {
            log.warn("⏳ Oldingi UPDATE hali tugamagan, cron skip qilindi");
            return;
        }

        try {
            tradeDataService.updateTradeStatistics();
        } finally {
            running.set(false);
        }
    }

}
