package com.saarthi.ai.mcp;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure delegation from McpToolClient to the official MCP Java SDK client.
 * No timeouts, no caching, no fallbacks here - McpMemoryService owns all
 * resilience. Constructed by McpClientConfig, never new'd elsewhere.
 */
public class McpSdkToolClient implements McpToolClient {

    private final McpAsyncClient mcpClient;

    public McpSdkToolClient(McpAsyncClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public Mono<Void> initialize() {
        return mcpClient.initialize().then();
    }

    @Override
    public Mono<List<String>> listToolNames() {
        return mcpClient.listTools()
                .map(result -> result == null || result.tools() == null ? List.<String>of()
                        : result.tools().stream()
                                .map(McpSchema.Tool::name)
                                .collect(Collectors.toList()));
    }

    @Override
    public Mono<String> callToolText(String name, Map<String, Object> arguments) {
        return mcpClient.callTool(new McpSchema.CallToolRequest(name, arguments))
                .map(result -> {
                    if (result == null || Boolean.TRUE.equals(result.isError())
                            || result.content() == null) {
                        throw new IllegalStateException("mcp-tool-error");
                    }
                    return result.content().stream()
                            .filter(McpSchema.TextContent.class::isInstance)
                            .map(content -> ((McpSchema.TextContent) content).text())
                            .collect(Collectors.joining());
                });
    }
}
