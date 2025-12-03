import {RouteLocationRaw} from "vue-router";
import {Execution} from "../../../../stores/executions";

type Types = "namespaces" | "flows" | "executions";

/**
 * Generates a Vue Router link object for a given execution and type.
 *
 * @param execution - The execution object containing tenantId, namespace, flowId, and id.
 * @param type - The type of route ("namespaces", "flows", or "executions").
 * @returns A RouteLocationRaw object to be used with router navigation.
 */
export const createLink = (
    execution: Execution,
    type: Types,
): RouteLocationRaw => {
    const params: Record<string, string> = {tab: "overview"};

    if (execution.tenantId) params.tenant = execution.tenantId;

    switch (type) {
        case "namespaces":
            params.id = execution.namespace;
            break;
        case "flows":
            params.namespace = execution.namespace;
            params.id = execution.flowId;
            break;
        case "executions":
            params.namespace = execution.namespace;
            params.flowId = execution.flowId;
            params.id = execution.id;
            break;
    }

    return {name: `${type}/update`, params};
};
