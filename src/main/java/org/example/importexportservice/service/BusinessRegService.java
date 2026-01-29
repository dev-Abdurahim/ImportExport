package org.example.importexportservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.importexportservice.custom.BusinessRegApiException;
import org.example.importexportservice.dto.repsonse.IndividualResponseDto;
import org.example.importexportservice.dto.repsonse.LegalResponseDto;
import org.example.importexportservice.token.TokenHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

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

    public LegalResponseDto getLegalDetails(String tin) {

        if (tin == null || tin.length() != 9) {
            log.warn("Noto'g'ri TIN uzunligi: {}", tin);
            return null;

        }
        return callApi(
                        ignored -> UriComponentsBuilder.fromUriString(legalUrl)
                                .queryParam("tin", tin)
                                .build()
                                .toUri(),
                        HttpMethod.GET,
                        null,
                        LegalResponseDto.class
        );

    }

    public IndividualResponseDto getIndividualDetails(String pinfl) {

        if (pinfl == null || pinfl.length() != 14) {
            log.warn("Noto'g'ri PINFL uzunligi: {}", pinfl);
            return null;

        }

         Map<String, String> body = Map.of("pinfl",pinfl);
            return callApi(
                uriBuilder -> UriComponentsBuilder.fromUriString(individualsUrl)
                        .build()
                        .toUri(),
                HttpMethod.POST,
        body,
        IndividualResponseDto.class

        );

    }

    private <T> T callApi(
            Function<UriBuilder, URI> uriFunction,
            HttpMethod method,
            Object body,
            Class<T> responseType) {

        try {
            WebClient.RequestBodyUriSpec request = webClient.method(method);

            WebClient.RequestHeadersSpec<?> headersSpec = request
                    .uri(uriFunction)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHolder.getToken())
                    .accept(MediaType.APPLICATION_JSON);

            if (body != null && method == HttpMethod.POST) {
                headersSpec = ((WebClient.RequestBodySpec) headersSpec)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body);
            }

            return headersSpec
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(
                                            new BusinessRegApiException(
                                                    "BusinessReg API xatosi: " + response.statusCode() +
                                                            ", body: " + errorBody)))
                    )
                    .bodyToMono(responseType)
                    .timeout(Duration.ofSeconds(10))
                    .block();

        } catch (Exception e) {
            log.error("BusinessReg API xatosi → Method: {}, Sabab: {}", method, e.getMessage(), e);
            return null;
        }
    }
}
