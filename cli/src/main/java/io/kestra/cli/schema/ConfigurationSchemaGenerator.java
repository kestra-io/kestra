package io.kestra.cli.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.BeanDefinitionReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.ServiceConfigurationError;

final class ConfigurationSchemaGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationSchemaGenerator.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final String PLUGIN_PROPERTY_ANNOTATION = "io.kestra.core.models.annotations.PluginProperty";
    private static final String SCHEMA_ANNOTATION = "io.swagger.v3.oas.annotations.media.Schema";
    private static final String BINDABLE_ANNOTATION = "io.micronaut.core.bind.annotation.Bindable";

    private final Map<Class<?>, String> rawPrefixes = new LinkedHashMap<>();
    private final Map<Class<?>, String> resolvedPrefixes = new HashMap<>();
    private final Map<Class<?>, Boolean> eachPropertyList = new HashMap<>();
    private final Set<Class<?>> eachPropertyClasses = new HashSet<>();
    private final Set<Class<?>> visiting = new HashSet<>();

    ObjectNode generate(@Nullable PluginRegistry pluginRegistry) {
        discoverConfigClasses();

        ObjectNode root = MAPPER.createObjectNode();
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.put("title", "Kestra Configuration Schema");
        root.put("type", "object");

        for (var entry : rawPrefixes.entrySet()) {
            Class<?> clazz = entry.getKey();
            String prefix = resolvePrefix(clazz);
            if (prefix.isEmpty()) continue;

            ObjectNode target = navigateOrCreate(root, prefix);

            if (eachPropertyClasses.contains(clazz)) {
                ObjectNode itemSchema = MAPPER.createObjectNode();
                itemSchema.put("type", "object");
                addPropertiesViaReflection(itemSchema, clazz);

                if (Boolean.TRUE.equals(eachPropertyList.get(clazz))) {
                    target.put("type", "array");
                    target.set("items", itemSchema);
                } else {
                    target.put("type", "object");
                    target.set("additionalProperties", itemSchema);
                }
            } else {
                addPropertiesViaReflection(target, clazz);
            }
        }

        if (pluginRegistry != null) {
            addPluginStorageSchemas(root, pluginRegistry);
            addPluginSecretSchemas(root, pluginRegistry);
        }

        return root;
    }

    static void write(ObjectNode schema, File outputFile) throws IOException {
        MAPPER.writeValue(outputFile, schema);
    }

    private void addPluginStorageSchemas(ObjectNode root, PluginRegistry pluginRegistry) {
        ObjectNode storageNode = navigateOrCreate(root, "kestra.storage");

        if (!storageNode.has("properties")) {
            storageNode.putObject("properties");
        }
        ObjectNode storageProperties = (ObjectNode) storageNode.get("properties");

        ObjectNode typeProperty = MAPPER.createObjectNode();
        typeProperty.put("type", "string");
        typeProperty.put("description", "The storage plugin type identifier");
        ArrayNode typeEnum = typeProperty.putArray("enum");

        for (RegisteredPlugin registeredPlugin : pluginRegistry.plugins()) {
            for (Class<?> storageClass : registeredPlugin.getStorages()) {
                Optional<String> pluginId = Plugin.getId(storageClass);
                if (pluginId.isEmpty()) continue;

                String id = pluginId.get();
                typeEnum.add(id);

                ObjectNode pluginStorageSchema = MAPPER.createObjectNode();
                pluginStorageSchema.put("type", "object");
                addPluginProperties(pluginStorageSchema, storageClass);

                storageProperties.set(id, pluginStorageSchema);
            }
        }

        if (!typeEnum.isEmpty()) {
            storageProperties.set("type", typeProperty);
        }
    }

    private void addPluginSecretSchemas(ObjectNode root, PluginRegistry pluginRegistry) {
        ObjectNode secretNode = navigateOrCreate(root, "kestra.secret");

        if (!secretNode.has("properties")) {
            secretNode.putObject("properties");
        }
        ObjectNode secretProperties = (ObjectNode) secretNode.get("properties");

        ObjectNode typeProperty = MAPPER.createObjectNode();
        typeProperty.put("type", "string");
        typeProperty.put("description", "The secret manager plugin type identifier");
        ArrayNode typeEnum = typeProperty.putArray("enum");

        for (RegisteredPlugin registeredPlugin : pluginRegistry.plugins()) {
            for (Class<?> secretClass : registeredPlugin.getSecrets()) {
                Optional<String> pluginId = Plugin.getId(secretClass);
                if (pluginId.isEmpty()) continue;

                String id = pluginId.get();
                typeEnum.add(id);

                ObjectNode pluginSecretSchema = MAPPER.createObjectNode();
                pluginSecretSchema.put("type", "object");
                addPluginProperties(pluginSecretSchema, secretClass);

                secretProperties.set(id, pluginSecretSchema);
            }
        }

        if (!typeEnum.isEmpty()) {
            secretProperties.set("type", typeProperty);
        }
    }

    private void addPluginProperties(ObjectNode target, Class<?> clazz) {
        if (!target.has("properties")) {
            target.putObject("properties");
        }
        ObjectNode properties = (ObjectNode) target.get("properties");
        List<String> required = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (var prop : getPluginPropertyFields(clazz)) {
            if (!seen.add(prop.name())) continue;
            processProperty(properties, required, prop, false);
        }

        for (var prop : getPluginPropertyMethods(clazz)) {
            if (!seen.add(prop.name())) continue;
            processProperty(properties, required, prop, false);
        }

        if (!required.isEmpty()) {
            ArrayNode requiredArray = target.putArray("required");
            required.forEach(requiredArray::add);
        }
    }

    private void processProperty(ObjectNode properties, List<String> required,
                                 PropertyInfo prop, boolean includeBindableDefaults) {
        String key = toKebabCase(prop.name());
        ObjectNode propSchema = typeToSchema(prop.genericType());
        applySchemaAnnotation(propSchema, prop.annotations());

        if (includeBindableDefaults) {
            String defaultVal = findBindableDefault(prop.annotations());
            if (defaultVal != null) {
                setTypedDefault(propSchema, defaultVal, prop.type());
            }
        }

        if (isNullable(prop.annotations())) {
            makeNullable(propSchema);
        }

        if (isNonNull(prop.annotations())) {
            required.add(key);
        }

        if (hasAnnotation(prop.annotations(), Deprecated.class)) {
            propSchema.put("deprecated", true);
        }

        properties.set(key, propSchema);
    }

    private void makeNullable(ObjectNode schema) {
        if (schema.has("type")) {
            String currentType = schema.get("type").asText();
            ArrayNode types = MAPPER.createArrayNode();
            types.add(currentType);
            types.add("null");
            schema.set("type", types);
        }
    }

    private List<PropertyInfo> getPluginPropertyFields(Class<?> clazz) {
        List<PropertyInfo> props = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (Modifier.isTransient(f.getModifiers())) continue;
                if (!hasPluginPropertyAnnotation(f.getAnnotations())) continue;
                props.add(new PropertyInfo(f.getName(), f.getType(), f.getGenericType(), f.getAnnotations()));
            }
        }
        return props;
    }

    private List<PropertyInfo> getPluginPropertyMethods(Class<?> clazz) {
        List<PropertyInfo> props = new ArrayList<>();
        for (Class<?> iface : getAllInterfaces(clazz)) {
            for (Method m : iface.getDeclaredMethods()) {
                if (!hasPluginPropertyAnnotation(m.getAnnotations())) continue;
                String name = extractPropertyName(m.getName());
                if (name == null) continue;
                props.add(new PropertyInfo(name, m.getReturnType(), m.getGenericReturnType(), m.getAnnotations()));
            }
        }
        return props;
    }

    private Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                collectInterfaces(iface, interfaces);
            }
        }
        return interfaces;
    }

    private void collectInterfaces(Class<?> iface, Set<Class<?>> result) {
        if (result.add(iface)) {
            for (Class<?> parent : iface.getInterfaces()) {
                collectInterfaces(parent, result);
            }
        }
    }

    private static String extractPropertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return null;
    }

    private boolean hasPluginPropertyAnnotation(Annotation[] annotations) {
        for (Annotation a : annotations) {
            if (a.annotationType().getName().equals(PLUGIN_PROPERTY_ANNOTATION)) {
                return true;
            }
        }
        return false;
    }

    private void discoverConfigClasses() {
        SoftServiceLoader<BeanDefinitionReference> loader =
            SoftServiceLoader.load(BeanDefinitionReference.class);

        var iterator = loader.iterator();
        while (iterator.hasNext()) {
            try {
                var sd = iterator.next();
                if (!sd.isPresent()) continue;
                BeanDefinitionReference<?> ref = sd.load();
                var meta = ref.getAnnotationMetadata();

                if (meta.hasStereotype(ConfigurationProperties.class)) {
                    String prefix = meta.stringValue(ConfigurationProperties.class).orElse("");
                    Class<?> beanType = ref.getBeanType();
                    if (isKestraClass(beanType)) {
                        rawPrefixes.put(beanType, prefix);
                    }
                }

                if (meta.hasStereotype(EachProperty.class)) {
                    String prefix = meta.stringValue(EachProperty.class).orElse("");
                    boolean isList = meta.booleanValue(EachProperty.class, "list").orElse(false);
                    Class<?> beanType = ref.getBeanType();
                    if (isKestraClass(beanType)) {
                        rawPrefixes.put(beanType, prefix);
                        eachPropertyClasses.add(beanType);
                        eachPropertyList.put(beanType, isList);
                    }
                }
            } catch (Exception | LinkageError | ServiceConfigurationError e) {
                LOG.debug("Skipping unloadable bean definition: {}", e.getMessage());
            }
        }
    }

    private boolean isKestraClass(Class<?> clazz) {
        String pkg = clazz.getPackageName();
        return pkg.startsWith("io.kestra");
    }

    private String resolvePrefix(Class<?> clazz) {
        if (resolvedPrefixes.containsKey(clazz)) {
            return resolvedPrefixes.get(clazz);
        }

        String own = rawPrefixes.getOrDefault(clazz, "");
        Class<?> enclosing = clazz.getEnclosingClass();
        if (enclosing != null && rawPrefixes.containsKey(enclosing)) {
            String parentPrefix = resolvePrefix(enclosing);
            String full = parentPrefix.isEmpty() ? own : parentPrefix + "." + own;
            resolvedPrefixes.put(clazz, full);
            return full;
        }

        resolvedPrefixes.put(clazz, own);
        return own;
    }

    private ObjectNode navigateOrCreate(ObjectNode root, String dottedPrefix) {
        String[] segments = dottedPrefix.split("\\.");
        ObjectNode current = root;

        for (String segment : segments) {
            if (!current.has("properties")) {
                current.putObject("properties");
            }
            ObjectNode properties = (ObjectNode) current.get("properties");
            if (!properties.has(segment)) {
                ObjectNode child = MAPPER.createObjectNode();
                child.put("type", "object");
                properties.set(segment, child);
            }
            current = (ObjectNode) properties.get(segment);
        }

        return current;
    }

    private void addPropertiesViaReflection(ObjectNode target, Class<?> clazz) {
        applySchemaAnnotation(target, clazz.getAnnotations());

        if (!target.has("properties")) {
            target.putObject("properties");
        }
        ObjectNode properties = (ObjectNode) target.get("properties");
        List<String> required = new ArrayList<>();

        for (var prop : getClassProperties(clazz)) {
            if (rawPrefixes.containsKey(prop.type())) continue;
            processProperty(properties, required, prop, true);
        }

        if (!required.isEmpty()) {
            ArrayNode requiredArray = target.has("required")
                ? (ArrayNode) target.get("required")
                : target.putArray("required");
            required.forEach(requiredArray::add);
        }
    }

    private void applySchemaAnnotation(ObjectNode node, Annotation[] annotations) {
        for (Annotation a : annotations) {
            if (!a.annotationType().getName().equals(SCHEMA_ANNOTATION)) continue;
            try {
                String title = (String) a.annotationType().getMethod("title").invoke(a);
                if (title != null && !title.isEmpty()) {
                    node.put("title", title);
                }
                String description = (String) a.annotationType().getMethod("description").invoke(a);
                if (description != null && !description.isEmpty()) {
                    node.put("description", description);
                }
                String type = (String) a.annotationType().getMethod("type").invoke(a);
                if (type != null && !type.isEmpty()) {
                    node.put("type", type);
                }
                String format = (String) a.annotationType().getMethod("format").invoke(a);
                if (format != null && !format.isEmpty()) {
                    node.put("format", format);
                }
                String pattern = (String) a.annotationType().getMethod("pattern").invoke(a);
                if (pattern != null && !pattern.isEmpty()) {
                    node.put("pattern", pattern);
                }
                String minimum = (String) a.annotationType().getMethod("minimum").invoke(a);
                if (minimum != null && !minimum.isEmpty()) {
                    node.put("minimum", minimum);
                }
                String maximum = (String) a.annotationType().getMethod("maximum").invoke(a);
                if (maximum != null && !maximum.isEmpty()) {
                    node.put("maximum", maximum);
                }
                String example = (String) a.annotationType().getMethod("example").invoke(a);
                if (example != null && !example.isEmpty()) {
                    ArrayNode examples = node.putArray("examples");
                    examples.add(example);
                }
            } catch (Exception e) {
                LOG.debug("Failed to extract @Schema annotation values: {}", e.getMessage());
            }
        }
    }

    private record PropertyInfo(String name, Class<?> type, Type genericType, Annotation[] annotations) {}

    private List<PropertyInfo> getClassProperties(Class<?> clazz) {
        if (clazz.isRecord()) {
            return Arrays.stream(clazz.getRecordComponents())
                .map(rc -> new PropertyInfo(rc.getName(), rc.getType(), rc.getGenericType(), rc.getAnnotations()))
                .toList();
        }

        List<PropertyInfo> props = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (Modifier.isTransient(f.getModifiers())) continue;
                props.add(new PropertyInfo(f.getName(), f.getType(), f.getGenericType(), f.getAnnotations()));
            }
        }
        return props;
    }

    private String findBindableDefault(Annotation[] annotations) {
        for (Annotation a : annotations) {
            if (a.annotationType().getName().equals(BINDABLE_ANNOTATION)) {
                try {
                    var method = a.annotationType().getMethod("defaultValue");
                    String val = (String) method.invoke(a);
                    return val.isEmpty() ? null : val;
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private boolean isNullable(Annotation[] annotations) {
        for (Annotation a : annotations) {
            String name = a.annotationType().getSimpleName();
            if ("Nullable".equals(name)) return true;
        }
        return false;
    }

    private boolean isNonNull(Annotation[] annotations) {
        for (Annotation a : annotations) {
            String name = a.annotationType().getSimpleName();
            if ("NotNull".equals(name) || "NonNull".equals(name) || "Nonnull".equals(name)
                || "NotEmpty".equals(name) || "NotBlank".equals(name)) return true;
        }
        return false;
    }

    private boolean hasAnnotation(Annotation[] annotations, Class<? extends Annotation> target) {
        for (Annotation a : annotations) {
            if (target.isAssignableFrom(a.annotationType())) return true;
        }
        return false;
    }

    private void setTypedDefault(ObjectNode schema, String value, Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            schema.put("default", Boolean.parseBoolean(value));
        } else if (type == int.class || type == Integer.class) {
            try { schema.put("default", Integer.parseInt(value)); } catch (NumberFormatException e) { schema.put("default", value); }
        } else if (type == long.class || type == Long.class) {
            try { schema.put("default", Long.parseLong(value)); } catch (NumberFormatException e) { schema.put("default", value); }
        } else if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            try { schema.put("default", Double.parseDouble(value)); } catch (NumberFormatException e) { schema.put("default", value); }
        } else {
            schema.put("default", value);
        }
    }

    private ObjectNode typeToSchema(Type genericType) {
        if (genericType instanceof Class<?> clazz) {
            return typeToSchemaForClass(clazz);
        }

        if (genericType instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            Type[] typeArgs = pt.getActualTypeArguments();

            if (Collection.class.isAssignableFrom(raw)) {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("type", "array");
                if (typeArgs.length > 0) {
                    node.set("items", typeToSchema(typeArgs[0]));
                }
                return node;
            }
            if (Map.class.isAssignableFrom(raw)) {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("type", "object");
                if (typeArgs.length > 1) {
                    node.set("additionalProperties", typeToSchema(typeArgs[1]));
                }
                return node;
            }
            if (Optional.class.isAssignableFrom(raw) && typeArgs.length > 0) {
                return typeToSchema(typeArgs[0]);
            }
            return typeToSchemaForClass(raw);
        }

        if (genericType instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            if (upper.length > 0 && upper[0] != Object.class) {
                return typeToSchema(upper[0]);
            }
        }

        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "object");
        return node;
    }

    private ObjectNode typeToSchemaForClass(Class<?> type) {
        ObjectNode node = MAPPER.createObjectNode();

        if (type == String.class || type == CharSequence.class) {
            node.put("type", "string");
        } else if (type == int.class || type == Integer.class) {
            node.put("type", "integer");
        } else if (type == long.class || type == Long.class) {
            node.put("type", "integer");
            node.put("format", "int64");
        } else if (type == short.class || type == Short.class) {
            node.put("type", "integer");
        } else if (type == boolean.class || type == Boolean.class) {
            node.put("type", "boolean");
        } else if (type == double.class || type == Double.class) {
            node.put("type", "number");
            node.put("format", "double");
        } else if (type == float.class || type == Float.class) {
            node.put("type", "number");
            node.put("format", "float");
        } else if (Duration.class.isAssignableFrom(type)) {
            node.put("type", "string");
            node.put("format", "duration");
        } else if (type == URI.class || type == URL.class) {
            node.put("type", "string");
            node.put("format", "uri");
        } else if (Path.class.isAssignableFrom(type)) {
            node.put("type", "string");
            node.put("format", "path");
        } else if (Instant.class.isAssignableFrom(type)
                   || ZonedDateTime.class.isAssignableFrom(type)
                   || LocalDateTime.class.isAssignableFrom(type)) {
            node.put("type", "string");
            node.put("format", "date-time");
        } else if (LocalDate.class.isAssignableFrom(type)) {
            node.put("type", "string");
            node.put("format", "date");
        } else if (type.isEnum()) {
            node.put("type", "string");
            ArrayNode enumValues = node.putArray("enum");
            for (Object c : type.getEnumConstants()) {
                enumValues.add(((Enum<?>) c).name());
            }
        } else if (Collection.class.isAssignableFrom(type)) {
            node.put("type", "array");
        } else if (Map.class.isAssignableFrom(type)) {
            node.put("type", "object");
        } else {
            if (!visiting.add(type)) {
                node.put("type", "object");
                return node;
            }
            try {
                List<PropertyInfo> nestedProps = getClassProperties(type);
                if (!nestedProps.isEmpty() && !type.getPackageName().startsWith("java.")) {
                    node.put("type", "object");
                    ObjectNode nested = node.putObject("properties");
                    for (var prop : nestedProps) {
                        nested.set(toKebabCase(prop.name()), typeToSchema(prop.genericType()));
                    }
                } else {
                    node.put("type", "object");
                }
            } finally {
                visiting.remove(type);
            }
        }

        return node;
    }

    static String toKebabCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) return camelCase;
        return camelCase
            .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
            .replaceAll("([A-Z])([A-Z][a-z])", "$1-$2")
            .toLowerCase();
    }

    ConfigurationSchemaGenerator() {}
}
