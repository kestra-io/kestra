import * as TriggersAPI from "@kestra-io/kestra-sdk/triggers"
import {downloadBlob} from "./utils"

interface TriggerSearchOptions {
    sort?: string;
    [key: string]: unknown;
}

interface TriggerFindOptions {
    namespace: string;
    flowId: string;
    [key: string]: unknown;
}

export interface TriggerDeleteOptions {
    id?: string;
    namespace: string;
    flowId: string;
    triggerId: string;
}

export interface TriggerExportOptions {
    filters?: unknown;
    [key: string]: unknown;
}

export async function searchTriggers(options: TriggerSearchOptions) {
    const {sort, ...rest} = options
    return TriggersAPI.searchTriggers({...rest, sort: sort ? [sort] : undefined} as Parameters<typeof TriggersAPI.searchTriggers>[0])
}

export async function searchTriggersForFlow(options: TriggerFindOptions) {
    const {sort, ...rest} = options
    return TriggersAPI.searchTriggersForFlow({...rest, sort: sort ? [sort] : undefined} as Parameters<typeof TriggersAPI.searchTriggersForFlow>[0])
}

export async function exportTriggersAsCSV(options: TriggerExportOptions) {
    const response: unknown = await TriggersAPI.exportTriggers({
        filters: options.filters as NonNullable<Parameters<typeof TriggersAPI.exportTriggers>[0]>["filters"],
    }, {
        headers: {Accept: "text/csv"},
    })
    downloadBlob(new Blob([response as string], {type: "text/csv"}), "triggers.csv")
}

export function blockerMessage(blocker: {reason?: string, message?: string}, t: (key: string) => string) {
    switch (blocker.reason) {
        case "POLICY":
            // message is always explicitly set on policy failure, fallback added just in case
            return blocker.message || t("trigger blocked unknown")
        case "POLICY_CHECK_FAILED":
            return t("trigger blocked policy check failed")
        default:
            return t("trigger blocked unknown")
    }
}
