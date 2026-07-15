package io.kestra.plugin.core.flow;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class Version {
    @Schema(
        title = "The date before which revisions should be purged.",
        description = "Must be an ISO-8601 instant, for example `2026-01-01T00:00:00Z`. Using this filter will never delete the latest revision or the latest non-draft revision of a flow to avoid accidental flow deletion."
    )
    private String before;

    @Schema(
        title = "How many revisions should be kept for each matching flow.",
        description = "The latest revision and the latest non-draft revision are always kept."
    )
    @Min(1)
    private Integer keepAmount;

    List<FlowWithSource> revisionsToPurge(String tenantId, String namespace, String flowId, FlowRepositoryInterface flowRepository) {
        List<FlowWithSource> revisions = flowRepository.findRevisions(tenantId, namespace, flowId, false);
        if (revisions.size() <= 1) {
            return List.of();
        }

        Integer latestRevision = revisions.stream()
            .map(FlowWithSource::getRevision)
            .max(Integer::compareTo)
            .orElse(null);
        Integer latestNonDraftRevision = revisions.stream()
            .filter(revision -> !revision.isDraft())
            .map(FlowWithSource::getRevision)
            .max(Integer::compareTo)
            .orElse(null);

        List<FlowWithSource> oldRevisions = revisions.stream()
            .filter(revision -> !revision.getRevision().equals(latestRevision))
            .filter(revision -> !revision.getRevision().equals(latestNonDraftRevision))
            .toList();

        if (keepAmount != null) {
            return revisions.stream()
                .sorted(Comparator.comparing(FlowWithSource::getRevision).reversed())
                .skip(keepAmount)
                .filter(revision -> !revision.getRevision().equals(latestRevision))
                .filter(revision -> !revision.getRevision().equals(latestNonDraftRevision))
                .toList();
        }

        if (before != null) {
            Instant beforeInstant = Instant.parse(before);
            return oldRevisions.stream()
                .filter(revision -> revision.getUpdated() != null && !revision.getUpdated().isAfter(beforeInstant))
                .toList();
        }

        return oldRevisions;
    }

    @AssertTrue(message = "Cannot set both 'before' and 'keepAmount' properties")
    boolean isValidPurgeConfiguration() {
        return before == null || keepAmount == null;
    }
}
