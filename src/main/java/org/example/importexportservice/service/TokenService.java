package org.example.importexportservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    @Value("${app.api.token-url}")
    private String tokenUrl;

    @Value("${app.api.username}")
    private String username;

    @Value("${app.api.password}")
    private String password;

    private final WebClient webClient;

    public TokenService(WebClient webClient) {
        this.webClient = webClient;
    }

    public String getAccessToken() {
        log.info("Token olish so'rovi yuborilmoqda: {}", tokenUrl);

        String authHeader = "Basic YVJmYU1SOEJSdW5lVGY0MG1wZUhmYnVHNnk4YTpvd1pFdlJSUERjb25vdGJibVI3NENUbWlkQ29h";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("username", username);
        formData.add("password", password);

        try {
            String response = webClient.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                                log.error("Server xatosi: {}, Body: {}", clientResponse.statusCode(), errorBody);
                                return Mono.error(new RuntimeException("OAuth2 xatosi: " + errorBody));
                            })
                    )
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response);
            String accessToken = jsonNode.get("access_token").asText();

            log.info("Token muvaffaqiyatli olingan: {}...", accessToken.substring(0, 15));
            return accessToken;

        } catch (Exception e) {
            log.error("Xatolik: {}", e.getMessage());
            throw new RuntimeException("Access token olishda xato", e);
        }
    }
}
