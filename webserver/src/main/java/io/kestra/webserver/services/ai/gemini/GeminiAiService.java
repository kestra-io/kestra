package io.kestra.webserver.services.ai.gemini;

import com.devskiller.friendly_id.FriendlyId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.adk.agents.*;
import com.google.adk.events.Event;
import com.google.adk.models.Gemini;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.Client;
import com.google.genai.types.*;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.exceptions.AiException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.micronaut.context.annotation.Requires;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.kestra.core.plugins.RegisteredPlugin.*;

@Singleton
@Requires(property = "kestra.ai.type", value = "gemini")
@Requires(property = "kestra.ai.gemini.api-key")
@Slf4j
public class GeminiAiService implements AiServiceInterface {
    private static final String KESTRA_FLOW_AGENT_APP_ID = "kestra_flow_agent";
    private static final String KESTRA_FLOW_BUILDER_NAME = "kestra_flow_builder_agent";
    private static final String ALREADY_VALID_FLOW = "This flow already performs the requested action. Please provide additional instructions if you would like to request modifications.";
    private static final String NON_FLOW_REQUEST_ERROR = "I can only assist with creating Kestra flows.";
    private static final String UNABLE_TO_GENERATE_FLOW_ERROR = "The prompt did not provide enough information to generate a valid flow. Please clarify your request.";
    private static final List<String> POSSIBLE_ERROR_MESSAGES = List.of(ALREADY_VALID_FLOW, NON_FLOW_REQUEST_ERROR, UNABLE_TO_GENERATE_FLOW_ERROR);
    private static final List<String> EXCLUDED_PLUGIN_TYPES = List.of(
        STORAGES_GROUP_NAME,
        SECRETS_GROUP_NAME,
        APPS_GROUP_NAME,
        APP_BLOCKS_GROUP_NAME,
        CHARTS_GROUP_NAME,
        DATA_FILTERS_GROUP_NAME,
        DATA_FILTERS_KPI_GROUP_NAME
    );
    public static final int SEED = 50000;

    private final InMemoryRunner inMemoryRunner;

    public GeminiAiService(
        final GeminiConfiguration geminiConfiguration,
        final PluginRegistry pluginRegistry,
        final JsonSchemaGenerator jsonSchemaGenerator,
        final TenantService tenantService,
        final FlowService flowService
    ) {
        SequentialAgent kestraFlowBuilder = initializeAgents(geminiConfiguration, pluginRegistry, jsonSchemaGenerator, tenantService, flowService);

        inMemoryRunner = new InMemoryRunner(kestraFlowBuilder);
    }

    private static LlmAgent createMostRelevantTypesAgent(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, Gemini model) {
        Map<String, String> descriptionByType = pluginRegistry.plugins().stream()
            .flatMap(plugin -> plugin.allClassGrouped().entrySet().stream().filter(e -> !EXCLUDED_PLUGIN_TYPES.contains(e.getKey())).map(Map.Entry::getValue).flatMap(Collection::stream))
            .map(clazz -> Map.entry(clazz.getName(), Optional.ofNullable(((Class<?>) clazz).getDeclaredAnnotation(io.swagger.v3.oas.annotations.media.Schema.class))))
            .filter(e -> !e.getValue().map(io.swagger.v3.oas.annotations.media.Schema::deprecated).orElse(false))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue()
                        .map(io.swagger.v3.oas.annotations.media.Schema::title)
                        .orElse("")
                )
            );
        String pluginRelevanceInstructions;
        String serializedPlugins;
        try {
            serializedPlugins = JacksonMapper.ofJson().writeValueAsString(descriptionByType.entrySet().stream().map(e ->
                Map.of("type", e.getKey(), "description", e.getValue())
            ).toList());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize plugin types for Gemini AI agent", e);
            serializedPlugins = "[]";
        }

        pluginRelevanceInstructions = """
            You are a plugin specialist for the Kestra Flow builder service. Your task is to identify the most relevant plugin types for building a Kestra Flow based on the user's intent, using the list below. Follow these guidelines:
            - Match plugin types by technology name or similar terms (e.g., "mongo" matches "MongoDB", "postgres" matches "PostgreSQL").
            - If a task requires repeating some action a set number of times, use the ForEach task. If the user asks to perform some action until a condition is met, use the LoopUntil task.
            - If data fetching is required, include appropriate tasks (e.g., database or HTTP tasks). If the flow needs to obtain data not already available, include a relevant data-fetching task.
            - For state-detection concepts, include KV tasks to fetch and store state to track changes between executions.
            - Triggers initiate a Flow execution based on events or interval, while tasks perform actions within a Flow. Always distinguish between them and include both as needed.
            - Include AT LEAST ONE trigger if execution should start based on an event or interval.
            - Every flow must include at least one task that is not a trigger.
            - ALWAYS include all plugin types present in the current Flow YAML. If the user asks for troubleshooting, also include additional types if required.
            - If you don't include any plugin types, explain why.
            Use only the plugin types from the list below. You may select up to 10 types but you MUST ALWAYS return at least one valid type. Below is the list of all available plugin types in Kestra, each formatted as 'type:description':
            ```
            %s
            ```
            IMPORTANT: Respond with a comma-separated list of valid plugin types from the list above. Return only the types. Do not include any additional explanation or text.
            """.formatted(
            serializedPlugins
        );


        return LlmAgent.builder()
            .name("kestra_plugin_relevance_agent")
            .description("An agent to identify most relevant plugins to build a Kestra Flow YAML.")
            .model(model)
            .generateContentConfig(GenerateContentConfig.builder().temperature(0.7f).seed(SEED).maxOutputTokens(1024).build())
            .instruction(pluginRelevanceInstructions)
            .outputKey("mostRelevantTypes")
            .afterAgentCallback(callbackContext -> {
                String mostRelevantTypes = ((String) callbackContext.state().get("mostRelevantTypes")).trim();
                if (POSSIBLE_ERROR_MESSAGES.contains(mostRelevantTypes)) {
                    callbackContext.state().put("responseForUser", mostRelevantTypes);
                    return shortCircuitLlmResponse(callbackContext, mostRelevantTypes);
                }

                if (mostRelevantTypes.isEmpty()) {
                    return Maybe.empty();
                }

                String[] identifiedTypes = mostRelevantTypes.split(" ?, ?");

                JsonNode minifiedSchema = JacksonMapper.ofJson().convertValue(jsonSchemaGenerator.schemas(Flow.class, false, Arrays.asList(identifiedTypes)), JsonNode.class);
                minifySchema(minifiedSchema);
                try {
                    callbackContext.state().put(
                        "flowSchema",
                        JacksonMapper.ofJson().writeValueAsString(minifiedSchema)
                    );
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize flow schema for AI Agent", e);
                }

                return Maybe.empty();
            })
            .build();
    }

    private static LlmAgent createYamlBuilderAgent(Gemini model) {
        return LlmAgent.builder()
            .name("kestra_init_flow_builder_agent")
            .description("An agent to bootstrap a Kestra Flow YAML.")
            .model(model)
            .generateContentConfig(GenerateContentConfig.builder().temperature(0.7f).seed(SEED).maxOutputTokens(50000).build())
            .outputKey("responseForUser")
            .beforeAgentCallback((beforeAgentCallback) -> {
                Object responseForUser = beforeAgentCallback.state().get("responseForUser");
                if (responseForUser != null) {
                    return shortCircuitLlmResponse(beforeAgentCallback, responseForUser.toString());
                }

                return Maybe.empty();
            })
            .instruction(
                """
                    You are an expert in generating Kestra Flow YAML. Your task is to generate a valid Kestra Flow YAML strictly following the schema below:
                    ```
                    {{flowSchema}}
                    ```
                    
                    Here are the rules:
                    - Use examples, properties, and outputs only as specified in the schema.
                    - If the user asks for troubleshooting, try to fix any related expression or task.
                    - If the user current flow seems unrelated, you can discard it and start from scratch, otherwise try to keep what you can from the current Flow while still replying to the user's intent.
                    - Identify if the user requests an addition, deletion, or modification of specific tasks, or a full rewrite of their flow. Only modify the relevant part.
                      If the change scope is unclear, discard the initial Flow if it does not fit the user’s needs.
                      Avoid duplicating existing intent (e.g., if the Flow logs "hi" and the user wants "hello world", replace the existing message).
                    - Use only the types and properties explicitly defined in the above schema. Do not invent, guess, or use properties from other types.
                    - If a property is not present in the schema for a given type or you are unsure whether it exists or not DO NOT INCLUDE IT.
                    - The type of each property must match the schema exactly.
                    - Do not use any types not present in the schema in a given section.
                    - Use only {{...}} expressions available in the provided examples and schema.
                    - The following expressions are always available: `{{flow.id}}`, `{{flow.namespace}}`, `{{flow.name}}`, `{{flow.description}}`, `{{execution.id}}`.
                    - Use provided examples to guide property usage and structure. Adapt them as needed; do not copy them verbatim.
                    - Some properties accept multiple types (string, array, object). Choose the right type based on the provided examples.
                    - Adjust `default` property values to match the user's intent.
                    - Flow-level outputs are used to return values from the Flow execution. If the user asks that the Flow should output something, you can include flow outputs. Otherwise, use only task outputs and only if explicitly requested to pass data between tasks.
                    - Use ForEach task to perform an action a given number of times; use LoopUntil to perform some action until a condition is met.
                    - Detect and include any required data-fetching tasks (HTTP, database, etc.).
                    - For state-detection concepts, include KV tasks to fetch and store state to track changes between executions.
                    - Triggers initiate a Flow execution based on events or interval, while tasks perform actions within a Flow. Always distinguish between them and include both as needed.
                    - Include AT LEAST ONE trigger if execution should start based on an event or interval.
                    - Unless specified by the user, never assume a local port to serve any content, always use a remote URL (like a public HTTP server) to fetch content.
                    - Unless specified by the user, do not use any authenticated API, always use public APIs or those that don't require authentication.
                    - To avoid escaping quotes, use double quotes first and if you need quotes inside, use single ones. Only escape them if you have 3+ level quotes, for example: `message: "Hello {{inputs.userJson | jq('.name')}}"`.
                    - A property key is unique within each type.
                    - When fetching data from the JDBC plugin, always use fetchType: STORE.
                    - Manipulating date in expressions can be done through `dateAdd` (`{{now()|dateAdd(-1,'DAYS')}}`) and `date` filters (`{{"July 24, 2001"|date("yyyy-MM-dd",existingFormat="MMMM dd, yyyy")}}`)
                    - Always preserve root-level `id` and `namespace` if provided.
                    - If the user uses vague references (“it,” “that”), infer context from the current Flow YAML.
                    - Except for error scenarios, output only the raw YAML, with no explanation or additional text.
                    
                    IMPORTANT: If the user prompt cannot be fulfilled with the schema, instead of generating a Flow, reply: "%s".
                    Do not invent properties or types. Strictly follow the provided schema.""".formatted(NON_FLOW_REQUEST_ERROR)
            )
            .build();
    }

    private static LoopAgent createYamlFixingAgent(Gemini model, TenantService tenantService, FlowService flowService) {
        LlmAgent yamlFixerAgent = LlmAgent.builder()
            .name("kestra_flow_yaml_fixer")
            .description("An agent to build a Kestra Flow YAML.")
            .generateContentConfig(GenerateContentConfig.builder().temperature(0.7f).seed(SEED).maxOutputTokens(50000).build())
            .beforeAgentCallback((beforeAgentCallback) -> {
                String responseForUser = (String) beforeAgentCallback.state().get("responseForUser");
                if (responseForUser == null || responseForUser.isBlank()) {
                    return shortCircuitLlmResponse(beforeAgentCallback, UNABLE_TO_GENERATE_FLOW_ERROR);
                }

                if (POSSIBLE_ERROR_MESSAGES.contains(responseForUser)) {
                    return shortCircuitLlmResponse(beforeAgentCallback, responseForUser);
                }

                if (responseForUser.startsWith("```")) {
                    responseForUser = responseForUser.replaceAll("\\s?```(?:yaml)?\\s?", "");
                }

                beforeAgentCallback.state().put("responseForUser", responseForUser);

                List<ValidateConstraintViolation> validate = flowService.validate(tenantService.resolveTenant(), responseForUser);

                List<String> constraints = validate.stream().map(ValidateConstraintViolation::getConstraints).filter(c -> c != null && !c.isEmpty()).toList();
                beforeAgentCallback.state().put("validationErrors", String.join("\n", constraints));

                if (constraints.isEmpty()) {
                    String currentFlowYamlInstruction = beforeAgentCallback.userContent().get().parts().get().getFirst().text().get();
                    String initialYaml = currentFlowYamlInstruction.substring("Current Flow YAML:\n```yaml\n".length(), currentFlowYamlInstruction.lastIndexOf("\n```"));
                    if (initialYaml.equals(responseForUser)) {
                        responseForUser = ALREADY_VALID_FLOW;
                        beforeAgentCallback.state().put("responseForUser", responseForUser);
                    }
                    return shortCircuitLlmResponse(beforeAgentCallback, responseForUser);
                }

                return Maybe.empty();
            })
            .model(model)
            .instruction(
                """
                    You are an expert in debugging incorrect Kestra Flow YAML. Fix only the provided validation issues: `{{validationErrors}}`
                    - If the YAML is not valid, modify only the parts that are invalid.
                    - Preserve the original intent of the Flow.
                    - Reply only with the fixed YAML.
                    - If you cannot fix the YAML, reply with "%s".
                    - You must strictly follow the schema from the current discussion; these are the only properties and outputs you may use.
                    IMPORTANT: Your response must be a valid Kestra Flow YAML""".formatted(UNABLE_TO_GENERATE_FLOW_ERROR)
            )
            .afterModelCallback((afterModelContext, llmResponse) -> {
                String responseForUser = llmResponse.content().map(Content::text).orElse("");
                if (responseForUser.equals(UNABLE_TO_GENERATE_FLOW_ERROR)) {
                    return shortCircuitLlmResponse(afterModelContext, responseForUser).map(c -> LlmResponse.builder().content(c).build());
                }
                if (responseForUser.startsWith("```")) {
                    responseForUser = responseForUser.replaceAll("\\s?```(?:yaml)?\\s?", "");
                }
                afterModelContext.state().put("responseForUser", responseForUser);
                return Maybe.empty();
            })
            .build();

        return LoopAgent.builder()
            .name("kestra_flow_yaml_fixing_loop")
            .description("An agent to fix Kestra Flow YAML.")
            .subAgents(yamlFixerAgent)
            .maxIterations(5)
            .build();
    }

    private static SequentialAgent initializeAgents(GeminiConfiguration geminiConfiguration, PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, TenantService tenantService, FlowService flowService) {
        Gemini model = new Gemini(geminiConfiguration.modelName(), Client.builder().apiKey(geminiConfiguration.apiKey()).build());
        LlmAgent mostRelevantTypesAgent = createMostRelevantTypesAgent(pluginRegistry, jsonSchemaGenerator, model);

        LlmAgent yamlBuilderAgent = createYamlBuilderAgent(model);

        LoopAgent yamlFixingAgent = createYamlFixingAgent(model, tenantService, flowService);

        return SequentialAgent.builder()
            .name(KESTRA_FLOW_BUILDER_NAME)
            .subAgents(mostRelevantTypesAgent, yamlBuilderAgent, yamlFixingAgent)
            .afterAgentCallback(callbackContext -> {
                String responseForUser = (String) callbackContext.state().get("responseForUser");
                if (responseForUser == null || responseForUser.isBlank()) {
                    return Maybe.just(
                        Content.builder()
                            .role("model")
                            .parts(List.of(Part.fromText(UNABLE_TO_GENERATE_FLOW_ERROR)))
                            .build()
                    );
                }

                return Maybe.just(
                    Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText(responseForUser)))
                        .build()
                );
            })
            .build();
    }

    // Utility to minify a JSON schema by removing unnecessary fields
    private static void minifySchema(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.remove("$dynamic");
            obj.remove("$group");
            if (obj.optional("default").map(d -> d.isBoolean() && !d.asBoolean()).orElse(false)) {
                obj.remove("default");
            }
            obj.properties().forEach(e -> minifySchema(e.getValue()));
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode item : arr) {
                minifySchema(item);
            }
        }
    }

    private static Maybe<Content> shortCircuitLlmResponse(CallbackContext callbackContext, String response) {
        callbackContext.eventActions().setEscalate(true);
        return Maybe.just(Content.builder()
            .role("model")
            .parts(List.of(Part.fromText(response)))
            .build()
        );
    }

    @Override
    public Mono<String> generateFlow(FlowGenerationPrompt flowGenerationPrompt) {
        String generatedUserId = FriendlyId.createFriendlyId();
        Content userMsg = Content.fromParts(
            Part.fromText("Current Flow YAML:\n```yaml\n" + flowGenerationPrompt.flowYaml() + "\n```"),
            Part.fromText("User's prompt:\n```\n" + flowGenerationPrompt.userPrompt() + "\n```")
        );

        AtomicReference<Session> sessionRef = new AtomicReference<>();
        return Mono.from(
            inMemoryRunner.sessionService().createSession(KESTRA_FLOW_AGENT_APP_ID, generatedUserId)
                .doOnSuccess(sessionRef::set)
                .flatMapPublisher(session -> inMemoryRunner.runAsync(session, userMsg, RunConfig.builder().build()))
                .filter(e -> e.finalResponse() && e.author().equals(KESTRA_FLOW_BUILDER_NAME))
                .map(Event::stringifyContent)
                .reduce("", (acc, content) -> acc + content)
                .doOnSuccess(s -> {
                    if (POSSIBLE_ERROR_MESSAGES.contains(s)) {
                        throw new AiException(s);
                    }
                })
                .observeOn(Schedulers.io())
                .doFinally(() -> inMemoryRunner.sessionService().deleteSession(KESTRA_FLOW_AGENT_APP_ID, generatedUserId, sessionRef.get().id()).blockingAwait())
                .toFlowable()
        );
    }
}
