package com.saarthi.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Java-side wrapper over saarthi-mcp-server tools. THE only MCP integration
 * point in the live path: MemoryAgent asks here for recent moods before Llama.
 *
 * Resilience order (13_MCP_CONNECTION.md section 13): Redis cache (10 min) ->
 * MCP tool call (5s timeout) -> direct JPA fallback in MemoryAgent. A dead MCP
 * server degrades to one WARN plus silent JPA fallback with a 60s retry
 * window - the conversation never waits and logs never spam. MCP_ENABLED=false
 * skips all MCP traffic entirely (demo-day safety net).
 *
 * Security: user_id is injected here from the JWT-authenticated caller, never
 * from the frontend or Llama. The server rejects calls without it.
 */
@Slf4j
@Service
public class McpMemoryService {

    private static final int MOOD_DAYS = 7;
    private static final String MOOD_CACHE_PREFIX = "mcp:mood:";
    private static final long RETRY_BACKOFF_MS = 60_000L;

    private final McpToolClient mcpClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final java.time.Duration timeout;
    private final java.time.Duration cacheTtl;
    private final AtomicBoolean available = new AtomicBoolean(false);
    private final AtomicLong nextRetryEpochMs = new AtomicLong(0L);

    public McpMemoryService(
            McpToolClient mcpClient,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            @Value("${saarthi.mcp.enabled:true}") boolean enabled,
            @Value("${saarthi.mcp.timeout-ms:5000}") long timeoutMs,
            @Value("${saarthi.mcp.cache-ttl-seconds:600}") long cacheTtlSeconds) {
        this.mcpClient = mcpClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.timeout = java.time.Duration.ofMillis(timeoutMs);
        this.cacheTtl = java.time.Duration.ofSeconds(cacheTtlSeconds);
    }

    /**
     * Startup discovery: initialize the session and list tools. Any failure
     * leaves the service unavailable (JPA fallback) without failing boot.
     */
    @PostConstruct
    void discoverTools() {
        if (!enabled) {
            log.info("[MCP] disabled (MCP_ENABLED=false), MemoryAgent uses direct JPA");
            return;
        }
        try {
            mcpClient.initialize().block(timeout);
            List<String> names = mcpClient.listToolNames().block(timeout);
            if (names == null) {
                names = List.of();
            }
            log.info("[MCP] Client connected: saarthi-mcp-server, tools discovered: {} {}",
                    names.size(), names);
            available.set(true);
        } catch (RuntimeException e) {
            log.warn("[MCP] Server unreachable at startup, JPA fallback active");
        }
    }

    /**
     * Recent moods as "STRESSED, HAPPY" for the Llama prompt. Present (possibly
     * empty for a fresh user) on MCP success; empty on disabled/unavailable/
     * failure - the caller falls back to direct JPA. Never throws, never null.
     */
    public Optional<String> recentMoodsCsv(UUID userId) {
        if (!enabled || !shouldAttempt()) {
            return Optional.empty();
        }
        String key = MOOD_CACHE_PREFIX + userId;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Optional.of(cached);
            }
        } catch (RuntimeException e) {
            log.debug("[MCP] Redis read failed, calling tool uncached");
        }
        long startNanos = System.nanoTime();
        try {
            String json = mcpClient.callToolText("get_recent_moods",
                            Map.of("user_id", userId.toString(), "days", MOOD_DAYS))
                    .block(timeout);
            String csv = extractMoodNames(json);
            try {
                redisTemplate.opsForValue().set(key, csv, cacheTtl);
            } catch (RuntimeException e) {
                log.debug("[MCP] Redis write failed, result still returned");
            }
            log.info("[MCP] get_recent_moods latency: {}ms",
                    (System.nanoTime() - startNanos) / 1_000_000);
            available.set(true);
            return Optional.of(csv);
        } catch (RuntimeException e) {
            available.set(false);
            nextRetryEpochMs.set(System.currentTimeMillis() + RETRY_BACKOFF_MS);
            log.warn("[MCP] get_recent_moods failed, JPA fallback active");
            return Optional.empty();
        }
    }

    private boolean shouldAttempt() {
        if (available.get()) {
            return true;
        }
        return System.currentTimeMillis() >= nextRetryEpochMs.get();
    }

    /**
     * Reads mood names out of a get_recent_moods JSON payload. Blank and
     * non-array payloads yield "" (no moods); malformed JSON throws and the
     * caller falls back to JPA - MCP data must never corrupt the prompt.
     */
    String extractMoodNames(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        final JsonNode rows;
        try {
            rows = objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("mcp-bad-json");
        }
        List<String> names = new ArrayList<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                JsonNode mood = row.path("mood");
                if (mood.isTextual() && !mood.asText().isBlank()) {
                    names.add(mood.asText().trim().toUpperCase());
                }
            }
        }
        return String.join(", ", names);
    }
}
