import {defineStore} from "pinia"
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

interface TriggerIdOptions {
    namespace: string;
    flowId: string;
    triggerId: string;
}

interface TriggerBulkOptions {
    [key: string]: any;
}

export interface TriggerDeleteOptions {
    id?: string;
    namespace: string;
    flowId: string;
    triggerId: string;
}

interface CreateBackfillOptions {
    namespace: string;
    flowId: string;
    triggerId: string;
    backfill: any;
}

interface TriggerDisabledOptions {
    namespace: string;
    flowId: string;
    triggerId: string;
    disabled: boolean;
    /** When true, missed schedules are recovered on enable according to the trigger's own configuration. */
    recoverMissedSchedules?: boolean;
}

/** Bulk trigger id list, as forwarded raw by callers (e.g. a data table's row selection). */
type TriggerIdList = TriggerIdOptions[];

export const useTriggerStore = defineStore("trigger", () => {
    async function search(options: TriggerSearchOptions) {
        const {sort, ...rest} = options
        return TriggersAPI.searchTriggers({...rest, sort: sort ? [sort] : undefined})
    }

    async function unlock(options: TriggerIdOptions) {
        return TriggersAPI.unlockTrigger(options)
    }

    async function restart(options: TriggerIdOptions) {
        return TriggersAPI.restartTrigger(options)
    }

    async function find(options: TriggerFindOptions) {
        const {sort, ...rest} = options
        return TriggersAPI.searchTriggersForFlow({...rest, sort: sort ? [sort] : undefined} as Parameters<typeof TriggersAPI.searchTriggersForFlow>[0])
    }

    async function pauseBackfill(options: TriggerIdOptions) {
        return TriggersAPI.pauseBackfill(options)
    }

    async function unpauseBackfill(options: TriggerIdOptions) {
        return TriggersAPI.unpauseBackfill(options)
    }

    async function deleteBackfill(options: TriggerIdOptions) {
        return TriggersAPI.deleteBackfill(options)
    }

    async function unlockByQuery(options: TriggerBulkOptions) {
        return TriggersAPI.unlockTriggersByQuery(options)
    }

    async function unlockByTriggers(triggers: TriggerIdList) {
        return TriggersAPI.unlockTriggersByIds({body: triggers})
    }

    async function unpauseBackfillByQuery(options: TriggerBulkOptions) {
        return TriggersAPI.unpauseBackfillByQuery(options)
    }

    async function unpauseBackfillByTriggers(triggers: TriggerIdList) {
        return TriggersAPI.unpauseBackfillByIds({body: triggers})
    }

    async function pauseBackfillByQuery(options: TriggerBulkOptions) {
        return TriggersAPI.pauseBackfillByQuery(options)
    }

    async function pauseBackfillByTriggers(triggers: TriggerIdList) {
        return TriggersAPI.pauseBackfillByIds({body: triggers})
    }

    async function deleteBackfillByQuery(options: TriggerBulkOptions) {
        return TriggersAPI.deleteBackfillByQuery(options)
    }

    async function deleteBackfillByTriggers(triggers: TriggerIdList) {
        return TriggersAPI.deleteBackfillByIds({body: triggers})
    }

    async function createBackfill(options: CreateBackfillOptions) {
        return TriggersAPI.createBackfill(options)
    }

    async function setDisabled(options: TriggerDisabledOptions) {
        return TriggersAPI.disableTriggerById(options)
    }

    async function setDisabledByQuery(options: TriggerBulkOptions) {
        return TriggersAPI.disabledTriggersByQuery(options)
    }

    async function setDisabledByTriggers(options: {triggers: TriggerIdList, disabled: boolean, recoverMissedSchedules?: boolean}) {
        return TriggersAPI.disabledTriggersByIds(options)
    }

    async function deleteTrigger(options: TriggerDeleteOptions) {
        return TriggersAPI.deleteTrigger(options)
    }

    async function deleteByQuery(options: TriggerBulkOptions) {
        return TriggersAPI.deleteTriggersByQuery(options)
    }

    async function deleteByTriggers(triggers: TriggerIdList) {
        return TriggersAPI.deleteTriggersByIds({body: triggers})
    }

    async function exportTriggersAsCSV(options: any) {
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

    return {
        search,
        find,
        pauseBackfill,
        unpauseBackfill,
        deleteBackfill,
        createBackfill,
        unlock,
        restart,
        setDisabled,
        unlockByQuery,
        unlockByTriggers,
        unpauseBackfillByQuery,
        unpauseBackfillByTriggers,
        pauseBackfillByQuery,
        pauseBackfillByTriggers,
        deleteBackfillByQuery,
        deleteBackfillByTriggers,
        setDisabledByQuery,
        setDisabledByTriggers,
        delete: deleteTrigger,
        deleteByQuery,
        deleteByTriggers,
        exportTriggersAsCSV,
    }
})
