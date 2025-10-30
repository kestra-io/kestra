package io.kestra.plugin.core.kv;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.kv.PurgeKV.Output;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Execution(ExecutionMode.SAME_THREAD)
@KestraTest
public class PurgeKVTest {
    public static final String PARENT_NAMESPACE = "parent";
    public static final String CHILD_NAMESPACE = "parent.child";
    public static final String NAMESPACE = "io.kestra.tests";
    public static final String KEY_EXPIRED = "key_expired";
    public static final String KEY = "key";
    public static final String KEY2_NEVER_EXPIRING = "key2_never_expired";
    public static final String KEY3_NEVER_EXPIRING = "key3_never_expired";

    @Inject
    TestRunContextFactory runContextFactory;

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    ModelValidator modelValidator;


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
        kvStore2.put(KEY2_NEVER_EXPIRING, new KVValueAndMetadata(new KVMetadata("unused", (Duration) null), "unused"));
        kvStore2.put(KEY3_NEVER_EXPIRING, new KVValueAndMetadata(null, "unused"));

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .build();
        Output output = purgeKV.run(runContext);

        assertThat(output.getSize()).isEqualTo(2L);
        assertThat(kvStore1.get(KEY_EXPIRED)).isEmpty();
        assertThat(kvStore1.get(KEY)).isPresent();
        assertThat(kvStore2.get(KEY_EXPIRED)).isEmpty();
        assertThat(kvStore2.get(KEY)).isPresent();
        assertThat(kvStore2.get(KEY2_NEVER_EXPIRING)).isPresent();
        assertThat(kvStore2.get(KEY3_NEVER_EXPIRING)).isPresent();
    }

    @Test
    void should_delete_every_expired_and_non_expired() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();
        addNamespace(namespace);

        RunContext runContext = runContextFactory.of(namespace);
        KVStore kvStore1 = runContext.namespaceKv(namespace);
        kvStore1.put(KEY_EXPIRED, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMillis(1L)), "unused"));
        kvStore1.put(KEY, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMinutes(1L)), "unused"));
        kvStore1.put(KEY2_NEVER_EXPIRING, new KVValueAndMetadata(new KVMetadata("unused", (Duration) null), "unused"));
        kvStore1.put(KEY3_NEVER_EXPIRING, new KVValueAndMetadata(null, "unused"));

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .behavior(Property.ofValue(Key.builder().expiredOnly(false).build()))
            .build();
        Output output = purgeKV.run(runContext);

        assertThat(output.getSize()).isEqualTo(4L);
        assertThat(kvStore1.get(KEY_EXPIRED)).isEmpty();
        assertThat(kvStore1.get(KEY)).isEmpty();
        assertThat(kvStore1.get(KEY2_NEVER_EXPIRING)).isEmpty();
        assertThat(kvStore1.get(KEY3_NEVER_EXPIRING)).isEmpty();
    }

    @Test
    void expiredOnly_still_supported_and_overrides_behavior() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();
        addNamespace(namespace);

        RunContext runContext = runContextFactory.of(namespace);
        KVStore kvStore1 = runContext.namespaceKv(namespace);
        kvStore1.put(KEY_EXPIRED, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMillis(1L)), "unused"));
        kvStore1.put(KEY, new KVValueAndMetadata(new KVMetadata("unused", Duration.ofMinutes(1L)), "unused"));
        kvStore1.put(KEY2_NEVER_EXPIRING, new KVValueAndMetadata(new KVMetadata("unused", (Duration) null), "unused"));
        kvStore1.put(KEY3_NEVER_EXPIRING, new KVValueAndMetadata(null, "unused"));

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .behavior(Property.ofValue(Key.builder().expiredOnly(true).build()))
            .expiredOnly(Property.ofValue(false))
            .build();
        Output output = purgeKV.run(runContext);

        assertThat(output.getSize()).isEqualTo(4L);
        assertThat(kvStore1.get(KEY_EXPIRED)).isEmpty();
        assertThat(kvStore1.get(KEY)).isEmpty();
        assertThat(kvStore1.get(KEY2_NEVER_EXPIRING)).isEmpty();
        assertThat(kvStore1.get(KEY3_NEVER_EXPIRING)).isEmpty();
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
        List<KVEntry> kvEntries = kvStore1.listAll();
        assertThat(kvEntries.size()).isEqualTo(1);
        assertThat(kvEntries.getFirst().key()).isEqualTo("not_found");
    }

    @Test
    void version_filter_by_date() throws Exception {
        String namespace = TestsUtils.randomNamespace();
        addNamespace(namespace);

        RunContext runContext = runContextFactory.of(namespace);
        KVStore kvStore = runContext.namespaceKv(namespace);

        kvStore.put("my-key", new KVValueAndMetadata(new KVMetadata("Some description", Instant.now().plus(Duration.ofMinutes(5))), "some value"));
        Instant afterFirstVersion = Instant.now();
        String changedDescription = "Another description";
        kvStore.put("my-key", new KVValueAndMetadata(new KVMetadata(changedDescription, Instant.now().plus(Duration.ofMinutes(5))), "some value"));

        List<KVEntry> kvs = kvStore.list(Pageable.UNPAGED, Collections.emptyList(), true, true, FetchVersion.ALL);
        assertThat(kvs.size()).isEqualTo(2);

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .behavior(Property.ofValue(Version.builder().before(afterFirstVersion.toString()).build()))
            .build();
        Output run = purgeKV.run(runContext);

        assertThat(run.getSize()).isEqualTo(1L);

        kvs = kvStore.list(Pageable.UNPAGED, Collections.emptyList(), true, true, FetchVersion.ALL);
        assertThat(kvs.size()).isEqualTo(1);
        assertThat(kvs.getFirst().description()).isEqualTo(changedDescription);
    }

    @Test
    void version_filter_by_keep_amount() throws Exception {
        String namespace = TestsUtils.randomNamespace();
        addNamespace(namespace);

        RunContext runContext = runContextFactory.of(namespace);
        KVStore kvStore = runContext.namespaceKv(namespace);

        kvStore.put("my-key", new KVValueAndMetadata(new KVMetadata("Some description", Instant.now().plus(Duration.ofMinutes(5))), "some value"));
        String secondDescription = "Another description";
        kvStore.put("my-key", new KVValueAndMetadata(new KVMetadata(secondDescription, Instant.now().plus(Duration.ofMinutes(5))), "some value"));
        String thirdDescription = "Yet another description";
        kvStore.put("my-key", new KVValueAndMetadata(new KVMetadata(thirdDescription, Instant.now().plus(Duration.ofMinutes(5))), "some value"));

        List<KVEntry> kvs = kvStore.list(Pageable.UNPAGED, Collections.emptyList(), true, true, FetchVersion.ALL);
        assertThat(kvs.size()).isEqualTo(3);

        PurgeKV purgeKV = PurgeKV.builder()
            .type(PurgeKV.class.getName())
            .behavior(Property.ofValue(Version.builder().keepAmount(2).build()))
            .build();
        Output run = purgeKV.run(runContext);

        assertThat(run.getSize()).isEqualTo(1L);

        kvs = kvStore.list(Pageable.UNPAGED, Collections.emptyList(), true, true, FetchVersion.ALL);
        assertThat(kvs.size()).isEqualTo(2);
        assertThat(kvs.stream().map(KVEntry::description)).containsExactlyInAnyOrder(secondDescription, thirdDescription);
    }

    @Test
    void validation() throws Exception {
        // valid
        assertThat(modelValidator.isValid(PurgeKV.builder()
            .id(IdUtils.create())
            .type(PurgeKV.class.getName())
            .behavior(Property.ofValue(Version.builder().before(Instant.now().toString()).build()))
            .build()).isPresent()).isFalse();
        assertThat(modelValidator.isValid(PurgeKV.builder()
            .id(IdUtils.create())
            .type(PurgeKV.class.getName())
            .behavior(Property.ofValue(Version.builder().keepAmount(2).build()))
            .build()).isPresent()).isFalse();

        // invalid
        Optional<ConstraintViolationException> invalid = modelValidator.isValid(PurgeKV.builder()
            .id(IdUtils.create())
            .type(PurgeKV.class.getName())
            .behavior(Property.ofValue(Version.builder().before(Instant.now().toString()).keepAmount(2).build()))
            .build());
        assertThat(invalid.isPresent()).isTrue();
        assertThat(invalid.get().getMessage()).contains("behavior: Cannot set both 'before' and 'keepAmount' properties");
    }

    private void addNamespaces() {
        addNamespace(NAMESPACE);
        addNamespace(PARENT_NAMESPACE);
        addNamespace(CHILD_NAMESPACE);
    }

    private void addNamespace(String namespace) {
        flowRepositoryInterface.create(GenericFlow.of(Flow.builder()
            .tenantId(MAIN_TENANT)
            .namespace(namespace)
            .id("flow1")
            .tasks(List.of(PurgeKV.builder().type(PurgeKV.class.getName()).build()))
            .build()));
    }

}
