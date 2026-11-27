import {computed, type Component, type Ref} from "vue"
import type {RouteLocationRaw} from "vue-router"

// No-op in OSS. The EE build overrides this file (via the `override/` Vite alias) to
// surface execution relations — linked cases today — as items in the Overview banner
// meta row. Each component receives the `execution` prop and gates itself on its own
// feature flag / permissions.
export const executionBannerRelations: Component[] = []

export interface ApprovalLink {
    label: string;
    to: RouteLocationRaw;
}

// No-op in OSS; EE links the approval that backs the request, when there is one.
export function useApprovalLink(
    _execution: () => {id: string; namespace?: string},
    _taskRun: () => {id: string},
): Readonly<Ref<ApprovalLink | undefined>> {
    return computed(() => undefined)
}
