package org.example.importexportservice.webconfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebConfiguration {


    @Bean
    @Primary
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
