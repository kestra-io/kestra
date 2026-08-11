package io.kestra.webserver.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.Label;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.SourceMatch;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.SourceSearchScope;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.RegexUtils;
import io.kestra.core.utils.SourceSearchMatcher;
import io.kestra.webserver.controllers.domain.IdWithNamespace;
import io.kestra.webserver.models.flows.SourceSearchReplaceApplyResponse;
import io.kestra.webserver.models.flows.SourceSearchReplaceApplyResponse.SkipReason;
import io.kestra.webserver.models.flows.SourceSearchReplaceApplyResponse.SkippedFlow;
import io.kestra.webserver.models.flows.SourceSearchReplacePreviewResponse;
import io.kestra.webserver.models.flows.SourceSearchResult;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SourceSearchService {

    private final FlowRepositoryInterface flowRepository;
    private final FlowService flowService;

    @Inject
    public SourceSearchService(FlowRepositoryInterface flowRepository, FlowService flowService) {
        this.flowRepository = Objects.requireNonNull(flowRepository);
        this.flowService = Objects.requireNonNull(flowService);
    }

    public ArrayListTotal<SourceSearchResult> search(
        Pageable pageable,
        String tenantId,
        @Nullable String namespace,
        @Nullable String query,
        boolean caseSensitive,
        boolean wholeWord,
        boolean regex,
        SourceSearchScope scope
    ) {
        SourceSearchMatcher.ensureSafeQuery(query, regex);

        return flowRepository
            .findSourceCode(pageable, query, caseSensitive, wholeWord, regex, scope, tenantId, namespace)
            .map(result -> new SourceSearchResult(
                result.getModel().getNamespace(),
                result.getModel().getId(),
                isEditable(result.getModel()),
                result.getMatches()
            ));
    }

    public SourceSearchReplacePreviewResponse preview(
        String tenantId,
        @Nullable String namespace,
        String query,
        boolean caseSensitive,
        boolean wholeWord,
        boolean regex,
        SourceSearchScope scope,
        String replacement
    ) {
        Pattern pattern = SourceSearchMatcher.toPattern(query, caseSensitive, wholeWord, regex);
        SourceSearchMatcher.ensureValidReplacement(pattern, replacement, regex);
        String effectiveReplacement = regex ? replacement : Matcher.quoteReplacement(replacement);

        List<SearchResult<Flow>> matched = flowRepository.findSourceCode(Pageable.UNPAGED, query, caseSensitive, wholeWord, regex, scope, tenantId, namespace);

        List<SourceSearchReplacePreviewResponse.FlowMatches> flows = new ArrayList<>(matched.size());
        int totalMatches = 0;
        int editableFlowCount = 0;
        for (SearchResult<Flow> result : matched) {
            boolean editable = isEditable(result.getModel());
            List<SourceSearchReplacePreviewResponse.Match> matches = replacementMatches(result.getMatches(), pattern, effectiveReplacement);
            flows.add(new SourceSearchReplacePreviewResponse.FlowMatches(result.getModel().getNamespace(), result.getModel().getId(), editable, matches));
            totalMatches += matches.size();
            if (editable) {
                editableFlowCount++;
            }
        }

        return new SourceSearchReplacePreviewResponse(totalMatches, flows.size(), editableFlowCount, flows);
    }

    public SourceSearchReplaceApplyResponse apply(
        String tenantId,
        String query,
        boolean caseSensitive,
        boolean wholeWord,
        boolean regex,
        SourceSearchScope scope,
        String replacement,
        List<IdWithNamespace> selection
    ) throws QueueException {
        Pattern pattern = SourceSearchMatcher.toPattern(query, caseSensitive, wholeWord, regex);
        SourceSearchMatcher.ensureValidReplacement(pattern, replacement, regex);
        String effectiveReplacement = regex ? replacement : Matcher.quoteReplacement(replacement);

        List<FlowWithSource> updated = new ArrayList<>();
        List<SkippedFlow> skipped = new ArrayList<>();

        for (IdWithNamespace ref : selection) {
            Optional<FlowWithSource> existing = flowRepository.findByIdWithSource(tenantId, ref.getNamespace(), ref.getId());
            if (existing.isEmpty()) {
                skipped.add(SkippedFlow.of(ref, SkipReason.NOT_FOUND));
                continue;
            }
            if (!isEditable(existing.get())) {
                skipped.add(SkippedFlow.of(ref, SkipReason.READ_ONLY));
                continue;
            }

            FlowWithSource current = existing.get();
            String newSource = SourceSearchMatcher.replaceWithinScope(current.getSource(), pattern, effectiveReplacement, scope);
            if (newSource.equals(current.getSource())) {
                skipped.add(SkippedFlow.of(ref, SkipReason.NO_CHANGE));
                continue;
            }

            try {
                GenericFlow genericFlow = GenericFlow.fromYaml(tenantId, newSource);
                updated.add(flowService.update(genericFlow, current));
            } catch (ConstraintViolationException | FlowProcessingException | DeserializationException e) {
                log.warn("Skipping flow {}.{} during Source Search replace: {}", ref.getNamespace(), ref.getId(), e.getMessage());
                skipped.add(SkippedFlow.of(ref, SkipReason.INVALID_FLOW, e.getMessage()));
            }
        }

        return new SourceSearchReplaceApplyResponse(updated, skipped);
    }

    public SourceSearchReplaceApplyResponse applyLine(
        String tenantId,
        String query,
        boolean caseSensitive,
        boolean wholeWord,
        boolean regex,
        String replacement,
        String namespace,
        String id,
        int line,
        int column
    ) throws QueueException {
        IdWithNamespace ref = new IdWithNamespace(namespace, id);
        Optional<FlowWithSource> existing = flowRepository.findByIdWithSource(tenantId, namespace, id);
        if (existing.isEmpty()) {
            return skippedOnly(ref, SkipReason.NOT_FOUND);
        }
        if (!isEditable(existing.get())) {
            return skippedOnly(ref, SkipReason.READ_ONLY);
        }

        FlowWithSource current = existing.get();
        String[] lines = current.getSource().split("\n", -1);
        if (line < 1 || line > lines.length) {
            return skippedOnly(ref, SkipReason.NO_MATCH);
        }

        String originalLine = lines[line - 1];
        if (column < 0 || column > originalLine.length()) {
            return skippedOnly(ref, SkipReason.NO_MATCH);
        }

        Pattern pattern = SourceSearchMatcher.toPattern(query, caseSensitive, wholeWord, regex);
        SourceSearchMatcher.ensureValidReplacement(pattern, replacement, regex);
        String effectiveReplacement = regex ? replacement : Matcher.quoteReplacement(replacement);

        Matcher matcher = RegexUtils.matcher(pattern, originalLine);
        if (!matcher.find(column) || matcher.start() != column) {
            return skippedOnly(ref, SkipReason.NO_MATCH);
        }

        StringBuilder builder = new StringBuilder();
        matcher.appendReplacement(builder, effectiveReplacement);
        matcher.appendTail(builder);
        String replacedLine = builder.toString();

        if (replacedLine.equals(originalLine)) {
            return skippedOnly(ref, SkipReason.NO_CHANGE);
        }
        lines[line - 1] = replacedLine;
        String newSource = String.join("\n", lines);

        try {
            GenericFlow genericFlow = GenericFlow.fromYaml(tenantId, newSource);
            return new SourceSearchReplaceApplyResponse(List.of(flowService.update(genericFlow, current)), List.of());
        } catch (ConstraintViolationException | FlowProcessingException | DeserializationException e) {
            log.warn("Skipping flow {}.{} line {} during Source Search replace: {}", namespace, id, line, e.getMessage());
            return skippedOnly(ref, SkipReason.INVALID_FLOW, e.getMessage());
        }
    }

    /**
     * Whether the caller may rewrite the given flow. OSS has no per-flow permission, so only the
     * {@code system.readOnly} label and soft deletion make a flow read-only; the Enterprise Edition
     * overrides this to also check {@code FLOW · UPDATE} and Git-synced namespaces.
     */
    protected boolean isEditable(FlowInterface flow) {
        return !flow.isDeleted() && !isFlaggedReadOnly(flow);
    }

    private static boolean isFlaggedReadOnly(FlowInterface flow) {
        return ListUtils.emptyOnNull(flow.getLabels()).stream()
            .anyMatch(label -> Label.READ_ONLY.equals(label.key()) && Boolean.parseBoolean(label.value()));
    }

    private static SourceSearchReplaceApplyResponse skippedOnly(IdWithNamespace ref, SkipReason reason) {
        return new SourceSearchReplaceApplyResponse(List.of(), List.of(SkippedFlow.of(ref, reason)));
    }

    private static SourceSearchReplaceApplyResponse skippedOnly(IdWithNamespace ref, SkipReason reason, String message) {
        return new SourceSearchReplaceApplyResponse(List.of(), List.of(SkippedFlow.of(ref, reason, message)));
    }

    private static List<SourceSearchReplacePreviewResponse.Match> replacementMatches(
        List<SourceMatch> matches,
        Pattern pattern,
        String effectiveReplacement
    ) {
        return matches.stream()
            .map(match -> {
                String before = stripMarkers(match.snippet());
                String after = RegexUtils.matcher(pattern, before).replaceAll(effectiveReplacement);
                return new SourceSearchReplacePreviewResponse.Match(match.line(), before, after);
            })
            .toList();
    }

    private static String stripMarkers(String snippet) {
        return snippet.replace("[mark]", "").replace("[/mark]", "");
    }
}
