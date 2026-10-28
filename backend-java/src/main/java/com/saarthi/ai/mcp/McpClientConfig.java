package com.saarthi.ai.mcp;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * MCP client transport to saarthi-mcp-server (Python FastMCP, Streamable HTTP).
 * Building the client opens no connection - initialize() and discovery happen
 * in McpMemoryService with fallbacks, so a down MCP server can never fail
 * Spring boot. Internal traffic only, never exposed to the frontend.
 */
@Configuration
public class McpClientConfig {

    @Bean
    public McpAsyncClient mcpAsyncClient(
            @Value("${saarthi.mcp.server-url:http://localhost:8001}") String serverUrl,
            @Value("${saarthi.mcp.endpoint:/mcp}") String endpoint,
            @Value("${saarthi.mcp.timeout-ms:5000}") long timeoutMs) {
        HttpClientStreamableHttpTransport transport =
                HttpClientStreamableHttpTransport.builder(serverUrl + endpoint).build();
        return McpClient.async(transport)
                .requestTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Bean
    public McpToolClient mcpToolClient(McpAsyncClient mcpClient) {
        return new McpSdkToolClient(mcpClient);
    }
}
