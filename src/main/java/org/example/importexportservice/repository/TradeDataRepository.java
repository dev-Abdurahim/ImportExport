package org.example.importexportservice.repository;

import org.example.importexportservice.entity.TradeData;
import org.example.importexportservice.enums.TradeOperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TradeDataRepository extends JpaRepository<TradeData,Long> {

    @Query("SELECT t.uniqueHash FROM TradeData t WHERE t.uniqueHash IN :hashes")
    List<String> findExistingHashes(@Param(("hashes"))Set<String> hashes);

    @Query("SELECT DISTINCT t.companyInn FROM TradeData t where t.companyInn NOT IN (SELECT o.inn FROM Organization o)")
    List<String> findInnsToEnrich();

    @Query("SELECT t FROM TradeData t WHERE  t.uniqueHash = :hash")
    Optional<TradeData> findByHash(@Param("hash") String hash);

    List<TradeData> findByCompanyInnAndHsCodeAndDeclarationDateAndTradeOperationType(
            String companyInn,
            String hsCode,
            LocalDate declarationDate,
            TradeOperationType tradeOperationType
    );


}
