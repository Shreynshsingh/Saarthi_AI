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
 * Groq (OpenAI-compatible) HTTP client. The API key lives only here, server-side -
 * the frontend never sees it. Timeouts guarantee we never leave the user hanging.
 */
@Configuration
public class GroqConfig {

    @Bean
    public WebClient groqWebClient(
            WebClient.Builder builder,
            @Value("${groq.api.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${groq.api.key}") String apiKey) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(Duration.ofSeconds(30))))
                .build();
    }
}
