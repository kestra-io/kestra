import {RouteLocationRaw} from "vue-router";
import {Execution} from "../../../../stores/executions";

type Types = "namespaces" | "flows" | "executions";

/**
 * Generates a Vue Router link object for a given execution and type.
 *
 * @param type - The type of route ("namespaces", "flows", or "executions").
 * @param execution - The execution object containing tenantId, namespace, flowId, and id.
 * @param customID - Optional ID to use instead of execution.id (only applies to "executions").
 * @returns A RouteLocationRaw object to be used with router navigation.
 */
export const createLink = (
    type: Types,
    execution: Execution,
    customID?: string,
): RouteLocationRaw => {
    if (!execution) return {};

    const params: Record<string, string> = {tab: "overview"};

    if (execution?.tenantId) params.tenant = execution.tenantId;

    switch (type) {
        case "namespaces":
            params.id = execution.namespace;
            break;
        case "flows":
            params.id = execution.flowId;
            params.namespace = execution.namespace;
            break;
        case "executions":
            params.id = customID ?? execution.id; // Use customID if provided, otherwise fallback to execution.id
            params.namespace = execution.namespace;
            params.flowId = execution.flowId;
            break;
    }

    return {name: `${type}/update`, params};
};
