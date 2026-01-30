package org.example.importexportservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.importexportservice.dto.repsonse.IndividualResponseDto;
import org.example.importexportservice.dto.repsonse.LegalResponseDto;
import org.example.importexportservice.token.TokenHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessRegService {

    private final WebClient webClient;
    private final TokenHolder tokenHolder;

    @Value("${app.api.endpoints.business-reg.legal}")
    private String legalUrl;

    @Value("${app.api.endpoints.business-reg.individuals}")
    private String individualsUrl;

    /**
     * TIN (INN) bo'yicha yuridik shaxs ma'lumotlarini oladi
     *
     * @param tin 9 raqamli INN
     * @return LegalResponseDto yoki null (xato bo'lsa)
     */

    public Mono<LegalResponseDto> getLegalDetails(String tin) {

        if (tin == null || tin.length() != 9) {
            log.warn("⚠️ Noto‘g‘ri TIN uzunligi: {}", tin);
            return Mono.empty();
        }

        log.debug("🏢 Legal API chaqirilmoqda → INN={}", tin);

        return webClient.get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromUriString(legalUrl)
                        .queryParam("tin", tin)
                        .build()
                        .toUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHolder.getToken())
                .accept(MediaType.APPLICATION_JSON)

                .retrieve()

                .onStatus(
                        status -> status.value() == 429,
                        response -> {
                            log.warn("⏳ Legal API rate limit (429) → INN={} SKIP", tin);
                            return Mono.error(new RuntimeException("Rate limit 429"));
                        }
                )

                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("🔥 Legal API 5xx → INN={}, body={}", tin, body);
                                    return Mono.error(new RuntimeException("Legal API 5xx"));
                                })
                )

                .bodyToMono(LegalResponseDto.class)

                .timeout(Duration.ofSeconds(10))

                .onErrorResume(e -> {
                    log.warn("⚠️ Legal API SKIP → INN={}", tin);
                    return Mono.empty();
                });

    }

    public Mono<IndividualResponseDto> getIndividualDetails(String pinfl) {
        if (pinfl == null || pinfl.length() != 14) {
            log.warn("⚠️ Noto‘g‘ri PINFL uzunligi: {}", pinfl);
            return Mono.empty();
        }

        log.debug("👤 Individual API chaqirilmoqda → PINFL={}", pinfl);

        Map<String, String> body = Map.of("pinfl", pinfl);

        return webClient.post()
                .uri(individualsUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHolder.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)

                .retrieve()

                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(b -> {
                                    log.error("🔥 Individual API error → PINFL={}, body={}", pinfl, b);
                                    return Mono.error(new RuntimeException("Individual API error"));
                                })
                )

                .bodyToMono(IndividualResponseDto.class)

                .timeout(Duration.ofSeconds(10))

                // ✅ Bitta xato butun enrichni to‘xtatmasin
                .onErrorResume(e -> {
                    log.warn("⚠️ Individual API SKIP → PINFL={}", pinfl);
                    return Mono.empty();
                });
    }
}





