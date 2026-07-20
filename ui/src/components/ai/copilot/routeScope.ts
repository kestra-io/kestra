import type {ScopeBinding} from "./types"

/** The subset of the current route the scope mapping reads. */
interface RouteLike {
    name?: string | symbol | null
    params?: Record<string, string | string[]>
}

/** First value of a route param (params can be string | string[]); undefined when absent/empty. */
function param(params: RouteLike["params"], key: string): string | undefined {
    const value = params?.[key]
    const first = Array.isArray(value) ? value[0] : value
    return first || undefined
}

/**
 * Derives the Copilot `inFocus` scope from the current route, so a turn started on a
 * flow / execution / namespace detail page carries that context to the agent. Returns
 * null on routes with no meaningful scope (list pages, home, settings, …).
 *
 * Route shapes (see `routes.ts`):
 *   - executions/update → /:tenant?/executions/:namespace/:flowId/:id
 *   - flows/update      → /:tenant?/flows/edit/:namespace/:id
 *   - namespaces/update → /:tenant?/namespaces/edit/:id
 */
export function scopeFromRoute(route: RouteLike | undefined | null): ScopeBinding | null {
    const name = typeof route?.name === "string" ? route.name : ""
    const params = route?.params

    switch (name) {
        case "executions/update":
            return {
                kind: "EXECUTION",
                namespace: param(params, "namespace"),
                flowId: param(params, "flowId"),
                executionId: param(params, "id"),
            }
        case "flows/update":
            return {kind: "FLOW", namespace: param(params, "namespace"), flowId: param(params, "id")}
        case "namespaces/update":
            return {kind: "NAMESPACE", namespace: param(params, "id")}
        default:
            return null
    }
}

/**
 * Converts a scope into the free-form `additionalContext` map sent on a chat turn (what the user is
 * currently viewing). Returns undefined when there's no scope, and omits absent fields.
 */
export function scopeToContext(scope: ScopeBinding | null | undefined): Record<string, unknown> | undefined {
    if (!scope) return undefined
    const currentView: Record<string, unknown> = {kind: scope.kind}
    if (scope.namespace) currentView.namespace = scope.namespace
    if (scope.flowId) currentView.flowId = scope.flowId
    if (scope.executionId) currentView.executionId = scope.executionId
    return {currentView}
}
