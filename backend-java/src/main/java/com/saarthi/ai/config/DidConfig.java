package com.saarthi.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * HTTP client for the D-ID Streaming API. The key travels as-is in the
 * Authorization header (Basic ${DID_API_KEY}) — never split, never logged.
 * A blank key still builds a client (never crash startup); the impl refuses
 * to use it and the app behaves as static.
 */
@Configuration
public class DidConfig {

    @Bean
    public WebClient didWebClient(
            WebClient.Builder builder,
            @Value("${did.api.base-url:https://api.d-id.com}") String baseUrl,
            @Value("${did.api.key:}") String apiKey) {
        WebClient.Builder configured = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(Duration.ofSeconds(20))));
        if (apiKey != null && !apiKey.isBlank()) {
            String credentials = "Basic " + apiKey.trim();
            configured = configured.defaultHeader(HttpHeaders.AUTHORIZATION, credentials);
        }
        return configured.build();
    }
}
