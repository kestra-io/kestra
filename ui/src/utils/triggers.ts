import * as TriggersAPI from "@kestra-io/kestra-sdk/triggers"

interface TriggerSearchOptions {
    sort?: string;
    [key: string]: any;
}

interface TriggerFindOptions {
    namespace: string;
    flowId: string;
    [key: string]: any;
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

export async function exportTriggersAsCSV(options: any) {
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
