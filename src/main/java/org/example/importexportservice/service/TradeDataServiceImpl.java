package org.example.importexportservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.importexportservice.dto.TradeStatisticDTO;
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
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

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
        log.info("🚀 Import boshlandi → {}", reqDate);

        Flux.generate(
                        () -> 1,
                        (Integer page, SynchronousSink<Integer> sink) -> {
                            sink.next(page);
                            return page + 1;
                        }
                )
                .flatMap(page ->
                                fetchPageReactive(page, reqDate, SENDER_PIN)
                                        .doOnSubscribe(s ->
                                                log.debug("📥 Sahifa so‘raldi → page={}", page))
                                        .doOnNext(r ->
                                                log.debug("📄 Sahifa keldi → page={}, size={}",
                                                        page,
                                                        r.getTradeStatistics().size()))
                        , 10
                )

                .takeUntil(r -> isEmpty(r.getTradeStatistics()))

                .filter(r -> !isEmpty(r.getTradeStatistics()))

                .flatMapIterable(TradeStatisticDTO.TradeStatRespDto::getTradeStatistics)

                .buffer(BUFFER_SIZE)

                .flatMap(batch ->
                        Mono.fromRunnable(() -> {
                                    log.info("💾 Batch saqlanmoqda → size={}", batch.size());
                                    processAndSaveBuffer(batch);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                )

                .doOnComplete(() -> {
                    log.info("📌 Trade import tugadi, organization enrich boshlanmoqda...");
                    enrichMissingOrganizations();
                    log.info("✅ Import to‘liq yakunlandi");
                })

                .doOnError(e -> log.error("❌ Import jarayonida xato", e))

                // 8️⃣ Oqimni yakunigacha kutish
                .blockLast();
    }

    @Transactional
    protected void processAndSaveBuffer(List<TradeStatisticDTO> dtos) {

        int received = dtos.size();
        int inserted = 0;
        int updated = 0;
        int skipped = 0;


        Map<String, TradeData> batchMap = new LinkedHashMap<>();

        for (TradeStatisticDTO dto : dtos) {
            if (dto.getCompanyInn() == null || dto.getCompanyInn().isBlank()) {
                continue;
            }

            TradeData entity = tradeStatisticsMapper.toEntity(dto);
            entity.setCompanyInn(dto.getCompanyInn());

            if(entity.getCompanyInn().length() == 9){
                entity.setTradeType(TradeType.LEGAL);
            } else if (entity.getCompanyInn().length() == 14) {
                entity.setTradeType(TradeType.INDIVIDUAL);

            }

            // identity key (hash emas!)
            String identityKey = entity.getCompanyInn() + "|" +
                    entity.getHsCode() + "|" +
                    entity.getDeclarationDate() + "|" +
                    entity.getTradeOperationType();

            // ⚠️ batch ichidagi duplicate’ni kesamiz
            batchMap.putIfAbsent(identityKey, entity);

        }

        int batchDeduplicated = dtos.size() - batchMap.size();
        log.debug("♻️ Batch ichida kesildi: {}", batchDeduplicated);

        for (TradeData entity : batchMap.values()) {

            List<TradeData> existings =
                    tradeDataRepository
                            .findByCompanyInnAndHsCodeAndDeclarationDateAndTradeOperationType(
                                    entity.getCompanyInn(),
                                    entity.getHsCode(),
                                    entity.getDeclarationDate(),
                                    entity.getTradeOperationType()
                            );

            if (existings.isEmpty()) {
                entity.setUniqueHash(entity.calculateHash());
                tradeDataRepository.save(entity);
                inserted++;
                continue;
            }

            TradeData existing = existings.get(0);

            String newHash = entity.calculateHash();

            if (newHash.equals(existing.getUniqueHash())) {
                skipped++;
                continue;
            }

            existing.setGoodsValue(entity.getGoodsValue());
            existing.setCountryCode(entity.getCountryCode());
            existing.setUniqueHash(newHash);

            tradeDataRepository.save(existing);
            updated++;
        }

        log.info("""
        📊 IMPORT / UPDATE STATISTIKASI:
        📥 Kelgan DTO: {}
        🆕 Yangi: {}
        🔄 Update: {}
        ⏭ Skip: {}
        """, received, inserted, updated, skipped);
    }

    @Override
    public void updateTradeStatistics() {

        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now();

        log.info("🔄 Trade uchun UPDATE boshlandi → {} → {}", fromDate, toDate);

        Flux.fromStream(fromDate.datesUntil(toDate.plusDays(1)))
                .concatMap(updateDate -> {
                    log.info("📅 UPDATE sana → {}", updateDate);
                    return Flux.generate(
                                    () -> 1,
                                    (Integer page, SynchronousSink<Integer> sink) -> {
                                        sink.next(page);
                                        return page + 1;
                                    }
                            )
                            .flatMap(page -> fetchPageReactive(page,updateDate,SENDER_PIN),
                                    5)
                            .takeUntil(r -> isEmpty(r.getTradeStatistics()))
                            .filter( r -> !isEmpty(r.getTradeStatistics()))
                            .flatMapIterable(TradeStatisticDTO.TradeStatRespDto::getTradeStatistics)
                            .buffer(BUFFER_SIZE)
                            .flatMap(batch ->
                                    Mono.fromRunnable(() -> {
                                                log.info("🔄 UPDATE batch → date={}, size={}",
                                                        updateDate, batch.size());
                                                processAndSaveBuffer(batch);

                                            })
                                            .subscribeOn(Schedulers.boundedElastic())
                            );
                })
                .doOnComplete(() ->
                        log.info("✅ Trade UPDATE yakunlandi → {} → {}", fromDate, toDate)
                )
                .doOnError(e ->
                        log.error("❌ Trade UPDATE jarayonida xato", e)
                )
                .blockLast();


    }


    private Mono<TradeStatisticDTO.TradeStatRespDto> fetchPageReactive(int page, LocalDate reqDate, String senderPin) {
        return webClient.get()
                .uri(uriBuilder -> UriComponentsBuilder.fromUriString(hsFullUrl)
                        .queryParam("transaction_id", "545645645645645645")
                        .queryParam("sender_pin", senderPin)
                        .queryParam("consent", 1)
                        .queryParam("reqDate", reqDate.toString())
                        .queryParam("page", page)
                        .queryParam("size", pageSize)
                        .build()
                        .toUri())

                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHolder.getToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(TradeStatisticDTO.TradeStatRespDto.class)

                .retryWhen(
                        Retry.backoff(MAX_RETRIES, Duration.ofSeconds(2))
                                .doBeforeRetry(r ->
                                        log.warn("🔁 Retry {} → page={}",
                                                r.totalRetries() + 1, page))
                )
                .onErrorResume(e -> {
                    log.error("❌ Sahifa yuklanmadi → page={}", page, e);
                    return Mono.empty();

                });

    }

    @Override
    @Transactional
    public void enrichMissingOrganizations() {

        List<String> inns = tradeDataRepository.findInnsToEnrich();

        Flux.fromIterable(inns)
                .delayElements(Duration.ofMillis(300))
                .concatMap(this::enrichSingleOrganization)
                .doOnComplete(() -> log.info("🏁 Organization enrich tugadi"))
                .doOnError(e -> log.error("❌ Enrich jarayonida xato", e))
                .blockLast();
    }


    private Mono<Void> enrichSingleOrganization(String inn) {

        if (inn.length() == 9) {
            return businessRegService.getLegalDetails(inn)
                    .flatMap(dto -> {
                        Organization org = Organization.builder()
                                .inn(inn)
                                .type(TradeType.LEGAL)
                                .name(dto.getCompanyName())
                                .shortName(dto.getCompanyShortName())
                                .region(toStr(dto.getHomeRegion()))
                                .district(toStr(dto.getCertificateGivenBy()))
                                .build();
                        return saveOrUpdateOrganization(org);
                    })
                    .onErrorResume(e -> {
                        log.warn("⚠️ Legal enrich skip → INN={}", inn);
                        return Mono.empty();
                    })
                    .then();
        }

        if (inn.length() == 14) {
            return businessRegService.getIndividualDetails(inn)
                    .flatMap(dto -> {
                        Organization org = Organization.builder()
                                .inn(inn)
                                .type(TradeType.INDIVIDUAL)
                                .firstName(dto.getFirstname())
                                .lastName(dto.getLastname())
                                .region(toStr(dto.getRegistrationRegionSoato()))
                                .district(toStr(dto.getCertGivenBy()))
                                .build();
                        return saveOrUpdateOrganization(org);
                    })
                    .onErrorResume(e -> {
                        log.warn("⚠️ Individual enrich skip → PINFL={}", inn);
                        return Mono.empty();
                    })
                    .then();
        }
        log.warn("❗ Noma'lum INN/PINFL uzunligi → {}", inn);
        return Mono.empty();
    }

    @Transactional
    protected Mono<Void> saveOrUpdateOrganization(Organization org) {

        return Mono.fromRunnable(() -> {
            organizationRepository.findById(org.getInn())
                    .ifPresentOrElse(existing -> {

                        if (org.getName() != null) existing.setName(org.getName());
                        if (org.getShortName() != null) existing.setShortName(org.getShortName());
                        if (org.getFirstName() != null) existing.setFirstName(org.getFirstName());
                        if (org.getLastName() != null) existing.setLastName(org.getLastName());
                        if (org.getRegion() != null) existing.setRegion(org.getRegion());
                        if (org.getDistrict() != null) existing.setDistrict(org.getDistrict());
                        organizationRepository.save(existing);
                    }, () -> organizationRepository.save(org));
        }).subscribeOn(Schedulers.boundedElastic()).then();

    }

    private String toStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();

    }

}


