import type {Router} from "vue-router"

export interface OpenFlowTarget {
    namespace: string
    flowId: string
    executionId?: string
    tab?: string
}

export function openFlowInNewTab(target: OpenFlowTarget, router: Router): void {
    const tenant = router.currentRoute.value.params.tenant

    const resolved = target.executionId
        ? router.resolve({
            name: "executions/update",
            params: {
                namespace: target.namespace,
                flowId: target.flowId,
                tab: target.tab ?? "topology",
                id: target.executionId,
                tenant,
            },
        })
        : router.resolve({
            name: "flows/update",
            params: {
                namespace: target.namespace,
                id: target.flowId,
                tab: target.tab ?? "overview",
                tenant,
            },
        })

    window.open(resolved.href, "_blank")
}
