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
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Data
@Table(
        name = "trade_data",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {
                        "company_inn",
                        "hs_code",
                        "declaration_date",
                        "operation_type"
                }
        )
)@AllArgsConstructor
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

    @Column(name = "hs_code")
    private String hsCode;

    @Column(name = "goods_value", precision = 19, scale = 3)
    private BigDecimal goodsValue;

    private String normalizeGoodsValue(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "declaration_date")
    private LocalDate declarationDate;

    @Column(name = "unique_hash",nullable = false, length = 64)
    private String uniqueHash;


    public String calculateHash() {
        String data = String.join("|",
                tradeOperationType.name(),
                hsCode,
                normalizeGoodsValue(goodsValue),
                countryCode,
                companyInn,
                declarationDate.toString()
        );
        return DigestUtils.sha256Hex(data);
    }
}
