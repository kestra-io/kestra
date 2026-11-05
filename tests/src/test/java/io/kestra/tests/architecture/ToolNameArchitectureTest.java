package io.kestra.tests.architecture;

import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "io.kestra", importOptions = ImportOption.DoNotIncludeTests.class)
public class ToolNameArchitectureTest {
    private static final String TOOL_ANNOTATION = "dev.langchain4j.agent.tool.Tool";
    private static final Pattern TOOL_NAME = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)+$");
    private static final int MAX_TOOL_NAME_LENGTH = 64;

    @ArchTest
    public static final ArchRule tool_names_are_snake_case_verb_noun = methods()
        .that().areAnnotatedWith(TOOL_ANNOTATION)
        .should(new ArchCondition<>("declare a snake_case verb_noun name of at most " + MAX_TOOL_NAME_LENGTH + " characters") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String name = String.valueOf(method.getAnnotationOfType(TOOL_ANNOTATION).get("name").orElse(""));
                if (!TOOL_NAME.matcher(name).matches() || name.length() > MAX_TOOL_NAME_LENGTH) {
                    events.add(SimpleConditionEvent.violated(
                        method,
                        "%s declares tool name '%s'; set @Tool(name = ...) to a snake_case verb_noun such as read_flow".formatted(method.getFullName(), name)
                    ));
                }
            }
        })
        .because("tool names are the contract with LLM providers and MCP clients, so they must not depend on Java method names or drift between tools");
}
