package io.modelcontextprotocol.server.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class WebMvcNoSseServerTransportProvider implements McpServerTransportProvider {

	private static final Logger logger = LoggerFactory.getLogger(WebMvcNoSseServerTransportProvider.class);

	public static final String RESULT_KEY = "RESULT_KEY";

	public static final String DEFAULT_MCP_ENDPOINT = "/mcp";

	private final ObjectMapper objectMapper;

	private final String mcpEndpoint;

	private final String baseUrl;

	private final RouterFunction<ServerResponse> routerFunction;

	private McpServerSession.Factory sessionFactory;

	public WebMvcNoSseServerTransportProvider(ObjectMapper objectMapper) {
		this(objectMapper, DEFAULT_MCP_ENDPOINT);
	}

	public WebMvcNoSseServerTransportProvider(ObjectMapper objectMapper, String mcpEndpoint) {
		this(objectMapper, "", mcpEndpoint);
	}

	public WebMvcNoSseServerTransportProvider(ObjectMapper objectMapper, String baseUrl, String mcpEndpoint) {
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		Assert.notNull(baseUrl, "Message base URL must not be null");
		Assert.notNull(mcpEndpoint, "Message endpoint must not be null");

		this.objectMapper = objectMapper;
		this.baseUrl = baseUrl;
		this.mcpEndpoint = mcpEndpoint;
		this.routerFunction = RouterFunctions.route()
			.POST(this.baseUrl + this.mcpEndpoint, this::handleMessage)
			.build();
	}

	private ServerResponse handleMessage(ServerRequest request) {
		String sessionId = request.headers().firstHeader("mcp-session-id");
		if (sessionId == null) {
			sessionId = UUID.randomUUID().toString();
		}
		logger.debug("current request session id {}", sessionId);
		Map<String, String> requestMap = request.headers().asHttpHeaders().toSingleValueMap();
		logger.debug("current request session id {} request map {}", sessionId, requestMap);
		WebMvcNoSseMcpSessionTransport sessionTransport = new WebMvcNoSseMcpSessionTransport(sessionId, request);
		McpServerSession session = sessionFactory.create(sessionTransport);

		try {
			String body = request.body(String.class);
			McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, body);

			if (message instanceof McpSchema.JSONRPCNotification) {
				return ServerResponse.ok().build();
			}

			// Process the message through the session's handle method
			session.handle(message).block(); // Block for WebMVC compatibility

			Object result = request.servletRequest().getAttribute(RESULT_KEY);
			logger.debug("current request session id {} result {}", sessionId, result);
			return result != null ? ServerResponse.ok().body(result) : ServerResponse.ok().build();
		}
		catch (IllegalArgumentException | IOException e) {
			logger.error("Failed to deserialize message: {}", e.getMessage());
			return ServerResponse.badRequest().body(new McpError("Invalid message format"));
		}
		catch (Exception e) {
			logger.error("Error handling message: {}", e.getMessage(), e);
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new McpError(e.getMessage()));
		}
	}

	@Override
	public void setSessionFactory(McpServerSession.Factory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public Mono<Void> notifyClients(String method, Object params) {
		return Mono.fromRunnable(() -> {
		});
	}

	@Override
	public void close() {
	}

	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(() -> {
		});
	}

	public RouterFunction<ServerResponse> getRouterFunction() {
		return routerFunction;
	}

	private class WebMvcNoSseMcpSessionTransport implements McpServerTransport {

		private final String sessionId;

		private final ServerRequest request;

		WebMvcNoSseMcpSessionTransport(String sessionId, ServerRequest request) {
			this.sessionId = sessionId;
			this.request = request;
		}

		@Override
		public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
			return Mono.fromRunnable(() -> {
				try {
					request.servletRequest().setAttribute(RESULT_KEY, message);
				}
				catch (Exception e) {
					logger.error("Failed to send message to session {}: {}", sessionId, e.getMessage(), e);
					throw new McpError(e);
				}
			});
		}

		/**
		 * Converts data from one type to another using the configured ObjectMapper.
		 * @param data The source data object to convert
		 * @param typeRef The target type reference
		 * @return The converted object of type T
		 * @param <T> The target type
		 */
		@Override
		public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
			return objectMapper.convertValue(data, typeRef);
		}

		@Override
		public Mono<Void> closeGracefully() {
			return Mono.fromRunnable(() -> {
			});
		}

		@Override
		public void close() {
		}

	}

}
