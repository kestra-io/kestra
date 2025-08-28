package io.kestra.plugin.core.kv;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.FlowRepositoryInterface;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@KestraTest
public class PurgeKVTest {

    public static final String PARENT_NAMESPACE = "parent";
    public static final String CHILD_NAMESPACE = "parent.child";
    public static final String NAMESPACE = "io.kestra.tests";

    @Inject
    TestRunContextFactory runContextFactory;

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;


    @BeforeEach
    protected void setup() {
        flowRepositoryInterface.findAll(MAIN_TENANT).forEach(flow -> flowRepositoryInterface.delete(flow));
    }

    @Test
    void should_find_all_namespaces() throws IllegalVariableEvaluationException {
        addNamespaces();

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .build();
        List<String> namespaces = purgeKV.findNamespaces(runContextFactory.of(NAMESPACE));

        assertThat(namespaces).containsExactlyInAnyOrder(NAMESPACE, CHILD_NAMESPACE, PARENT_NAMESPACE);
    }

    @Test
    void should_find_all_namespaces_with_glob_pattern() throws IllegalVariableEvaluationException {
        addNamespaces();

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .namespacePattern(Property.ofValue("*arent*"))
            .build();
        List<String> namespaces = purgeKV.findNamespaces(runContextFactory.of(NAMESPACE));

        assertThat(namespaces).containsExactlyInAnyOrder(CHILD_NAMESPACE, PARENT_NAMESPACE);
    }

    @Test
    void should_find_all_namespaces_with_namespace_list_without_child() throws IllegalVariableEvaluationException {
        addNamespaces();

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .namespaces(Property.ofValue(List.of("ns1", "ns2", PARENT_NAMESPACE)))
            .includeChildNamespaces(Property.ofValue(false))
            .build();
        List<String> namespaces = purgeKV.findNamespaces(runContextFactory.of(NAMESPACE));

        assertThat(namespaces).containsExactlyInAnyOrder(PARENT_NAMESPACE);
    }

    @Test
    void should_find_all_namespaces_with_namespace_list_with_child() throws IllegalVariableEvaluationException {
        addNamespaces();

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .namespaces(Property.ofValue(List.of("ns1", "ns2", PARENT_NAMESPACE)))
            .includeChildNamespaces(Property.ofValue(true))
            .build();
        List<String> namespaces = purgeKV.findNamespaces(runContextFactory.of(NAMESPACE));

        assertThat(namespaces).containsExactlyInAnyOrder(PARENT_NAMESPACE, CHILD_NAMESPACE);
    }

    @Test
    void should_not_find_namespaces_with_incorrect_parameters() throws IllegalVariableEvaluationException {
        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .namespaces(Property.ofValue(List.of("ns1", "ns2", PARENT_NAMESPACE)))
            .namespacePattern(Property.ofValue("*par*"))
            .build();
        assertThrows(ValidationErrorException.class, () -> purgeKV.findNamespaces(runContextFactory.of(NAMESPACE)));
    }

    private void addNamespaces() {
        List<Task> tasks = List.of(PurgeKV.builder().type(PurgeKV.class.getName()).build());
        flowRepositoryInterface.create(GenericFlow.of(Flow.builder()
            .tenantId(MAIN_TENANT)
            .namespace(NAMESPACE)
            .id("flow1")
            .tasks(tasks)
            .build()));
        flowRepositoryInterface.create(GenericFlow.of(Flow.builder()
            .tenantId(MAIN_TENANT)
            .namespace(PARENT_NAMESPACE)
            .id("flow2")
            .tasks(tasks)
            .build()));
        flowRepositoryInterface.create(GenericFlow.of(Flow.builder()
            .tenantId(MAIN_TENANT)
            .namespace(CHILD_NAMESPACE)
            .id("flow3")
            .tasks(tasks)
            .build()));
    }

}
