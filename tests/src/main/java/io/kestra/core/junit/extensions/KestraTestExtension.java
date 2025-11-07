package io.kestra.core.junit.extensions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.TestQueueFactory;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.test.annotation.MicronautTestValue;
import io.micronaut.test.extensions.junit5.MicronautJunit5Extension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

@Slf4j
public class KestraTestExtension extends MicronautJunit5Extension {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(KestraTestExtension.class);

    @Override
    protected MicronautTestValue buildMicronautTestValue(Class<?> testClass) {
        return AnnotationSupport
            .findAnnotation(testClass, KestraTest.class)
            .map(kestraTestAnnotation -> new MicronautTestValue(
                kestraTestAnnotation.application(),
                kestraTestAnnotation.environments(),
                kestraTestAnnotation.packages(),
                kestraTestAnnotation.propertySources(),
                kestraTestAnnotation.rollback(),
                kestraTestAnnotation.transactional(),
                kestraTestAnnotation.rebuildContext(),
                kestraTestAnnotation.contextBuilder(),
                kestraTestAnnotation.transactionMode(),
                kestraTestAnnotation.startApplication(),
                kestraTestAnnotation.resolveParameters()
            ))
            .orElse(null);
    }

    @Override
    protected ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getRoot().getStore(NAMESPACE);
    }

    @Override
    protected boolean hasExpectedAnnotations(Class<?> testClass) {
        return AnnotationSupport.isAnnotated(testClass, KestraTest.class);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        super.beforeAll(extensionContext);
        KestraTest kestraTest = extensionContext.getTestClass()
            .orElseThrow()
            .getAnnotation(KestraTest.class);

        if (kestraTest.startRunner()) {
            TestRunner runner = applicationContext.getBean(TestRunner.class);
            if (!runner.isRunning()) {
                runner.setSchedulerEnabled(kestraTest.startScheduler());
                runner.setWorkerEnabled(kestraTest.startWorker());
                runner.run();
            }
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        super.afterTestExecution(context);

        TestsUtils.queueConsumersCleanup();

        KestraTest kestraTest = context.getTestClass()
            .orElseThrow()
            .getAnnotation(KestraTest.class);
        Optional<TestQueueFactory> testQueueFactory = Optional.of(applicationContext.containsBean(TestQueueFactory.class)).flatMap(contains -> contains ? Optional.of(applicationContext.getBean(TestQueueFactory.class)) : Optional.empty());
        List<Execution> testExecutions = testQueueFactory.map(TestQueueFactory::getTestExecutions).orElse(Collections.emptyList());
        if (!testExecutions.isEmpty()
            && applicationContext.containsBean(ExecutionRepositoryInterface.class)
            && applicationContext.containsBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.KILL_NAMED))) {
            ExecutionRepositoryInterface executionRepository = applicationContext.getBean(ExecutionRepositoryInterface.class);
            QueueInterface<ExecutionKilled> killQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.KILL_NAMED));

            retryingExecutionKill(testExecutions, executionRepository, killQueue, 10);

            testExecutions.clear();
        }
    }


    private void retryingExecutionKill(List<Execution> testExecutions, ExecutionRepositoryInterface executionRepository, QueueInterface<ExecutionKilled> killQueue, int retriesLeft) throws InterruptedException {
        try {
            ListUtils.distinctByKey(
                    testExecutions.stream().flatMap(launchedExecution -> executionRepository.findById(launchedExecution.getTenantId(), launchedExecution.getId()).stream()).toList(),
                    Execution::getId
                ).stream().filter(inRepository -> inRepository.getState().isRunning() || inRepository.getState().isPaused() || inRepository.getState().isQueued())
                .forEach(inRepository -> {
                    log.warn("Execution {} is still running after test execution, killing it", inRepository.getId());
                    try {
                        killQueue.emit(ExecutionKilledExecution.builder()
                            .tenantId(inRepository.getTenantId())
                            .executionId(inRepository.getId())
                            .state(ExecutionKilled.State.REQUESTED)
                            .isOnKillCascade(true)
                            .build()
                        );
                    } catch (QueueException e) {
                        log.warn("Couldn't kill execution {} after test execution", inRepository.getId(), e);
                    }
                });
        } catch (ConcurrentModificationException e) {
            // We intentionally don't use a CopyOnWriteArrayList to retry on concurrent modification exceptions to make sure to get rid of flakiness due to overflowing executions
            if (retriesLeft <= 0) {
                log.warn("Couldn't kill executions after test execution, due to concurrent modifications, this could impact further tests", e);
                return;
            }
            Thread.sleep(100);
            retryingExecutionKill(testExecutions, executionRepository, killQueue, retriesLeft - 1);
        }
    }
}
