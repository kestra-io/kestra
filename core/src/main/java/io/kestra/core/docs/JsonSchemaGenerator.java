package io.kestra.core.docs;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.members.HierarchicType;
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
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class JsonSchemaGenerator {

    private final PluginRegistry pluginRegistry;

    @Inject
    public JsonSchemaGenerator(final PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    Map<Class<?>, Object> defaultInstances = new HashMap<>();

    public <T> Map<String, Object> schemas(Class<? extends T> cls) {
        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_7,
            OptionPreset.PLAIN_JSON
        );

        this.build(builder,true);

        SchemaGeneratorConfig schemaGeneratorConfig = builder.build();

        SchemaGenerator generator = new SchemaGenerator(schemaGeneratorConfig);
        try {
            ObjectNode objectNode = generator.generateSchema(cls);
            objectNode.findParents("anyOf").forEach(jsonNode -> {
                if (jsonNode instanceof ObjectNode oNode) {
                    oNode.set("oneOf", oNode.remove("anyOf"));
                }
            });
            return JacksonMapper.toMap(objectNode);
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

    protected void build(SchemaGeneratorConfigBuilder builder, boolean draft7) {
        builder

            .with(new JakartaValidationModule(
                JakartaValidationOption.NOT_NULLABLE_METHOD_IS_REQUIRED,
                JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
                JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS
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
                if (pluginPropertyAnnotation.beta()) {
                    memberAttributes.put("$beta", true);
                }
            }

            Schema schema = member.getAnnotationConsideringFieldAndGetter(Schema.class);
            if (schema != null && schema.deprecated()) {
                memberAttributes.put("$deprecated", true);
            }

            Deprecated deprecated = member.getAnnotationConsideringFieldAndGetter(Deprecated.class);
            if (deprecated != null) {
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

                    if (!examples.isEmpty()) {
                        collectedTypeAttributes.set("$examples", context.getGeneratorConfig().createArrayNode().addAll(examples));
                    }

                    List<ObjectNode> metrics = Arrays
                        .stream(pluginAnnotation.metrics())
                        .map(metric -> context.getGeneratorConfig().createObjectNode()
                            .put("name", metric.name())
                            .put("type", metric.type())
                            .put("unit", metric.unit())
                            .put("description", metric.description())
                        )
                        .collect(Collectors.toList());

                    if (!metrics.isEmpty()) {
                        collectedTypeAttributes.set("$metrics", context.getGeneratorConfig().createArrayNode().addAll(metrics));
                    }

                    if (pluginAnnotation.beta()) {
                        collectedTypeAttributes.put("$beta", true);
                    }
                }

                // handle deprecated tasks
                Schema schema = scope.getType().getErasedType().getAnnotation(Schema.class);
                Deprecated deprecated = scope.getType().getErasedType().getAnnotation(Deprecated.class);
                if ((schema != null && schema.deprecated()) || deprecated != null ) {
                    collectedTypeAttributes.put("$deprecated", "true");
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
                        return getRegisteredPlugins()
                            .stream()
                            .flatMap(registeredPlugin -> registeredPlugin.getTasks().stream())
                            .filter(Predicate.not(io.kestra.core.models.Plugin::isInternal))
                            .map(clz -> typeContext.resolveSubtype(declaredType, clz))
                            .collect(Collectors.toList());
                    } else if (declaredType.getErasedType() == AbstractTrigger.class) {
                        return getRegisteredPlugins()
                            .stream()
                            .flatMap(registeredPlugin -> registeredPlugin.getTriggers().stream())
                            .filter(Predicate.not(io.kestra.core.models.Plugin::isInternal))
                            .map(clz -> typeContext.resolveSubtype(declaredType, clz))
                            .collect(Collectors.toList());
                    } else if (declaredType.getErasedType() == Condition.class) {
                        return getRegisteredPlugins()
                            .stream()
                            .flatMap(registeredPlugin -> registeredPlugin.getConditions().stream())
                            .filter(Predicate.not(io.kestra.core.models.Plugin::isInternal))
                            .map(clz -> typeContext.resolveSubtype(declaredType, clz))
                            .collect(Collectors.toList());
                    } else if (declaredType.getErasedType() == ScheduleCondition.class) {
                        return getRegisteredPlugins()
                            .stream()
                            .flatMap(registeredPlugin -> registeredPlugin.getConditions().stream())
                            .filter(ScheduleCondition.class::isAssignableFrom)
                            .filter(Predicate.not(io.kestra.core.models.Plugin::isInternal))
                            .map(clz -> typeContext.resolveSubtype(declaredType, clz))
                            .collect(Collectors.toList());
                    } else if (declaredType.getErasedType() == TaskRunner.class) {
                        return getRegisteredPlugins()
                            .stream()
                            .flatMap(registeredPlugin -> registeredPlugin.getTaskRunners().stream())
                            .filter(Predicate.not(io.kestra.core.models.Plugin::isInternal))
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
                        if (!collectedTypeAttributes.get("properties").get(jsonNode.asText()).has("default")
                            && !defaultInAllOf(collectedTypeAttributes.get("properties").get(jsonNode.asText()))) {
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

            // Ensure that `type` is defined as a constant in JSON Schema.
            // The `const` property is used by editors for auto-completion based on that schema.
            builder.forTypesInGeneral().withTypeAttributeOverride((collectedTypeAttributes, scope, context) -> {
                final Class<?> pluginType = scope.getType().getErasedType();
                if (pluginType.getAnnotation(Plugin.class) != null) {
                    ObjectNode properties = (ObjectNode) collectedTypeAttributes.get("properties");
                    if (properties != null) {
                        properties.set("type", context.getGeneratorConfig().createObjectNode()
                            .put("const", pluginType.getName())
                        );
                    }
                }
            });
        }
    }

    protected List<RegisteredPlugin> getRegisteredPlugins() {
        return pluginRegistry.plugins();
    }

    private boolean defaultInAllOf(JsonNode property) {
        if (property.has("allOf")) {
            for (Iterator<JsonNode> it = property.get("allOf").elements(); it.hasNext(); ) {
                JsonNode child = it.next();
                if(child.has("default")) {
                    return true;
                }
            }
        }
        return false;
    }

    protected <T> Map<String, Object> generate(Class<? extends T> cls, @Nullable Class<T> base) {
        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2019_09,
            OptionPreset.PLAIN_JSON
        );

        this.build(builder,false);

        // we don't return base properties unless specified with @PluginProperty
        builder
            .forFields()
            .withIgnoreCheck(fieldScope -> base != null && fieldScope.getAnnotation(PluginProperty.class) == null && fieldScope.getDeclaringType().getTypeName().equals(base.getName()));

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
            // we must retrieve the instance class that leads to this field in this abstract class.
            // there is no direct way, so we use the hierarchy of classes and get the first one that is not a mixin (not overriden)
            Optional<HierarchicType> concreteCls = target.getDeclaringTypeMembers().mainTypeAndOverrides()
                .stream()
                .filter(type -> !type.isMixin())
                .findFirst();

            if (concreteCls.isPresent()) {
                baseCls = concreteCls.get().getErasedType();
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
        if (mainClassDef.has("$metrics")) {
            objectNode.set("$metrics", mainClassDef.get("$metrics"));
        }
        if (mainClassDef.has("$deprecated")) {
            objectNode.set("$deprecated", mainClassDef.get("$deprecated"));
        }
        if (mainClassDef.has("$beta")) {
            objectNode.set("$beta", mainClassDef.get("$beta"));
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
