package com.saarthi.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP resilience contract: disabled/down/malformed all yield Optional.empty
 * (the MemoryAgent JPA fallback signal), while genuine empty history yields
 * Optional.of(""). No real MCP server, no real Redis - fakes only.
 */
class McpMemoryServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void disabledSkipsClientAndRedisEntirely() {
        AtomicBoolean touched = new AtomicBoolean(false);
        McpToolClient client = fakeClient(touched, null, null);
        McpMemoryService service = new McpMemoryService(client, fakeRedis(touched),
                new ObjectMapper(), false, 5000L, 600L);

        assertEquals(Optional.empty(), service.recentMoodsCsv(USER));
        assertTrue(!touched.get(), "Disabled must touch nothing");
    }

    @Test
    void cacheHitReturnsWithoutToolCall() {
        AtomicInteger calls = new AtomicInteger(0);
        McpToolClient client = fakeClient(null, calls, (name, args) -> Mono.just("[]"));
        Map<String, String> store = new HashMap<>();
        store.put("mcp:mood:" + USER, "TIRED");
        McpMemoryService service = new McpMemoryService(client, fakeRedis(store),
                new ObjectMapper(), true, 5000L, 600L);

        assertEquals(Optional.of("TIRED"), service.recentMoodsCsv(USER));
        assertEquals(0, calls.get());
    }

    @Test
    void cacheMissCallsToolParsesAndCaches() {
        AtomicInteger calls = new AtomicInteger(0);
        String json = "[{\"mood\":\"stressed\",\"confidence\":0.9},{\"mood\":\"HAPPY\"}]";
        McpToolClient client = fakeClient(null, calls, (name, args) -> Mono.just(json));
        Map<String, String> store = new HashMap<>();
        McpMemoryService service = new McpMemoryService(client, fakeRedis(store),
                new ObjectMapper(), true, 5000L, 600L);

        assertEquals(Optional.of("STRESSED, HAPPY"), service.recentMoodsCsv(USER));
        assertEquals(1, calls.get());
        assertEquals("STRESSED, HAPPY", store.get("mcp:mood:" + USER));
    }

    @Test
    void toolFailureReturnsEmptyAndBacksOff() {
        AtomicInteger calls = new AtomicInteger(0);
        McpToolClient client = fakeClient(null, calls,
                (name, args) -> Mono.error(new RuntimeException("refused")));
        McpMemoryService service = new McpMemoryService(client, fakeRedis(new HashMap<>()),
                new ObjectMapper(), true, 5000L, 600L);

        assertEquals(Optional.empty(), service.recentMoodsCsv(USER));
        assertEquals(Optional.empty(), service.recentMoodsCsv(USER));
        assertEquals(1, calls.get(), "Second call must back off silently");
    }

    @Test
    void malformedJsonReturnsEmpty() {
        McpToolClient client =
                fakeClient(null, null, (name, args) -> Mono.just("not-json{{{"));
        McpMemoryService service = new McpMemoryService(client, fakeRedis(new HashMap<>()),
                new ObjectMapper(), true, 5000L, 600L);

        assertEquals(Optional.empty(), service.recentMoodsCsv(USER));
    }

    @Test
    void emptyHistoryReturnsPresentEmpty() {
        McpToolClient client = fakeClient(null, null, (name, args) -> Mono.just("[]"));
        McpMemoryService service = new McpMemoryService(client, fakeRedis(new HashMap<>()),
                new ObjectMapper(), true, 5000L, 600L);

        assertEquals(Optional.of(""), service.recentMoodsCsv(USER));
    }

    @Test
    void discoverFailureStillAttemptsFirstCall() {
        AtomicInteger calls = new AtomicInteger(0);
        McpToolClient client = new McpToolClient() {
            @Override
            public Mono<Void> initialize() {
                return Mono.error(new RuntimeException("down at boot"));
            }

            @Override
            public Mono<List<String>> listToolNames() {
                return Mono.just(List.of());
            }

            @Override
            public Mono<String> callToolText(String name, Map<String, Object> arguments) {
                calls.incrementAndGet();
                return Mono.just("[]");
            }
        };
        McpMemoryService service = new McpMemoryService(client, fakeRedis(new HashMap<>()),
                new ObjectMapper(), true, 5000L, 600L);

        service.discoverTools();
        assertEquals(Optional.of(""), service.recentMoodsCsv(USER));
        assertEquals(1, calls.get(), "Boot failure must not brick later calls");
    }

    @Test
    void extractMoodNamesSkipsBlankAndMissing() {
        McpMemoryService service = new McpMemoryService(dummyClient(), fakeRedis(new HashMap<>()),
                new ObjectMapper(), true, 5000L, 600L);

        assertEquals("STRESSED", service.extractMoodNames(
                "[{\"mood\":\" stressed \"},{\"mood\":\"\"},{\"other\":1}]"));
        assertEquals("", service.extractMoodNames("[]"));
        assertEquals("", service.extractMoodNames("{\"error\":\"x\"}"));
        assertEquals("", service.extractMoodNames("  "));
        assertThrows(IllegalStateException.class, () -> service.extractMoodNames("{oops"));
    }

    private interface ToolHandler {
        Mono<String> call(String name, Map<String, Object> args);
    }

    private static McpToolClient fakeClient(AtomicBoolean touched, AtomicInteger calls,
                                            ToolHandler handler) {
        return (McpToolClient) Proxy.newProxyInstance(McpMemoryServiceTest.class.getClassLoader(),
                new Class<?>[]{McpToolClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("callToolText")) {
                        if (touched != null) {
                            touched.set(true);
                        }
                        if (calls != null) {
                            calls.incrementAndGet();
                        }
                        return handler.call((String) args[0], (Map<String, Object>) args[1]);
                    }
                    if (method.getName().equals("initialize")) {
                        return Mono.empty();
                    }
                    if (method.getName().equals("listToolNames")) {
                        return Mono.just(List.of());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static McpToolClient dummyClient() {
        return (McpToolClient) Proxy.newProxyInstance(McpMemoryServiceTest.class.getClassLoader(),
                new Class<?>[]{McpToolClient.class}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static RedisTemplate<String, String> fakeRedis(Map<String, String> store) {
        return fakeRedis(null, store);
    }

    private static RedisTemplate<String, String> fakeRedis(AtomicBoolean touched) {
        return fakeRedis(touched, new HashMap<>());
    }

    private static RedisTemplate<String, String> fakeRedis(AtomicBoolean touched,
                                                           Map<String, String> store) {
        ValueOperations<String, String> ops = (ValueOperations<String, String>) Proxy.newProxyInstance(
                McpMemoryServiceTest.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class}, (proxy, method, args) -> {
                    if (method.getName().equals("get")) {
                        if (touched != null) {
                            touched.set(true);
                        }
                        return store.get(args[0]);
                    }
                    if (method.getName().equals("set")) {
                        store.put((String) args[0], (String) args[1]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        RedisTemplate<String, String> template = new RedisTemplate<>() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return ops;
            }
        };
        return template;
    }
}
