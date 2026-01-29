package org.example.importexportservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.importexportservice.enums.TradeOperationType;
import org.example.importexportservice.enums.TradeType;
import org.hibernate.sql.Insert;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@Table(name = "trade_data")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TradeData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type")
    private TradeOperationType tradeOperationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type")
    private TradeType tradeType;

    @Column(name = "company_inn")
    private String companyInn;

    @Column(columnDefinition = "TEXT")
    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String companyShortName;

    @Column(columnDefinition = "TEXT")
    private String firstName;

    @Column(columnDefinition = "TEXT")
    private String lastName;

    @Column(name = "hs_code")
    private String hsCode;

    @Column(name = "goods_value", precision = 19, scale = 3)
    private BigDecimal goodsValue;

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "declaration_date")
    private LocalDate declarationDate;

    @Column(name = "unique_hash", unique = true, nullable = false, length = 64)
    private String uniqueHash;

    private String region;
    @PrePersist
    @PreUpdate
    private void generateUniqueHash() {
        if (this.uniqueHash == null || this.uniqueHash.isEmpty()) {
            this.uniqueHash = calculateHash();
        }
    }

    public String calculateHash() {
        String data = String.format("%s|%s|%s|%s|%s|%s",
                tradeOperationType, hsCode, goodsValue, countryCode, companyInn, declarationDate);
        return DigestUtils.sha256Hex(data);
    }




}
