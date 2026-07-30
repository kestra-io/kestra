import type {ScopeBinding} from "./types"
import {EXECUTION_PARENT_ROUTE} from "../../executions/executionTabs"
import {FLOW_PARENT_ROUTE} from "../../flows/flowTabs"
import {NAMESPACE_PARENT_ROUTE} from "../../../utils/namespaceTabRoutes"

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
 * The parent routes below only redirect (see their `*TabRoutes.ts`); the actually-matched
 * route is always one of their children, so match by prefix rather than exact name.
 */
export function scopeFromRoute(route: RouteLike | undefined | null): ScopeBinding | null {
    const name = typeof route?.name === "string" ? route.name : ""
    const params = route?.params

    if (name === EXECUTION_PARENT_ROUTE || name.startsWith(`${EXECUTION_PARENT_ROUTE}/`)) {
        return {
            kind: "EXECUTION",
            namespace: param(params, "namespace"),
            flowId: param(params, "flowId"),
            executionId: param(params, "id"),
        }
    }
    if (name === FLOW_PARENT_ROUTE || name.startsWith(`${FLOW_PARENT_ROUTE}/`)) {
        return {kind: "FLOW", namespace: param(params, "namespace"), flowId: param(params, "id")}
    }
    if (name === NAMESPACE_PARENT_ROUTE || name.startsWith(`${NAMESPACE_PARENT_ROUTE}/`)) {
        return {kind: "NAMESPACE", namespace: param(params, "id")}
    }
    return null
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
