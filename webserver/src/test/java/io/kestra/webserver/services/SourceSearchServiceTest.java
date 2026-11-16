package io.kestra.webserver.services;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.micronaut.data.model.Pageable;
import jakarta.validation.ConstraintViolationException;

import io.kestra.core.exceptions.InvalidSourceSearchQueryException;
import io.kestra.core.models.Label;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.SourceMatch;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.SourceSearchScope;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.SourceSearchMatcher;
import io.kestra.webserver.controllers.domain.IdWithNamespace;
import io.kestra.webserver.models.flows.KeyUsagesRequest;
import io.kestra.webserver.models.flows.KeyUsagesRequest.Key;
import io.kestra.webserver.models.flows.KeyUsagesResponse;
import io.kestra.webserver.models.flows.SourceSearchReplaceApplyResponse;
import io.kestra.webserver.models.flows.SourceSearchReplaceApplyResponse.SkipReason;
import io.kestra.webserver.models.flows.SourceSearchReplaceApplyResponse.SkippedFlow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceSearchServiceTest {

    private final FlowRepositoryInterface flowRepository = mock(FlowRepositoryInterface.class);
    private final FlowService flowService = mock(FlowService.class);

    @Test
    void shouldSkipFlowsTheCallerIsNotAllowedToEditWhenApplyingReplace() throws Exception {
        String tenantId = "main";
        IdWithNamespace ref = new IdWithNamespace("io.kestra.tests", "locked-flow");
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace(ref.getNamespace())
            .id(ref.getId())
            .source("id: locked-flow\nnamespace: io.kestra.tests\ndescription: legacy-value here\n")
            .build();
        when(flowRepository.findByIdWithSource(tenantId, ref.getNamespace(), ref.getId())).thenReturn(Optional.of(flow));

        SourceSearchService service = new SourceSearchService(flowRepository, flowService) {
            @Override
            protected boolean isEditable(FlowInterface flow) {
                return false;
            }
        };

        SourceSearchReplaceApplyResponse response = service.apply(
            tenantId, "legacy-value", false, false, false, null, "new-value", List.of(ref)
        );

        assertThat(response.updated()).isEmpty();
        assertThat(response.skipped()).containsExactly(SkippedFlow.of(ref, SkipReason.READ_ONLY));
        verify(flowService, never()).update(any(), any());
    }

    @Test
    void shouldSkipFlowsCarryingTheReadOnlySystemLabelWhenApplyingReplace() throws Exception {
        String tenantId = "main";
        IdWithNamespace ref = new IdWithNamespace("io.kestra.tests", "git-synced-flow");
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace(ref.getNamespace())
            .id(ref.getId())
            .labels(List.of(new Label(Label.READ_ONLY, "true")))
            .source("id: git-synced-flow\nnamespace: io.kestra.tests\ndescription: legacy-value here\n")
            .build();
        when(flowRepository.findByIdWithSource(tenantId, ref.getNamespace(), ref.getId())).thenReturn(Optional.of(flow));

        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        SourceSearchReplaceApplyResponse response = service.apply(
            tenantId, "legacy-value", false, false, false, null, "new-value", List.of(ref)
        );

        assertThat(response.updated()).isEmpty();
        assertThat(response.skipped()).containsExactly(SkippedFlow.of(ref, SkipReason.READ_ONLY));
        verify(flowService, never()).update(any(), any());
    }

    @Test
    void shouldSkipFlowsNotFoundWhenApplyingReplace() throws Exception {
        String tenantId = "main";
        IdWithNamespace ref = new IdWithNamespace("io.kestra.tests", "missing-flow");
        when(flowRepository.findByIdWithSource(tenantId, ref.getNamespace(), ref.getId())).thenReturn(Optional.empty());

        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        SourceSearchReplaceApplyResponse response = service.apply(
            tenantId, "legacy-value", false, false, false, null, "new-value", List.of(ref)
        );

        assertThat(response.updated()).isEmpty();
        assertThat(response.skipped()).containsExactly(SkippedFlow.of(ref, SkipReason.NOT_FOUND));
        verify(flowService, never()).update(any(), any());
    }

    @Test
    void shouldSkipFlowsLeftUnchangedByTheReplacement() throws Exception {
        String tenantId = "main";
        IdWithNamespace ref = new IdWithNamespace("io.kestra.tests", "untouched-flow");
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace(ref.getNamespace())
            .id(ref.getId())
            .source("id: untouched-flow\nnamespace: io.kestra.tests\ndescription: nothing to see\n")
            .build();
        when(flowRepository.findByIdWithSource(tenantId, ref.getNamespace(), ref.getId())).thenReturn(Optional.of(flow));

        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        SourceSearchReplaceApplyResponse response = service.apply(
            tenantId, "legacy-value", false, false, false, null, "new-value", List.of(ref)
        );

        assertThat(response.updated()).isEmpty();
        assertThat(response.skipped()).containsExactly(SkippedFlow.of(ref, SkipReason.NO_CHANGE));
        verify(flowService, never()).update(any(), any());
    }

    @Test
    void shouldSkipFlowsThatFailToSaveWhenApplyingReplace() throws Exception {
        String tenantId = "main";
        IdWithNamespace ref = new IdWithNamespace("io.kestra.tests", "unsavable-flow");
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace(ref.getNamespace())
            .id(ref.getId())
            .source("id: unsavable-flow\nnamespace: io.kestra.tests\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: legacy-value\n")
            .build();
        when(flowRepository.findByIdWithSource(tenantId, ref.getNamespace(), ref.getId())).thenReturn(Optional.of(flow));
        when(flowService.update(any(), any())).thenThrow(new ConstraintViolationException("Invalid type", Set.of()));

        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        SourceSearchReplaceApplyResponse response = service.apply(
            tenantId, "legacy-value", false, false, false, null, "new-value", List.of(ref)
        );

        assertThat(response.updated()).isEmpty();
        assertThat(response.skipped()).hasSize(1);
        assertThat(response.skipped().getFirst().reason()).isEqualTo(SkipReason.INVALID_FLOW);
        assertThat(response.skipped().getFirst().message()).contains("Invalid type");
    }

    @Test
    void shouldReplaceOnlyTheTargetLineWhenApplyingLineReplace() throws Exception {
        String tenantId = "main";
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.tests")
            .id("multi")
            .source("id: multi\nnamespace: io.kestra.tests\ntasks:\n  - id: a\n    type: io.kestra.plugin.core.log.Log\n    message: legacy-value\n  - id: b\n    type: io.kestra.plugin.core.log.Log\n    message: legacy-value\n")
            .build();
        when(flowRepository.findByIdWithSource(tenantId, "io.kestra.tests", "multi")).thenReturn(Optional.of(flow));
        when(flowService.update(any(), any())).thenReturn(flow);

        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        service.applyLine(tenantId, "legacy-value", false, false, false, "new-value", "io.kestra.tests", "multi", 6, 13);

        ArgumentCaptor<GenericFlow> captor = ArgumentCaptor.forClass(GenericFlow.class);
        verify(flowService).update(captor.capture(), any());
        String saved = captor.getValue().getSource();
        assertThat(saved).contains("    message: new-value\n");
        assertThat(saved).contains("    message: legacy-value\n");
    }

    @Test
    void shouldReplaceOnlyTheTargetedOccurrenceWhenMultipleMatchesOnSameLine() throws Exception {
        String tenantId = "main";
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.tests")
            .id("dup")
            .source("id: dup\nnamespace: io.kestra.tests\ntasks:\n  - id: a\n    type: io.kestra.plugin.core.log.Log\n    message: legacy-value legacy-value\n")
            .build();
        when(flowRepository.findByIdWithSource(tenantId, "io.kestra.tests", "dup")).thenReturn(Optional.of(flow));
        when(flowService.update(any(), any())).thenReturn(flow);

        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        // Line 6 is "    message: legacy-value legacy-value" - target only the second occurrence (column 26).
        service.applyLine(tenantId, "legacy-value", false, false, false, "new-value", "io.kestra.tests", "dup", 6, 26);

        ArgumentCaptor<GenericFlow> captor = ArgumentCaptor.forClass(GenericFlow.class);
        verify(flowService).update(captor.capture(), any());
        String saved = captor.getValue().getSource();
        assertThat(saved).contains("    message: legacy-value new-value\n");
    }

    @Test
    void shouldThrowInvalidSourceSearchQueryExceptionForBadBackreferenceWhenApplyingReplace() {
        String tenantId = "main";
        IdWithNamespace ref = new IdWithNamespace("io.kestra.tests", "flow");
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace(ref.getNamespace())
            .id(ref.getId())
            .source("id: flow\nnamespace: io.kestra.tests\ndescription: aaa\n")
            .build();
        when(flowRepository.findByIdWithSource(tenantId, ref.getNamespace(), ref.getId())).thenReturn(Optional.of(flow));

        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        assertThatThrownBy(() -> service.apply(tenantId, "(a)", false, false, true, null, "$9", List.of(ref)))
            .isInstanceOf(InvalidSourceSearchQueryException.class);
    }

    @Test
    void shouldThrowInvalidSourceSearchQueryExceptionForBadBackreferenceWhenApplyingLineReplace() {
        String tenantId = "main";
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.tests")
            .id("flow")
            .source("id: flow\nnamespace: io.kestra.tests\ndescription: aaa\n")
            .build();
        when(flowRepository.findByIdWithSource(tenantId, "io.kestra.tests", "flow")).thenReturn(Optional.of(flow));

        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        assertThatThrownBy(() -> service.applyLine(tenantId, "(a)", false, false, true, "$9", "io.kestra.tests", "flow", 3, 13))
            .isInstanceOf(InvalidSourceSearchQueryException.class);
    }

    @Test
    void shouldRejectRegexProneToCatastrophicBacktrackingWhenSearching() {
        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        assertThatThrownBy(() -> service.search(Pageable.UNPAGED, "main", null, "(a+)+$", false, false, true, SourceSearchScope.ALL))
            .isInstanceOf(InvalidSourceSearchQueryException.class)
            .hasMessageContaining("unsafe");

        verify(flowRepository, never()).findSourceCode(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    void shouldAcceptRegexProneToCatastrophicBacktrackingWhenSearchedAsALiteral() {
        SourceSearchService service = new SourceSearchService(flowRepository, flowService);
        when(flowRepository.findSourceCode(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any()))
            .thenReturn(ArrayListTotal.of(Pageable.UNPAGED, List.of()));

        assertThat(service.search(Pageable.UNPAGED, "main", null, "(a+)+$", false, false, false, SourceSearchScope.ALL)).isEmpty();
    }

    @Test
    void shouldSkipTheLineWhenTheColumnIsPastTheEndOfTheLine() throws Exception {
        String tenantId = "main";
        IdWithNamespace ref = new IdWithNamespace("io.kestra.tests", "flow");
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace(ref.getNamespace())
            .id(ref.getId())
            .source("id: flow\nnamespace: io.kestra.tests\ndescription: legacy-value\n")
            .build();
        when(flowRepository.findByIdWithSource(tenantId, ref.getNamespace(), ref.getId())).thenReturn(Optional.of(flow));

        SourceSearchService service = new SourceSearchService(flowRepository, flowService);

        SourceSearchReplaceApplyResponse response = service.applyLine(
            tenantId, "legacy-value", false, false, false, "new-value", ref.getNamespace(), ref.getId(), 3, 9999
        );

        assertThat(response.updated()).isEmpty();
        assertThat(response.skipped()).containsExactly(SkippedFlow.of(ref, SkipReason.NO_MATCH));
        verify(flowService, never()).update(any(), any());
    }

    @Test
    void shouldCountFlowsInTheTargetNamespaceAndItsDescendants() {
        // Given
        givenMatches("kv('k'", match("company", "a", "x: kv('k')"), match("company.team", "b", "x: kv('k')"), match("companyx", "c", "x: kv('k')"));
        givenMatches("kv(\"k\"");

        // When
        KeyUsagesResponse response = countKv(new Key("company", "k"));

        // Then
        assertThat(response.flows()).isEqualTo(2);
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void shouldCountFlowOutsideTheNamespaceWhenItsLineNamesTheNamespace() {
        // Given
        givenMatches("kv('k'", match("other", "a", "x: kv('k', 'company')"), match("other", "b", "x: kv('k')"));
        givenMatches("kv(\"k\"");

        // When
        KeyUsagesResponse response = countKv(new Key("company", "k"));

        // Then
        assertThat(response.flows()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreANamespaceMentionedWithoutQuotes() {
        // Given
        givenMatches("kv('k'", match("other", "a", "x: kv('k') # company"));
        givenMatches("kv(\"k\"");

        // When
        KeyUsagesResponse response = countKv(new Key("company", "k"));

        // Then
        assertThat(response.flows()).isZero();
    }

    @Test
    void shouldCountFlowsMatchedByEitherQuoteStyle() {
        // Given
        givenMatches("kv('k'", match("company", "single", "x: kv('k')"));
        givenMatches("kv(\"k\"", match("company", "double", "x: kv(\"k\")"));

        // When
        KeyUsagesResponse response = countKv(new Key("company", "k"));

        // Then
        assertThat(response.flows()).isEqualTo(2);
    }

    @Test
    void shouldKeepEveryFlowWhenTheKeyHasNoNamespace() {
        // Given
        givenMatches("secret('S'", match("a", "one", "x: secret('S')"), match("b", "two", "x: secret('S')"));
        givenMatches("secret(\"S\"");

        // When
        KeyUsagesResponse response = service().countKeyUsages("main", new KeyUsagesRequest(KeyUsagesRequest.Kind.SECRET, List.of(new Key(null, "S")))).getFirst();

        // Then
        assertThat(response.flows()).isEqualTo(2);
    }

    @Test
    void shouldCountADistinctFlowOnceAcrossLinesAndQueries() {
        // Given
        FlowMatch twoLines = match("company", "a", "x: kv('k')", "y: kv('k')");
        givenMatches("kv('k'", twoLines);
        givenMatches("kv(\"k\"", match("company", "a", "z: kv(\"k\")"));

        // When
        KeyUsagesResponse response = countKv(new Key("company", "k"));

        // Then
        assertThat(response.flows()).isEqualTo(1);
    }

    @Test
    void shouldReturnOneResponseByKeyInRequestOrder() {
        // Given
        givenMatches("kv('first'", match("company", "a", "x: kv('first')"));
        givenMatches("kv(\"first\"");
        givenMatches("kv('second'");
        givenMatches("kv(\"second\"");

        // When
        List<KeyUsagesResponse> responses = service().countKeyUsages("main", new KeyUsagesRequest(KeyUsagesRequest.Kind.KV, List.of(new Key("company", "first"), new Key("company", "second"))));

        // Then
        assertThat(responses).extracting(KeyUsagesResponse::key).containsExactly("first", "second");
        assertThat(responses).extracting(KeyUsagesResponse::flows).containsExactly(1, 0);
    }

    @Test
    void shouldFlagTruncationWhenTheCandidateCapIsReached() {
        // Given
        FlowMatch[] capped = new FlowMatch[SourceSearchMatcher.MAX_SOURCE_SEARCH_CANDIDATES];
        for (int i = 0; i < capped.length; i++) {
            capped[i] = match("company", "flow-" + i, "x: kv('k')");
        }
        givenMatches("kv('k'");
        givenMatches("kv(\"k\"", capped);

        // When
        KeyUsagesResponse response = countKv(new Key("company", "k"));

        // Then
        assertThat(response.truncated()).isTrue();
    }

    @Test
    void shouldRejectAKeyContainingANewline() {
        assertThatThrownBy(() -> countKv(new Key("company", "a\nb"))).isInstanceOf(InvalidSourceSearchQueryException.class);
    }

    private SourceSearchService service() {
        return new SourceSearchService(flowRepository, flowService);
    }

    private KeyUsagesResponse countKv(Key key) {
        return service().countKeyUsages("main", new KeyUsagesRequest(KeyUsagesRequest.Kind.KV, List.of(key))).getFirst();
    }

    private record FlowMatch(String namespace, String id, List<String> lines) {
    }

    private static FlowMatch match(String namespace, String id, String... lines) {
        return new FlowMatch(namespace, id, List.of(lines));
    }

    private void givenMatches(String query, FlowMatch... flows) {
        List<SearchResult<Flow>> results = java.util.Arrays.stream(flows)
            .map(flow -> {
                List<SourceMatch> matches = new java.util.ArrayList<>();
                for (int i = 0; i < flow.lines().size(); i++) {
                    matches.add(new SourceMatch(i + 1, 0, "[mark]" + flow.lines().get(i) + "[/mark]"));
                }
                Flow model = Flow.builder().tenantId("main").namespace(flow.namespace()).id(flow.id()).build();
                return new SearchResult<>(model, matches, true);
            })
            .toList();
        when(flowRepository.findSourceCode(eq(Pageable.UNPAGED), eq(query), eq(true), eq(false), eq(false), eq(SourceSearchScope.ALL), eq("main"), eq(null)))
            .thenReturn(new ArrayListTotal<>(results, results.size()));
    }
}
