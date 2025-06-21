package com.scd.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.server.autoconfigure.McpServerProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@RestController
public class McpStreamableServerController {
    private static final Logger logger = LoggerFactory.getLogger(McpStreamableServerController.class);

    private final ObjectMapper objectMapper;

    private final McpSchema.ServerCapabilities serverCapabilities;

    private final McpSchema.Implementation serverInfo;

    private final String instructions;

    private final CopyOnWriteArrayList<McpServerFeatures.AsyncToolSpecification> tools = new CopyOnWriteArrayList<>();

    private List<String> protocolVersions = List.of(McpSchema.LATEST_PROTOCOL_VERSION);

    public McpStreamableServerController(ObjectMapper objectMapper,
                                         McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpServerProperties serverProperties,
                                         ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> tools,
                                         List<ToolCallbackProvider> toolCallbackProvider) {
        this.objectMapper = objectMapper;

        this.serverInfo = new McpSchema.Implementation(serverProperties.getName(),
                serverProperties.getVersion());

        // Create the server with both tool and resource capabilities
        McpServer.SyncSpecification serverBuilder = McpServer.sync(new WebMvcSseServerTransportProvider(objectMapper, "/-")).serverInfo(serverInfo);

        // Tools
        if (serverProperties.getCapabilities().isTool()) {
            logger.info("Enable tools capabilities, notification: {}",  serverProperties.isToolChangeNotification());
            capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());

            List<McpServerFeatures.SyncToolSpecification> toolSpecifications = new ArrayList<>(
                    tools.stream().flatMap(List::stream).toList());

            List<ToolCallback> providerToolCallbacks = toolCallbackProvider.stream()
                    .map(pr -> List.of(pr.getToolCallbacks()))
                    .flatMap(List::stream)
                    .filter(Objects::nonNull)
                    .toList();
            toolSpecifications.addAll(this.toSyncToolSpecifications(providerToolCallbacks, serverProperties));

            if (!CollectionUtils.isEmpty(toolSpecifications)) {
                for (var tool : toolSpecifications) {
                    this.tools.add(new McpServerFeatures.AsyncToolSpecification(tool.tool(),
                            (exchange, map) -> Mono
                                    .fromCallable(() -> tool.call().apply(new McpSyncServerExchange(exchange), map))
                                    .subscribeOn(Schedulers.boundedElastic())));
                }
                logger.info("Registered tools: {}" , toolSpecifications.size());
            }
        }
        this.serverCapabilities = capabilitiesBuilder.build();
        this.instructions = serverProperties.getInstructions();
    }

    @GetMapping("/mcp")
    public String  mcp() {
        return "OK";
    }

    @PostMapping("/mcp")
    public Object mcpPost(HttpServletRequest httpServletRequest) throws IOException {
        Optional<String> inputParam = getBody(httpServletRequest);
        if (inputParam.isEmpty()) {
            return "not body param";
        }
        logger.info("request param is {}", inputParam.get());
        McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, inputParam.get());
       if (message instanceof McpSchema.JSONRPCRequest request) {
            logger.debug("Received request: {}", request);
            return handleIncomingRequest(request);
        }
        else if (message instanceof McpSchema.JSONRPCNotification notification) {
           logger.debug("Received notification: {}", notification);
            return handleIncomingNotification(notification);
        } else {
            logger.warn("Received unknown message type: {}", message);
            return "";
        }
    }

    private Optional<String> getBody(HttpServletRequest request) {
        try (ServletInputStream servletInputStream = request.getInputStream()) {
            byte[] data = servletInputStream.readAllBytes();
            return Optional.of(new String(data));
        } catch (IOException e) {
            logger.error("read data error ", e);
        }
        return Optional.empty();
    }


    private Object handleIncomingRequest(McpSchema.JSONRPCRequest request) {
            if (McpSchema.METHOD_INITIALIZE.equals(request.method())) {

                McpSchema.InitializeRequest initializeRequest = objectMapper.convertValue(request.params(), new TypeReference<>() {
                });

                logger.info("Client initialize request - Protocol: {}, Capabilities: {}, Info: {}",
                        initializeRequest.protocolVersion(), initializeRequest.capabilities(),
                        initializeRequest.clientInfo());

                String serverProtocolVersion = this.protocolVersions.getLast();

                if (this.protocolVersions.contains(initializeRequest.protocolVersion())) {
                    // If the server supports the requested protocol version, it MUST
                    // respond
                    // with the same version.
                    serverProtocolVersion = initializeRequest.protocolVersion();
                }
                else {
                    logger.warn(
                            "Client requested unsupported protocol version: {}, so the server will suggest the {} version instead",
                            initializeRequest.protocolVersion(), serverProtocolVersion);
                }
                McpSchema.InitializeResult mcpInit = new McpSchema.InitializeResult(serverProtocolVersion, this.serverCapabilities,
                        this.serverInfo, this.instructions);
                return new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), mcpInit, null);

            } else if (McpSchema.METHOD_TOOLS_LIST.equals(request.method())) {

                List<McpSchema.Tool> tools = this.tools.stream().map(McpServerFeatures.AsyncToolSpecification::tool).toList();
                McpSchema.ListToolsResult mcpListTool = new McpSchema.ListToolsResult(tools, null);
                return new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), mcpListTool, null);

            } else if (McpSchema.METHOD_TOOLS_CALL.equals(request.method())) {
                McpSchema.CallToolRequest callToolRequest = objectMapper.convertValue(request.params(), new TypeReference<>() {});

                Optional<McpServerFeatures.AsyncToolSpecification> toolSpecification = this.tools.stream()
                        .filter(tr -> callToolRequest.name().equals(tr.tool().name()))
                        .findAny();

                if (toolSpecification.isEmpty()) {
                    return new McpError("Tool not found: " + callToolRequest.name());
                }

                Object mcpCallResult = toolSpecification.map(tool -> tool.call().apply(null, callToolRequest.arguments()))
                        .orElse(Mono.error(new McpError("Tool not found: " + callToolRequest.name()))).block();
                return new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), mcpCallResult, null);
            }
            return "";
    }

    private String handleIncomingNotification(McpSchema.JSONRPCNotification notification) {
        if (McpSchema.METHOD_NOTIFICATION_INITIALIZED.equals(notification.method())) {

        }
        return "";
    }

    private List<McpServerFeatures.SyncToolSpecification> toSyncToolSpecifications(List<ToolCallback> tools,
                                                                                   McpServerProperties serverProperties) {

        // De-duplicate tools by their name, keeping the first occurrence of each tool
        // name
        return tools.stream() // Key: tool name
                .collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool, // Value:
                        // the
                        // tool
                        // itself
                        (existing, replacement) -> existing)) // On duplicate key, keep the
                // existing tool
                .values()
                .stream()
                .map(tool -> {
                    String toolName = tool.getToolDefinition().name();
                    MimeType mimeType = (serverProperties.getToolResponseMimeType().containsKey(toolName))
                            ? MimeType.valueOf(serverProperties.getToolResponseMimeType().get(toolName)) : null;
                    return McpToolUtils.toSyncToolSpecification(tool, mimeType);
                })
                .toList();
    }
}
