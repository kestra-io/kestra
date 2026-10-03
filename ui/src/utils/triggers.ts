import * as TriggersAPI from "@kestra-io/kestra-sdk/triggers"
import type {
    SearchTriggersData,
    SearchTriggersForFlowData,
} from "@kestra-io/kestra-sdk/openapi"
import type {LocationQuery} from "vue-router"

type TriggerSearchOptions = Omit<NonNullable<SearchTriggersData["query"]>, "sort"> & {
    sort?: string | null
}

type TriggerFindOptions = Omit<NonNullable<SearchTriggersForFlowData["query"]>, "sort"> & {
    namespace: string
    flowId: string
    sort?: string | null
}

export interface TriggerDeleteOptions {
    id?: string;
    namespace: string;
    flowId: string;
    triggerId: string;
}

export async function searchTriggers(options: TriggerSearchOptions) {
    const {sort, ...rest} = options
    return TriggersAPI.searchTriggers({...rest, sort: sort ? [sort] : undefined})
}

export async function searchTriggersForFlow(options: TriggerFindOptions) {
    const {sort, ...rest} = options
    return TriggersAPI.searchTriggersForFlow({...rest, sort: sort ? [sort] : undefined} as Parameters<typeof TriggersAPI.searchTriggersForFlow>[0])
}

export async function exportTriggersAsCSV(options: LocationQuery) {
    const response: unknown = await TriggersAPI.exportTriggers({
        filters: options.filters,
    }, {
        headers: {Accept: "text/csv"},
    })
    const url = window.URL.createObjectURL(new Blob([response as string], {type: "text/csv"}))
    const link = document.createElement("a")
    link.href = url
    link.setAttribute("download", "triggers.csv")
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
}
