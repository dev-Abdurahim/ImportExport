package org.example.importexportservice.service;

import reactor.core.publisher.Mono;

public interface TradeDataService {

    void importTradeStatistics();

   void enrichMissingOrganizations();
}
