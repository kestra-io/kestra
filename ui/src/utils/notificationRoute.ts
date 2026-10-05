import type {RouteParamsRawGeneric} from "vue-router"
import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"

import type {Notification} from "../stores/notifications"

export interface NotificationRoute {
    name: string;
    params: RouteParamsRawGeneric;
    query?: Record<string, string>;
}

export type RouteResolver = (notification: Notification) => NotificationRoute | null | Promise<NotificationRoute | null>

// Producers register an entry here when they introduce a concrete route target (e.g. an EE
// case-management feature, async-operation progress). Any unregistered type resolves to
// `null` (mark-read-only).
const NOTIFICATION_ROUTES: Record<string, RouteResolver> = {
    // Needs namespace+flowId (not in the notification); fetched directly, not via the store, so it never clobbers the execution the user is viewing.
    HUMAN_TASK_PENDING: resolveExecutionRoute,
    SYSTEM_EXECUTION_FAILED: resolveExecutionRoute,
    ASYNC_OPERATION: resolveAsyncOperationRoute,
}

async function resolveExecutionRoute(notification: Notification): Promise<NotificationRoute | null> {
    const execution = await ExecutionsAPI.execution({executionId: notification.referenceId as string}).catch(() => null)
    return execution ? {name: "executions/update", params: {namespace: execution.namespace, flowId: execution.flowId, id: execution.id}} : null
}

function resolveAsyncOperationRoute(notification: Notification): NotificationRoute | null {
    if (!notification.resourceType) {
        return null
    }

    const query = {"filters[operationId][EQUALS]": notification.referenceId as string}
    return notification.resourceType === "TRIGGER"
        ? {name: "admin/triggers", params: {tab: "manage"}, query}
        : {name: "executions/list", params: {}, query}
}

export function registerNotificationRoute(type: string, resolver: RouteResolver) {
    NOTIFICATION_ROUTES[type] = resolver
}

// Sync, type-only check (no lookups) — used by NotificationRow to style the referenceId as a
// link without triggering a network call on every rendered row.
export function isNotificationLinkable(type: string): boolean {
    return type in NOTIFICATION_ROUTES
}

export async function resolveNotificationRoute(notification: Notification): Promise<NotificationRoute | null> {
    if (notification.referenceId === null) {
        return null
    }

    const resolver = NOTIFICATION_ROUTES[notification.type]
    return resolver ? await resolver(notification) : null
}
