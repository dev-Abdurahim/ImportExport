package org.example.importexportservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.importexportservice.dto.TradeStatisticDTO;
import org.example.importexportservice.dto.repsonse.IndividualResponseDto;
import org.example.importexportservice.dto.repsonse.LegalResponseDto;
import org.example.importexportservice.entity.Organization;
import org.example.importexportservice.entity.TradeData;
import org.example.importexportservice.enums.TradeType;
import org.example.importexportservice.mapper.TradeStatisticsMapper;
import org.example.importexportservice.repository.OrganizationRepository;
import org.example.importexportservice.repository.TradeDataRepository;
import org.example.importexportservice.token.TokenHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;


import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor

public class TradeDataServiceImpl implements TradeDataService {

    private final WebClient webClient;
    private final TokenHolder tokenHolder;
    private final TradeDataRepository tradeDataRepository;
    private final OrganizationRepository organizationRepository;
    private final TradeStatisticsMapper tradeStatisticsMapper;
    private final BusinessRegService businessRegService;


    @Value("${app.api.endpoints.hscode}")
    private String hsFullUrl;

    @Value("${app.api.hscode.page-size}")
    private int pageSize;

    private static final String SENDER_PIN = "54646546541234";


    private static final int BUFFER_SIZE = 500;
    private static final int MAX_RETRIES = 3;

    @Override
    @Transactional
    public void importTradeStatistics() {

        LocalDate reqDate = LocalDate.now();
        log.info("Import boshlandi → reqDate: {}", reqDate);

        int page = 1;
        List<TradeStatisticDTO> buffer = new ArrayList<>(BUFFER_SIZE);

        while (true){

            TradeStatisticDTO.TradeStatRespDto pageResponse = fetchPageWithRetry(page,reqDate,SENDER_PIN);

            if(pageResponse == null || isEmpty(pageResponse.getTradeStatistics())){
                break;
            }

            buffer.addAll(pageResponse.getTradeStatistics());

            if(buffer.size() >= BUFFER_SIZE){
                processAndSaveBuffer(buffer);
                buffer.clear();
            }

            if(page >= pageResponse.getTotalPages()){
                break;
            }
                page++;

        }

        if(!buffer.isEmpty()){
            processAndSaveBuffer(buffer);

        }

        enrichMissingOrganizations();
        log.info("Import yakunlandi");

    }
    @Transactional
    protected void processAndSaveBuffer(List<TradeStatisticDTO> dtos) {


       Map<String,TradeData> uniqueMap = new LinkedHashMap<>();

        for (TradeStatisticDTO dto : dtos) {

            String inn = dto.getCompanyInn();
            if (inn == null || inn.isBlank()) {
                continue;
            }

           TradeType tradeType;
            if (inn.length() == 9){
                tradeType = TradeType.LEGAL;
            } else if (inn.length() == 14) {
                tradeType = TradeType.INDIVIDUAL;
            }
            else {
                log.warn("Noma'lum INN/PINFL uzunligi: {}", inn);
                continue;
            }

            TradeData entity = tradeStatisticsMapper.toEntity(dto);

            entity.setCompanyInn(inn);
            entity.setTradeType(tradeType);

            String hash = entity.calculateHash();
            entity.setUniqueHash(hash);

            uniqueMap.putIfAbsent(hash,entity);


        }

        if (uniqueMap.isEmpty()){
            return;
        }

        Set<String> hashes = uniqueMap.keySet();

        Set<String> existingHashes = new HashSet<>(tradeDataRepository.findExistingHashes(hashes));

        List<TradeData> toSave = uniqueMap.values().stream()
                .filter(e -> !existingHashes.contains(e.getUniqueHash()))
                .toList();

        if(!toSave.isEmpty()){
            tradeDataRepository.saveAll(toSave);
            log.info("{} ta yangi trade saqlandi", toSave.size());

        }
    }


    /**

     * Bitta sahifani retry mexanizmi bilan yuklaydi

     */

    private TradeStatisticDTO.TradeStatRespDto fetchPageWithRetry(int page, LocalDate reqDate,String senderPin) {


        return webClient.get()
                .uri(uriBuilder -> UriComponentsBuilder.fromUriString(hsFullUrl)
                        .queryParam("transaction_id","545645645645645645")
                        .queryParam("sender_pin",senderPin)
                        .queryParam("consent",1)
                        .queryParam("reqDate",reqDate.toString() )
                        .queryParam("page", page)
                        .queryParam("size", pageSize)
                        .build()
                        .toUri())

                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHolder.getToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(TradeStatisticDTO.TradeStatRespDto.class)
                .retryWhen(
                        Retry.backoff(MAX_RETRIES, Duration.ofSeconds(2))
                        .filter(ex -> ex instanceof WebClientResponseException || ex instanceof TimeoutException)
                                .doBeforeRetry(r ->
                                        log.warn("Retry {} → page {}",
                                                r.totalRetries() + 1, page))
                )
                .onErrorResume(throwable -> {
                    log.error("Sahifa {} yuklanmadi: {}", page, throwable.getMessage());
                    return Mono.empty();

                })

                .block();

    }

    @Transactional
    @Override
    public void enrichMissingOrganizations() {

        List<String> inns = tradeDataRepository.findInnsToEnrich();

        for (String inn : inns) {
            enrichSingleOrganization(inn);

        }
    }


    private void enrichSingleOrganization(String inn) {

        Organization org = null;

        if (inn.length() == 9) {

            LegalResponseDto dto = businessRegService.getLegalDetails(inn);
            if (dto != null) {

                org = Organization.builder()
                        .inn(inn)
                        .type(TradeType.LEGAL)
                        .name(dto.getCompanyName())
                        .shortName(dto.getCompanyShortName())
                        .region(toStr(dto.getHomeRegion()))
                        .district(toStr(dto.getCertificateGivenBy()))
                        .build();

            }
        } else if (inn.length() == 14) {
            IndividualResponseDto dto = businessRegService.getIndividualDetails(inn);
            if (dto != null) {
                org = Organization.builder()
                        .inn(inn)
                        .type(TradeType.INDIVIDUAL)
                        .firstName(dto.getFirstname())
                        .lastName(dto.getLastname())
                        .region(toStr(dto.getRegistrationRegionSoato()))
                        .district(toStr(dto.getCertGivenBy()))
                        .build();
            }

        }

        if (org == null)
            return;


        final Organization finalOrg = org;

        organizationRepository.findById(inn).ifPresentOrElse(existing -> {
            if (finalOrg.getName() != null) existing.setName(finalOrg.getName());
            if (finalOrg.getShortName() != null) existing.setShortName(finalOrg.getShortName());
            if (finalOrg.getFirstName() != null) existing.setFirstName(finalOrg.getFirstName());
            if (finalOrg.getLastName() != null) existing.setLastName(finalOrg.getLastName());
            if (finalOrg.getRegion() != null) existing.setRegion(finalOrg.getRegion());
            if (finalOrg.getDistrict() != null) existing.setDistrict(finalOrg.getDistrict());
            organizationRepository.save(existing);

        }, () -> organizationRepository.save(finalOrg));
    }

    private String toStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();

    }

}


