package io.kestra.core.runners.pebble.filters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.kestra.core.serializers.JacksonMapper;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class JqFilter implements Filter {
    private static final AtomicReference<Scope> CACHED_SCOPE = new AtomicReference<>();
    private final List<String> argumentNames = new ArrayList<>();

    public JqFilter() {
        this.argumentNames.add("expression");
    }

    private static Scope getScope() {
        if (CACHED_SCOPE.get() == null) {
            synchronized (CACHED_SCOPE) {
                if (CACHED_SCOPE.get() == null) {
                    try {
                        Scope scope = Scope.newEmptyScope();
                        try {
                            BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, scope);
                            log.debug("Successfully loaded JQ functions using standard loader");
                        } catch (Exception e) {
                            log.warn("Standard JQ function loading failed, attempting fallback loading method", e);
                            try {
                                loadJqFunctionsManually(scope);
                                log.debug("Successfully loaded JQ functions using manual loader");
                            } catch (Exception manualEx) {
                                log.warn("Manual JQ function loading also failed", manualEx);
                                try {
                                    log.info("Using empty JQ scope as fallback - only basic operations will be available");
                                } catch (Exception fallbackEx) {
                                    log.error("All JQ function loading methods failed", fallbackEx);
                                }
                            }
                        }
                        CACHED_SCOPE.set(scope);
                    } catch (Exception e) {
                        log.error("Failed to initialize JQ filter", e);
                        CACHED_SCOPE.set(Scope.newEmptyScope());
                    }
                }
            }
        }
        return CACHED_SCOPE.get();
    }
    
    private static void loadJqFunctionsManually(Scope scope) throws IOException {
        final String resourcePath = "net/thisptr/jackson/jq/jq.json";
        InputStream is = null;
        
        try {
            is = JqFilter.class.getResourceAsStream("/" + resourcePath);
            
            if (is == null) {
                is = JqFilter.class.getClassLoader().getResourceAsStream(resourcePath);
            }
            
            if (is == null) {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
            }
            
            if (is == null) {
                is = ClassLoader.getSystemResourceAsStream(resourcePath);
            }
            
            if (is != null) {
                try {
                    BuiltinFunctionLoader.loadFunctions(JqFilter.class.getClassLoader(), is, scope);
                    log.info("Successfully loaded JQ functions from {}", resourcePath);
                } catch (Exception e) {
                    log.warn("Error loading jq functions from stream", e);
                    throw e;
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        log.warn("Failed to close jq.json input stream", e);
                    }
                }
            } else {
                throw new IOException("Resource not found: " + resourcePath);
            }
        } catch (Exception e) {
            log.error("Exception during jq function loading", e);
            throw new IOException("Failed to load jq functions", e);
        }
    }

    @Override
    public List<String> getArgumentNames() {
        return this.argumentNames;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (!args.containsKey("expression")) {
            throw new PebbleException(null, "The 'jq' filter expects an argument 'expression'.", lineNumber, self.getName());
        }

        String pattern = (String) args.get("expression");
        
        try {
            Scope scope = getScope();
            JsonQuery q = JsonQuery.compile(pattern, Versions.JQ_1_6);

            JsonNode in;
            if (input instanceof String stringValue) {
                try {
                    in = JacksonMapper.ofJson().readTree(stringValue);
                } catch (Exception e) {
                    log.debug("Failed to parse input as JSON string, trying as raw value: {}", e.getMessage());
                    ObjectNode objectNode = JacksonMapper.ofJson().createObjectNode();
                    objectNode.put("value", stringValue);
                    in = objectNode;
                }
            } else {
                in = JacksonMapper.ofJson().valueToTree(input);
            }

            final List<Object> out = new ArrayList<>();

            try {
                q.apply(scope, in, v -> {
                    if (v instanceof TextNode) {
                        out.add(v.textValue());
                    } else if (v instanceof NullNode) {
                        out.add(null);
                    } else if (v instanceof NumericNode) {
                        out.add(v.numberValue());
                    } else if (v instanceof BooleanNode) {
                        out.add(v.booleanValue());
                    } else if (v instanceof ObjectNode) {
                        out.add(JacksonMapper.ofJson().convertValue(v, Map.class));
                    } else if (v instanceof ArrayNode) {
                        out.add(JacksonMapper.ofJson().convertValue(v, List.class));
                    } else {
                        out.add(v);
                    }
                });
            } catch (Exception e) {
                throw new Exception("Failed to resolve JQ expression '" + pattern + "' with input type '" + 
                    (input != null ? input.getClass().getName() : "null") + "'", e);
            }

            return out;
        } catch (Exception e) {
            throw new PebbleException(e, "Unable to parse jq value '" + input + "' with type '" + 
                (input != null ? input.getClass().getName() : "null") + "'", lineNumber, self.getName());
        }
    }
}
