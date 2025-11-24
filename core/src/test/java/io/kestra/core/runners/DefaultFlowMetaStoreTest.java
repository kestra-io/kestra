package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class DefaultFlowMetaStoreTest {
    @Inject
    private DefaultFlowMetaStore flowMetaStore;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @AfterEach
    void clean() {
        flowMetaStore.clearCache();
    }

    @Test
    void findById() {
        Flow test = createFlow();
        flowRepository.create(GenericFlow.of(test));

        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty());

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());

        flowRepository.delete(test);
    }

    @Test
    void findByIdShouldReturnEmptyForAbsentFlow() {
        Flow test = createFlow();
        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty());

        assertThat(maybeFlow).isEmpty();
    }

    @Test
    void findByIdShouldReturnLastRevision() {
        Flow test = createFlow();
        flowRepository.create(GenericFlow.of(test));
        Flow updated = test.toBuilder().tasks(List.of(Return.builder().id("return").format(Property.ofValue("new format")).type(Return.class.getName()).build())).build();
        flowRepository.update(GenericFlow.of(updated), test);

        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.of(2));

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());
        assertThat(maybeFlow.get().getRevision()).isEqualTo(2);

        flowRepository.delete(test);
    }

    @Test
    void findByIdShouldReturnPreviousRevision() {
        Flow test = createFlow();
        flowRepository.create(GenericFlow.of(test));
        flowRepository.update(GenericFlow.of(test.toBuilder().revision(2).build()), test);

        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.of(1));

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());
        assertThat(maybeFlow.get().getRevision()).isEqualTo(1);

        flowRepository.delete(test);
    }

    @Test
    void findByIdShouldReturnEmptyForDeletedFlow() throws InterruptedException {
        Flow test = createFlow();
        flowRepository.create(GenericFlow.of(test));
        flowRepository.delete(test);
        Thread.sleep(100); // make sure the metastore receive the deletion

        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty());

        assertThat(maybeFlow).isEmpty();
    }

    @Test
    void findByExecution() {
        Flow test = createFlow();
        FlowWithSource created = flowRepository.create(GenericFlow.of(test));
        Execution execution = Execution.newExecution(created, null, null, Optional.empty());

        Optional<FlowInterface> maybeFlow = flowMetaStore.findByExecution(execution);

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());

        flowRepository.delete(test);
    }

    @Test
    void findByExecutionShouldReturnEmptyForAbsentFlow() {
        Flow test = createFlow();
        Execution execution = Execution.newExecution(test, null, null, Optional.empty());

        Optional<FlowInterface> maybeFlow = flowMetaStore.findByExecution(execution);

        assertThat(maybeFlow).isEmpty();
    }

    @Test
    void findByExecutionThenInjectDefaults() {
        Flow test = createFlow();
        FlowWithSource created = flowRepository.create(GenericFlow.of(test));
        Execution execution = Execution.newExecution(created, null, null, Optional.empty());

        Optional<FlowWithSource> maybeFlow = flowMetaStore.findByExecutionThenInjectDefaults(execution);

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());

        flowRepository.delete(test);
    }

    @Test
    void findByExecutionThenInjectDefaultsShouldReturnEmptyForAbsentFlow() {
        Flow test = createFlow();
        Execution execution = Execution.newExecution(test, null, null, Optional.empty());

        Optional<FlowWithSource> maybeFlow = flowMetaStore.findByExecutionThenInjectDefaults(execution);

        assertThat(maybeFlow).isEmpty();
    }

    @Test
    void allLastVersion() throws TimeoutException, InterruptedException {
        FlowWithSource test1 = createFlow();
        flowRepository.create(GenericFlow.of(test1));
        FlowWithSource test2 = createFlow();
        flowRepository.create(GenericFlow.of(test2));
        Thread.sleep(100); // make sure the metastore receive the items

        Collection<FlowWithSource> flows = flowMetaStore.allLastVersion();

        assertThat(flows).hasSize(2);
        assertThat(flows).extracting(flow -> flow.getId()).contains(test1.getId(), test2.getId());
    }

    @Test
    void findByIdFromTask() {
        Flow test = createFlow();
        flowRepository.create(GenericFlow.of(test));

        Optional<FlowInterface> maybeFlow = flowMetaStore.findByIdFromTask(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty(), test.getTenantId(), test.getNamespace(), test.getId());

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());

        flowRepository.delete(test);
    }

    @Test
    void findByIdFromTaskShouldReturnEmptyForAbsentFlow() {
        Flow test = createFlow();
        Optional<FlowInterface> maybeFlow = flowMetaStore.findByIdFromTask(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty(), test.getTenantId(), test.getNamespace(), test.getId());

        assertThat(maybeFlow).isEmpty();
    }

    private FlowWithSource createFlow() {
        return FlowWithSource.builder()
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.tests")
            .id(IdUtils.create())
            .tasks(List.of(Return.builder().id("return").format(Property.ofValue("format")).type(Return.class.getName()).build()))
            .build();
    }
}