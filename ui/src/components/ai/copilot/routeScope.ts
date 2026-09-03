import type {ScopeBinding, ContextPart} from "./types"
import {EXECUTION_PARENT_ROUTE} from "../../executions/executionTabs"
import {FLOW_PARENT_ROUTE} from "../../flows/flowTabs"
import {NAMESPACE_PARENT_ROUTE} from "../../../utils/namespaceTabRoutes"

/**
 * i18n metadata for each scope field:
 *   - `keypath` + `slot`: the chip's pill label ("Flow: {flow}" / "Execution: {id}" / …), one slot each.
 *   - `noun`: the bare, lowercase type word ("flow", "namespace", …) under `ai.copilot.contextNoun`,
 *     used by the transcript's context-change notices ("Added {type} {id} to context.").
 */
export const CONTEXT_PART_I18N: Record<ContextPart, {keypath: string; slot: string; noun: string}> = {
    flowId: {keypath: "ai.copilot.context.flow", slot: "flow", noun: "ai.copilot.contextNoun.flow"},
    executionId: {keypath: "ai.copilot.context.execution", slot: "id", noun: "ai.copilot.contextNoun.execution"},
    dashboardId: {keypath: "ai.copilot.context.dashboard", slot: "dashboard", noun: "ai.copilot.contextNoun.dashboard"},
    appId: {keypath: "ai.copilot.context.app", slot: "app", noun: "ai.copilot.contextNoun.app"},
    testId: {keypath: "ai.copilot.context.test", slot: "test", noun: "ai.copilot.contextNoun.test"},
    blueprintId: {keypath: "ai.copilot.context.blueprint", slot: "blueprint", noun: "ai.copilot.contextNoun.blueprint"},
    pluginId: {keypath: "ai.copilot.context.plugin", slot: "plugin", noun: "ai.copilot.contextNoun.plugin"},
    namespace: {keypath: "ai.copilot.context.namespace", slot: "namespace", noun: "ai.copilot.contextNoun.namespace"},
}

/** The primary resource field for each scope kind — the pill shown first, and the one a navigation announces. */
export const CONTEXT_PRIMARY: Record<ScopeBinding["kind"], ContextPart> = {
    FLOW: "flowId",
    EXECUTION: "executionId",
    DASHBOARD: "dashboardId",
    APP: "appId",
    TEST: "testId",
    BLUEPRINT: "blueprintId",
    PLUGIN: "pluginId",
    NAMESPACE: "namespace",
}

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
    if (name === "flows/create") {
        return {kind: "FLOW"}
    }
    if (name === NAMESPACE_PARENT_ROUTE || name.startsWith(`${NAMESPACE_PARENT_ROUTE}/`)) {
        return {kind: "NAMESPACE", namespace: param(params, "id")}
    }
    // Leaf detail routes (no tabs). Dashboard + blueprint + plugin exist in OSS; app + test are
    // EE-only routes, so these names simply never match in OSS.
    if (name === "dashboards/update") {
        return {kind: "DASHBOARD", dashboardId: param(params, "dashboard")}
    }
    if (name === "apps/update") {
        return {kind: "APP", appId: param(params, "id")}
    }
    if (name === "tests/result") {
        return {kind: "TEST", namespace: param(params, "namespace"), testId: param(params, "testSuiteId")}
    }
    if (name === "blueprints/view") {
        return {kind: "BLUEPRINT", blueprintId: param(params, "blueprintId")}
    }
    if (name === "plugins/view") {
        return {kind: "PLUGIN", pluginId: param(params, "cls")}
    }
    return null
}

/**
 * Converts a scope into the free-form `additionalContext` map sent on a chat turn (what the user is
 * currently viewing). Returns undefined when there's no scope and no source, and omits absent fields.
 * `flowSource` is the flow editor's current buffer: the only place a new or edited-but-unsaved flow
 * exists, so the agent cannot read it through a tool (kestra-io/kestra-ee#10419).
 */
export function scopeToContext(scope: ScopeBinding | null | undefined, flowSource?: string): Record<string, unknown> | undefined {
    if (!scope && !flowSource) return undefined
    const currentView: Record<string, unknown> = {kind: scope?.kind ?? "FLOW"}
    if (scope?.namespace) currentView.namespace = scope.namespace
    if (scope?.flowId) currentView.flowId = scope.flowId
    if (scope?.executionId) currentView.executionId = scope.executionId
    if (scope?.dashboardId) currentView.dashboardId = scope.dashboardId
    if (scope?.appId) currentView.appId = scope.appId
    if (scope?.testId) currentView.testId = scope.testId
    if (scope?.blueprintId) currentView.blueprintId = scope.blueprintId
    if (scope?.pluginId) currentView.pluginId = scope.pluginId
    if (flowSource) currentView.flowSource = flowSource
    return {currentView}
}
