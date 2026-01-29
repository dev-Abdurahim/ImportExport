package org.example.importexportservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeStatisticDTO implements Serializable {

    @JsonProperty("g01A")
    private String operationType;

    @JsonProperty("g33A")
    private String hsCode;

    @JsonProperty("g46")
    private BigDecimal goodsValue;

    @JsonProperty("g15_17")
    private String countryCode;

    @JsonProperty("inn")
    private String companyInn;

    @JsonProperty("g54D")
    private String declarationDate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TradeStatRespDto {

        @JsonProperty("resList")
        private List<TradeStatisticDTO> tradeStatistics;

        @JsonProperty("error")
        private String errorCode;

        private Integer totalPages;

        private Integer totalElements;


    }
}
