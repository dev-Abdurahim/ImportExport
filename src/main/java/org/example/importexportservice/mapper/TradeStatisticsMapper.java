package org.example.importexportservice.mapper;


import org.example.importexportservice.dto.TradeStatisticDTO;
import org.example.importexportservice.entity.TradeData;
import org.example.importexportservice.enums.TradeOperationType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TradeStatisticsMapper {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy");

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uniqueHash", ignore = true)
    @Mapping(target = "tradeType", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "tradeOperationType", expression = "java(mapOpType(dto.getOperationType()))")
    @Mapping(target = "declarationDate", expression = "java(mapDate(dto.getDeclarationDate()))")
    TradeData toEntity(TradeStatisticDTO dto);

    default TradeOperationType mapOpType(String op) {
        if ("ИМ".equals(op))
            return TradeOperationType.IMPORT;
        else if ("ЭК".equals(op))
            return TradeOperationType.EXPORT;

        return null;
    }
    default LocalDate mapDate(String date) {
        try {
            return (date != null && !date.isBlank()) ? LocalDate.parse(date, formatter) : null;
        } catch (Exception e) {
            return null;
        }
    }


}
