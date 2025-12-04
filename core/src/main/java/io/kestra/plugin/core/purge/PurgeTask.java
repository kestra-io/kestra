package io.kestra.plugin.core.purge;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ListUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public interface PurgeTask<T> {
    Property<List<String>> getNamespaces();
    Property<String> getNamespacePattern();
    Property<Boolean> getIncludeChildNamespaces();
    Property<String> filterPattern();

    String filterTargetExtractor(T item);

    @VisibleForTesting
    default List<String> findNamespaces(RunContext runContext) throws IllegalVariableEvaluationException {
        String tenantId = runContext.flowInfo().tenantId();
        FlowRepositoryInterface flowRepositoryInterface = ((DefaultRunContext) runContext)
            .getApplicationContext().getBean(FlowRepositoryInterface.class);
        List<String> distinctNamespaces = flowRepositoryInterface.findDistinctNamespace(tenantId);
        List<String> renderedNamespaces = runContext.render(getNamespaces()).asList(String.class);
        String renderedNamespacePattern = runContext.render(getNamespacePattern()).as(String.class).orElse(null);

        if (!ListUtils.isEmpty(renderedNamespaces) && StringUtils.isNotBlank(renderedNamespacePattern)) {
            throw new ValidationErrorException(List.of("Properties `namespaces` and `namespacePattern` can't be used at the same time — use one or the other."));
        }

        List<String> filesNamespaces = new ArrayList<>();
        if (StringUtils.isNotBlank(renderedNamespacePattern)) {
            filesNamespaces.addAll(distinctNamespaces.stream()
                .filter(ns -> FilenameUtils.wildcardMatch(ns, renderedNamespacePattern))
                .toList());
        } else if (!renderedNamespaces.isEmpty()) {
            if (runContext.render(getIncludeChildNamespaces()).as(Boolean.class).orElse(true)) {
                filesNamespaces.addAll(distinctNamespaces.stream()
                    .filter(ns -> {
                        for (String renderedNamespace : renderedNamespaces) {
                            if (ns.startsWith(renderedNamespace)) {
                                return true;
                            }
                        }
                        return false;
                    }).toList());
            } else {
                filesNamespaces.addAll(distinctNamespaces.stream()
                    .filter(ns -> {
                        for (String renderedNamespace : renderedNamespaces) {
                            if (ns.equals(renderedNamespace)) {
                                return true;
                            }
                        }
                        return false;
                    }).toList());
            }
        } else {
            filesNamespaces.addAll(distinctNamespaces);
        }

        for (String ns : filesNamespaces) {
            runContext.acl().allowNamespace(ns).check();
        }
        return filesNamespaces;
    }

    default List<T> filterItems(RunContext runContext, List<T> items) throws IllegalVariableEvaluationException {
        String renderedFilterPattern = runContext.render(this.filterPattern()).as(String.class).orElse(null);
        if (StringUtils.isNotBlank(renderedFilterPattern)) {
            return items.stream()
                .filter(item -> FilenameUtils.wildcardMatch(filterTargetExtractor(item), renderedFilterPattern))
                .toList();
        }
        return items;
    }
}
