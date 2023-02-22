package io.kestra.core.docs;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.generator.impl.DefinitionKey;
import com.github.victools.jsonschema.generator.naming.DefaultSchemaDefinitionNamingStrategy;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.javax.validation.JavaxValidationModule;
import com.github.victools.jsonschema.module.javax.validation.JavaxValidationOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.PluginService;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class JsonSchemaGenerator {
    @Inject
    private PluginService pluginService;

    Map<Class<?>, Object> defaultInstances = new HashMap<>();

    public <T> Map<String, Object> schemas(Class<? extends T> cls) {
        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_7,
            OptionPreset.PLAIN_JSON
        );

        this.build(builder, cls, true);

        SchemaGeneratorConfig schemaGeneratorConfig = builder.build();

        SchemaGenerator generator = new SchemaGenerator(schemaGeneratorConfig);
        try {
            ObjectNode objectNode = generator.generateSchema(cls);

            Map<String, Object> map = JacksonMapper.toMap(objectNode);

            // hack
            if (cls == Flow.class) {
                fixFlow(map);
            }


            return map;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to generate jsonschema for '" + cls.getName() + "'", e);
        }
    }

    private void mutateDescription(ObjectNode collectedTypeAttributes) {
        if (collectedTypeAttributes.has("description")) {
            collectedTypeAttributes.set("markdownDescription", collectedTypeAttributes.get("description"));
            collectedTypeAttributes.remove("description");
        }

        if (collectedTypeAttributes.has("description")) {
            collectedTypeAttributes.set("markdownDescription", collectedTypeAttributes.get("description"));
            collectedTypeAttributes.remove("description");
        }

        if (collectedTypeAttributes.has("default")) {
            StringBuilder sb = new StringBuilder();
            if (collectedTypeAttributes.has("markdownDescription")) {
                sb.append(collectedTypeAttributes.get("markdownDescription").asText());
                sb.append("\n\n");
            }

            try {
                sb.append("Default value is : `")
                    .append(JacksonMapper.ofYaml().writeValueAsString(collectedTypeAttributes.get("default")).trim())
                    .append("`");
            } catch (JsonProcessingException ignored) {

            }

            collectedTypeAttributes.set("markdownDescription", new TextNode(sb.toString()));
        }
    }

    @SuppressWarnings("unchecked")
    private static void fixFlow(Map<String, Object> map) {
        var definitions = (Map<String, Map<String, Object>>) map.get("definitions");
        var flow = (Map<String, Object>) definitions.get("io.kestra.core.models.flows.Flow");

        var requireds = (List<String>) flow.get("required");
        requireds.remove("deleted");

        var properties = (Map<String, Object>) flow.get("properties");
        properties.remove("deleted");
    }

    public <T> Map<String, Object> properties(Class<T> base, Class<? extends T> cls) {
        return this.generate(cls, base);
    }

    public <T> Map<String, Object> outputs(Class<T> base, Class<? extends T> cls) {
        List<Class<?>> superClass = new ArrayList<>();

        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            if (c == base) {
                break;
            }

            superClass.add(c);
        }

        return superClass
            .stream()
            .flatMap(r -> Arrays.stream(r.getGenericInterfaces()))
            .filter(type -> type instanceof ParameterizedType)
            .map(type -> (ParameterizedType) type)
            .flatMap(parameterizedType -> Arrays.stream(parameterizedType.getActualTypeArguments()))
            .filter(type -> type instanceof Class)
            .map(type -> (Class<?>) type)
            .filter(Output.class::isAssignableFrom)
            .findFirst()
            .map(c -> this.generate(c, null))
            .orElse(ImmutableMap.of());
    }

    protected <T> void build(SchemaGeneratorConfigBuilder builder, Class<? extends T> cls, boolean draft7) {
        builder

            .with(new JavaxValidationModule(
                JavaxValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
                JavaxValidationOption.INCLUDE_PATTERN_EXPRESSIONS
            ))
            .with(new Swagger2Module())
            .with(Option.DEFINITIONS_FOR_ALL_OBJECTS)
            .with(Option.DEFINITION_FOR_MAIN_SCHEMA)
            .with(Option.PLAIN_DEFINITION_KEYS)
            .with(Option.ALLOF_CLEANUP_AT_THE_END);

        if (!draft7) {
            builder
                .with(new JacksonModule(JacksonOption.IGNORE_TYPE_INFO_TRANSFORM))
                .with(Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES);
        } else {
            builder
                .with(new JacksonModule());
        }

        // default value
        builder.forFields().withDefaultResolver(this::defaults);

        // def name
        builder.forTypesInGeneral()
            .withDefinitionNamingStrategy(new DefaultSchemaDefinitionNamingStrategy() {
                @Override
                public String getDefinitionNameForKey(DefinitionKey key, SchemaGenerationContext context) {
                    TypeContext typeContext = context.getTypeContext();
                    ResolvedType type = key.getType();
                    return typeContext.getFullTypeDescription(type);
                }

                @Override
                public String adjustNullableName(DefinitionKey key, String definitionName, SchemaGenerationContext context) {
                    return definitionName;
                }
            });

        // inline some type
        builder.forTypesInGeneral()
            .withCustomDefinitionProvider(new CustomDefinitionProviderV2() {
                @Override
                public CustomDefinition provideCustomSchemaDefinition(ResolvedType javaType, SchemaGenerationContext context) {
                    if (javaType.isInstanceOf(Map.class) || javaType.isInstanceOf(Enum.class)) {
                        ObjectNode definition = context.createStandardDefinition(javaType, this);
                        return new CustomDefinition(definition, true);
                    } else if (javaType.isInstanceOf(Duration.class)) {
                        ObjectNode definitionReference = context
                            .createDefinitionReference(context.getTypeContext().resolve(String.class))
                            .put("format", "duration");

                        return new CustomDefinition(definitionReference, true);
                    } else {
                        return null;
                    }
                }
            });

        // PluginProperty $dynamic && deprecated swagger properties
        builder.forFields().withInstanceAttributeOverride((memberAttributes, member, context) -> {
            PluginProperty pluginPropertyAnnotation = member.getAnnotationConsideringFieldAndGetter(PluginProperty.class);
            if (pluginPropertyAnnotation != null) {
                memberAttributes.put("$dynamic", pluginPropertyAnnotation.dynamic());
            }


            Schema schema = member.getAnnotationConsideringFieldAndGetter(Schema.class);
            if (schema != null && schema.deprecated()) {
                memberAttributes.put("$deprecated", true);
            }
        });

        // Add Plugin annotation special docs
        builder.forTypesInGeneral()
            .withTypeAttributeOverride((collectedTypeAttributes, scope, context) -> {
                Plugin pluginAnnotation = scope.getType().getErasedType().getAnnotation(Plugin.class);
                if (pluginAnnotation != null) {
                    List<ObjectNode> examples = Arrays
                        .stream(pluginAnnotation.examples())
                        .map(example -> context.getGeneratorConfig().createObjectNode()
                            .put("full", example.full())
                            .put("code", String.join("\n", example.code()))
                            .put("lang", example.lang())
                            .put("title", example.title())
                        )
                        .collect(Collectors.toList());

                    if (examples.size() > 0) {
                        collectedTypeAttributes.set("$examples", context.getGeneratorConfig().createArrayNode().addAll(examples));
                    }
                }
            });

        // PluginProperty additionalProperties
        builder.forFields().withAdditionalPropertiesResolver(target -> {
            PluginProperty pluginPropertyAnnotation = target.getAnnotationConsideringFieldAndGetter(PluginProperty.class);
            if (pluginPropertyAnnotation != null) {
                return pluginPropertyAnnotation.additionalProperties();
            }

            return Object.class;
        });
        if(builder.build().getSchemaVersion() != SchemaVersion.DRAFT_2019_09) {
            builder.forTypesInGeneral()
                .withSubtypeResolver((declaredType, context) -> {
                    TypeContext typeContext = context.getTypeContext();

                    if (declaredType.getErasedType() == Task.class) {
                        return pluginService
                            .allPlugins()
                            .stream()
                            .flatMap(registeredPlugin -> registeredPlugin.getTasks().stream())
                            .map(clz -> typeContext.resolveSubtype(declaredType, clz))
                            .collect(Collectors.toList());
                    } else if (declaredType.getErasedType() == AbstractTrigger.class) {
                        return pluginService
                            .allPlugins()
                            .stream()
                            .flatMap(registeredPlugin -> registeredPlugin.getTriggers().stream())
                            .map(clz -> typeContext.resolveSubtype(declaredType, clz))
                            .collect(Collectors.toList());
                    } else if (declaredType.getErasedType() == Condition.class) {
                        return pluginService
                            .allPlugins()
                            .stream()
                            .flatMap(registeredPlugin -> registeredPlugin.getConditions().stream())
                            .map(clz -> typeContext.resolveSubtype(declaredType, clz))
                            .collect(Collectors.toList());
                    } else if (declaredType.getErasedType() == ScheduleCondition.class) {
                        return pluginService
                            .allPlugins()
                            .stream()
                            .flatMap(registeredPlugin -> registeredPlugin.getConditions().stream())
                            .filter(ScheduleCondition.class::isAssignableFrom)
                            .map(clz -> typeContext.resolveSubtype(declaredType, clz))
                            .collect(Collectors.toList());
                    }

                    return null;
                });
            // description as Markdown
            builder.forTypesInGeneral().withTypeAttributeOverride((collectedTypeAttributes, scope, context) -> {
                this.mutateDescription(collectedTypeAttributes);
            });

            builder.forFields().withInstanceAttributeOverride((collectedTypeAttributes, scope, context) -> {
                this.mutateDescription(collectedTypeAttributes);
            });

            // default is no more required
            builder.forTypesInGeneral().withTypeAttributeOverride((collectedTypeAttributes, scope, context) -> {
                if (collectedTypeAttributes.has("required") && collectedTypeAttributes.get("required") instanceof ArrayNode) {
                    ArrayNode required = context.getGeneratorConfig().createArrayNode();

                    collectedTypeAttributes.get("required").forEach(jsonNode -> {
                        if (!collectedTypeAttributes.get("properties").get(jsonNode.asText()).has("default")) {
                            required.add(jsonNode.asText());
                        }
                    });

                    collectedTypeAttributes.set("required", required);
                }
            });

            // invalid regexp for jsonschema
            builder.forFields().withInstanceAttributeOverride((collectedTypeAttributes, scope, context) -> {
                if (collectedTypeAttributes.has("pattern") && collectedTypeAttributes.get("pattern").asText().contains("javaJavaIdentifier")) {
                    collectedTypeAttributes.remove("pattern");
                }
            });

            // examples in description
            builder.forTypesInGeneral().withTypeAttributeOverride((collectedTypeAttributes, scope, context) -> {
                if (collectedTypeAttributes.has("$examples")) {
                    ArrayNode examples = (ArrayNode) collectedTypeAttributes.get("$examples");

                    String doc = StreamSupport.stream(examples.spliterator(), true)
                        .map(jsonNode -> {
                            String description = "";
                            if (jsonNode.has("title")) {
                                description += "> " + jsonNode.get("title").asText() + "\n";
                            }

                            description += "```" +
                                (jsonNode.has("lang") ? jsonNode.get("lang").asText() : "yaml")
                                + "\n" +
                                jsonNode.get("code").asText() +
                                "\n```";

                            return description;
                        })
                        .collect(Collectors.joining("\n\n"));

                    String description = collectedTypeAttributes.has("markdownDescription") ?
                        collectedTypeAttributes.get("markdownDescription").asText() :
                        "";

                    description += "##### Examples\n" + doc;

                    collectedTypeAttributes.set("markdownDescription", new TextNode(description));

                    collectedTypeAttributes.remove("$examples");
                }
            });
        }
    }

    protected <T> Map<String, Object> generate(Class<? extends T> cls, @Nullable Class<T> base) {
        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2019_09,
            OptionPreset.PLAIN_JSON
        );

        this.build(builder, cls, false);

        // base is passed, we don't return base properties
        builder
            .forFields()
            .withIgnoreCheck(fieldScope -> base != null && fieldScope.getDeclaringType().getTypeName().equals(base.getName()));

        SchemaGeneratorConfig schemaGeneratorConfig = builder.build();

        SchemaGenerator generator = new SchemaGenerator(schemaGeneratorConfig);
        try {
            ObjectNode objectNode = generator.generateSchema(cls);

            return JacksonMapper.toMap(extractMainRef(objectNode));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to generate jsonschema for '" + cls.getName() + "'", e);
        }
    }

    protected Object defaults(FieldScope target) {
        if (target.getOverriddenType() != null) {
            return null;
        }

        // class is abstract we try with cls passed to method, we try to find a derived one, optimistic approach
        Class<?> baseCls = target.getMember().getDeclaringType().getErasedType();
        if (Modifier.isAbstract(baseCls.getModifiers())) {
            Class<?> abstractBaseCls = baseCls;
            Optional<Map.Entry<Class<?>, Object>> derivedOne = defaultInstances
                .entrySet()
                .stream()
                .filter(e -> !Modifier.isAbstract(e.getKey().getModifiers()))
                .filter(e -> abstractBaseCls.isAssignableFrom(e.getKey()))
                .findFirst();

            if (derivedOne.isPresent()) {
                defaultInstances.put(baseCls, derivedOne.get());
                baseCls = derivedOne.get().getKey();
            }
        }

        if (!defaultInstances.containsKey(baseCls)) {
            defaultInstances.put(baseCls, buildDefaultInstance(baseCls));
        }

        Object instance = defaultInstances.get(baseCls);

        return instance == null ? null : defaultValue(instance, baseCls, target.getName());
    }

    private ObjectNode extractMainRef(ObjectNode objectNode) {
        TextNode ref = (TextNode) objectNode.get("$ref");
        ObjectNode defs = (ObjectNode) objectNode.get("$defs");

        if (ref == null) {
            throw new IllegalArgumentException("Missing $ref");
        }
        String mainClassName = ref.asText().substring(ref.asText().lastIndexOf("/") + 1);

        if (mainClassName.endsWith("-2")) {
            mainClassName = mainClassName.substring(0, mainClassName.length() - 2);
            JsonNode mainClassDef = defs.get(mainClassName + "-1");

            this.addMainRefProperties(mainClassDef, objectNode);

            defs.remove(mainClassName + "-1");
            defs.remove(mainClassName + "-2");
        } else {
            JsonNode mainClassDef = defs.get(mainClassName);
            this.addMainRefProperties(mainClassDef, objectNode);

            defs.remove(mainClassName);
        }

        objectNode.remove("$ref");

        return objectNode;
    }

    private void addMainRefProperties(JsonNode mainClassDef, ObjectNode objectNode) {
        objectNode.set("properties", mainClassDef.get("properties"));
        if (mainClassDef.has("required")) {
            objectNode.set("required", mainClassDef.get("required"));
        }
        if (mainClassDef.has("title")) {
            objectNode.set("title", mainClassDef.get("title"));
        }
        if (mainClassDef.has("description")) {
            objectNode.set("description", mainClassDef.get("description"));
        }
        if (mainClassDef.has("$examples")) {
            objectNode.set("$examples", mainClassDef.get("$examples"));
        }
    }

    private Object buildDefaultInstance(Class<?> cls) {
        try {
            Method builderMethod = cls.getMethod("builder");
            Object builder = builderMethod.invoke(null);

            Method build = builder.getClass().getMethod("build");
            build.setAccessible(true);
            return build.invoke(builder);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private Object defaultValue(Object instance, Class<?> cls, String fieldName) {
        try {
            Method field = cls.getMethod("get" + fieldName.substring(0, 1).toUpperCase(Locale.ROOT) + fieldName.substring(1));

            field.setAccessible(true);
            return field.invoke(instance);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {

        }

        try {
            Method field = cls.getMethod("is" + fieldName.substring(0, 1).toUpperCase(Locale.ROOT) + fieldName.substring(1));

            field.setAccessible(true);
            return field.invoke(instance);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {

        }

        return null;
    }
}
