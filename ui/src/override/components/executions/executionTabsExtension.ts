import type {Component} from "vue"
import type {RouteRecordRaw} from "vue-router"

// No-op in OSS; the EE build overrides this file through the `override/` alias.
export function extraExecutionTabRoutes(_parentRoute: string): RouteRecordRaw[] {
    return []
}

export function isExecutionTabEnabled(_tabName: string, _ctx: {namespace: string | undefined}): boolean {
    return true
}

/** Top bar action shown instead of the state action while the keyed tab is active. */
export const secondaryActionComponents: Record<string, Component> = {}
