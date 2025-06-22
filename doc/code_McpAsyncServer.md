```java
private McpServerSession.RequestHandler<McpSchema.CompleteResult> completionCompleteRequestHandler() {
		return (exchange, params) -> {
			McpSchema.CompleteRequest request = parseCompletionParams(params);

			if (request.ref() == null) {
				return Mono.error(new McpError("ref must not be null"));
			}

			if (request.ref().type() == null) {
				return Mono.error(new McpError("type must not be null"));
			}

			String type = request.ref().type();

			String argumentName = request.argument().name();

			// check if the referenced resource exists
			if (type.equals("ref/prompt") && request.ref() instanceof McpSchema.PromptReference promptReference) {
				McpServerFeatures.AsyncPromptSpecification promptSpec = this.prompts.get(promptReference.name());
				if (promptSpec == null) {
					return Mono.error(new McpError("Prompt not found: " + promptReference.name()));
				}
				if (!promptSpec.prompt()
					.arguments()
					.stream()
					.filter(arg -> arg.name().equals(argumentName))
					.findFirst()
					.isPresent()) {

					return Mono.error(new McpError("Argument not found: " + argumentName));
				}
			}

			if (type.equals("ref/resource") && request.ref() instanceof McpSchema.ResourceReference resourceReference) {
				McpServerFeatures.AsyncResourceSpecification resourceSpec = this.resources.get(resourceReference.uri());
				if (resourceSpec == null) {
					return Mono.error(new McpError("Resource not found: " + resourceReference.uri()));
				}
				if (!uriTemplateManagerFactory.create(resourceSpec.resource().uri())
					.getVariableNames()
					.contains(argumentName)) {
					return Mono.error(new McpError("Argument not found: " + argumentName));
				}

			}

			McpServerFeatures.AsyncCompletionSpecification specification = this.completions.get(request.ref());

			if (specification == null) {
				return Mono.error(new McpError("AsyncCompletionSpecification not found: " + request.ref()));
			}

			return specification.completionHandler().apply(exchange, request);
		};
	}
```