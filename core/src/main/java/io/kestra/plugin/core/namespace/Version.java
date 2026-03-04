package io.kestra.plugin.core.namespace;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.validations.FilesVersionBehaviorValidation;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@SuperBuilder
@Getter
@NoArgsConstructor
@FilesVersionBehaviorValidation
public class Version extends FilesPurgeBehavior {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected String type = "version";

    @Schema(
        title = "The date before which versions should be purged.",
        description = "Using this filter will never delete the last version of a KV to avoid accidental full data loss."
    )
    private String before;

    @Schema(
        title = "How much versions should be kept for each matching KV.",
        description = "By default, every matching versions will be purged."
    )
    private Integer keepAmount;

    @Override
    protected List<NamespaceFile> entriesToPurge(String tenantId, Namespace namespaceStorage) {
        List<NamespaceFile> entries = namespaceStorage.find(
            Pageable.UNPAGED.withSort(Sort.of(Sort.Order.desc("version"))),
            before == null
                ? Collections.emptyList()
                : List.of(QueryFilter.builder().field(QueryFilter.Field.UPDATED).operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO).value(ZonedDateTime.parse(before)).build()),
            true,
            before == null ? FetchVersion.ALL : FetchVersion.OLD
        );

        if (keepAmount != null) {
            return entries.stream()
                .collect(Collectors.groupingBy(NamespaceFile::path)).values().stream()
                .flatMap(entriesForAKey -> entriesForAKey.stream().skip(keepAmount)).toList();
        }

        return entries;
    }
}
