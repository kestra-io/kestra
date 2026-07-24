package io.kestra.webserver.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.InvalidSourceSearchQueryException;
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
import io.kestra.core.utils.RegexUtils;
import io.kestra.core.utils.SourceSearchMatcher;
import io.kestra.webserver.controllers.domain.IdWithNamespace;
import io.kestra.webserver.models.flows.SourceSearchReplaceApplyResponse;
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
        List<SearchResult<Flow>> matched = flowRepository.findSourceCode(Pageable.UNPAGED, query, caseSensitive, wholeWord, regex, scope, tenantId, namespace);
        Pattern pattern = SourceSearchMatcher.toPattern(query, caseSensitive, wholeWord, regex);
        String effectiveReplacement = regex ? replacement : Matcher.quoteReplacement(replacement);

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
        String effectiveReplacement = regex ? replacement : Matcher.quoteReplacement(replacement);

        List<FlowWithSource> updated = new ArrayList<>();
        List<IdWithNamespace> skipped = new ArrayList<>();

        for (IdWithNamespace ref : selection) {
            Optional<FlowWithSource> existing = flowRepository.findByIdWithSource(tenantId, ref.getNamespace(), ref.getId());
            if (existing.isEmpty() || !isEditable(existing.get())) {
                skipped.add(ref);
                continue;
            }

            FlowWithSource current = existing.get();
            String newSource;
            try {
                newSource = SourceSearchMatcher.replaceWithinScope(current.getSource(), pattern, effectiveReplacement, scope);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                throw new InvalidSourceSearchQueryException(invalidReplacementMessage(e));
            }
            if (newSource.equals(current.getSource())) {
                skipped.add(ref);
                continue;
            }

            try {
                GenericFlow genericFlow = GenericFlow.fromYaml(tenantId, newSource);
                updated.add(flowService.update(genericFlow, current));
            } catch (ConstraintViolationException | FlowProcessingException | DeserializationException e) {
                log.warn("Skipping flow {}.{} during Source Search replace: {}", ref.getNamespace(), ref.getId(), e.getMessage());
                skipped.add(ref);
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
        if (existing.isEmpty() || !isEditable(existing.get())) {
            return new SourceSearchReplaceApplyResponse(List.of(), List.of(ref));
        }

        FlowWithSource current = existing.get();
        String[] lines = current.getSource().split("\n", -1);
        if (line < 1 || line > lines.length) {
            return new SourceSearchReplaceApplyResponse(List.of(), List.of(ref));
        }

        String originalLine = lines[line - 1];
        Pattern pattern = SourceSearchMatcher.toPattern(query, caseSensitive, wholeWord, regex);
        String effectiveReplacement = regex ? replacement : Matcher.quoteReplacement(replacement);

        Matcher matcher = RegexUtils.matcher(pattern, originalLine);
        if (column < 0 || !matcher.find(column) || matcher.start() != column) {
            return new SourceSearchReplaceApplyResponse(List.of(), List.of(ref));
        }

        String replacedLine;
        try {
            StringBuilder builder = new StringBuilder();
            matcher.appendReplacement(builder, effectiveReplacement);
            matcher.appendTail(builder);
            replacedLine = builder.toString();
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            throw new InvalidSourceSearchQueryException(invalidReplacementMessage(e));
        }

        if (replacedLine.equals(originalLine)) {
            return new SourceSearchReplaceApplyResponse(List.of(), List.of(ref));
        }
        lines[line - 1] = replacedLine;
        String newSource = String.join("\n", lines);

        try {
            GenericFlow genericFlow = GenericFlow.fromYaml(tenantId, newSource);
            return new SourceSearchReplaceApplyResponse(List.of(flowService.update(genericFlow, current)), List.of());
        } catch (ConstraintViolationException | FlowProcessingException | DeserializationException e) {
            log.warn("Skipping flow {}.{} line {} during Source Search replace: {}", namespace, id, line, e.getMessage());
            return new SourceSearchReplaceApplyResponse(List.of(), List.of(ref));
        }
    }

    protected boolean isEditable(FlowInterface flow) {
        return true;
    }

    private static List<SourceSearchReplacePreviewResponse.Match> replacementMatches(
        List<SourceMatch> matches,
        Pattern pattern,
        String effectiveReplacement
    ) {
        return matches.stream()
            .map(match -> {
                String before = stripMarkers(match.snippet());
                String after;
                try {
                    after = RegexUtils.matcher(pattern, before).replaceAll(effectiveReplacement);
                } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                    throw new InvalidSourceSearchQueryException(invalidReplacementMessage(e));
                }
                return new SourceSearchReplacePreviewResponse.Match(match.line(), before, after);
            })
            .toList();
    }

    private static String stripMarkers(String snippet) {
        return snippet.replace("[mark]", "").replace("[/mark]", "");
    }

    private static String invalidReplacementMessage(RuntimeException e) {
        return "Invalid replacement: " + e.getMessage();
    }
}
