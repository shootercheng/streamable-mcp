package com.scd;

import com.scd.mcp.tools.CalculateService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class StreamableMcpApplication {

	public static void main(String[] args) {
		SpringApplication.run(StreamableMcpApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider calculateTool(CalculateService calculateService) {
		return MethodToolCallbackProvider.builder().toolObjects(calculateService).build();
	}

	@Bean
	public List<McpServerFeatures.SyncPromptSpecification> greetingPrompts() {
		var prompt = new McpSchema.Prompt("greeting", "A friendly greeting prompt",
				List.of(new McpSchema.PromptArgument("name", "The name to greet", true)));

		var promptSpecification = new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, getPromptRequest) -> {
			String nameArgument = (String) getPromptRequest.arguments().get("name");
			if (nameArgument == null) { nameArgument = "friend"; }
			var userMessage = new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent("Hello " + nameArgument + "! How can I assist you today?"));
			return new McpSchema.GetPromptResult("A personalized greeting message", List.of(userMessage));
		});

		return List.of(promptSpecification);
	}

	@Bean
	public List<McpServerFeatures.SyncCompletionSpecification> myCompletions() {
		var completion = new McpServerFeatures.SyncCompletionSpecification(
				new McpSchema.PromptReference("greeting"),
				(exchange, request) -> {
					// Implementation that returns completion suggestions
					return new McpSchema.CompleteResult(new McpSchema.CompleteResult
							.CompleteCompletion(Arrays.asList("scd", "chengdu"),
							2, true)
					);
				}
		);

		var completion1 = new McpServerFeatures.SyncCompletionSpecification(
				new McpSchema.ResourceReference("file://{path}"),
				(exchange, request) -> {
					// Implementation that returns completion suggestions
					return new McpSchema.CompleteResult(new McpSchema.CompleteResult
							.CompleteCompletion(Arrays.asList("doc/mcp.md", "doc/code_McpAsyncServer.md"),
							2, false)
					);
				}
		);

		return List.of(completion, completion1);
	}

	@Bean
	public List<McpServerFeatures.SyncResourceSpecification> myResource() {
		var resourceSpecification = getSyncResourceSpecification();

		var resourceTemplateSpecification = getSyncResourceTemplateSpecification();

		return List.of(resourceSpecification, resourceTemplateSpecification);
	}

	private static McpServerFeatures.SyncResourceSpecification getSyncResourceTemplateSpecification() {
		// uri.contains("{")  template
		var systemInfoResource1 = new McpSchema.Resource(
				"file://{path}",
				"doc_template",
				"资源模板",
				"application/json",
				new McpSchema.Annotations(List.of(McpSchema.Role.USER), 1.0)
		);
        return new McpServerFeatures.SyncResourceSpecification(systemInfoResource1,
                (exchange, request) -> {
                    try {
                        String uri = request.uri();
                        if (systemInfoResource1.uri().equals(uri)) {
                            uri = "file://doc/mcp.md";
                        }
                        String decodeUri = URLDecoder.decode(uri, StandardCharsets.UTF_8);
                        String filePath = decodeUri.replace("file://", "");
                        String content = new String(Files.readAllBytes(Paths.get(filePath)));
                        return new McpSchema.ReadResourceResult(
                                List.of(new McpSchema.TextResourceContents(uri, "application/json", content)));
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Failed to read file info", e);
                    }
                });
	}

	private static McpServerFeatures.SyncResourceSpecification getSyncResourceSpecification() {
		var systemInfoResource = new McpSchema.Resource(
				"file://doc/mcp.md",
				"doc_mcp",
				"MCP运行记录文档",
				"application/json",
				new McpSchema.Annotations(List.of(McpSchema.Role.USER), 1.0)
		);
        return new McpServerFeatures.SyncResourceSpecification(systemInfoResource,
                (exchange, request) -> {
            try {
                String uri = request.uri();
                String filePath = uri.replace("file://", "");
                String content = new String(Files.readAllBytes(Paths.get(filePath)));
                return new McpSchema.ReadResourceResult(
                        List.of(new McpSchema.TextResourceContents(uri, "application/json", content)));
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to file info", e);
            }
        });
	}


}
