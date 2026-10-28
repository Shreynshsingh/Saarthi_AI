package com.saarthi.ai.mcp;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Narrow seam over the MCP Java SDK. McpMemoryService depends only on this
 * interface (trivially faked in unit tests), while McpSdkToolClient adapts
 * the concrete SDK client. If the SDK renames a method in v3, exactly one
 * class changes.
 */
public interface McpToolClient {

    /** Opens the MCP session. Errors when the server is unreachable. */
    Mono<Void> initialize();

    /** Tool names the server exposes. Errors when the server is unreachable. */
    Mono<List<String>> listToolNames();

    /**
     * Calls one tool and joins its text content. Errors on transport failure
     * or when the server marks the result isError - never returns null.
     */
    Mono<String> callToolText(String name, Map<String, Object> arguments);
}
