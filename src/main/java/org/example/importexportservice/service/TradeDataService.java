package org.example.importexportservice.service;


    public interface TradeDataService {

    void importTradeStatistics();

    void enrichMissingOrganizations();

    void updateTradeStatistics();
}
