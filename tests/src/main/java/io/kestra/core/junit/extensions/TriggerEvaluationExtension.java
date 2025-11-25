package io.kestra.core.junit.extensions;

import io.kestra.core.junit.annotations.EvaluateTrigger;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.YamlParser;
import io.micronaut.context.ApplicationContext;
import lombok.SneakyThrows;
import org.junit.jupiter.api.extension.*;

import java.net.URL;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * JUnit 5 extension to evaluate triggers and inject its Optional<Execution>.
 */
public class TriggerEvaluationExtension implements ParameterResolver {
    ApplicationContext context;


    RunContextFactory runContextFactory;


    RunContextInitializer runContextInitializer;

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == Optional.class;
    }

    @SneakyThrows
    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        ensureContext(extensionContext);

        EvaluateTrigger evaluateTrigger = extensionContext.getRequiredTestMethod()
            .getAnnotation(EvaluateTrigger.class);

        String path = evaluateTrigger.flow();
        URL url = getClass().getClassLoader().getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("Unable to load flow: " + path);
        }

        Flow flow = YamlParser.parse(Paths.get(url.toURI()).toFile(), Flow.class);


        AbstractTrigger trigger =  flow.getTriggers().stream()
            .filter(t -> t.getId().equals(evaluateTrigger.triggerId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Trigger not found: " + evaluateTrigger.triggerId()));


    return evaluateTrigger(trigger,flow);
    }

    private void ensureContext(ExtensionContext extensionContext) {
        if (context == null) {
            context = extensionContext.getRoot()
                .getStore(ExtensionContext.Namespace.create(KestraTestExtension.class, extensionContext.getTestClass().get()))
                .get(ApplicationContext.class, ApplicationContext.class);

            if (context == null) {
                throw new IllegalStateException("No ApplicationContext found. Add @KestraTest to the test class.");
            }
            runContextFactory = context.getBean(RunContextFactory.class);
            runContextInitializer = context.getBean(RunContextInitializer.class);
        }
    }
    private Optional<Execution> evaluateTrigger(AbstractTrigger trigger,Flow flow) throws Exception {

        if(trigger instanceof PollingTriggerInterface pollingTrigger){
            TriggerContext triggerContext = triggerContext(trigger, flow);
            ConditionContext conditionContext = conditionContext(trigger, flow);

            return pollingTrigger.evaluate(conditionContext, triggerContext);
        }
        else {
            throw new IllegalArgumentException("Unsupported trigger type: " + trigger.getClass());
        }
    }
    private ConditionContext conditionContext(AbstractTrigger trigger, Flow flow) {

        TriggerContext triggerContext=triggerContext(trigger,flow);

        return ConditionContext.builder()
            .runContext(runContextInitializer.forScheduler(
                (DefaultRunContext) runContextFactory.of(), triggerContext, trigger
            ))
            .flow(flow)
            .build();
    }
    private TriggerContext triggerContext(AbstractTrigger trigger,Flow flow)
    {
        return TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .date(ZonedDateTime.now())
            .build();
    }


}
