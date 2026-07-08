package io.kestra.plugin.core.flow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
@KestraTest
class PurgeFlowsTest {
    @Inject
    TestRunContextFactory runContextFactory;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    ModelValidator modelValidator;

    @BeforeEach
    void setup() {
        flowRepository.findAll(MAIN_TENANT).forEach(flow -> flowRepository.delete(flow));
    }

    @Test
    void shouldPurgeOldRevisionsByKeepAmount() throws Exception {
        String namespace = TestsUtils.randomNamespace();
        String flowId = IdUtils.create();
        FlowWithSource revision1 = flowRepository.create(testingFlow(namespace, flowId, "first"));
        FlowWithSource revision2 = flowRepository.update(testingFlow(namespace, flowId, "second"), revision1);
        flowRepository.update(testingFlow(namespace, flowId, "third"), revision2);

        PurgeFlows purgeFlows = PurgeFlows.builder()
            .type(PurgeFlows.class.getName())
            .namespaces(Property.ofValue(List.of(namespace)))
            .behavior(Property.ofValue(Version.builder().keepAmount(2).build()))
            .build();

        PurgeFlows.Output output = purgeFlows.run(runContextFactory.of(namespace));

        assertThat(output.getSize()).isEqualTo(1L);
        assertThat(flowRepository.findRevisions(MAIN_TENANT, namespace, flowId, false))
            .extracting(FlowWithSource::getRevision)
            .containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void shouldPurgeOldRevisionsByDateWithoutDeletingLatestRevision() throws Exception {
        String namespace = TestsUtils.randomNamespace();
        String flowId = IdUtils.create();
        FlowWithSource revision1 = flowRepository.create(testingFlow(namespace, flowId, "first"));
        Instant afterFirstRevision = Instant.now();
        FlowWithSource revision2 = flowRepository.update(testingFlow(namespace, flowId, "second"), revision1);
        flowRepository.update(testingFlow(namespace, flowId, "third"), revision2);

        PurgeFlows purgeFlows = PurgeFlows.builder()
            .type(PurgeFlows.class.getName())
            .namespaces(Property.ofValue(List.of(namespace)))
            .behavior(Property.ofValue(Version.builder().before(afterFirstRevision.toString()).build()))
            .build();

        PurgeFlows.Output output = purgeFlows.run(runContextFactory.of(namespace));

        assertThat(output.getSize()).isEqualTo(1L);
        assertThat(flowRepository.findRevisions(MAIN_TENANT, namespace, flowId, false))
            .extracting(FlowWithSource::getRevision)
            .containsExactlyInAnyOrder(2, 3);
        assertThat(flowRepository.findById(MAIN_TENANT, namespace, flowId).isPresent()).isTrue();
    }

    @Test
    void shouldPurgeOnlyFlowsMatchingPattern() throws Exception {
        String namespace = TestsUtils.randomNamespace();
        String matchingFlowId = "matching_" + IdUtils.create();
        String otherFlowId = "other_" + IdUtils.create();
        FlowWithSource matchingRevision1 = flowRepository.create(testingFlow(namespace, matchingFlowId, "first"));
        flowRepository.update(testingFlow(namespace, matchingFlowId, "second"), matchingRevision1);
        FlowWithSource otherRevision1 = flowRepository.create(testingFlow(namespace, otherFlowId, "first"));
        flowRepository.update(testingFlow(namespace, otherFlowId, "second"), otherRevision1);

        PurgeFlows purgeFlows = PurgeFlows.builder()
            .type(PurgeFlows.class.getName())
            .namespaces(Property.ofValue(List.of(namespace)))
            .flowPattern(Property.ofValue("matching_*"))
            .build();

        PurgeFlows.Output output = purgeFlows.run(runContextFactory.of(namespace));

        assertThat(output.getSize()).isEqualTo(1L);
        assertThat(flowRepository.findRevisions(MAIN_TENANT, namespace, matchingFlowId, false))
            .extracting(FlowWithSource::getRevision)
            .containsExactly(2);
        assertThat(flowRepository.findRevisions(MAIN_TENANT, namespace, otherFlowId, false))
            .extracting(FlowWithSource::getRevision)
            .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void validation() {
        PurgeFlows valid = PurgeFlows.builder()
            .id(IdUtils.create())
            .type(PurgeFlows.class.getName())
            .behavior(Property.ofValue(Version.builder().keepAmount(2).build()))
            .build();
        PurgeFlows invalid = PurgeFlows.builder()
            .id(IdUtils.create())
            .type(PurgeFlows.class.getName())
            .behavior(Property.ofValue(Version.builder().before(Instant.now().toString()).keepAmount(2).build()))
            .build();

        Optional<ConstraintViolationException> validException = modelValidator.isValid(valid);
        Optional<ConstraintViolationException> invalidException = modelValidator.isValid(invalid);

        assertThat(validException).isEmpty();
        assertThat(invalidException).isPresent();
        assertThat(invalidException.get().getMessage()).contains("behavior.validPurgeConfiguration: Cannot set both 'before' and 'keepAmount' properties");
    }

    private GenericFlow testingFlow(String namespace, String flowId, String message) {
        return GenericFlow.of(
            Flow.builder()
                .tenantId(MAIN_TENANT)
                .namespace(namespace)
                .id(flowId)
                .tasks(List.of(Return.builder().id("return").type(Return.class.getName()).format(Property.ofValue(message)).build()))
                .build()
        );
    }
}
