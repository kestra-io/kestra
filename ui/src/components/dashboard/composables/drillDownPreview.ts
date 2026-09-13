import type {RouteLocationRaw} from "vue-router"
import {useExecutionsStore} from "../../../stores/executions"
import {useFlowStore} from "../../../stores/flow"

export interface PreviewColumn {
    prop: string;
    label: string;
    type?: "status" | "date" | "labels";
}

export type PreviewConfig =
    | {
        mode: "table";
        columns: PreviewColumn[];
        fetch: (params: Record<string, any>) => Promise<{results: any[]; total: number}>;
        rowDetail: (row: any, tenant: string | undefined) => RouteLocationRaw;
    }
    | {mode: "logs"}
    | {mode: "none"} // explicit opt-out: no drawer preview, keep the full-page redirect

const previewRegistry: Record<string, PreviewConfig> = {
    "executions/list": {
        mode: "table",
        columns: [
            {prop: "id", label: "Id"},
            {prop: "namespace", label: "Namespace"},
            {prop: "flowId", label: "Flow"},
            {prop: "state.current", label: "State", type: "status"},
            {prop: "state.startDate", label: "Start Date", type: "date"},
        ],
        fetch: (params) => useExecutionsStore().findExecutions({...params, commit: false}),
        rowDetail: (row, tenant) => ({
            name: "executions/update",
            params: {tenant, namespace: row.namespace, flowId: row.flowId, id: row.id},
        }),
    },
    "flows/list": {
        mode: "table",
        columns: [
            {prop: "id", label: "Id"},
            {prop: "namespace", label: "Namespace"},
            {prop: "labels", label: "Labels", type: "labels"},
        ],
        fetch: (params) => useFlowStore().findFlows({...params, commit: false}),
        rowDetail: (row, tenant) => ({
            name: "flows/update",
            params: {tenant, namespace: row.namespace, id: row.id},
        }),
    },
    "logs/list": {mode: "logs"},
}

/**
 * Registers the drawer preview behavior for a dashboard drill-down route (keyed by route name,
 * same key as the `route` field registered via registerDrillDown). Lets editions declare how
 * their own drill-down targets render in the preview drawer, or opt out with `{mode: "none"}`
 * to keep today's full-page redirect.
 */
export function registerDrillDownPreview(routeName: string, config: PreviewConfig): void {
    previewRegistry[routeName] = config
}

export function getDrillDownPreview(routeName: string): PreviewConfig | undefined {
    return previewRegistry[routeName]
}
