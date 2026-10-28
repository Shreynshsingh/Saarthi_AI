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
 * HTTP client for ai-service-python (FastAPI sidecar). Serves TTS today
 * (POST /tts); sign-language endpoints plug into the same base URL later.
 * No auth header: the sidecar is reachable only inside our own network.
 */
@Configuration
public class PythonServiceConfig {

    @Bean
    public WebClient pythonWebClient(
            WebClient.Builder builder,
            @Value("${saarthi.python-service.url:http://localhost:8000}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(Duration.ofSeconds(20))))
                .build();
    }
}
