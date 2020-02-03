package org.kestra.core.docs;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import com.github.jknack.handlebars.internal.lang3.ObjectUtils;
import com.google.common.base.Charsets;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.annotations.OutputProperty;
import org.kestra.core.models.tasks.Output;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.plugins.RegisteredPlugin;
import org.kestra.core.runners.handlebars.helpers.InstantHelper;
import org.kestra.core.runners.handlebars.helpers.JsonHelper;
import org.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract public class DocumentationGenerator {
    private static Pattern DEFAULT_PACKAGES_TO_IGNORE = Pattern.compile("^(?:"
        + "|java"
        + "|javax"
        + ")\\..*$");

    private static List<String> SIMPLE_NAME = Arrays.asList(
        "java.lang",
        "java.util",
        "java.net"
    );

    private static Handlebars handlebars = new Handlebars()
        .with(EscapingStrategy.NOOP)
        .registerHelpers(ConditionalHelpers.class)
        .registerHelpers(EachHelper.class)
        .registerHelpers(LogHelper.class)
        .registerHelpers(StringHelpers.class)
        .registerHelpers(UnlessHelper.class)
        .registerHelpers(WithHelper.class)
        .registerHelpers(InstantHelper.class)
        .registerHelpers(JsonHelper.class);

    public static List<Document> generate(RegisteredPlugin registeredPlugin) throws IOException {

        System.out.println(Collections.list(DocumentationGenerator.class.getClassLoader().getResources("docs")));
        String hbsTemplate = IOUtils.toString(
            Objects.requireNonNull(DocumentationGenerator.class.getClassLoader().getResourceAsStream("docs/task.hbs")),
            Charsets.UTF_8
        );

        Template template = handlebars.compileInline(hbsTemplate);

        return registeredPlugin
            .getTasks()
            .stream()
            .map(r -> PluginDocumentation.of(registeredPlugin, r))
            .map(pluginDocumentation -> {
                try {
                    String project = ObjectUtils.firstNonNull(
                        registeredPlugin.getManifest() != null ? registeredPlugin.getManifest().getMainAttributes().getValue("X-Kestra-Title") : null,
                        registeredPlugin.getExternalPlugin() != null ? FilenameUtils.getBaseName(registeredPlugin.getExternalPlugin().getLocation().getPath()) : null,
                        "core"
                    );

                    return new Document(
                        project + "/tasks/" +
                            (pluginDocumentation.getSubGroup() != null ? pluginDocumentation.getSubGroup() + "/" : "") +
                            pluginDocumentation.getCls().getName() + ".md",
                        template.apply(JacksonMapper.toMap(pluginDocumentation))
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());
    }

    private static List<Field> getFields(Class<?> cls) {
        if (cls.isInterface()) {
            return new ArrayList<>();
        }

        List<Field> fields = new ArrayList<>();
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            if (c == Task.class) {
                break;
            }

            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }

        return fields;
    }

    public static Documentation getClassDoc(Class<?> cls) {
        return Arrays.stream(cls.getAnnotationsByType(Documentation.class)).findFirst().orElse(null);
    }

    public static List<Example> getClassExample(Class<?> cls) {
        return Arrays.stream(cls.getAnnotationsByType(Example.class)).collect(Collectors.toList());
    }

    public static Map<String, InputDocumentation> getMainInputs(Class<?> cls) {
        return new TreeMap<>(flatten(getInputs(cls)));
    }

    private static List<InputDocumentation> getInputs(Class<?> cls) {
        return getFields(cls)
            .stream()
            .filter(f -> !Modifier.isTransient(f.getModifiers()))
            .map(field -> new InputDocumentation(
                cls,
                field,
                Arrays.stream(field.getAnnotationsByType(InputProperty.class)).findFirst().orElse(null)
            ))
            .collect(Collectors.toList());
    }

    private static boolean isValidChild(final Class<?> cls) {
        return !DEFAULT_PACKAGES_TO_IGNORE.matcher(cls.getPackageName()).matches() &&
            !cls.isEnum();
    }

    public static <T extends AbstractChildDocumentation<T>> Map<String, T> flatten(List<T> list) {
        return flatten(list, null);
    }

    private static <T extends AbstractChildDocumentation<T>> Map<String, T> flatten(List<T> list, String parentName) {
        Map<String, T> result = new HashMap<>();

        for (T current : list) {
            result.put(flattenKey(current.getName(), parentName), current);
            result.putAll(flatten(current.getChilds(), current.getName()));
        }

        return result;
    }

    private static String flattenKey(String current, String parent) {
        return (parent != null ? parent + "." : "") + current;
    }

    public static List<InputDocumentation> getChildsInputs(Field field) {
        return isValidChild(field.getType()) ?
            DocumentationGenerator.getInputs(field.getType()) :
            new ArrayList<>();
    }

    public static Map<String, OutputDocumentation> getMainOutput(Class<?> cls) {
        List<OutputDocumentation> list = Arrays.stream(cls.getGenericInterfaces())
            .filter(type -> type instanceof ParameterizedType)
            .map(type -> (ParameterizedType) type)
            .flatMap(parameterizedType -> Arrays.stream(parameterizedType.getActualTypeArguments()))
            .filter(type -> type instanceof Class)
            .map(type -> (Class<?>) type)
            .filter(Output.class::isAssignableFrom)
            .flatMap(c -> getOutputs(c).stream())
            .collect(Collectors.toList());

        return new TreeMap<>(flatten(list));
    }

    private static List<OutputDocumentation> getOutputs(Class<?> cls) {
        return getFields(cls)
            .stream()
            .filter(f -> !Modifier.isTransient(f.getModifiers()))
            .map(field -> new OutputDocumentation(
                cls,
                field,
                Arrays.stream(field.getAnnotationsByType(OutputProperty.class)).findFirst().orElse(null)
            ))
            .collect(Collectors.toList());
    }

    public static List<OutputDocumentation> getChildsOutputs(Field field) {
        return isValidChild(field.getType()) ?
            DocumentationGenerator.getOutputs(field.getType()) :
            new ArrayList<>();
    }

    public static String typeName(Class<?> cls) {
        String name = cls.getName();

        if (cls.getPackage() != null && SIMPLE_NAME.contains(cls.getPackage().getName())) {
            name = cls.getSimpleName();
        }

        if (cls.isEnum()) {
            name = "Enum";
        }

        if (cls.isArray()) {
            name = typeName(cls.getComponentType()) + "[]";
        }

        if (cls.isMemberClass()) {
            name = cls.getSimpleName();
        }

        return name;
    }

    public static String typeName(Field field) {
        return genericName(typeName(field.getType()), field);
    }

    private static String genericName(String current, Field field) {
        String generic = Stream.of(field.getGenericType())
            .filter(type -> type instanceof ParameterizedType)
            .map(type -> (ParameterizedType) type)
            .flatMap(parameterizedType -> Arrays.stream(parameterizedType.getActualTypeArguments()))
            .filter(type -> type instanceof Class)
            .map(type -> (Class<?>) type)
            .map(DocumentationGenerator::typeName)
            .collect(Collectors.joining(", "));

        return generic.equals("") ? current : current + "<" + generic + ">";
    }
}
