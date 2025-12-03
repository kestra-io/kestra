package io.kestra.plugin.core.namespace;

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
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
public class PurgeFilesTest {
    public static final String PARENT_NAMESPACE = "parent";
    public static final String CHILD_NAMESPACE = "parent.child";
    public static final String NAMESPACE = "io.kestra.tests";
    public static final String KEY = "file";

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

        PurgeFiles purgeFiles = PurgeFiles.builder()
            .type(PurgeFiles.class.getName())
            .build();
        List<String> namespaces = purgeFiles.findNamespaces(runContextFactory.of(NAMESPACE));

        assertThat(namespaces).containsExactlyInAnyOrder(NAMESPACE, CHILD_NAMESPACE, PARENT_NAMESPACE);
    }

    @Test
    void should_find_all_namespaces_with_glob_pattern() throws IllegalVariableEvaluationException {
        addNamespaces();

        PurgeFiles purgeFiles = PurgeFiles.builder()
            .type(PurgeFiles.class.getName())
            .namespacePattern(Property.ofValue("*arent*"))
            .build();
        List<String> namespaces = purgeFiles.findNamespaces(runContextFactory.of(NAMESPACE));

        assertThat(namespaces).containsExactlyInAnyOrder(CHILD_NAMESPACE, PARENT_NAMESPACE);
    }

    @Test
    void should_find_all_namespaces_with_namespace_list_without_child() throws IllegalVariableEvaluationException {
        addNamespaces();

        PurgeFiles purgeFiles = PurgeFiles.builder()
            .type(PurgeFiles.class.getName())
            .namespaces(Property.ofValue(List.of("ns1", "ns2", PARENT_NAMESPACE)))
            .includeChildNamespaces(Property.ofValue(false))
            .build();
        List<String> namespaces = purgeFiles.findNamespaces(runContextFactory.of(NAMESPACE));

        assertThat(namespaces).containsExactlyInAnyOrder(PARENT_NAMESPACE);
    }

    @Test
    void should_find_all_namespaces_with_namespace_list_with_child() throws IllegalVariableEvaluationException {
        addNamespaces();

        PurgeFiles purgeFiles = PurgeFiles.builder()
            .type(PurgeFiles.class.getName())
            .namespaces(Property.ofValue(List.of("ns1", "ns2", PARENT_NAMESPACE)))
            .includeChildNamespaces(Property.ofValue(true))
            .build();
        List<String> namespaces = purgeFiles.findNamespaces(runContextFactory.of(NAMESPACE));

        assertThat(namespaces).containsExactlyInAnyOrder(PARENT_NAMESPACE, CHILD_NAMESPACE);
    }

    @Test
    void should_not_find_namespaces_with_incorrect_parameters() {
        PurgeFiles purgeFiles = PurgeFiles.builder()
            .type(PurgeFiles.class.getName())
            .namespaces(Property.ofValue(List.of("ns1", "ns2", PARENT_NAMESPACE)))
            .namespacePattern(Property.ofValue("*par*"))
            .build();
        assertThrows(ValidationErrorException.class, () -> purgeFiles.findNamespaces(runContextFactory.of(NAMESPACE)));
    }

    @Test
    void should_delete_every_files_matching_pattern() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();
        addNamespace(namespace);

        RunContext runContext = runContextFactory.of(namespace);
        Namespace namespaceStorage = runContext.storage().namespace(namespace);
        namespaceStorage.putFile(Path.of("my/first/file.txt"), new ByteArrayInputStream("unused".getBytes(StandardCharsets.UTF_8)));
        namespaceStorage.putFile(Path.of("my/second/file.txt"), new ByteArrayInputStream("unused".getBytes(StandardCharsets.UTF_8)));
        namespaceStorage.putFile(Path.of("not/found.txt"), new ByteArrayInputStream("unused".getBytes(StandardCharsets.UTF_8)));


        PurgeFiles purgeFiles = PurgeFiles.builder()
            .type(PurgeFiles.class.getName())
            .filePattern(Property.ofValue("*il*"))
            .behavior(Property.ofValue(Version.builder().keepAmount(0).build()))
            .build();
        PurgeFiles.Output output = purgeFiles.run(runContext);

        assertThat(output.getSize()).isEqualTo(2L);
        List<NamespaceFile> namespaceFiles = namespaceStorage.all();
        assertThat(namespaceFiles.size()).isEqualTo(1);
        assertThat(namespaceFiles.getFirst().path()).isEqualTo("not/found.txt");
    }

    @Test
    void version_filter_by_date() throws Exception {
        String namespace = TestsUtils.randomNamespace();
        addNamespace(namespace);

        RunContext runContext = runContextFactory.of(namespace);
        Namespace namespaceStorage = runContext.storage().namespace(namespace);

        namespaceStorage.putFile(Path.of("my/first/file.txt"), new ByteArrayInputStream("some value".getBytes(StandardCharsets.UTF_8)));
        Instant afterFirstVersion = Instant.now();
        namespaceStorage.putFile(Path.of("my/first/file.txt"), new ByteArrayInputStream("another value".getBytes(StandardCharsets.UTF_8)));

        List<NamespaceFile> namespaceFiles = namespaceStorage.find(Pageable.UNPAGED, Collections.emptyList(), true, FetchVersion.ALL);
        // "/", "/my", "/my/first", "/my/first/file.txt" x2 versions
        assertThat(namespaceFiles.size()).isEqualTo(5);

        PurgeFiles purgeFiles = PurgeFiles.builder()
            .type(PurgeFiles.class.getName())
            .behavior(Property.ofValue(Version.builder().before(afterFirstVersion.toString()).build()))
            .build();
        PurgeFiles.Output run = purgeFiles.run(runContext);

        assertThat(run.getSize()).isEqualTo(1L);

        namespaceFiles = namespaceStorage.find(Pageable.UNPAGED, Collections.emptyList(), true, FetchVersion.ALL);
        assertThat(namespaceFiles.size()).isEqualTo(4);
        List<NamespaceFile> files = namespaceFiles.stream().filter(nsFile -> nsFile.path().endsWith("file.txt")).toList();
        assertThat(files.size()).isEqualTo(1);
        assertThat(files.getFirst().version()).isEqualTo(2);
    }

    @Test
    void version_filter_by_keep_amount() throws Exception {
        String namespace = TestsUtils.randomNamespace();
        addNamespace(namespace);

        RunContext runContext = runContextFactory.of(namespace);
        Namespace namespaceStorage = runContext.storage().namespace(namespace);

        namespaceStorage.putFile(Path.of("my/first/file.txt"), new ByteArrayInputStream("some value".getBytes(StandardCharsets.UTF_8)));
        namespaceStorage.putFile(Path.of("my/first/file.txt"), new ByteArrayInputStream("another value".getBytes(StandardCharsets.UTF_8)));
        namespaceStorage.putFile(Path.of("my/first/file.txt"), new ByteArrayInputStream("yet another value".getBytes(StandardCharsets.UTF_8)));

        List<NamespaceFile> namespaceFiles = namespaceStorage.find(Pageable.UNPAGED, Collections.emptyList(), true, FetchVersion.ALL);
        assertThat(namespaceFiles.size()).isEqualTo(6);

        PurgeFiles purgeFiles = PurgeFiles.builder()
            .type(PurgeFiles.class.getName())
            .behavior(Property.ofValue(Version.builder().keepAmount(2).build()))
            .build();
        PurgeFiles.Output run = purgeFiles.run(runContext);

        assertThat(run.getSize()).isEqualTo(1L);

        namespaceFiles = namespaceStorage.find(Pageable.UNPAGED, Collections.emptyList(), true, FetchVersion.ALL);
        assertThat(namespaceFiles.size()).isEqualTo(5);
        List<NamespaceFile> files = namespaceFiles.stream().filter(nsFile -> nsFile.path().endsWith("file.txt")).toList();
        assertThat(files.size()).isEqualTo(2);
        assertThat(files.stream().map(NamespaceFile::version)).containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void validation() throws Exception {
        // valid
        assertThat(modelValidator.isValid(PurgeFiles.builder()
            .id(IdUtils.create())
            .type(PurgeFiles.class.getName())
            .behavior(Property.ofValue(Version.builder().before(Instant.now().toString()).build()))
            .build()).isPresent()).isFalse();
        assertThat(modelValidator.isValid(PurgeFiles.builder()
            .id(IdUtils.create())
            .type(PurgeFiles.class.getName())
            .behavior(Property.ofValue(Version.builder().keepAmount(2).build()))
            .build()).isPresent()).isFalse();

        // invalid
        Optional<ConstraintViolationException> invalid = modelValidator.isValid(PurgeFiles.builder()
            .id(IdUtils.create())
            .type(PurgeFiles.class.getName())
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
            .tasks(List.of(PurgeFiles.builder().type(PurgeFiles.class.getName()).build()))
            .build()));
    }

}
