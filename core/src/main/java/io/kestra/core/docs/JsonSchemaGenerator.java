package io.kestra.core.docs;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.generator.impl.DefinitionKey;
import com.github.victools.jsonschema.generator.naming.DefaultSchemaDefinitionNamingStrategy;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.javax.validation.JavaxValidationModule;
import com.github.victools.jsonschema.module.javax.validation.JavaxValidationOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class JsonSchemaGenerator {
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

    private <T> Map<String, Object> generate(Class<? extends T> cls, @Nullable Class<T> base) {
        // store current class
        Map<Class<?>, Object> defaultInstances = new HashMap<>();
        defaultInstances.put(cls, buildDefaultInstance(cls));

        // init schema generator
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2019_09,
            OptionPreset.PLAIN_JSON
        )
            .with(new JacksonModule())
            .with(new JavaxValidationModule(
                JavaxValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
                JavaxValidationOption.INCLUDE_PATTERN_EXPRESSIONS
            ))
            .with(new Swagger2Module())
            .with(Option.DEFINITIONS_FOR_ALL_OBJECTS)
            .with(Option.DEFINITION_FOR_MAIN_SCHEMA)
            .with(Option.PLAIN_DEFINITION_KEYS)
            .with(Option.ALLOF_CLEANUP_AT_THE_END);

        // base is passed, we don't return base properties
        if (base != null) {
            configBuilder
                .forFields()
                .withIgnoreCheck(fieldScope -> fieldScope.getDeclaringType().getTypeName().equals(base.getName()));
        }

        // def name
        configBuilder.forTypesInGeneral()
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
        configBuilder.forTypesInGeneral()
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

        // default value
        configBuilder.forFields().withDefaultResolver(target -> {
            if (target.getOverriddenType() != null) {
                return null;
            }

            // class is abstract we try with cls passed to method, optimistic approach
            Class<?> baseCls = target.getMember().getDeclaringType().getErasedType();
            if (Modifier.isAbstract(baseCls.getModifiers()) && baseCls.isAssignableFrom(cls)) {
                baseCls = cls;
            }

            if (!defaultInstances.containsKey(baseCls)) {
                defaultInstances.put(baseCls, buildDefaultInstance(baseCls));
            }

            Object instance = defaultInstances.get(baseCls);

            return instance == null ? null : defaultValue(instance, baseCls, target.getName());
        });


        // PluginProperty $dynamic && deprecated swagger properties
        configBuilder.forFields().withInstanceAttributeOverride((memberAttributes, member, context) -> {
            PluginProperty pluginPropertyAnnotation = member.getAnnotation(PluginProperty.class);
            if (pluginPropertyAnnotation != null) {
                memberAttributes.put("$dynamic", pluginPropertyAnnotation.dynamic());
            }


            Schema schema = member.getAnnotation(Schema.class);
            if (schema != null && schema.deprecated()) {
                memberAttributes.put("$deprecated", true);
            }
        });

        // PluginProperty additionalProperties
        configBuilder.forFields().withAdditionalPropertiesResolver(target -> {
            PluginProperty pluginPropertyAnnotation = target.getAnnotation(PluginProperty.class);
            if (pluginPropertyAnnotation != null) {
                return pluginPropertyAnnotation.additionalProperties();
            }

            return Object.class;
        });

        // generate json schema
        SchemaGeneratorConfig schemaGeneratorConfig = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(schemaGeneratorConfig);
        try {
            ObjectNode objectNode = generator.generateSchema(cls);

            // add Plugin annotation special docs
            Plugin pluginAnnotation = cls.getDeclaredAnnotation(Plugin.class);

            if (pluginAnnotation != null) {
                List<ObjectNode> examples = Arrays
                    .stream(pluginAnnotation.examples())
                    .map(example -> schemaGeneratorConfig.createObjectNode()
                        .put("full", example.full())
                        .put("code", String.join("\n", example.code()))
                        .put("lang", example.lang())
                        .put("title", example.title())
                    )
                    .collect(Collectors.toList());

                if (examples.size() > 0) {
                    objectNode.set("$examples", schemaGeneratorConfig.createArrayNode().addAll(examples));
                }
            }

            return JacksonMapper.toMap(extractMainRef(objectNode));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to generate jsonschema for '" + cls.getName() + "'", e);
        }
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

            objectNode.set("properties", mainClassDef.get("properties"));
            if (mainClassDef.has("required")) {
                objectNode.set("required", mainClassDef.get("required"));
            }

            defs.remove(mainClassName + "-1");
            defs.remove(mainClassName + "-2");
        } else {
            JsonNode mainClassDef = defs.get(mainClassName);
            defs.remove(mainClassName);
            objectNode.set("properties", mainClassDef.get("properties"));
            if (mainClassDef.has("required")) {
                objectNode.set("required", mainClassDef.get("required"));
            }
        }

        objectNode.remove("$ref");

        return objectNode;
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
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
            return null;
        }
    }
}
