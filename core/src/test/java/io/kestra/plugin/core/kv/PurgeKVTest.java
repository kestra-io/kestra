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
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.kv.PurgeKV.Output;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import javax.print.attribute.standard.MediaSize.NA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@KestraTest
public class PurgeKVTest {

    public static final String PARENT_NAMESPACE = "parent";
    public static final String CHILD_NAMESPACE = "parent.child";
    public static final String NAMESPACE = "io.kestra.tests";
    public static final String KEY_EXPIRED = "key_expired";
    public static final String KEY = "key";

    @Inject
    TestRunContextFactory runContextFactory;

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;


    @BeforeEach
    protected void setup() throws IOException {
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
    void should_not_find_namespaces_with_incorrect_parameters() {
        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .namespaces(Property.ofValue(List.of("ns1", "ns2", PARENT_NAMESPACE)))
            .namespacePattern(Property.ofValue("*par*"))
            .build();
        assertThrows(ValidationErrorException.class, () -> purgeKV.findNamespaces(runContextFactory.of(NAMESPACE)));
    }

    @Test
    void should_delete_every_expired_from_every_namespaces_without_parameters() throws Exception {
        String namespace1 = "io.kestra." + IdUtils.create();
        String namespace2 = "io.kestra." + IdUtils.create();
        addNamespace(namespace1);
        addNamespace(namespace2);

        RunContext runContext = runContextFactory.of(namespace1);
        KVStore kvStore1 = runContext.namespaceKv(namespace1);
        kvStore1.put(KEY_EXPIRED, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMillis(1L)), "unused"));
        kvStore1.put(KEY, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMinutes(1L)), "unused"));

        KVStore kvStore2 = runContext.namespaceKv(namespace2);
        kvStore2.put(KEY_EXPIRED, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMillis(1L)), "unused"));
        kvStore2.put(KEY, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMinutes(1L)), "unused"));

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .build();
        Output output = purgeKV.run(runContext);

        assertThat(output.getSize()).isEqualTo(2L);
        assertThat(kvStore1.get(KEY_EXPIRED)).isEmpty();
        assertThat(kvStore1.get(KEY)).isPresent();
        assertThat(kvStore2.get(KEY_EXPIRED)).isEmpty();
        assertThat(kvStore2.get(KEY)).isPresent();
    }

    @Test
    void should_delete_every_exipred_and_non_expired() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();
        addNamespace(namespace);

        RunContext runContext = runContextFactory.of(namespace);
        KVStore kvStore1 = runContext.namespaceKv(namespace);
        kvStore1.put(KEY_EXPIRED, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMillis(1L)), "unused"));
        kvStore1.put(KEY, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMinutes(1L)), "unused"));

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .expiredOnly(Property.ofValue(false))
            .build();
        Output output = purgeKV.run(runContext);

        assertThat(output.getSize()).isEqualTo(2L);
        assertThat(kvStore1.get(KEY_EXPIRED)).isEmpty();
        assertThat(kvStore1.get(KEY)).isEmpty();
    }

    @Test
    void should_delete_every_keys_matching_pattern() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();
        addNamespace(namespace);

        RunContext runContext = runContextFactory.of(namespace);
        KVStore kvStore1 = runContext.namespaceKv(namespace);
        kvStore1.put("key_1", new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMillis(1L)), "unused"));
        kvStore1.put("key_2", new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMillis(1L)), "unused"));
        kvStore1.put("not_found", new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMillis(1L)), "unused"));


        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .keyPattern(Property.ofValue("*ey*"))
            .build();
        Output output = purgeKV.run(runContext);

        assertThat(output.getSize()).isEqualTo(2L);
        assertThat(kvStore1.get("key_1")).isEmpty();
        assertThat(kvStore1.get("key_2")).isEmpty();
        assertThat(kvStore1.get("not_found")).isEmpty();
    }

    private void addNamespaces() {
        addNamespace(NAMESPACE);
        addNamespace(PARENT_NAMESPACE);
        addNamespace(CHILD_NAMESPACE);
    }

    private void addNamespace(String namespace){
        flowRepositoryInterface.create(GenericFlow.of(Flow.builder()
            .tenantId(MAIN_TENANT)
            .namespace(namespace)
            .id("flow1")
            .tasks(List.of(PurgeKV.builder().type(PurgeKV.class.getName()).build()))
            .build()));
    }

}
