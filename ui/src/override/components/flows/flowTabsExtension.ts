import type {RouteRecordRaw} from "vue-router"

// No-op in OSS; the EE build overrides this file through the `override/` alias.
export function extraFlowTabRoutes(_parentRoute: string): RouteRecordRaw[] {
    return []
}

export function isFlowTabEnabled(_tabName: string, _ctx: {namespace: string | undefined}): boolean {
    return true
}
