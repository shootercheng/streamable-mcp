package com.scd.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.WebMvcNoSseServerTransportProvider;
import org.springframework.ai.mcp.server.autoconfigure.McpServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class McpConfig {

    @Bean
    public WebMvcNoSseServerTransportProvider webMvcNoSseServerTransportProvider(
            ObjectProvider<ObjectMapper> objectMapperProvider, McpServerProperties serverProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new WebMvcNoSseServerTransportProvider(objectMapper, serverProperties.getBaseUrl(),
                serverProperties.getSseMessageEndpoint());
    }

    @Bean
    public RouterFunction<ServerResponse> mvcNoSSeMcpRouterFunction(WebMvcNoSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }
}
