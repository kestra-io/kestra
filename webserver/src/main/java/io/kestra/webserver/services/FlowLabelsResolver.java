package io.kestra.webserver.services;

import java.util.List;

import io.kestra.core.models.Label;
import io.kestra.core.models.flows.FlowInterface;

import jakarta.inject.Singleton;

/**
 * Resolves which of the labels a caller supplied apply to an execution of a given flow.
 *
 * <p>
 * A caller may override a label the flow declares, or add one of its own — that is the point of passing labels
 * when starting an execution. This service is the single place deciding when they may not, so that every route
 * accepting caller labels shares one rule, and the executor's creation path stays free of any such lookup.
 */
@Singleton
public class FlowLabelsResolver {

    /**
     * @param flow the flow the execution will run
     * @param labels the labels the caller supplied, already parsed
     * @return the labels to carry onto the execution; all of them here, editions may restrict
     */
    public List<Label> resolve(FlowInterface flow, List<Label> labels) {
        return labels;
    }
}
